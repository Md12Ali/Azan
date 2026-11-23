package com.md12ali.sakinah.ui.prayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.md12ali.sakinah.SakinahApp
import com.md12ali.sakinah.alarm.AdhanScheduler
import com.md12ali.sakinah.core.location.City
import com.md12ali.sakinah.core.location.MosqueProfile
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

    private fun hijriLabel(date: LocalDate): String = runCatching {
        // Umm al-Qura calendar – UK moonsighting can differ by a day; shown as a guide only.
        HijrahDate.from(date).format(DateTimeFormatter.ofPattern("d MMMM y", Locale.ENGLISH)) + " AH"
    }.getOrDefault("")
}
