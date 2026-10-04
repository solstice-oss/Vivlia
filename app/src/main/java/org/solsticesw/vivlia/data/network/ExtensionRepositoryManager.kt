package org.solsticesw.vivlia.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.CachedManifestEntity
import org.solsticesw.vivlia.data.local.entity.ExtensionEntity
import org.solsticesw.vivlia.data.local.entity.ExtensionRepositoryEntity
import org.solsticesw.vivlia.data.local.entity.SourceEntity
import org.solsticesw.vivlia.domain.model.ProviderType

class ExtensionRepositoryManager(
    private val database: AppDatabase,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val repositoryDao = database.repositoryDao()
    private val extensionDao = database.extensionDao()
    private val sourceDao = database.sourceDao()

    suspend fun addRepository(
        inputUrl: String,
        customName: String? = null,
        forcedProviderType: ProviderType? = null
    ): Result<ExtensionRepositoryEntity> = withContext(Dispatchers.IO) {
        try {
            val defaultIndex = if (forcedProviderType == ProviderType.LN_READER || inputUrl.contains("plugins.min.json")) {
                "plugins.min.json"
            } else {
                "index.json"
            }

            val normalized = UrlNormalizer.normalize(inputUrl, defaultIndex)
            val repoId = normalized.repoId

            // Fetch manifest content from network
            val (contentJson, eTag) = fetchManifestContent(normalized.indexUrl)

            // Determine provider type
            val providerType = forcedProviderType
                ?: detectProviderType(normalized.indexUrl, contentJson)

            val repoName = if (!customName.isNullOrBlank()) {
                customName
            } else {
                deriveRepoName(normalized.originalUrl, repoId, providerType)
            }

            val repoEntity = ExtensionRepositoryEntity(
                id = repoId,
                name = repoName,
                url = normalized.originalUrl,
                rawUrl = normalized.indexUrl,
                type = providerType.name,
                lastRefreshedAt = System.currentTimeMillis(),
                isCustom = true,
                enabled = true
            )

            // Parse catalog
            val (extensions, sources) = parseCatalog(
                type = providerType,
                jsonString = contentJson,
                repoId = repoId,
                rawBaseUrl = normalized.rawBaseUrl
            )

            // Persist repository, extensions, sources, and cached manifest
            saveRepositoryData(repoEntity, contentJson, eTag, extensions, sources)

            Result.success(repoEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshRepository(repoId: String): Result<ExtensionRepositoryEntity> = withContext(Dispatchers.IO) {
        try {
            val repo = repositoryDao.getRepositoryById(repoId)
                ?: return@withContext Result.failure(IllegalArgumentException("Repository $repoId not found"))

            val providerType = ProviderType.fromString(repo.type)
            val defaultIndex = if (providerType == ProviderType.LN_READER) "plugins.min.json" else "index.json"
            val normalized = UrlNormalizer.normalize(repo.url, defaultIndex)

            val cachedManifest = repositoryDao.getCachedManifest(normalized.indexUrl)
            val (contentJson, newETag) = fetchManifestContent(normalized.indexUrl, cachedManifest?.eTag)

            val (extensions, sources) = parseCatalog(
                type = providerType,
                jsonString = contentJson,
                repoId = repoId,
                rawBaseUrl = normalized.rawBaseUrl
            )

            val updatedRepo = repo.copy(lastRefreshedAt = System.currentTimeMillis())
            saveRepositoryData(updatedRepo, contentJson, newETag, extensions, sources)

            Result.success(updatedRepo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAllRepositories(): List<Result<ExtensionRepositoryEntity>> = withContext(Dispatchers.IO) {
        val repos = repositoryDao.getAllRepositories()
        repos.map { repo ->
            refreshRepository(repo.id)
        }
    }

    suspend fun deleteRepository(repoId: String) = withContext(Dispatchers.IO) {
        val repo = repositoryDao.getRepositoryById(repoId)
        if (repo != null) {
            sourceDao.deleteSourcesForRepo(repoId)
            extensionDao.deleteExtensionsForRepo(repoId)
            repositoryDao.deleteCachedManifest(repo.rawUrl)
            repositoryDao.deleteRepository(repoId)
        }
    }

    private fun fetchManifestContent(url: String, eTag: String? = null): Pair<String, String?> {
        val requestBuilder = Request.Builder().url(url)
        if (!eTag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", eTag)
        }
        val request = requestBuilder.build()

        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 304 && !eTag.isNullOrBlank()) {
                val cached = database.repositoryDao().run {
                    kotlinx.coroutines.runBlocking { getCachedManifest(url) }
                }
                if (cached != null) {
                    return Pair(cached.contentJson, cached.eTag)
                }
            }

            if (!response.isSuccessful) {
                // If secondary fallback needed, try index.min.json or fallback
                if (response.code == 404 && url.endsWith("index.json")) {
                    val fallbackUrl = url.replace("index.json", "index.min.json")
                    return fetchManifestContent(fallbackUrl, null)
                }
                throw IllegalStateException("HTTP Error ${response.code} fetching catalog from $url")
            }

            val body = response.body.string()
            if (body.isEmpty()) {
                throw IllegalStateException("Empty response from $url")
            }
            val responseETag = response.header("ETag")
            return Pair(body, responseETag)
        }
    }

    private fun detectProviderType(url: String, contentJson: String): ProviderType {
        if (url.contains("plugins.min.json")) return ProviderType.LN_READER
        if (contentJson.contains("\"site\"") && contentJson.contains("\"type\"")) {
            return ProviderType.LN_READER
        }
        return ProviderType.MIHON
    }

    private fun parseCatalog(
        type: ProviderType,
        jsonString: String,
        repoId: String,
        rawBaseUrl: String
    ): Pair<List<ExtensionEntity>, List<SourceEntity>> {
        return when (type) {
            ProviderType.LN_READER -> LnReaderCatalogParser.parseAndMap(jsonString, repoId, rawBaseUrl)
            else -> MihonCatalogParser.parseAndMap(jsonString, repoId, rawBaseUrl)
        }
    }

    private suspend fun saveRepositoryData(
        repo: ExtensionRepositoryEntity,
        contentJson: String,
        eTag: String?,
        newExtensions: List<ExtensionEntity>,
        newSources: List<SourceEntity>
    ) {
        // Retrieve local state mappings to preserve user customizations
        val existingSources = sourceDao.getSourcesForRepo(repo.id).associateBy { it.id }
        val existingExtensions = extensionDao.getExtensionsForRepo(repo.id).associateBy { it.id }

        // Merge local states (pinned, enabled for sources; installed, hasUpdate for extensions)
        val mergedSources = newSources.map { source ->
            val existing = existingSources[source.id]
            if (existing != null) {
                source.copy(
                    pinned = existing.pinned,
                    enabled = existing.enabled
                )
            } else {
                source
            }
        }

        val mergedExtensions = newExtensions.map { ext ->
            val existing = existingExtensions[ext.id]
            if (existing != null) {
                ext.copy(
                    installed = existing.installed,
                    hasUpdate = existing.hasUpdate
                )
            } else {
                ext
            }
        }

        database.runInTransaction {
            kotlinx.coroutines.runBlocking {
                repositoryDao.insertRepository(repo)
                repositoryDao.insertCachedManifest(
                    CachedManifestEntity(
                        repoUrl = repo.rawUrl,
                        contentJson = contentJson,
                        fetchedAt = System.currentTimeMillis(),
                        eTag = eTag
                    )
                )
                extensionDao.deleteExtensionsForRepo(repo.id)
                extensionDao.insertExtensions(mergedExtensions)
                sourceDao.deleteSourcesForRepo(repo.id)
                sourceDao.insertSources(mergedSources)
            }
        }
    }

    private fun deriveRepoName(originalUrl: String, repoId: String, providerType: ProviderType): String {
        val typeLabel = if (providerType == ProviderType.LN_READER) "LNReader" else "Mihon"
        val cleanUrl = originalUrl.trim().removeSuffix("/")
        val lastSegment = cleanUrl.substringAfterLast("/")
        return if (lastSegment.isNotBlank() && !lastSegment.endsWith(".json")) {
            "$lastSegment ($typeLabel)"
        } else {
            "$repoId ($typeLabel)"
        }
    }


}
