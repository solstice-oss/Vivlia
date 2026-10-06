package org.solsticesw.vivlia.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.solsticesw.vivlia.data.local.dao.AppPreferenceDao
import org.solsticesw.vivlia.data.local.dao.BookmarkDao
import org.solsticesw.vivlia.data.local.dao.ChapterDao
import org.solsticesw.vivlia.data.local.dao.ExtensionDao
import org.solsticesw.vivlia.data.local.dao.LibraryEntryDao
import org.solsticesw.vivlia.data.local.dao.LocalContentIndexDao
import org.solsticesw.vivlia.data.local.dao.PageDao
import org.solsticesw.vivlia.data.local.dao.ReadingSessionDao
import org.solsticesw.vivlia.data.local.dao.RepositoryDao
import org.solsticesw.vivlia.data.local.dao.SourceDao
import org.solsticesw.vivlia.data.local.entity.AppPreferenceEntity
import org.solsticesw.vivlia.data.local.entity.BookmarkEntity
import org.solsticesw.vivlia.data.local.entity.CachedManifestEntity
import org.solsticesw.vivlia.data.local.entity.ChapterEntity
import org.solsticesw.vivlia.data.local.entity.EntryAuthorEntity
import org.solsticesw.vivlia.data.local.entity.EntryExternalIdEntity
import org.solsticesw.vivlia.data.local.entity.EntryGenreEntity
import org.solsticesw.vivlia.data.local.entity.EntryTagEntity
import org.solsticesw.vivlia.data.local.entity.EntryTitleEntity
import org.solsticesw.vivlia.data.local.entity.ExtensionEntity
import org.solsticesw.vivlia.data.local.entity.ExtensionRepositoryEntity
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.local.entity.LocalContentIndexEntity
import org.solsticesw.vivlia.data.local.entity.PageEntity
import org.solsticesw.vivlia.data.local.entity.ReadingSessionEntity
import org.solsticesw.vivlia.data.local.entity.SourceEntity

@Database(
    entities = [
        LibraryEntryEntity::class,
        EntryTitleEntity::class,
        EntryAuthorEntity::class,
        EntryGenreEntity::class,
        EntryTagEntity::class,
        EntryExternalIdEntity::class,
        ChapterEntity::class,
        PageEntity::class,
        ReadingSessionEntity::class,
        BookmarkEntity::class,
        ExtensionRepositoryEntity::class,
        ExtensionEntity::class,
        SourceEntity::class,
        CachedManifestEntity::class,
        AppPreferenceEntity::class,
        LocalContentIndexEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun libraryEntryDao(): LibraryEntryDao
    abstract fun chapterDao(): ChapterDao
    abstract fun pageDao(): PageDao
    abstract fun repositoryDao(): RepositoryDao
    abstract fun extensionDao(): ExtensionDao
    abstract fun sourceDao(): SourceDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun appPreferenceDao(): AppPreferenceDao
    abstract fun localContentIndexDao(): LocalContentIndexDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_content_index` (
                        `entryId` INTEGER NOT NULL,
                        `rootUri` TEXT NOT NULL,
                        `contentUri` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `lastModified` INTEGER NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `fingerprint` TEXT NOT NULL,
                        `available` INTEGER NOT NULL,
                        PRIMARY KEY(`entryId`),
                        FOREIGN KEY(`entryId`) REFERENCES `library_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_content_index_rootUri` ON `local_content_index` (`rootUri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_content_index_contentUri` ON `local_content_index` (`contentUri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_content_index_fingerprint` ON `local_content_index` (`fingerprint`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vivlia_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
