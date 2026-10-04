package org.solsticesw.vivlia.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.solsticesw.vivlia.data.local.dao.AppPreferenceDao
import org.solsticesw.vivlia.data.local.dao.BookmarkDao
import org.solsticesw.vivlia.data.local.dao.ChapterDao
import org.solsticesw.vivlia.data.local.dao.ExtensionDao
import org.solsticesw.vivlia.data.local.dao.LibraryEntryDao
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
        AppPreferenceEntity::class
    ],
    version = 1,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vivlia_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
