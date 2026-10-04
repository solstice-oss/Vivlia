package org.solsticesw.vivlia.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.solsticesw.vivlia.data.local.entity.ExtensionEntity
import org.solsticesw.vivlia.data.local.entity.SourceEntity
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.ProviderType

@Serializable
data class LnReaderPluginDto(
    val id: String = "",
    val name: String = "",
    val site: String = "",
    val sourceSite: String = "",
    val lang: String = "English",
    val version: String = "1.0.0",
    val icon: String = "",
    val url: String = "",
    val path: String = "",
    val type: String = "novel"
)

object LnReaderCatalogParser {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun parseCatalog(jsonString: String): List<LnReaderPluginDto> {
        return try {
            json.decodeFromString<List<LnReaderPluginDto>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseAndMap(
        jsonString: String,
        repoId: String,
        rawBaseUrl: String
    ): Pair<List<ExtensionEntity>, List<SourceEntity>> {
        val dtos = parseCatalog(jsonString)
        val extensions = mutableListOf<ExtensionEntity>()
        val sources = mutableListOf<SourceEntity>()

        for (dto in dtos) {
            val pluginId = dto.id.ifBlank { dto.name.lowercase().replace(" ", "_") }
            if (pluginId.isBlank()) continue

            val extensionId = "${repoId}_$pluginId"
            val iconUrl = if (dto.icon.isNotBlank()) resolveUrl(rawBaseUrl, dto.icon) else null
            val pluginCodeUrl = resolveUrl(rawBaseUrl, dto.url.ifBlank { dto.path })

            val extensionEntity = ExtensionEntity(
                id = extensionId,
                repoId = repoId,
                name = dto.name,
                pkgName = pluginId,
                versionName = dto.version,
                versionCode = parseVersionCode(dto.version),
                lang = dto.lang,
                isNsfw = false,
                iconUrl = iconUrl,
                apkUrl = pluginCodeUrl
            )
            extensions.add(extensionEntity)

            val baseUrl = dto.site.ifBlank { dto.sourceSite }
            val sourceEntity = SourceEntity(
                id = "${extensionId}_main",
                extensionId = extensionId,
                repoId = repoId,
                name = dto.name,
                lang = dto.lang,
                baseUrl = baseUrl,
                iconUrl = iconUrl,
                providerType = ProviderType.LN_READER.name,
                mediaType = MediaType.NOVEL.name,
                supportsLatest = true,
                isNsfw = false
            )
            sources.add(sourceEntity)
        }

        return Pair(extensions, sources)
    }

    private fun parseVersionCode(version: String): Int {
        return try {
            val parts = version.split(".")
            var code = 0
            for (part in parts) {
                code = code * 100 + (part.filter { it.isDigit() }.toIntOrNull() ?: 0)
            }
            if (code == 0) 1 else code
        } catch (e: Exception) {
            1
        }
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        if (path.isEmpty()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.drop(1) else path
        return cleanBase + cleanPath
    }
}
