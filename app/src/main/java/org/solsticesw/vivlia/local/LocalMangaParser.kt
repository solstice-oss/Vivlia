package org.solsticesw.vivlia.local

import android.content.Context
import android.net.Uri
import android.text.Html
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.RemoteChapter
import org.solsticesw.vivlia.domain.model.RemoteEntryDetails
import org.solsticesw.vivlia.domain.model.RemotePage
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

data class ParsedLocalManga(
    val details: RemoteEntryDetails,
    val uri: String,
    val rootUri: String,
    val relativePath: String,
    val lastModified: Long,
    val sizeBytes: Long,
    val fingerprint: String,
    val chapters: List<RemoteChapter>
)

class LocalMangaParser(private val context: Context) {
    private val cacheDirectory = File(context.cacheDir, "local-archives")

    suspend fun parse(uri: Uri, rootUri: String, relativePath: String): ParsedLocalManga? =
        withContext(Dispatchers.IO) {
            val document = document(uri) ?: return@withContext null
            if (!document.isFile && !isMangaRoot(document)) return@withContext null
            val indexed = parseKotatsuIndex(document, uri)
            if (indexed != null) {
                val fallbackChapters = if (indexed.chapters.isEmpty()) collectChapters(document, uri) else indexed.chapters
                if (fallbackChapters.isEmpty()) return@withContext null
                return@withContext indexed.copy(
                    chapters = fallbackChapters,
                    sizeBytes = contentSize(document),
                    lastModified = document.lastModified().coerceAtLeast(0L),
                    fingerprint = fingerprint(document),
                    rootUri = rootUri,
                    relativePath = relativePath
                )
            }
            val epubFiles = when {
                document.isFile && LocalStorageRules.isEpub(document.name.orEmpty()) -> listOf(document)
                document.isDirectory -> document.listFiles()
                    .filter { it.isFile && LocalStorageRules.isEpub(it.name.orEmpty()) }
                    .sortedWith { a, b ->
                        LocalStorageRules.compareNatural(a.name.orEmpty(), b.name.orEmpty())
                    }
                else -> emptyList()
            }
            if (epubFiles.isNotEmpty()) return@withContext parseEpubs(epubFiles, document, uri, rootUri, relativePath)
            val chapters = collectChapters(document, uri)
            if (chapters.isEmpty()) return@withContext null
            val title = document.name.orEmpty().substringBeforeLast('.')
                .replace('_', ' ')
                .ifBlank { "Untitled" }
            val firstImage = findCover(document, uri)
            val modified = document.lastModified().coerceAtLeast(0L)
            val size = contentSize(document)
            val fingerprint = fingerprint(document)
            val authors = emptyList<String>()
            ParsedLocalManga(
                details = RemoteEntryDetails(
                    url = uri.toString(),
                    title = title,
                    author = authors.firstOrNull(),
                    coverUrl = firstImage,
                    mediaType = MediaType.MANGA,
                    sourceId = LOCAL_SOURCE_ID
                ),
                uri = uri.toString(),
                rootUri = rootUri,
                relativePath = relativePath,
                lastModified = modified,
                sizeBytes = size,
                fingerprint = fingerprint,
                chapters = chapters
            )
        }

    suspend fun parseDetails(uri: Uri): RemoteEntryDetails? =
        parse(uri, uri.toString(), "")?.details

    suspend fun parseChapters(uri: Uri): List<RemoteChapter> =
        parse(uri, uri.toString(), "")?.chapters.orEmpty()

