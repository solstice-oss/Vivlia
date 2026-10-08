package org.solsticesw.vivlia.data.extension

import android.app.Application
import android.content.Context
import androidx.room.withTransaction
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.solsticesw.vivlia.data.local.AppDatabase
import org.solsticesw.vivlia.data.local.entity.ExtensionEntity
import org.solsticesw.vivlia.data.local.entity.SourceEntity
import org.solsticesw.vivlia.domain.model.MediaType
import org.solsticesw.vivlia.domain.model.ProviderType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.util.concurrent.ConcurrentHashMap

class ExtensionManager(
    private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: ExtensionManager? = null

        fun getInstance(context: Context, database: AppDatabase): ExtensionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExtensionManager(context.applicationContext, database).also { INSTANCE = it }
            }
        }

        fun getInstance(context: Context): ExtensionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val db = AppDatabase.getInstance(context)
                    ExtensionManager(context.applicationContext, db).also { INSTANCE = it }
                }
            }
        }

        fun getInstanceOrNull(): ExtensionManager? = INSTANCE
    }

    private val loader = ExtensionLoader(context)
    private val activeSources = ConcurrentHashMap<Long, Source>()

    private val _loadedExtensionsFlow = MutableStateFlow<List<LoadedExtension>>(emptyList())
    val loadedExtensionsFlow: StateFlow<List<LoadedExtension>> = _loadedExtensionsFlow.asStateFlow()

    private val _activeSourcesFlow = MutableStateFlow<List<Source>>(emptyList())
    val activeSourcesFlow: StateFlow<List<Source>> = _activeSourcesFlow.asStateFlow()

    private val sourceDao = database.sourceDao()
    private val extensionDao = database.extensionDao()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        setupInjekt(context)
        scope.launch {
            refreshInstalledExtensions()
        }
    }

    private fun setupInjekt(context: Context) {
        val appContext = context.applicationContext
        val app = appContext as? Application

        try {
            if (app != null) {
                Injekt.addSingleton<Application>(app)
            }
        } catch (_: Throwable) {}

        try {
            Injekt.addSingleton<Context>(appContext)
        } catch (_: Throwable) {}

        val networkHelper = try {
            NetworkHelper(appContext)
        } catch (_: Throwable) {
            null
        }

        if (networkHelper != null) {
            try {
                Injekt.addSingleton(networkHelper)
            } catch (_: Throwable) {}

            try {
                Injekt.addSingleton<OkHttpClient>(networkHelper.client)
            } catch (_: Throwable) {}
        }

        try {
            Injekt.addSingleton(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            )
        } catch (_: Throwable) {}
    }

    suspend fun refreshInstalledExtensions(): List<LoadedExtension> = withContext(Dispatchers.IO) {
        val loaded = loader.loadInstalledExtensions()
        _loadedExtensionsFlow.value = loaded

        val extensionEntities = mutableListOf<ExtensionEntity>()
        val sourceEntities = mutableListOf<SourceEntity>()
        val newActiveSources = ConcurrentHashMap<Long, Source>()

        for (ext in loaded) {
            val repoId = "installed_extension"
            extensionEntities.add(
                ExtensionEntity(
                    id = ext.pkgName,
                    repoId = repoId,
                    name = ext.name,
                    pkgName = ext.pkgName,
                    versionName = ext.versionName,
                    versionCode = ext.versionCode.toInt(),
                    lang = ext.lang,
                    isNsfw = ext.isNsfw,
                    installed = true,
                    hasUpdate = false
                )
            )

            for (source in ext.sources) {
                newActiveSources[source.id] = source
                val baseUrl = (source as? HttpSource)?.baseUrl ?: ""
                sourceEntities.add(
                    SourceEntity(
                        id = source.id.toString(),
                        extensionId = ext.pkgName,
                        repoId = repoId,
                        name = source.name,
                        lang = source.lang,
                        baseUrl = baseUrl,
                        providerType = ProviderType.MIHON.name,
                        mediaType = MediaType.MANGA.name,
                        supportsLatest = source.supportsLatest,
                        isNsfw = ext.isNsfw,
                        enabled = true
                    )
                )
            }
        }

        activeSources.clear()
        activeSources.putAll(newActiveSources)
        _activeSourcesFlow.value = activeSources.values.toList()

        if (sourceEntities.isNotEmpty() || extensionEntities.isNotEmpty()) {
            val existingSources = sourceDao.getAllSources().associateBy { it.id }
            val mergedSources = sourceEntities.map { newSource ->
                val existing = existingSources[newSource.id]
                if (existing != null) {
                    newSource.copy(
                        pinned = existing.pinned,
                        enabled = true
                    )
                } else {
                    newSource
                }
            }
            database.withTransaction {
                extensionDao.insertExtensions(extensionEntities)
                if (mergedSources.isNotEmpty()) {
                    sourceDao.upsertSources(mergedSources)
                }
            }
        }

        loaded
    }

    fun getSource(sourceId: Long): Source? {
        if (activeSources.isEmpty()) {
            synchronized(this) {
                if (activeSources.isEmpty()) {
                    val loaded = loader.loadInstalledExtensions()
                    for (ext in loaded) {
                        for (source in ext.sources) {
                            activeSources[source.id] = source
                        }
                    }
                    _activeSourcesFlow.value = activeSources.values.toList()
                }
            }
        }
        return activeSources[sourceId]
    }

    fun getSource(sourceIdStr: String): Source? {
        val id = sourceIdStr.toLongOrNull() ?: return null
        return getSource(id)
    }

    fun getActiveSources(): List<Source> {
        return activeSources.values.toList()
    }
}
