package com.orbit.launcher.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_preferences")
data class AppPreference(
    @PrimaryKey val key: String,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val customLabel: String? = null,
    val launches: Int = 0,
    val lastLaunched: Long = 0,
)

@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM app_preferences")
    fun observeAll(): Flow<List<AppPreference>>

    @Query("SELECT * FROM app_preferences WHERE `key` = :key")
    suspend fun find(key: String): AppPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(preference: AppPreference)
}

@Database(entities = [AppPreference::class], version = 1, exportSchema = false)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun preferences(): AppPreferenceDao
}
