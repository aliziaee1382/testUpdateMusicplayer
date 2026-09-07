package ir.ali0003.musicplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        UserPreferencesEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        private const val CURRENT_VERSION = 13
        private const val DATABASE_NAME = "glass_audio_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * تولید خودکار مسیر مهاجرت برای هر نسخه مبدا به نسخه فعلی (و نسخه‌های آینده)
         */
        private fun buildUniversalMigrations(targetVersion: Int): Array<Migration> {
            val migrations = mutableListOf<Migration>()
            for (fromVersion in 1 until targetVersion) {
                migrations.add(object : Migration(fromVersion, targetVersion) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        ensureAllTablesAndColumns(db)
                    }
                })
            }
            return migrations.toTypedArray()
        }

        /**
         * بررسی و ایجاد کامل تمام جداول ۴گانه پروژه در صورت عدم وجود،
         * بدون دست زدن به ردیف‌ها و داده‌های ذخیره‌شده کاربر.
         */
        private fun ensureAllTablesAndColumns(db: SupportSQLiteDatabase) {
            // ۱. جدول ترک‌ها و آهنگ‌ها
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tracks` (
                    `id` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `album` TEXT NOT NULL,
                    `durationSeconds` INTEGER NOT NULL,
                    `audioUrl` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `coverGradientIndex` INTEGER NOT NULL,
                    `albumArtUri` TEXT,
                    `isLocal` INTEGER NOT NULL,
                    `isFavorite` INTEGER NOT NULL,
                    `playCount` INTEGER NOT NULL,
                    `lastPlayedTimestamp` INTEGER NOT NULL,
                    `folderName` TEXT NOT NULL,
                    `isHidden` INTEGER NOT NULL,
                    `listeningSeconds` INTEGER NOT NULL,
                    `dateAddedTimestamp` INTEGER NOT NULL,
                    `dateModifiedTimestamp` INTEGER NOT NULL,
                    `lyrics` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            // ۲. جدول پلی‌لیست‌ها
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlists` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `songCount` INTEGER NOT NULL,
                    `coverGradientIndex` INTEGER NOT NULL,
                    `isSystemPlaylist` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // ۳. جدول ارتباطی پلی‌لیست‌ها با آهنگ‌ها
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_track_cross_ref` (
                    `playlistId` INTEGER NOT NULL,
                    `trackId` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`, `trackId`)
                )
                """.trimIndent()
            )

            // ۴. جدول تنظیمات کاربر
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_preferences` (
                    `id` INTEGER NOT NULL,
                    `activeThemeId` TEXT NOT NULL,
                    `sortCriterion` TEXT NOT NULL,
                    `sortOrder` TEXT NOT NULL,
                    `lastSelectedCategory` TEXT NOT NULL,
                    `lastActiveNavTab` TEXT NOT NULL,
                    `lastLibrarySortTab` TEXT NOT NULL,
                    `eqPresetName` TEXT NOT NULL,
                    `eq60Hz` REAL NOT NULL,
                    `eq230Hz` REAL NOT NULL,
                    `eq910Hz` REAL NOT NULL,
                    `eq3600Hz` REAL NOT NULL,
                    `eq14000Hz` REAL NOT NULL,
                    `spatialAudioEnabled` INTEGER NOT NULL,
                    `flacModeEnabled` INTEGER NOT NULL,
                    `minDurationFilterSeconds` INTEGER NOT NULL,
                    `lastPlayedTrackId` INTEGER NOT NULL,
                    `lastPlaybackPositionMs` INTEGER NOT NULL,
                    `lastQueueTrackIds` TEXT NOT NULL,
                    `isAutoSystemTheme` INTEGER NOT NULL,
                    `listItemSize` TEXT NOT NULL,
                    `hiddenFolders` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            // ستون‌های اختیاری و الحاقی نسخه‌های قبل
            addColumnIfNotExists(db, "tracks", "lyrics", "TEXT DEFAULT NULL")
            addColumnIfNotExists(db, "tracks", "dateAddedTimestamp", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "tracks", "dateModifiedTimestamp", "INTEGER NOT NULL DEFAULT 0")
        }

        private fun addColumnIfNotExists(db: SupportSQLiteDatabase, tableName: String, columnName: String, columnDef: String) {
            try {
                val cursor = db.query("PRAGMA table_info(`$tableName`)")
                var exists = false
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex != -1 && cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                        exists = true
                        break
                    }
                }
                cursor.close()
                if (!exists) {
                    db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDef")
                }
            } catch (_: Exception) {}
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val migrations = buildUniversalMigrations(CURRENT_VERSION)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*migrations)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}