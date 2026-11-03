package com.orbit.launcher.core.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_preferences")
data class AppPreference(
    @PrimaryKey val key: String,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val customLabel: String? = null,
    val launches: Int = 0,
    val lastLaunched: Long = 0,
    @ColumnInfo(defaultValue = "0") val searchOnly: Boolean = false,
    val groupName: String? = null,
    val customIconUri: String? = null,
)

@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM app_preferences")
    fun observeAll(): Flow<List<AppPreference>>

    @Query("SELECT * FROM app_preferences WHERE `key` = :key")
    suspend fun find(key: String): AppPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(preference: AppPreference)

    @Query("SELECT * FROM app_preferences")
    suspend fun all(): List<AppPreference>

    @Query("DELETE FROM app_preferences")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(preferences: List<AppPreference>)

    @Transaction
    suspend fun replaceAll(preferences: List<AppPreference>) {
        clear()
        putAll(preferences)
    }
}

@Entity(tableName = "widgets")
data class WidgetPlacement(
    @PrimaryKey val widgetId: Int,
    val heightDp: Int = 180,
    val providerName: String? = null,
)

@Dao
interface WidgetDao {
    @Query("SELECT * FROM widgets ORDER BY widgetId")
    fun observeAll(): Flow<List<WidgetPlacement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(widget: WidgetPlacement)

    @Query("DELETE FROM widgets WHERE widgetId = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM widgets ORDER BY widgetId")
    suspend fun all(): List<WidgetPlacement>

    @Query("DELETE FROM widgets")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(widgets: List<WidgetPlacement>)

    @Transaction
    suspend fun replaceAll(widgets: List<WidgetPlacement>) { clear(); putAll(widgets) }

    @Transaction
    suspend fun replacePlaceholder(placeholderId: Int, widget: WidgetPlacement) { delete(placeholderId); put(widget) }
}

@Entity(tableName = "usage_context")
data class UsageContextEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appKey: String,
    val hour: Int,
    val weekday: Int,
    val charging: Boolean,
    val headphones: Boolean,
    val timestamp: Long,
    val locationCategory: String? = null,
)

@Dao
interface UsageContextDao {
    @Query("SELECT * FROM usage_context ORDER BY id DESC LIMIT 1000")
    fun observeRecent(): Flow<List<UsageContextEntity>>
    @Insert suspend fun insert(event: UsageContextEntity)
    @Query("DELETE FROM usage_context WHERE id NOT IN (SELECT id FROM usage_context ORDER BY id DESC LIMIT 1000)")
    suspend fun prune()
    @Query("DELETE FROM usage_context") suspend fun clear()
    @Query("UPDATE usage_context SET locationCategory = NULL") suspend fun clearLocations()
}

@Database(entities = [AppPreference::class, WidgetPlacement::class, UsageContextEntity::class], version = 7, exportSchema = false)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun preferences(): AppPreferenceDao
    abstract fun widgets(): WidgetDao
    abstract fun usageContext(): UsageContextDao
}
