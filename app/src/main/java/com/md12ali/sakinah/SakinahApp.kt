package com.md12ali.sakinah

import android.app.Application
import android.content.Context
import com.md12ali.sakinah.alarm.DailyRefreshWorker
import com.md12ali.sakinah.audio.Notifications
import com.md12ali.sakinah.data.local.AppDatabase
import com.md12ali.sakinah.data.remote.TimetableFetcher
import com.md12ali.sakinah.data.repo.PrayerRepository
import com.md12ali.sakinah.data.settings.SettingsRepository
import com.md12ali.sakinah.location.LocationHelper

/** Simple manual dependency container – no Hilt/Dagger needed for an app this size. */
class AppContainer(context: Context) {
    val db = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val location = LocationHelper(context)
    val geo = com.md12ali.sakinah.location.GeoHelper(context)
    val fetcher = TimetableFetcher(context, db.timetable())
    val prayerRepository = PrayerRepository(settings, db.timetable(), fetcher)
}

class SakinahApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        DailyRefreshWorker.schedule(this)

    }
}
