package org.solsticesw.vivlia.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.solsticesw.vivlia.data.local.entity.EntryAuthorEntity
import org.solsticesw.vivlia.data.local.entity.EntryGenreEntity
import org.solsticesw.vivlia.data.local.entity.EntryTagEntity
import org.solsticesw.vivlia.data.local.entity.EntryTitleEntity
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity

@Dao
interface LibraryEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LibraryEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LibraryEntryEntity>): List<Long>

    @Update
    suspend fun update(entry: LibraryEntryEntity)

    @Delete
    suspend fun delete(entry: LibraryEntryEntity)

    @Query("SELECT * FROM library_entries WHERE id = :id")
    suspend fun getById(id: Long): LibraryEntryEntity?

    @Query("SELECT * FROM library_entries WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<LibraryEntryEntity?>

    @Query("SELECT * FROM library_entries WHERE sourceId = :sourceId AND url = :url LIMIT 1")
    suspend fun getBySourceAndUrl(sourceId: String, url: String): LibraryEntryEntity?

    @Query("SELECT * FROM library_entries WHERE inLibrary = 1 ORDER BY title ASC")
    fun getAllLibraryEntriesFlow(): Flow<List<LibraryEntryEntity>>

    @Query("SELECT * FROM library_entries WHERE inLibrary = 1 AND title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchLibraryEntriesFlow(query: String): Flow<List<LibraryEntryEntity>>

    @Query("UPDATE library_entries SET inLibrary = :inLibrary, addedAt = :addedAt WHERE id = :id")
    suspend fun toggleInLibrary(id: Long, inLibrary: Boolean, addedAt: Long = System.currentTimeMillis())

    // Metadata Inserts & Queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTitles(titles: List<EntryTitleEntity>)

    @Query("DELETE FROM entry_titles WHERE entryId = :entryId")
    suspend fun deleteTitlesForEntry(entryId: Long)

    @Query("SELECT * FROM entry_titles WHERE entryId = :entryId")
    suspend fun getTitlesForEntry(entryId: Long): List<EntryTitleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthors(authors: List<EntryAuthorEntity>)

    @Query("DELETE FROM entry_authors WHERE entryId = :entryId")
    suspend fun deleteAuthorsForEntry(entryId: Long)

    @Query("SELECT * FROM entry_authors WHERE entryId = :entryId")
    suspend fun getAuthorsForEntry(entryId: Long): List<EntryAuthorEntity>

    @Query("SELECT * FROM entry_authors WHERE entryId = :entryId")
    fun getAuthorsForEntryFlow(entryId: Long): Flow<List<EntryAuthorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenres(genres: List<EntryGenreEntity>)

    @Query("DELETE FROM entry_genres WHERE entryId = :entryId")
    suspend fun deleteGenresForEntry(entryId: Long)

    @Query("SELECT * FROM entry_genres WHERE entryId = :entryId")
    suspend fun getGenresForEntry(entryId: Long): List<EntryGenreEntity>

    @Query("SELECT * FROM entry_genres WHERE entryId = :entryId")
    fun getGenresForEntryFlow(entryId: Long): Flow<List<EntryGenreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<EntryTagEntity>)

    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    suspend fun deleteTagsForEntry(entryId: Long)

    @Query("SELECT * FROM entry_tags WHERE entryId = :entryId")
    suspend fun getTagsForEntry(entryId: Long): List<EntryTagEntity>
}
