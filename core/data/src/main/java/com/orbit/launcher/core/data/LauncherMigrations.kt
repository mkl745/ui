package com.orbit.launcher.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Explicit migrations preserve existing user preferences through every released schema. */
object LauncherMigrations {
    val all: Array<Migration> = arrayOf(
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `widgets` (`widgetId` INTEGER NOT NULL, `heightDp` INTEGER NOT NULL, PRIMARY KEY(`widgetId`))")
            }
        },
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `app_preferences` ADD COLUMN `searchOnly` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `app_preferences` ADD COLUMN `groupName` TEXT")
            }
        },
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `usage_context` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `appKey` TEXT NOT NULL, `hour` INTEGER NOT NULL, `weekday` INTEGER NOT NULL, `charging` INTEGER NOT NULL, `headphones` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        },
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `app_preferences` ADD COLUMN `customIconUri` TEXT")
            }
        },
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `widgets` ADD COLUMN `providerName` TEXT")
            }
        },
        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `usage_context` ADD COLUMN `locationCategory` TEXT")
            }
        },
        object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `widgets` ADD COLUMN `widthDp` INTEGER NOT NULL DEFAULT 0")
            }
        },
    )
}
