package org.solsticesw.vivlia.local

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

data class LocalEpubBook(
    val title: String?,
    val authors: List<String>,
    val description: String?,
    val coverEntry: String?,
    val chapters: List<LocalEpubChapter>
)

data class LocalEpubChapter(
    val title: String,
    val entryName: String
)

object LocalEpubParser {
    private const val MAX_METADATA_BYTES = 4 * 1024 * 1024

    fun parse(file: File): LocalEpubBook = ZipFile(file).use { zip ->
        val container = zip.readTextEntry("META-INF/container.xml")
            ?: throw IllegalArgumentException("EPUB container metadata is missing")
        val opfPath = parseContainer(container)
            ?: throw IllegalArgumentException("EPUB package path is missing")
        val packageXml = zip.readTextEntry(opfPath)
            ?: throw IllegalArgumentException("EPUB package metadata is missing")
        val opfDirectory = opfPath.substringBeforeLast('/', "")
        parsePackage(packageXml, opfDirectory)
    }

    private fun ZipFile.readTextEntry(path: String): String? {
        val entry = getEntry(path) ?: return null
        if (entry.size > MAX_METADATA_BYTES) throw IllegalArgumentException("EPUB metadata is too large")
        return getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_METADATA_BYTES) { "EPUB metadata is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray().toString(Charsets.UTF_8).removePrefix("\uFEFF")
        }
    }

    private fun parseContainer(xml: String): String? {
        val parser = newParser(xml)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.localName() == "rootfile") {
                return parser.attribute("full-path")
            }
        }
        return null
    }

    private data class ManifestEntry(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )

    private fun parsePackage(xml: String, baseDirectory: String): LocalEpubBook {
        val parser = newParser(xml)
        var title: String? = null
        var description: String? = null
        var coverId: String? = null
        val authors = LinkedHashSet<String>()
        val manifest = LinkedHashMap<String, ManifestEntry>()
        val spine = ArrayList<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.localName()) {
                "title" -> if (title == null) title = parser.readTextContent()
                "creator" -> parser.readTextContent()?.let(authors::add)
                "description" -> if (description == null) description = parser.readTextContent()
                "meta" -> if (parser.attribute("name") == "cover") coverId = parser.attribute("content")
                "item" -> {
                    val id = parser.attribute("id") ?: continue
                    val href = parser.attribute("href") ?: continue
                    manifest[id] = ManifestEntry(
                        id = id,
                        href = href,
                        mediaType = parser.attribute("media-type").orEmpty(),
                        properties = parser.attribute("properties").orEmpty()
                    )
                }
                "spine" -> Unit
                "itemref" -> if (!parser.attribute("linear").equals("no", ignoreCase = true)) {
                    parser.attribute("idref")?.let(spine::add)
                }
            }
        }
        val navHrefs = manifest.values.filter { "nav" in it.properties }
            .mapTo(HashSet()) { resolveHref(baseDirectory, it.href) }
        val chapters = spine.mapNotNull { idref ->
            val entry = manifest[idref] ?: return@mapNotNull null
            val href = resolveHref(baseDirectory, entry.href)
            if (href in navHrefs || entry.mediaType.contains("nav", ignoreCase = true)) return@mapNotNull null
            LocalEpubChapter(
                title = href.substringAfterLast('/').substringBeforeLast('.')
                    .replace('_', ' ')
                    .ifBlank { "Chapter ${spine.indexOf(idref) + 1}" },
                entryName = href
            )
        }
        val cover = coverId?.let { manifest[it]?.href }
            ?: manifest.values.firstOrNull { "cover-image" in it.properties }?.href
            ?: manifest.values.firstOrNull {
                it.mediaType.startsWith("image/") &&
                    ("cover" in it.id.lowercase() || "cover" in it.href.lowercase())
            }?.href
        return LocalEpubBook(
            title = title?.trim()?.takeIf(String::isNotBlank),
            authors = authors.toList(),
            description = description?.trim()?.takeIf(String::isNotBlank),
            coverEntry = cover?.let { resolveHref(baseDirectory, it) },
            chapters = chapters
        )
    }

    private fun resolveHref(baseDirectory: String, href: String): String {
        val decoded = android.net.Uri.decode(href.substringBefore('#'))
        val segments = ArrayList<String>()
        if (baseDirectory.isNotEmpty() && !decoded.startsWith('/')) {
            segments.addAll(baseDirectory.split('/'))
        }
        for (segment in decoded.trimStart('/').replace('\\', '/').split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) {
                    throw IllegalArgumentException("EPUB entry escapes its package directory")
                } else {
                    segments.removeAt(segments.lastIndex)
                }
                else -> segments.add(segment)
            }
        }
        return segments.joinToString("/")
    }

    private fun newParser(xml: String): XmlPullParser = Xml.newPullParser().apply {
        setInput(xml.reader())
    }

    private fun XmlPullParser.localName(): String = name.orEmpty().substringAfterLast(':').lowercase()

    private fun XmlPullParser.attribute(name: String): String? {
        for (index in 0 until attributeCount) {
            if (getAttributeName(index).substringAfterLast(':').equals(name, ignoreCase = true)) {
                return getAttributeValue(index)
            }
        }
        return null
    }

    private fun XmlPullParser.readTextContent(): String? {
        if (eventType != XmlPullParser.START_TAG) return null
        val text = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> text.append(this.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return text.toString().trim().takeIf(String::isNotBlank)
    }
}