    suspend fun pagesForChapter(chapterUrl: String): List<RemotePage> = withContext(Dispatchers.IO) {
        val ref = LocalChapterReference.parse(chapterUrl) ?: return@withContext emptyList()
        val rootUri = Uri.parse(ref.rootUri)
        val root = document(rootUri) ?: return@withContext emptyList()
        val archiveDocument = when {
            ref.archiveUri != null -> document(Uri.parse(ref.archiveUri))
            root.isFile -> root
            else -> resolve(root, ref.relativePath)
        } ?: return@withContext emptyList()
        if (ref.epub) {
            epubPages(archiveDocument, ref.relativePath)
        } else if (ref.archive) {
            archivePages(archiveDocument, pattern = ref.entriesPattern)
                .mapIndexed { index, url -> RemotePage(index = index, imageUrl = url) }
        } else {
            archiveDocument.listFiles()
                .filter { it.isFile && !LocalStorageRules.isTemporaryOrHidden(it.name.orEmpty()) &&
                    LocalStorageRules.isImage(it.name.orEmpty()) &&
                    (ref.entriesPattern == null ||
                        Regex(ref.entriesPattern).matches(it.name.orEmpty().substringBeforeLast('.'))) }
                .sortedWith { a, b ->
                    LocalStorageRules.compareNatural(a.name.orEmpty(), b.name.orEmpty())
                }
                .mapIndexed { index, file -> RemotePage(index = index, imageUrl = file.uri.toString()) }
        }
    }

    suspend fun discover(rootUri: Uri): List<ParsedLocalManga> = withContext(Dispatchers.IO) {
        val root = if (rootUri.scheme == "file") {
            DocumentFile.fromFile(File(rootUri.path ?: return@withContext emptyList()))
        } else {
            DocumentFile.fromTreeUri(context, rootUri)
        } ?: return@withContext emptyList()
        if (!root.isDirectory || !root.canRead()) return@withContext emptyList()

        val children = root.listFiles().filterNot {
            LocalStorageRules.isTemporaryOrHidden(it.name.orEmpty()) ||
                it.name.equals(".notamanga", ignoreCase = true)
        }
        val mangaDirectories = findMarkedMangaDirectories(children, depth = 2)
        val candidates = when {
            mangaDirectories.isNotEmpty() -> mangaDirectories
            else -> children.filter(::isMangaRoot)
        }
        candidates.mapNotNull { candidate ->
            val path = relativePath(root, candidate)
            parse(candidate.uri, rootUri.toString(), path)
        }
    }

    fun findImportRoots(uri: Uri): List<Uri> {
        val root = document(uri) ?: throw java.io.IOException("The selected item is unavailable")
        if (root.isFile) {
            require(LocalStorageRules.isSupportedImport(root.name.orEmpty())) {
                "Unsupported file type"
            }
            return listOf(root.uri)
        }
        require(root.isDirectory && root.canRead()) { "The selected folder is not readable" }
        if (hasComicInfo(root)) return listOf(root.uri)
        val nestedManga = findMarkedMangaDirectories(root.listFiles().toList(), depth = 2)
        return nestedManga.map { it.uri }.ifEmpty { listOf(root.uri) }
    }

    private fun isMangaRoot(document: DocumentFile): Boolean {
        if (document.isFile) {
            val name = document.name.orEmpty()
            return LocalStorageRules.isArchive(name) || LocalStorageRules.isEpub(name)
        }
        if (!document.isDirectory || !document.canRead()) return false
        return hasMangaContent(document, depth = 3)
    }

    private fun hasMangaContent(document: DocumentFile, depth: Int): Boolean {
        val children = document.listFiles()
        if (children.any { child ->
                child.isFile && !LocalStorageRules.isTemporaryOrHidden(child.name.orEmpty()) &&
                    (LocalStorageRules.isImage(child.name.orEmpty()) ||
                        LocalStorageRules.isArchive(child.name.orEmpty()) ||
                        LocalStorageRules.isEpub(child.name.orEmpty()) ||
                        child.name.equals("index.json", ignoreCase = true))
            }
        ) return true
        return depth > 0 && children.any { it.isDirectory && hasMangaContent(it, depth - 1) }
    }

