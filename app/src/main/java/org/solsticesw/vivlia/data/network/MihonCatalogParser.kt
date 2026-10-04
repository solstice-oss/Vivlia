package org.solsticesw.vivlia.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.solsticesw.vivlia.data.local.entity.ExtensionEntity
import org.solsticesw.vivlia.data.local.entity.SourceEntity
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.ProviderType

@Serializable
data class MihonSourceDto(
    val id: JsonElement? = null,
    val name: String = "",
    val baseUrl: String = "",
    val lang: String = "en",
    val isNsfw: JsonElement? = null
)

@Serializable
data class MihonExtensionDto(
    val name: String = "",
    val pkg: String = "",
    val apk: String = "",
    val lang: String = "en",
    val code: Int = 1,
    val version: String = "1.0.0",
    val nsfw: JsonElement? = null,
    val icon: String? = null,
    val sources: List<MihonSourceDto> = emptyList()
)

object MihonCatalogParser {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun parseCatalog(jsonString: String): List<MihonExtensionDto> {
        return try {
            json.decodeFromString<List<MihonExtensionDto>>(jsonString)
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
            if (dto.pkg.isBlank()) continue

            val isExtensionNsfw = parseNsfw(dto.nsfw)
            val iconUrl = resolveUrl(rawBaseUrl, dto.icon ?: "icon/${dto.pkg}.png")
            val apkUrl = if (dto.apk.isNotBlank()) resolveUrl(rawBaseUrl, dto.apk) else null
            val extensionId = "${repoId}_${dto.pkg}"

            val extensionEntity = ExtensionEntity(
                id = extensionId,
                repoId = repoId,
                name = dto.name,
                pkgName = dto.pkg,
                versionName = dto.version,
                versionCode = dto.code,
                lang = dto.lang,
                isNsfw = isExtensionNsfw,
                iconUrl = iconUrl,
                apkUrl = apkUrl
            )
            extensions.add(extensionEntity)

            if (dto.sources.isNotEmpty()) {
                for (sourceDto in dto.sources) {
                    val sourceIdStr = parseSourceId(sourceDto.id, dto.pkg)
                    val isSourceNsfw = parseNsfw(sourceDto.isNsfw) || isExtensionNsfw

                    val sourceEntity = SourceEntity(
                        id = "${extensionId}_$sourceIdStr",
                        extensionId = extensionId,
                        repoId = repoId,
                        name = sourceDto.name.ifBlank { dto.name },
                        lang = sourceDto.lang.ifBlank { dto.lang },
                        baseUrl = sourceDto.baseUrl,
                        iconUrl = iconUrl,
                        providerType = ProviderType.MIHON.name,
                        mediaType = MediaType.MANGA.name,
                        supportsLatest = true,
                        isNsfw = isSourceNsfw
                    )
                    sources.add(sourceEntity)
                }
            } else {
                // If sources array is empty, create a source entry derived from extension metadata
                val sourceEntity = SourceEntity(
                    id = "${extensionId}_main",
                    extensionId = extensionId,
                    repoId = repoId,
                    name = dto.name,
                    lang = dto.lang,
                    baseUrl = "",
                    iconUrl = iconUrl,
                    providerType = ProviderType.MIHON.name,
                    mediaType = MediaType.MANGA.name,
                    supportsLatest = true,
                    isNsfw = isExtensionNsfw
                )
                sources.add(sourceEntity)
            }
        }

        return Pair(extensions, sources)
    }

    private fun parseSourceId(element: JsonElement?, pkg: String): String {
        if (element == null) return pkg.hashCode().toString()
        return try {
            val prim = element.jsonPrimitive
            if (prim.isString) prim.content else prim.content
        } catch (e: Exception) {
            pkg.hashCode().toString()
        }
    }

    private fun parseNsfw(element: JsonElement?): Boolean {
        if (element == null) return false
        return try {
            val prim = element.jsonPrimitive
            prim.booleanOrNull ?: ((prim.intOrNull ?: 0) != 0)
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveUrl(baseUrl: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.drop(1) else path
        return cleanBase + cleanPath
    }
}
