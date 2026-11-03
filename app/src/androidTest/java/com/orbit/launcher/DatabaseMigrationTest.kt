package com.orbit.launcher

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.launcher.core.data.LauncherDatabase
import com.orbit.launcher.core.data.LauncherMigrations
import com.orbit.launcher.core.data.WidgetPlacement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "orbit-migration-test.db"

    @After fun cleanUp() { context.deleteDatabase(name) }

    @Test fun migrateVersionOneWithoutLosingPreferences() = runBlocking {
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `app_preferences` (`key` TEXT NOT NULL, `pinned` INTEGER NOT NULL, `hidden` INTEGER NOT NULL, `customLabel` TEXT, `launches` INTEGER NOT NULL, `lastLaunched` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("INSERT INTO `app_preferences` VALUES ('app-key', 1, 0, 'Custom', 4, 1234)")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, LauncherDatabase::class.java, name)
            .addMigrations(*LauncherMigrations.all).build()
        try {
            val app = database.preferences().find("app-key")!!
            assertEquals("Custom", app.customLabel)
            assertEquals(4, app.launches)
            assertFalse(app.searchOnly)
            assertNull(app.groupName)
            assertNull(app.customIconUri)
            database.widgets().put(WidgetPlacement(42, 220, "pkg/pkg.Widget"))
            assertEquals(42, database.widgets().observeAll().first().single().widgetId)
            assertEquals(0, database.widgets().observeAll().first().single().widthDp)
        } finally {
            database.close()
        }
    }
}