    private fun findMarkedMangaDirectories(children: List<DocumentFile>, depth: Int): List<DocumentFile> {
        if (depth <= 0) return emptyList()
        val result = ArrayList<DocumentFile>()
        for (child in children.filter { it.isDirectory && !LocalStorageRules.isTemporaryOrHidden(it.name.orEmpty()) }) {
            if (hasComicInfo(child)) {
                result.add(child)
            } else {
                result.addAll(findMarkedMangaDirectories(child.listFiles().toList(), depth - 1))
            }
        }
        return result.sortedWith { a, b ->
            LocalStorageRules.compareNatural(a.name.orEmpty(), b.name.orEmpty())
        }
    }

    private fun hasComicInfo(manga: DocumentFile): Boolean {
        return manga.listFiles().any { chapter ->
            chapter.isDirectory && chapter.listFiles().any {
                it.isFile && it.name.equals("ComicInfo.xml", ignoreCase = true)
            }
        }
    }

    private fun collectChapters(root: DocumentFile, rootUri: Uri): List<RemoteChapter> {
        if (root.isFile && (LocalStorageRules.isArchive(root.name.orEmpty()) ||
                LocalStorageRules.isEpub(root.name.orEmpty()))) {
            return listOf(createChapter(root, rootUri, "", archive = true))
        }
        val imageDirectories = LinkedHashMap<String, DocumentFile>()
        val archives = ArrayList<Pair<String, DocumentFile>>()
        fun visit(directory: DocumentFile, relative: String) {
            for (child in directory.listFiles()) {
                val name = child.name.orEmpty()
                if (LocalStorageRules.isTemporaryOrHidden(name)) continue
                val childPath = if (relative.isEmpty()) name else "$relative/$name"
                when {
                    child.isDirectory -> visit(child, childPath)
                    LocalStorageRules.isArchive(name) -> archives.add(childPath to child)
                    LocalStorageRules.isImage(name) -> imageDirectories.putIfAbsent(relative, directory)
                }
            }
        }
        if (root.isDirectory) visit(root, "")
        val chapters = ArrayList<RemoteChapter>()
        imageDirectories.forEach { (relative, directory) ->
            val title = relative.substringAfterLast('/').ifBlank {
                root.name.orEmpty().substringBeforeLast('.').ifBlank { "Chapter" }
            }
            chapters.add(createChapter(directory, rootUri, relative, archive = false, title = title))
        }
        archives.forEach { (relative, file) ->
            chapters.add(
                createChapter(
                    file,
                    rootUri,
                    relative,
                    archive = true,
                    title = file.name.orEmpty().substringBeforeLast('.')
                )
            )
        }
        return chapters.sortedWith { a, b ->
            val numberComparison = a.chapterNumber.compareTo(b.chapterNumber)
            if (numberComparison != 0) numberComparison
            else LocalStorageRules.compareNatural(a.name, b.name)
        }
    }

