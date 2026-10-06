package org.solsticesw.vivlia.local

import java.io.File

object LocalStorageRules {
    private val chapterNumberPattern =
        Regex("""(?i)\b(?:chapter|ch)\s*[#._-]*\s*(\d+(?:\.\d+)?)""")
    private val volumeNumberPattern =
        Regex("""(?i)\b(?:volume|vol)\s*[#._-]*\s*(\d+(?:\.\d+)?)""")
    private val numberPattern = Regex("""(?:^|[\s._-])(\d+(?:\.\d+)?)(?:$|[\s._-])""")

    private val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "heic", "heif", "jxl"
    )

    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in imageExtensions

    fun isArchive(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension == "cbz" || extension == "zip"
    }

    fun isEpub(name: String): Boolean = name.substringAfterLast('.', "").equals("epub", ignoreCase = true)

    fun isSupportedImport(name: String): Boolean =
        isArchive(name) || isEpub(name) || name.substringAfterLast('.', "").equals("pdf", ignoreCase = true)

    fun isTemporaryOrHidden(name: String): Boolean =
        name.startsWith('.') || name.endsWith(".tmp", ignoreCase = true) || name.endsWith("_tmp", ignoreCase = true)

    fun isSafeArchiveEntry(name: String): Boolean {
        if (name.isBlank() || name.startsWith('/') || name.startsWith('\\')) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(name)) return false
        val normalized = name.replace('\\', '/')
        return normalized.split('/').none { it == ".." || it == "." }
    }

    fun hasMangaContent(file: File, depth: Int = 3): Boolean {
        val children = file.listFiles() ?: return false
        if (children.any { child ->
                child.isFile && (child.name.equals("index.json", true) ||
                    isImage(child.name) || isArchive(child.name) || isEpub(child.name))
            }
        ) {
            return true
        }
        return depth > 0 && children.any { it.isDirectory && hasMangaContent(it, depth - 1) }
    }

    fun compareNatural(first: String, second: String): Int {
        var left = 0
        var right = 0
        while (left < first.length && right < second.length) {
            val firstIsDigit = first[left].isDigit()
            val secondIsDigit = second[right].isDigit()
            if (firstIsDigit && secondIsDigit) {
                val firstEnd = first.indexOfFirstNonDigit(left)
                val secondEnd = second.indexOfFirstNonDigit(right)
                val firstDigits = first.substring(left, firstEnd).dropWhile { it == '0' }.ifEmpty { "0" }
                val secondDigits = second.substring(right, secondEnd).dropWhile { it == '0' }.ifEmpty { "0" }
                val lengthCompare = firstDigits.length.compareTo(secondDigits.length)
                if (lengthCompare != 0) return lengthCompare
                val numberCompare = firstDigits.compareTo(secondDigits)
                if (numberCompare != 0) return numberCompare
                val zeroCompare = (firstEnd - left).compareTo(secondEnd - right)
                if (zeroCompare != 0) return zeroCompare
                left = firstEnd
                right = secondEnd
            } else {
                val charCompare = first[left].lowercaseChar().compareTo(second[right].lowercaseChar())
                if (charCompare != 0) return charCompare
                left++
                right++
            }
        }
        return first.length.compareTo(second.length).takeIf { it != 0 } ?: first.compareTo(second)
    }

    fun parseChapterNumber(name: String): Float? {
        val match = chapterNumberPattern.find(name)
            ?: volumeNumberPattern.find(name)
            ?: numberPattern.find(name)
            ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    private fun String.indexOfFirstNonDigit(start: Int): Int {
        var index = start
        while (index < length && this[index].isDigit()) index++
        return index
    }
}
