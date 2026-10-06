package org.solsticesw.vivlia.local

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.AppPreferenceEntity
import org.solsticesw.vivlia.data.local.entity.ChapterEntity
import org.solsticesw.vivlia.data.local.entity.EntryAuthorEntity
import org.solsticesw.vivlia.data.local.entity.EntryGenreEntity
import org.solsticesw.vivlia.data.local.entity.LibraryEntryEntity
import org.solsticesw.vivlia.data.local.entity.LocalContentIndexEntity
import org.solsticesw.vivlia.domain.model.MediaType
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

data class LocalScanSummary(
    val mangaCount: Int,
    val inaccessibleRoots: List<String>
)

class LocalStorageRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val parser: LocalMangaParser = LocalMangaParser(context)
) {
    private val preferences = database.appPreferenceDao()
    private val entries = database.libraryEntryDao()
    private val chapters = database.chapterDao()
    private val index = database.localContentIndexDao()
    private val localDirectory = File(context.filesDir, LOCAL_DIRECTORY_NAME)

    val selectedRootUri: Flow<String?> = preferences.getPreferenceFlow(KEY_ROOT_URI)
    val localEntries: Flow<List<LibraryEntryEntity>> =
        entries.getAvailableLocalEntriesFlow(LOCAL_SOURCE_ID)
    val indexedContent: Flow<List<LocalContentIndexEntity>> = index.getAllFlow()

    suspend fun getSelectedRootUri(): String? = preferences.getPreference(KEY_ROOT_URI)

    suspend fun toggleFavorite(entryId: Long, favorite: Boolean) {
        entries.toggleInLibrary(entryId, favorite)
    }

    suspend fun setStorageRoot(uri: Uri) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri)
            ?: throw IOException("The selected storage folder is unavailable")
        if (!root.isDirectory || !root.canRead() || !root.canWrite()) {
            throw SecurityException("The selected folder must be readable and writable")
        }
        val probeName = ".vivlia_access_probe"
        val probe = root.createFile("application/octet-stream", probeName)
            ?: throw IOException("The selected folder does not allow creating files")
        if (!probe.delete()) {
            throw IOException("The selected folder does not allow deleting files")
        }
        val previousRoot = getSelectedRootUri()
        if (previousRoot != null && previousRoot != uri.toString()) {
            index.markRootUnavailable(previousRoot)
        }
        preferences.setPreference(
            AppPreferenceEntity(KEY_ROOT_URI, uri.toString())
        )
    }

    suspend fun resetStorageRoot() {
        val current = getSelectedRootUri()?.let(Uri::parse)
        if (current != null) {
            index.markRootUnavailable(current.toString())
            val permission = context.contentResolver.persistedUriPermissions
                .firstOrNull { it.uri == current }
            if (permission != null) {
                val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                    (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                context.contentResolver.releasePersistableUriPermission(current, flags)
            }
        }
        preferences.deletePreference(KEY_ROOT_URI)
    }

    suspend fun scan(onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }): LocalScanSummary =
        withContext(Dispatchers.IO) {
            localDirectory.mkdirs()
            val rootUris = buildList {
                add(Uri.fromFile(localDirectory).toString())
                getSelectedRootUri()?.let { selected ->
                    if (selected !in this) add(selected)
                }
            }
            index.markRootsUnavailableExcept(rootUris)
            val inaccessible = ArrayList<String>()
            val discovered = ArrayList<ParsedLocalManga>()
            for (rootUri in rootUris) {
                currentCoroutineContext().ensureActive()
                val root = rootDocument(Uri.parse(rootUri))
                if (root == null || !root.isDirectory || !root.canRead()) {
                    index.markRootUnavailable(rootUri)
                    inaccessible.add(rootUri)
                    continue
                }
                index.markRootUnavailable(rootUri)
                val found = try {
                    parser.discover(Uri.parse(rootUri))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: SecurityException) {
                    index.markRootUnavailable(rootUri)
                    inaccessible.add(rootUri)
                    continue
                }
                discovered.addAll(found)
            }
            for ((position, item) in discovered.withIndex()) {
                currentCoroutineContext().ensureActive()
                store(item)
                onProgress(position + 1, discovered.size)
            }
            LocalScanSummary(discovered.size, inaccessible)
        }

    suspend fun importUris(
        uris: List<Uri>,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "Choose at least one file or folder to import" }
        localDirectory.mkdirs()
        var imported = 0
        for ((position, uri) in uris.withIndex()) {
            currentCoroutineContext().ensureActive()
            val roots = parser.findImportRoots(uri)
            for (source in roots) {
                currentCoroutineContext().ensureActive()
                importOne(source)
                imported++
            }
            onProgress(position + 1, uris.size)
        }
        imported
    }

    suspend fun deleteLocalEntry(entryId: Long) = withContext(Dispatchers.IO) {
        val localIndex = index.getByEntryId(entryId)
            ?: throw IOException("Local content is no longer indexed")
        val deleted = deleteDocument(localIndex.contentUri)
        if (!deleted) throw IOException("The local content could not be deleted")

        try {
            database.withTransaction {
                val entry = entries.getById(entryId)
                val entryChapters = chapters.getChaptersForEntry(entryId)
                for (chapter in entryChapters) {
                    database.pageDao().deletePagesForChapter(chapter.id)
                    database.bookmarkDao().deleteBookmarksForChapter(chapter.id)
                }
                database.pageDao().deletePagesForEntry(entryId)
                database.bookmarkDao().deleteBookmarksForEntry(entryId)
                database.readingSessionDao().deleteForEntry(entryId)
                chapters.deleteChaptersForEntry(entryId)
                if (entry != null) entries.delete(entry)
                index.delete(entryId)
            }
        } catch (error: Exception) {
            index.markUnavailable(entryId)
            throw error
        }
    }

    private suspend fun store(item: ParsedLocalManga) {
        database.withTransaction {
            val oldIndex = index.getByContentUri(item.uri)
                ?: index.getByFingerprint(item.fingerprint)
            val currentEntry = oldIndex?.let { entries.getById(it.entryId) }
                ?: entries.getBySourceAndUrl(LOCAL_SOURCE_ID, item.uri)
            val now = System.currentTimeMillis()
            val entryId = if (currentEntry == null) {
                entries.insert(
                    LibraryEntryEntity(
                        sourceId = LOCAL_SOURCE_ID,
                        url = item.uri,
                        title = item.details.title,
                        coverUrl = item.details.coverUrl,
                        summary = item.details.summary,
                        status = "LOCAL",
                        mediaType = item.details.mediaType.name,
                        inLibrary = false,
                        addedAt = now,
                        updatedAt = now,
                        totalChapters = item.chapters.size,
                        unreadChapters = item.chapters.size
                    )
                )
            } else {
                entries.update(
                    currentEntry.copy(
                        url = item.uri,
                        title = item.details.title,
                        coverUrl = item.details.coverUrl,
                        summary = item.details.summary,
                        status = item.details.status ?: currentEntry.status,
                        mediaType = item.details.mediaType.name,
                        updatedAt = now,
                        totalChapters = item.chapters.size
                    )
                )
                currentEntry.id
            }

            val priorChapters = chapters.getChaptersForEntry(entryId).toMutableList()
            val retainedIds = HashSet<Long>()
            for ((chapterIndex, remote) in item.chapters.withIndex()) {
                val existing = priorChapters.firstOrNull { it.url == remote.url }
                    ?: priorChapters.firstOrNull {
                        it.id !in retainedIds &&
                            it.name.equals(remote.name, ignoreCase = true) &&
                            it.chapterNumber == remote.chapterNumber
                    }
                if (existing == null) {
                    chapters.insertChapter(
                        ChapterEntity(
                            entryId = entryId,
                            url = remote.url,
                            name = remote.name,
                            chapterNumber = remote.chapterNumber,
                            dateUploaded = remote.dateUploaded,
                            fetchedAt = now,
                            downloaded = true
                        )
                    )
                } else {
                    retainedIds.add(existing.id)
                    database.pageDao().deletePagesForChapter(existing.id)
                    chapters.updateChapter(
                        existing.copy(
                            url = remote.url,
                            name = remote.name,
                            chapterNumber = remote.chapterNumber,
                            dateUploaded = remote.dateUploaded,
                            fetchedAt = now,
                            downloaded = true
                        )
                    )
                }
            }
            priorChapters.filterNot { it.id in retainedIds || item.chapters.any { c -> c.url == it.url } }
                .forEach { stale ->
                    database.pageDao().deletePagesForChapter(stale.id)
                    database.bookmarkDao().deleteBookmarksForChapter(stale.id)
                    chapters.deleteChapterById(stale.id)
                }

            val storedEntry = entries.getById(entryId) ?: return@withTransaction
            val unread = chapters.getChaptersForEntry(entryId).count { !it.read }
            entries.update(storedEntry.copy(totalChapters = item.chapters.size, unreadChapters = unread))

            database.libraryEntryDao().deleteAuthorsForEntry(entryId)
            item.details.author?.takeIf(String::isNotBlank)?.let { author ->
                database.libraryEntryDao().insertAuthors(
                    listOf(EntryAuthorEntity(entryId = entryId, name = author, role = "author"))
                )
            }
            database.libraryEntryDao().deleteGenresForEntry(entryId)
            if (item.details.genres.isNotEmpty()) {
                database.libraryEntryDao().insertGenres(
                    item.details.genres.map { EntryGenreEntity(entryId = entryId, genre = it) }
                )
            }
            index.upsert(
                LocalContentIndexEntity(
                    entryId = entryId,
                    rootUri = item.rootUri,
                    contentUri = item.uri,
                    relativePath = item.relativePath,
                    lastModified = item.lastModified,
                    sizeBytes = item.sizeBytes,
                    fingerprint = item.fingerprint,
                    available = true
                )
            )
        }
    }

    private suspend fun importOne(sourceUri: Uri) {
        val source = rootDocument(sourceUri) ?: throw IOException("The selected item is unavailable")
        val sourceName = source.name?.takeIf(String::isNotBlank)
            ?: throw IOException("The selected item has no name")
        if (source.isFile) {
            require(LocalStorageRules.isSupportedImport(sourceName)) { "Unsupported file type: $sourceName" }
            if (sourceName.substringAfterLast('.', "").equals("pdf", ignoreCase = true)) {
                importPdf(source, sourceName)
            } else {
                val destination = uniqueDestination(sourceName)
                copyFile(source, destination)
            }
        } else {
            require(source.isDirectory && source.canRead()) { "The selected folder cannot be read" }
            val destination = uniqueDestination(sanitizeName(sourceName))
            if (!destination.mkdirs() && !destination.isDirectory) {
                throw IOException("Cannot create the import destination")
            }
            var copied = false
            try {
                copyDirectory(source, destination)
                copied = true
            } finally {
                if (!copied) destination.deleteRecursively()
            }
        }
    }

    private suspend fun copyDirectory(source: DocumentFile, destination: File) {
        for (child in source.listFiles()) {
            currentCoroutineContext().ensureActive()
            val name = child.name.orEmpty()
            if (LocalStorageRules.isTemporaryOrHidden(name)) continue
            val safeName = sanitizeName(name)
            if (child.isDirectory) {
                val target = File(destination, safeName)
                if (!target.mkdirs() && !target.isDirectory) throw IOException("Cannot create import folder")
                copyDirectory(child, target)
            } else if (child.isFile) {
                copyFile(child, File(destination, safeName))
            }
        }
    }

    private suspend fun copyFile(source: DocumentFile, destination: File) {
        val input = openInput(source) ?: throw IOException("Cannot read ${source.name.orEmpty()}")
        var copied = false
        try {
            input.use { stream ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            copied = true
        } finally {
            if (!copied) destination.delete()
        }
    }

    private suspend fun importPdf(source: DocumentFile, sourceName: String) {
        val descriptor = if (source.uri.scheme == "file") {
            ParcelFileDescriptor.open(File(source.uri.path ?: throw IOException("Invalid PDF path")), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(source.uri, "r")
        } ?: throw IOException("Cannot open PDF")
        val output = File(uniqueDestination(sourceName.substringBeforeLast('.') + ".cbz").path + ".tmp")
        try {
            renderPdf(descriptor, output)
            val finalFile = File(output.path.removeSuffix(".tmp"))
            if (!output.renameTo(finalFile)) throw IOException("Cannot finish importing PDF")
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            descriptor.close()
        }
    }

    private suspend fun renderPdf(descriptor: ParcelFileDescriptor, output: File) {
        PdfRenderer(descriptor).use { renderer ->
            require(renderer.pageCount > 0) { "The PDF contains no pages" }
            require(renderer.pageCount <= MAX_PDF_PAGES) { "The PDF contains too many pages" }
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                for (index in 0 until renderer.pageCount) {
                    currentCoroutineContext().ensureActive()
                    renderer.openPage(index).use { page ->
                        val scale = minOf(2f, MAX_PDF_DIMENSION / maxOf(page.width, page.height).toFloat())
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            zip.putNextEntry(ZipEntry(String.format(Locale.ROOT, "%05d.jpg", index + 1)))
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, zip)
                            zip.closeEntry()
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    private fun uniqueDestination(name: String): File {
        val baseName = sanitizeName(name)
        val candidate = File(localDirectory, baseName)
        if (!candidate.exists()) return candidate
        val stem = baseName.substringBeforeLast('.', baseName)
        val extension = baseName.substringAfterLast('.', "").takeIf { it != baseName }
        var suffix = 1
        while (true) {
            val nameWithSuffix = if (extension == null) "$stem ($suffix)" else "$stem ($suffix).$extension"
            val alternative = File(localDirectory, nameWithSuffix)
            if (!alternative.exists()) return alternative
            suffix++
        }
    }

    private fun sanitizeName(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .trim().trim('.').take(180).ifBlank { "Imported manga" }

    private fun rootDocument(uri: Uri): DocumentFile? {
        if (uri.scheme == "file") return uri.path?.let { DocumentFile.fromFile(File(it)) }
        return if ("tree" in uri.pathSegments) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
        }
    }

    private fun openInput(document: DocumentFile) =
        if (document.uri.scheme == "file") {
            document.uri.path?.let { File(it).inputStream() }
        } else {
            context.contentResolver.openInputStream(document.uri)
        }

    private fun deleteDocument(uriString: String): Boolean {
        val uri = Uri.parse(uriString)
        return if (uri.scheme == "file") {
            uri.path?.let { path ->
                val file = File(path)
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            } ?: false
        } else {
            rootDocument(uri)?.delete() == true
        }
    }

    private companion object {
        const val KEY_ROOT_URI = "local_storage_root_uri"
        const val LOCAL_DIRECTORY_NAME = "local-manga"
        const val MAX_PDF_PAGES = 2_000
        const val MAX_PDF_DIMENSION = 4_096f
    }
}