    private fun parseKotatsuIndex(root: DocumentFile, rootUri: Uri): ParsedLocalManga? {
        val indexJson = readIndexJson(root) ?: return null
        val isEpub = LocalStorageRules.isEpub(root.name.orEmpty())
        val title = indexJson.optString("title", root.name.orEmpty().substringBeforeLast('.'))
            .ifBlank { root.name.orEmpty().substringBeforeLast('.').ifBlank { "Untitled" } }
        val authors = indexJson.optJSONArray("authors")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        } ?: listOfNotNull(indexJson.optString("author").takeIf(String::isNotBlank))
        val tags = indexJson.optJSONArray("tags")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.optString("title")?.takeIf(String::isNotBlank)
            }
        }.orEmpty()
        val chapters = ArrayList<RemoteChapter>()
        val chapterObject = indexJson.optJSONObject("chapters")
        if (chapterObject != null) {
            val keys = chapterObject.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val chapter = chapterObject.optJSONObject(id) ?: continue
                val path = chapter.optString("file").takeIf(String::isNotBlank).orEmpty()
                if (!LocalStorageRules.isSafeArchiveEntry(path) && path.isNotEmpty()) continue
                val safePattern = chapter.optString("entries").takeIf(::isSafePagePattern)
                val archive = isEpub || path.isEmpty() || LocalStorageRules.isArchive(path)
                chapters.add(
                    RemoteChapter(
                        url = LocalChapterReference(
                            rootUri = rootUri.toString(),
                            relativePath = path,
                            archive = archive,
                            epub = isEpub,
                            entriesPattern = safePattern
                        ).toUri(),
                        name = chapter.optString("name").ifBlank { "Chapter $id" },
                        chapterNumber = chapter.optDouble("number", 0.0).toFloat(),
                        dateUploaded = chapter.optLong("uploadDate", 0L),
                        scanlator = chapter.optString("scanlator").takeIf(String::isNotBlank),
                        type = if (isEpub) MediaType.NOVEL else MediaType.MANGA
                    )
                )
            }
        }
        val coverEntry = indexJson.optString("cover_entry").takeIf(String::isNotBlank)
        val indexedCover = coverEntry?.let { entry ->
            if (root.isFile && (LocalStorageRules.isArchive(root.name.orEmpty()) ||
                        LocalStorageRules.isEpub(root.name.orEmpty()))) {
                extractSpecificArchiveEntry(root, entry)
            } else {
                resolve(root, entry)?.uri?.toString()
            }
        }
        val uri = rootUri.toString()
        val details = RemoteEntryDetails(
            url = uri,
            title = title,
            author = authors.firstOrNull(),
            summary = indexJson.optString("description").takeIf(String::isNotBlank),
            coverUrl = indexedCover ?: findCover(root, rootUri),
            status = indexJson.optString("state").takeIf(String::isNotBlank),
            genres = tags,
            tags = tags,
            alternativeTitles = indexJson.optJSONArray("alt_titles")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty(),
            mediaType = if (isEpub) MediaType.NOVEL else MediaType.MANGA,
            sourceId = LOCAL_SOURCE_ID
        )
        return ParsedLocalManga(
            details = details,
            uri = uri,
            rootUri = rootUri.toString(),
            relativePath = "",
            lastModified = root.lastModified().coerceAtLeast(0L),
            sizeBytes = contentSize(root),
            fingerprint = fingerprint(root),
            chapters = chapters.sortedBy { it.chapterNumber }
        )
    }

    private fun readIndexJson(root: DocumentFile): JSONObject? {
        val text = if (root.isFile && (LocalStorageRules.isArchive(root.name.orEmpty()) ||
                    LocalStorageRules.isEpub(root.name.orEmpty()))) {
            val archive = copyArchiveToCache(root) ?: return null
            try {
                ZipFile(archive).use { zip ->
                    val entry = zip.getEntry("index.json") ?: return null
                    require(entry.size in 0..MAX_INDEX_BYTES) { "Invalid manga index size" }
                    zip.getInputStream(entry).bufferedReader().use { it.readText() }
                }
            } finally {
                archive.delete()
            }
        } else {
            val indexFile = root.findFile("index.json") ?: return null
            val input = openDocumentInput(indexFile) ?: return null
            input.use { it.bufferedReader().use { reader -> reader.readText() } }
        }
        if (text.length > MAX_INDEX_BYTES) return null
        return try {
            JSONObject(text)
        } catch (error: org.json.JSONException) {
            null
        }
    }

    private fun isSafePagePattern(value: String): Boolean =
        value.length <= MAX_INDEX_PATTERN_LENGTH &&
            Regex("""^[0-9]{8}_[0-9]{4}\d{4}$""").matches(value)

    private fun openDocumentInput(document: DocumentFile) =
        if (document.uri.scheme == "file") {
            document.uri.path?.let { File(it).inputStream() }
        } else {
            context.contentResolver.openInputStream(document.uri)
        }

    private fun extractSpecificArchiveEntry(archive: DocumentFile, entryName: String): String? {
        if (!LocalStorageRules.isSafeArchiveEntry(entryName)) return null
        val archiveFile = copyArchiveToCache(archive) ?: return null
        return try {
            extractArchiveEntry(archiveFile, entryName, archive.uri.toString()).toURI().toString()
        } catch (_: java.io.IOException) {
            null
        } finally {
            archiveFile.delete()
        }
    }

    private fun createChapter(
        document: DocumentFile,
        rootUri: Uri,
        relativePath: String,
        archive: Boolean,
        title: String = document.name.orEmpty().substringBeforeLast('.')
    ): RemoteChapter {
        val number = LocalStorageRules.parseChapterNumber(title) ?: 0f
        val chapterUri = LocalChapterReference(
            rootUri = rootUri.toString(),
            relativePath = relativePath,
            archive = archive
        ).toUri()
        return RemoteChapter(
            url = chapterUri,
            name = title.replace('_', ' '),
            chapterNumber = number,
            dateUploaded = document.lastModified(),
            type = MediaType.MANGA
        )
    }

    private fun findCover(root: DocumentFile, rootUri: Uri): String? {
        if (root.isFile && LocalStorageRules.isArchive(root.name.orEmpty())) {
            return archivePages(root, firstOnly = true).firstOrNull()
        }
        val images = ArrayList<Pair<String, DocumentFile>>()
        fun visit(directory: DocumentFile, path: String) {
            for (child in directory.listFiles()) {
                val name = child.name.orEmpty()
                if (LocalStorageRules.isTemporaryOrHidden(name)) continue
                val childPath = if (path.isEmpty()) name else "$path/$name"
                if (child.isDirectory) {
                    visit(child, childPath)
                } else if (LocalStorageRules.isImage(name)) {
                    images.add(childPath to child)
                } else if (LocalStorageRules.isArchive(name)) {
                    archivePages(child, firstOnly = true).firstOrNull()?.let {
                        images.add("$childPath!$it" to child)
                    }
                }
            }
        }
        if (root.isDirectory) visit(root, "")
        val cover = images.minWithOrNull { a, b -> LocalStorageRules.compareNatural(a.first, b.first) }
            ?: return null
        return if ('!' in cover.first) cover.first.substringAfter('!') else cover.second.uri.toString()
    }

    private fun parseEpubs(
        files: List<DocumentFile>,
        root: DocumentFile,
        uri: Uri,
        storageRootUri: String,
        storageRelativePath: String
    ): ParsedLocalManga? {
        val books = files.mapNotNull { document ->
            val file = copyArchiveToCache(document) ?: return@mapNotNull null
            try {
                document to LocalEpubParser.parse(file)
            } finally {
                file.delete()
            }
        }
        if (books.isEmpty()) return null
        val isCollection = books.size > 1 || root.isDirectory
        val epubChapters = books.flatMapIndexed { bookIndex, (document, book) ->
            book.chapters.mapIndexed { chapterIndex, chapter ->
                val chapterUri = LocalChapterReference(
                    rootUri = uri.toString(),
                    relativePath = chapter.entryName,
                    archive = true,
                    epub = true,
                    archiveUri = if (isCollection) document.uri.toString() else null
                ).toUri()
                RemoteChapter(
                    url = chapterUri,
                    name = chapter.title,
                    chapterNumber = (epubChaptersOffset(books, bookIndex) + chapterIndex + 1).toFloat(),
                    dateUploaded = document.lastModified().coerceAtLeast(0L),
                    type = MediaType.NOVEL
                )
            }
        }
        val firstBook = books.first().second
        val title = if (isCollection) {
            root.name.orEmpty().substringBeforeLast('.').replace('_', ' ').ifBlank { "Untitled" }
        } else {
            firstBook.title ?: root.name.orEmpty().substringBeforeLast('.').ifBlank { "Untitled" }
        }
        val cover = books.firstNotNullOfOrNull { (document, book) ->
            book.coverEntry?.let { extractSpecificArchiveEntry(document, it) }
        }
        val details = RemoteEntryDetails(
            url = uri.toString(),
            title = title,
            author = firstBook.authors.firstOrNull(),
            summary = firstBook.description,
            coverUrl = cover,
            mediaType = MediaType.NOVEL,
            sourceId = LOCAL_SOURCE_ID
        )
        return ParsedLocalManga(
            details = details,
            uri = uri.toString(),
            rootUri = storageRootUri,
            relativePath = storageRelativePath,
            lastModified = root.lastModified().coerceAtLeast(0L),
            sizeBytes = contentSize(root),
            fingerprint = fingerprint(root),
            chapters = epubChapters
        )
    }

    private fun epubChaptersOffset(books: List<Pair<DocumentFile, LocalEpubBook>>, bookIndex: Int): Int =
        books.take(bookIndex).sumOf { it.second.chapters.size }

    private fun epubPages(archive: DocumentFile, entryName: String): List<RemotePage> {
        if (!LocalStorageRules.isSafeArchiveEntry(entryName)) return emptyList()
        val file = copyArchiveToCache(archive) ?: return emptyList()
        return try {
            val html = ZipFile(file).use { zip ->
                val entry = zip.getEntry(entryName) ?: return emptyList()
                require(entry.size in 0L..MAX_EPUB_CHAPTER_BYTES) { "EPUB chapter is too large" }
                zip.getInputStream(entry).use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_EPUB_CHAPTER_BYTES) { "EPUB chapter is too large" }
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            }
            val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(html).toString()
            }.trim()
            listOf(RemotePage(index = 0, text = text))
        } finally {
            file.delete()
        }
    }

    private fun archivePages(
        archive: DocumentFile,
        firstOnly: Boolean = false,
        pattern: String? = null
    ): List<String> {
        val file = copyArchiveToCache(archive) ?: return emptyList()
        return try {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory && LocalStorageRules.isSafeArchiveEntry(it.name) }
                    .filter { LocalStorageRules.isImage(it.name.substringAfterLast('/')) }
                    .filter { pattern == null || Regex(pattern).matches(it.name.substringBeforeLast('.')) }
                    .toList()
                require(entries.size <= MAX_ARCHIVE_ENTRIES) { "Archive contains too many pages" }
                val sorted = entries
                    .sortedWith { a, b -> LocalStorageRules.compareNatural(a.name, b.name) }
                val selected = if (firstOnly) sorted.take(1) else sorted
                var totalBytes = 0L
                selected.map { entry ->
                    if (entry.size >= 0L) {
                        totalBytes += entry.size
                        require(totalBytes <= MAX_TOTAL_PAGE_BYTES) { "Archive pages exceed the size limit" }
                    }
                    extractArchiveEntry(file, entry.name, archive.uri.toString()).toURI().toString()
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun extractArchiveEntry(archive: File, entryName: String, archiveUri: String): File {
        cacheDirectory.mkdirs()
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry(entryName) ?: error("Archive page disappeared")
            require(LocalStorageRules.isSafeArchiveEntry(entry.name)) { "Unsafe archive path" }
            require(entry.size <= MAX_PAGE_BYTES || entry.size < 0L) { "Archive image is too large" }
            val output = File(
                cacheDirectory,
                "${sha256("$archiveUri|$entryName|${entry.crc}|${entry.size}")}.${entryName.substringAfterLast('.', "img")}"
            )
            if (output.isFile && output.length() > 0L) return output
            var extracted = false
            try {
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(output).use { sink ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_PAGE_BYTES) { "Archive image is too large" }
                            sink.write(buffer, 0, count)
                        }
                    }
                }
                extracted = true
            } finally {
                if (!extracted) output.delete()
            }
            return output
        }
    }

    private fun copyArchiveToCache(document: DocumentFile): File? {
        cacheDirectory.mkdirs()
        val temporary = File.createTempFile("archive-", ".zip", cacheDirectory)
        return try {
            val input = if (document.uri.scheme == "file") {
                File(document.uri.path ?: throw java.io.FileNotFoundException("Invalid archive path")).inputStream()
            } else {
                context.contentResolver.openInputStream(document.uri)
                    ?: throw java.io.FileNotFoundException("Archive content is unavailable")
            }
            input.use { source -> temporary.outputStream().use(source::copyTo) }
            temporary
        } catch (_: SecurityException) {
            temporary.delete()
            null
        } catch (_: java.io.IOException) {
            temporary.delete()
            null
        }
    }

    private fun document(uri: Uri): DocumentFile? {
        if (uri.scheme == "file") return uri.path?.let { DocumentFile.fromFile(File(it)) }
        return if ("tree" in uri.pathSegments) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
        }
    }

    private fun resolve(root: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isEmpty()) return root
        var current = root
        for (segment in relativePath.split('/')) {
            if (segment.isBlank() || segment == "." || segment == "..") return null
            current = current.findFile(segment) ?: return null
        }
        return current
    }

    private fun relativePath(root: DocumentFile, child: DocumentFile): String {
        if (root.uri == child.uri) return child.name.orEmpty()
        return child.uri.lastPathSegment.orEmpty().substringAfterLast(':')
            .removePrefix("/")
            .ifBlank { child.name.orEmpty() }
    }

    private fun contentSize(document: DocumentFile): Long {
        if (document.isFile) return document.length().coerceAtLeast(0L)
        return document.listFiles().sumOf(::contentSize)
    }

    private fun fingerprint(document: DocumentFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun visit(current: DocumentFile, path: String) {
            if (current.isFile) {
                digest.update("$path\u0000${current.length()}\u0000${current.lastModified()}\n".toByteArray())
            } else {
                current.listFiles().filterNot {
                    LocalStorageRules.isTemporaryOrHidden(it.name.orEmpty())
                }.sortedWith { a, b ->
                    LocalStorageRules.compareNatural(a.name.orEmpty(), b.name.orEmpty())
                }.forEach { child ->
                    val name = child.name.orEmpty()
                    visit(child, if (path.isEmpty()) name else "$path/$name")
                }
            }
        }
        visit(document, "")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val LOCAL_CHAPTER_SCHEME = "vivlia-local"
        const val MAX_PAGE_BYTES = 64L * 1024L * 1024L
        const val MAX_TOTAL_PAGE_BYTES = 1024L * 1024L * 1024L
        const val MAX_ARCHIVE_ENTRIES = 10_000
        const val MAX_INDEX_BYTES = 4 * 1024 * 1024
        const val MAX_INDEX_PATTERN_LENGTH = 32
        const val MAX_EPUB_CHAPTER_BYTES = 16L * 1024L * 1024L
    }
}

