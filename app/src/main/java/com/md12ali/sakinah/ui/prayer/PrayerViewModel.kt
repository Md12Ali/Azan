package com.md12ali.sakinah.ui.prayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.md12ali.sakinah.SakinahApp
import com.md12ali.sakinah.alarm.AdhanScheduler
import com.md12ali.sakinah.core.location.City
import com.md12ali.sakinah.core.location.MosqueProfile
import com.md12ali.sakinah.core.location.NearbyMosque
import com.md12ali.sakinah.core.prayer.CalculationMethod
import com.md12ali.sakinah.core.prayer.Madhab
import com.md12ali.sakinah.core.prayer.Prayer
import com.md12ali.sakinah.core.prayer.PrayerDay
import com.md12ali.sakinah.data.remote.TimetableFetcher
import com.md12ali.sakinah.data.repo.ActivePlace
import com.md12ali.sakinah.data.settings.AlertMode
import com.md12ali.sakinah.data.settings.AppSettings
import com.md12ali.sakinah.data.settings.LocationMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PrayerUiState(
    val today: PrayerDay? = null,
    val tomorrow: PrayerDay? = null,
    val place: ActivePlace? = null,
    val settings: AppSettings = AppSettings(),
    val now: ZonedDateTime = ZonedDateTime.now(),
    val loading: Boolean = true,
    val busyMessage: String? = null,
    val message: String? = null,
    val exactAlarmAllowed: Boolean = true,
    val hijri: String = ""
) {
    /** Next Adhan prayer (rolls into tomorrow's Fajr after Isha). */
    val next: Pair<Prayer, ZonedDateTime>?
        get() = today?.nextAfter(now, adhanOnly = true) ?: tomorrow?.begins?.get(Prayer.FAJR)?.let { Prayer.FAJR to it }

    /** Previous Adhan time – used to draw the progress ring between prayers. */
    val previous: ZonedDateTime?
        get() = today?.begins?.filter { it.key.hasAdhan && !it.value.isAfter(now) }?.values?.maxOrNull()
            ?: today?.begins?.get(Prayer.ISHA)?.minusDays(1)
}

class PrayerViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SakinahApp).container
    private val _state = MutableStateFlow(PrayerUiState())
    val state: StateFlow<PrayerUiState> = _state.asStateFlow()

    data class NearbyUi(
        val loading: Boolean = false,
        val mosques: List<NearbyMosque> = emptyList(),
        val error: String? = null,
        val searched: Boolean = false,
        /** GPS accuracy of the search position in metres (null = not from a fresh GPS fix). */
        val accuracyM: Int? = null,
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    private val _nearby = MutableStateFlow(NearbyUi())
    val nearby: StateFlow<NearbyUi> = _nearby.asStateFlow()

    init {
        viewModelScope.launch { c.settings.settings.collect { s -> _state.update { it.copy(settings = s) } } }
        viewModelScope.launch {
            // 1-second ticker for the countdown; reload at midnight.
            var lastDate = LocalDate.now()
            while (true) {
                val now = ZonedDateTime.now()
                _state.update { it.copy(now = now) }
                if (now.toLocalDate() != lastDate) { lastDate = now.toLocalDate(); reload() }
                delay(1000)
            }
        }
        refresh(fetchGps = true)
    }

    /** Recompute from cache/calculation (instant, offline). */
    fun reload() = viewModelScope.launch {
        val date = LocalDate.now()
        val (today, place) = c.prayerRepository.day(date)
        val (tomorrow, _) = c.prayerRepository.day(date.plusDays(1))
        _state.update {
            it.copy(
                today = today, tomorrow = tomorrow, place = place, loading = false,
                exactAlarmAllowed = AdhanScheduler.canScheduleExact(getApplication()),
                hijri = hijriLabel(date)
            )
        }
        AdhanScheduler.rescheduleAll(getApplication())
    }

    /** Full refresh: GPS (if Auto) → nearest central mosque → download its timetable → reload. */
    fun refresh(fetchGps: Boolean = false, forceDownload: Boolean = false) = viewModelScope.launch {
        reload().join()
        val s = c.settings.current()
        if (fetchGps && s.mode == LocationMode.AUTO_GPS && c.location.hasPermission()) {
            _state.update { it.copy(busyMessage = "Finding your location…") }
            c.location.currentLocation()?.let { loc ->
                c.settings.setLastFix(loc.latitude, loc.longitude)
                reload().join()
                // Country + town name → right calculation method for wherever you are in the world.
                runCatching { c.geo.reverse(loc.latitude, loc.longitude) }.getOrNull()?.let { info ->
                    c.settings.setGpsPlace(info.countryCode, info.locality)
                }
                autoPickNearestMosque(loc.latitude, loc.longitude)
            }
            reload().join()
        }
        val mosque = c.prayerRepository.activePlace().mosque
        if (mosque != null) {
            _state.update { it.copy(busyMessage = "Checking ${mosque.name} timetable…") }
            val src = runCatching { c.prayerRepository.refreshTimetableIfNeeded(force = forceDownload) }.getOrNull()
            if (forceDownload) {
                _state.update {
                    it.copy(message = if (src != null) "Timetable updated from ${TimetableFetcher.describe(src)}"
                    else "Couldn't find an online timetable for ${mosque.name}. Using calculated times – you can attach a link in Location.")
                }
            }
        }
        _state.update { it.copy(busyMessage = null) }
        reload()
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) refresh(fetchGps = true)
        else _state.update { it.copy(message = "Location off – choose your city or mosque manually.") }
    }

    fun useAutoGps() = viewModelScope.launch { c.settings.setMode(LocationMode.AUTO_GPS); refresh(fetchGps = true) }
    fun chooseMosque(m: MosqueProfile) = viewModelScope.launch { c.settings.setMosque(m.id); refresh() }
    fun chooseCity(city: City) = viewModelScope.launch {
        c.settings.setCity(city.name, city.latitude, city.longitude, city.countryCode); refresh()
    }

    private val _search = MutableStateFlow<List<City>>(emptyList())
    val searchResults: StateFlow<List<City>> = _search.asStateFlow()
    private var searchJob: kotlinx.coroutines.Job? = null
    /** Worldwide place search (debounced). */
    fun search(q: String) {
        searchJob?.cancel()
        if (q.trim().length < 2) { _search.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(450)
            _search.value = c.geo.search(q)
        }
    }

    /** Downloads the real Adhan recordings now (also happens automatically in the background). */
    fun downloadAdhan() = viewModelScope.launch {
        _state.update { it.copy(busyMessage = "Downloading Adhan recordings…") }
        val n = com.md12ali.sakinah.audio.AdhanAudio.ensureDownloaded(getApplication())
        _state.update {
            it.copy(busyMessage = null, message = if (n > 0) "$n Adhan recordings ready – they now play offline."
            else "Couldn't download the Adhan recordings. Check your internet and try again.")
        }
    }
    fun setAlert(p: Prayer, mode: AlertMode) = viewModelScope.launch { c.settings.setAlert(p, mode); reload() }
    fun cycleAlert(p: Prayer) {
        val cur = _state.value.settings.alerts[p] ?: AlertMode.ADHAN
        setAlert(p, AlertMode.entries[(cur.ordinal + 1) % AlertMode.entries.size])
    }
    /** null = automatic (by country). */
    fun setMethod(m: CalculationMethod?) = viewModelScope.launch { c.settings.setMethod(m); reload() }
    fun setMadhab(m: Madhab?) = viewModelScope.launch { c.settings.setMadhab(m); refresh(forceDownload = true) }
    fun setAlertAtJamaat(v: Boolean) = viewModelScope.launch { c.settings.setAlertAtJamaat(v); reload() }
    fun setFullScreenAdhan(v: Boolean) = viewModelScope.launch { c.settings.setFullScreenAdhan(v); reload() }
    fun setUseAlarmVolume(v: Boolean) = viewModelScope.launch { c.settings.setUseAlarmVolume(v) }
    fun setFajrSound(p: String) = viewModelScope.launch { c.settings.setFajrSound(p) }
    fun setMainSound(p: String) = viewModelScope.launch { c.settings.setMainSound(p) }

    /** User pastes a Masjidbox / Mawaqit / CSV / website link for a mosque. */
    fun attachTimetableLink(mosque: MosqueProfile, link: String) = viewModelScope.launch {
        val src = TimetableFetcher.decode(link)
        if (src == null) { _state.update { it.copy(message = "That link wasn't recognised.") }; return@launch }
        c.settings.setMosqueSource(mosque.id, TimetableFetcher.encode(src))
        refresh(forceDownload = true)
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ------------------------------------------------------------------ nearby mosques (worldwide)


    /** GPS → mosques around you (Mawaqit timetables + every mosque on OpenStreetMap), sorted by distance. */
    fun findNearby(force: Boolean = false) = viewModelScope.launch {
        _nearby.update { it.copy(loading = true, error = null) }
        val loc = if (c.location.hasPermission()) c.location.preciseLocation() else null
        val lat = loc?.latitude ?: c.settings.current().lastLat ?: _state.value.place?.latitude
        val lon = loc?.longitude ?: c.settings.current().lastLon ?: _state.value.place?.longitude
        if (loc != null) c.settings.setLastFix(loc.latitude, loc.longitude)
        if (lat == null || lon == null) {
            _nearby.update { it.copy(loading = false, searched = true, error = "Turn on location so the app can find mosques near you.") }
            return@launch
        }
        val list = runCatching { c.nearby.find(lat, lon, force, myMosques()) }.getOrDefault(emptyList())
        _nearby.update {
            it.copy(loading = false, searched = true, mosques = list, latitude = lat, longitude = lon,
                accuracyM = loc?.takeIf { l -> l.hasAccuracy() }?.accuracy?.toInt(),
                error = if (list.isEmpty()) "No mosques found nearby – check your internet connection and try again." else null)
        }
    }

    /** Follow a found mosque: its own timetable if it publishes one, otherwise calculated for its location. */
    fun followMosque(m: NearbyMosque) = viewModelScope.launch {
        val centralId = m.id.removePrefix("central_")
        if (m.id.startsWith("central_") && com.md12ali.sakinah.core.location.Places.mosqueById(centralId) != null) {
            c.settings.setMosque(centralId)
        } else {
            c.settings.followFoundMosque(m.id, m.encode())
        }
        refresh(forceDownload = true).join()
        ensureTodayFrom(m)
        reload()
    }

    private suspend fun myMosques(): List<NearbyMosque> =
        c.settings.current().myMosques.mapNotNull { NearbyMosque.decode(it) }

    /**
     * Add a mosque from Google Maps: the user shares the place (Share → Azan) or pastes its link.
     * The exact pin and name are read from the link; the mosque is saved and followed for the Azan.
     */
    fun addMosqueFromGoogleMaps(text: String) = viewModelScope.launch {
        _nearby.update { it.copy(loading = true, error = null) }
        val m = runCatching { c.nearby.fromGoogleMapsText(text) }.getOrNull()
        if (m == null) {
            _nearby.update { it.copy(loading = false, error = "Couldn't read that link. In Google Maps open the mosque, tap Share → Copy link, then paste it here.") }
            _state.update { it.copy(message = "Couldn't read that Google Maps link – please try Share → Copy link again.") }
            return@launch
        }
        c.settings.addMyMosque(m.encode(), m.id)
        followMosque(m).join()
        _state.update { it.copy(message = "Added ${m.name} – the Azan now follows this mosque.") }
        findNearby(force = true)
    }

    fun removeMyMosque(m: NearbyMosque) = viewModelScope.launch {
        c.settings.removeMyMosque(m.id)
        findNearby(force = true)
    }

    fun setFollowNearest(v: Boolean) = viewModelScope.launch {
        c.settings.setFollowNearest(v)
        if (!v) c.settings.setAutoMosque(null)
        refresh(fetchGps = true)
    }

    /** Auto mode: outside the curated UK central mosques, follow the nearest mosque that publishes a timetable. */
    private suspend fun autoPickNearestMosque(lat: Double, lon: Double) {
        val s = c.settings.current()
        if (!s.followNearestMosque) return
        if (com.md12ali.sakinah.core.location.Places.nearestCentralMosque(lat, lon) != null) return
        val current = NearbyMosque.decode(s.autoMosque)
        if (current != null && com.md12ali.sakinah.core.location.Places.distanceKm(lat, lon, current.latitude, current.longitude) < 3.0) return
        val list = runCatching { c.nearby.find(lat, lon, saved = myMosques()) }.getOrDefault(emptyList())
        _nearby.update { it.copy(mosques = list, searched = true) }
        val pick = list.firstOrNull { it.mawaqitSlug != null && it.distanceKm <= 10.0 }
        c.settings.setAutoMosque(pick?.encode())
        if (pick != null) {
            runCatching { c.prayerRepository.refreshTimetableIfNeeded(force = true) }
            ensureTodayFrom(pick)
        }
    }

    private suspend fun ensureTodayFrom(m: NearbyMosque) {
        val (day, _) = c.prayerRepository.day(LocalDate.now())
        if (day.source != com.md12ali.sakinah.core.prayer.TimeSource.MOSQUE_TIMETABLE && m.todayTimes.isNotEmpty()) {
            c.nearby.saveTodayFromSearch(m)
        }
    }

    private fun hijriLabel(date: LocalDate): String = runCatching {
        // Umm al-Qura calendar – UK moonsighting can differ by a day; shown as a guide only.
        HijrahDate.from(date).format(DateTimeFormatter.ofPattern("d MMMM y", Locale.ENGLISH)) + " AH"
    }.getOrDefault("")
}
