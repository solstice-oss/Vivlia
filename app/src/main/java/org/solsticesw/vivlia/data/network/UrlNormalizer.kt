package org.solsticesw.vivlia.data.network

import java.net.URI
import java.net.URISyntaxException

object UrlNormalizer {

    data class NormalizedRepoInfo(
        val originalUrl: String,
        val repoId: String,
        val rawBaseUrl: String,
        val indexUrl: String
    )

    fun normalize(inputUrl: String, defaultIndexFile: String = "index.json"): NormalizedRepoInfo {
        var trimmed = inputUrl.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        } else if (trimmed.startsWith("http://")) {
            trimmed = "https://" + trimmed.substring(7)
        }

        val rawUrl = convertToRawUrl(trimmed)
        val indexUrl = determineIndexUrl(rawUrl, defaultIndexFile)
        val rawBaseUrl = deriveBaseUrl(indexUrl)
        val repoId = generateRepoId(trimmed)

        return NormalizedRepoInfo(
            originalUrl = inputUrl,
            repoId = repoId,
            rawBaseUrl = rawBaseUrl,
            indexUrl = indexUrl
        )
    }

    fun convertToRawUrl(url: String): String {
        var cleanUrl = url.trim()
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.dropLast(1)
        }

        try {
            val uri = URI(cleanUrl)
            val host = uri.host?.lowercase() ?: ""
            if (host == "github.com" || host == "www.github.com") {
                val pathSegments = uri.path.split("/").filter { it.isNotEmpty() }
                if (pathSegments.size >= 2) {
                    val user = pathSegments[0]
                    val repo = pathSegments[1]
                    if (pathSegments.size == 2) {
                        // e.g. https://github.com/user/repo -> https://raw.githubusercontent.com/user/repo/main
                        return "https://raw.githubusercontent.com/$user/$repo/main"
                    } else if (pathSegments.size >= 4 && (pathSegments[2] == "tree" || pathSegments[2] == "blob")) {
                        val branch = pathSegments[3]
                        val subPath = pathSegments.drop(4).joinToString("/")
                        return if (subPath.isNotEmpty()) {
                            "https://raw.githubusercontent.com/$user/$repo/$branch/$subPath"
                        } else {
                            "https://raw.githubusercontent.com/$user/$repo/$branch"
                        }
                    } else {
                        val remaining = pathSegments.drop(2).joinToString("/")
                        return "https://raw.githubusercontent.com/$user/$repo/$remaining"
                    }
                }
            }
        } catch (e: URISyntaxException) {
            // Fallback if URI parsing fails
        }
        return cleanUrl
    }

    private fun determineIndexUrl(rawUrl: String, defaultIndexFile: String): String {
        val lower = rawUrl.lowercase()
        return if (lower.endsWith(".json") || lower.endsWith(".pb") || lower.endsWith(".min.json")) {
            rawUrl
        } else {
            if (rawUrl.endsWith("/")) {
                "$rawUrl$defaultIndexFile"
            } else {
                "$rawUrl/$defaultIndexFile"
            }
        }
    }

    private fun deriveBaseUrl(indexUrl: String): String {
        val lastSlash = indexUrl.lastIndexOf('/')
        return if (lastSlash != -1) {
            indexUrl.substring(0, lastSlash + 1)
        } else {
            "$indexUrl/"
        }
    }

    private fun generateRepoId(url: String): String {
        return try {
            val uri = URI(url)
            val host = uri.host ?: "repo"
            val path = uri.path.trim('/')
            val sanitizedPath = path.replace('/', '_').replace('.', '_')
            if (sanitizedPath.isNotEmpty()) {
                "${host}_$sanitizedPath"
            } else {
                host
            }
        } catch (e: Exception) {
            url.replace("://", "_").replace('/', '_').replace('.', '_')
        }
    }
}
