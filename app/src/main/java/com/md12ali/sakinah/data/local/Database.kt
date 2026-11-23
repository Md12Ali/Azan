package com.md12ali.sakinah.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * A mosque's published times for one day, cached for offline use.
 * Times are "HH:mm" local strings (null = not published, e.g. no jamaat for Sunrise).
 */
@Entity(tableName = "timetable_day", primaryKeys = ["mosqueId", "date"])
data class TimetableDayEntity(
    val mosqueId: String,
    val date: String,              // ISO yyyy-MM-dd
    val fajr: String, val sunrise: String?, val dhuhr: String, val asr: String, val maghrib: String, val isha: String,
    val fajrJ: String?, val dhuhrJ: String?, val asrJ: String?, val maghribJ: String?, val ishaJ: String?,
    val source: String,            // e.g. "masjidbox:luton-central-mosque"
    val fetchedAt: Long
)

@Dao
interface TimetableDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<TimetableDayEntity>)

    @Query("SELECT * FROM timetable_day WHERE mosqueId = :mosqueId AND date = :date LIMIT 1")
    suspend fun day(mosqueId: String, date: String): TimetableDayEntity?

    @Query("SELECT COUNT(*) FROM timetable_day WHERE mosqueId = :mosqueId AND date >= :fromDate")
    suspend fun futureDays(mosqueId: String, fromDate: String): Int

    @Query("DELETE FROM timetable_day WHERE date < :beforeDate")
    suspend fun prune(beforeDate: String)
}

@Database(
    entities = [TimetableDayEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timetable(): TimetableDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "sakinah.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
