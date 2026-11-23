package com.md12ali.sakinah.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.md12ali.sakinah.core.prayer.CalculationMethod
import com.md12ali.sakinah.core.prayer.Madhab
import com.md12ali.sakinah.core.prayer.Prayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * AUTO_GPS  – follow the nearest city's central mosque (its published timetable), recalculated as you travel.
 * MOSQUE    – always follow one chosen mosque (e.g. your home mosque even when away).
 * CITY_CALC – plain astronomical calculation for a chosen city (no mosque timetable).
 */
enum class LocationMode { AUTO_GPS, MOSQUE, CITY_CALC }

enum class AlertMode(val label: String) {
    ADHAN("Full Adhan"),
    SOFT_ADHAN("Soft Adhan"),
    GENTLE("Gentle notification"),
    SILENT("Silent")
}

data class AppSettings(
    val mode: LocationMode = LocationMode.AUTO_GPS,
    val mosqueId: String? = null,
    val cityName: String? = null,
    val cityLat: Double? = null,
    val cityLon: Double? = null,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val lastFixAt: Long = 0,
    val method: CalculationMethod = CalculationMethod.MOONSIGHTING_COMMITTEE,
    val madhab: Madhab = Madhab.HANAFI,
    val alerts: Map<Prayer, AlertMode> = defaultAlerts,
    val fajrSound: String = "adhan/fajr_soft.mp3",
    val mainSound: String = "adhan/adhan_full.mp3",
    val useAlarmVolume: Boolean = true,
    /** Adhan at the mosque's jamaat time instead of the begin time (only when the mosque publishes jamaat). */
    val alertAtJamaat: Boolean = false,
    /** mosqueId → "masjidbox:slug" | "mawaqit:slug" | "csv:https://…" – set by the user or remembered after discovery. */
    val mosqueSources: Map<String, String> = emptyMap(),
    val onboardingDone: Boolean = false
) {
    companion object {
        val defaultAlerts = mapOf(
            Prayer.FAJR to AlertMode.SOFT_ADHAN, Prayer.DHUHR to AlertMode.ADHAN, Prayer.ASR to AlertMode.ADHAN,
            Prayer.MAGHRIB to AlertMode.ADHAN, Prayer.ISHA to AlertMode.ADHAN
        )
    }
}

class SettingsRepository(private val context: Context) {

    private object K {
        val mode = stringPreferencesKey("mode")
        val mosqueId = stringPreferencesKey("mosque_id")
        val cityName = stringPreferencesKey("city_name")
        val cityLat = doublePreferencesKey("city_lat")
        val cityLon = doublePreferencesKey("city_lon")
        val lastLat = doublePreferencesKey("last_lat")
        val lastLon = doublePreferencesKey("last_lon")
        val lastFixAt = longPreferencesKey("last_fix_at")
        val method = stringPreferencesKey("method")
        val madhab = stringPreferencesKey("madhab")
        val fajrSound = stringPreferencesKey("fajr_sound")
        val mainSound = stringPreferencesKey("main_sound")
        val alarmVol = booleanPreferencesKey("alarm_volume")
        val atJamaat = booleanPreferencesKey("at_jamaat")
        val sources = stringPreferencesKey("mosque_sources")   // "id=src\nid=src"
        val onboarding = booleanPreferencesKey("onboarding_done")
        fun alert(p: Prayer) = stringPreferencesKey("alert_${p.name}")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            mode = this[K.mode]?.let { runCatching { LocationMode.valueOf(it) }.getOrNull() } ?: d.mode,
            mosqueId = this[K.mosqueId],
            cityName = this[K.cityName], cityLat = this[K.cityLat], cityLon = this[K.cityLon],
            lastLat = this[K.lastLat], lastLon = this[K.lastLon], lastFixAt = this[K.lastFixAt] ?: 0,
            method = this[K.method]?.let { runCatching { CalculationMethod.valueOf(it) }.getOrNull() } ?: d.method,
            madhab = this[K.madhab]?.let { runCatching { Madhab.valueOf(it) }.getOrNull() } ?: d.madhab,
            alerts = Prayer.adhanPrayers.associateWith { p ->
                this[K.alert(p)]?.let { runCatching { AlertMode.valueOf(it) }.getOrNull() } ?: d.alerts.getValue(p)
            },
            fajrSound = this[K.fajrSound] ?: d.fajrSound,
            mainSound = this[K.mainSound] ?: d.mainSound,
            useAlarmVolume = this[K.alarmVol] ?: d.useAlarmVolume,
            alertAtJamaat = this[K.atJamaat] ?: false,
            mosqueSources = this[K.sources].orEmpty().lines().filter { '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') },
            onboardingDone = this[K.onboarding] ?: false
        )
    }

    suspend fun setMode(mode: LocationMode) = context.dataStore.edit { it[K.mode] = mode.name }
    suspend fun setMosque(id: String) = context.dataStore.edit { it[K.mosqueId] = id; it[K.mode] = LocationMode.MOSQUE.name }
    suspend fun setCity(name: String, lat: Double, lon: Double) = context.dataStore.edit {
        it[K.cityName] = name; it[K.cityLat] = lat; it[K.cityLon] = lon; it[K.mode] = LocationMode.CITY_CALC.name
    }
    suspend fun setLastFix(lat: Double, lon: Double) = context.dataStore.edit {
        it[K.lastLat] = lat; it[K.lastLon] = lon; it[K.lastFixAt] = System.currentTimeMillis()
    }
    suspend fun setMethod(m: CalculationMethod) = context.dataStore.edit { it[K.method] = m.name }
    suspend fun setMadhab(m: Madhab) = context.dataStore.edit { it[K.madhab] = m.name }
    suspend fun setAlert(p: Prayer, mode: AlertMode) = context.dataStore.edit { it[K.alert(p)] = mode.name }
    suspend fun setFajrSound(path: String) = context.dataStore.edit { it[K.fajrSound] = path }
    suspend fun setMainSound(path: String) = context.dataStore.edit { it[K.mainSound] = path }
    suspend fun setUseAlarmVolume(v: Boolean) = context.dataStore.edit { it[K.alarmVol] = v }
    suspend fun setAlertAtJamaat(v: Boolean) = context.dataStore.edit { it[K.atJamaat] = v }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.onboarding] = true }

    suspend fun setMosqueSource(mosqueId: String, source: String?) = context.dataStore.edit { prefs ->
        val map = prefs[K.sources].orEmpty().lines().filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }.toMutableMap()
        if (source == null) map.remove(mosqueId) else map[mosqueId] = source
        prefs[K.sources] = map.entries.joinToString("\n") { "${it.key}=${it.value}" }
    }
}
