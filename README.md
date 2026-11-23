# Sakinah – Adhan & Prayer Times (Android)

A calm, 100% free Android app (Kotlin + Jetpack Compose) that plays the Adhan using the **published timetable of the nearest UK central mosque** to wherever you are. No ads, no paywall, no Google Play Services needed.

## How it works

```
GPS fix ──► nearest central mosque (within 25 km) ──► that mosque's own timetable
               │                                          │ Masjidbox / Mawaqit / mosque website / CSV
               │                                          ▼
               │                                  cached in Room (works offline)
               ▼                                          │
     no mosque nearby / nothing published ──► on-device calculation (matches AlAdhan to the minute)
                                                          ▼
                     AlarmManager exact alarm ──► foreground media service plays the Adhan (screen locked OK)
```

* **Auto (GPS)**, the default: in Luton it follows Luton Central Mosque's times. Drive to Birmingham and the next refresh (every 6 h, or tap ⟳) switches to Birmingham Central Mosque.
* **Chosen mosque**: keep your home mosque's times while you're away.
* **City**: plain calculation (Moonsighting Committee + Hanafi Asr by default, both changeable).
* **Per-prayer alert**: Full Adhan / Soft Adhan (default for Fajr) / Gentle notification / Silent. Tap the bell icons on the dashboard.
* **Adhan at jamaat time** (optional) when the mosque publishes jamaat times.

### Finding each mosque's timetable automatically
UK mosques don't share one common feed, so each mosque has a list of sources, tried in this order (`core/location/Places.kt`):

1. The source that worked last time (it's remembered).
2. Known sources. Luton Central Mosque → `masjidbox.com/prayer-times/luton-central-mosque` (checked: the page shows begin and jamaat times).
3. **Website discovery**: the app downloads the mosque's website and looks for Masjidbox, Mawaqit or CSV links.
4. **Name guesses** on Masjidbox and Mawaqit (`birmingham-central-mosque` and so on).
5. If none of these works, it calculates the times with UK-mosque offsets (+5 min Dhuhr, +3 min Maghrib).

On the **Mosque** tab you can tap 🔗 next to any mosque and paste its Masjidbox or Mawaqit page, its website, or a CSV link (a Google Sheet "published as CSV" works). To add more mosques, add one line in `Places.mosques`.

> Masjidbox and Mawaqit have no official public API. The parsers read the public page, and if a site changes its layout they fail safely: the app moves on to the next source and finally to calculation, so the Adhan never just stops.

## Build
1. Open the folder in **Android Studio** (Ladybug or newer) and let Gradle sync (AGP 8.7, Kotlin 2.1, compileSdk 35, minSdk 26).
2. Add your Adhan audio to `app/src/main/assets/adhan/`:
   * `fajr_soft.mp3`: calm Fajr Adhan
   * `adhan_full.mp3`: full Adhan for the other prayers
   * Any other `.mp3`, `.ogg` or `.m4a` files also show up in **Adhan → sound picker**. Until files are added, the phone's alarm tone plays instead. Only use recordings you have permission to share.
3. Run on a phone. Allow **Location**, **Notifications** and **Alarms & reminders**. On Samsung, Xiaomi or OnePlus also set battery use to *Unrestricted* (there's a button in the Adhan tab).

Run the core tests with `./gradlew test`. They check the calculation against api.aladhan.com for Luton (winter and summer), a full year of ordered times at 52°, 56° and 59° N, the CSV / Masjidbox / Mawaqit parsers, and nearest-mosque selection.

## Architecture (MVVM, single module)
```
core/            pure Kotlin, no Android, unit-tested
  prayer/        PrayerCalculator (offline astronomy, MCW seasonal Fajr/Isha), models
  timetable/     TimetableCsv, OnlineTimetableParsers (Masjidbox, Mawaqit, link discovery)
  location/      Places: UK central mosques, sources, nearest-mosque lookup
data/
  local/         Room: cached mosque timetable days
  settings/      DataStore: mode, mosque, alerts, sounds, method
  remote/        Http + TimetableFetcher (tries sources, caches the result)
  repo/          PrayerRepository: mosque timetable → cache → calculation
location/        LocationHelper (LocationManager, no Play Services)
alarm/           AdhanScheduler (exact alarms, rolling 48 h), receivers, DailyRefreshWorker
audio/           AdhanPlaybackService (fade-in, alarm stream, Stop action), Notifications
ui/              Compose: Prayer dashboard (countdown ring), Mosque selector, Adhan settings
```

### Android rules handled
* **Exact alarms**: `SCHEDULE_EXACT_ALARM` with a `canScheduleExactAlarms()` check. Without the permission, alarms fall back to `setAndAllowWhileIdle`. `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` triggers a reschedule.
* **Foreground service**: type `mediaPlayback` plus `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Android 14). It's started from the exact-alarm receiver, which Android allows as a background start.
* **Android 15**: the boot receiver only reschedules alarms and never starts the media service from `BOOT_COMPLETED`.
* Alarms are rescheduled after reboot, app update, clock or timezone change, after every Adhan, and every 6 h by WorkManager.

### Play Store note
`USE_EXACT_ALARM` (granted automatically) is limited by Google Play to alarm-clock and calendar apps, so this project uses `SCHEDULE_EXACT_ALARM` and asks the user once.

## Get the APK without Android Studio
Push this project to GitHub. The workflow in `.github/workflows/build-apk.yml` builds the app on GitHub's servers and publishes **Releases → Sakinah-Adhan-N.apk**. Open that link on your phone, tap the APK and allow "Install unknown apps". If a build fails, the **Actions** tab shows the error log.