private data class LocalChapterReference(
    val rootUri: String,
    val relativePath: String,
    val archive: Boolean,
    val entriesPattern: String? = null,
    val epub: Boolean = false,
    val archiveUri: String? = null
) {
    fun toUri(): String = Uri.Builder()
        .scheme("vivlia-local")
        .authority("chapter")
        .appendQueryParameter("root", rootUri)
        .appendQueryParameter("path", relativePath)
        .appendQueryParameter("archive", archive.toString())
        .appendQueryParameter("epub", epub.toString())
        .apply { archiveUri?.let { appendQueryParameter("archive_uri", it) } }
        .apply { entriesPattern?.let { appendQueryParameter("pattern", it) } }
        .build()
        .toString()

    companion object {
        fun parse(value: String): LocalChapterReference? {
            val uri = Uri.parse(value)
            if (uri.scheme != "vivlia-local" || uri.authority != "chapter") return null
            val root = uri.getQueryParameter("root") ?: return null
            val path = uri.getQueryParameter("path") ?: return null
            return LocalChapterReference(
                rootUri = root,
                relativePath = path,
                archive = uri.getQueryParameter("archive") == "true",
                epub = uri.getQueryParameter("epub") == "true",
                archiveUri = uri.getQueryParameter("archive_uri"),
                entriesPattern = uri.getQueryParameter("pattern")?.takeIf {
                    Regex("""^[0-9]{8}_[0-9]{4}\\d\{4\}$""").matches(it)
                }
            )
        }
    }
}

const val LOCAL_SOURCE_ID = "vivlia_local"

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
