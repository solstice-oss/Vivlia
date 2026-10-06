package org.solsticesw.vivlia.local

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.io.IOException

class LocalStorageWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = LocalStorageRepository(applicationContext)
        return try {
            val importUris = inputData.getStringArray(KEY_IMPORT_URIS)
                ?.map(Uri::parse)
                .orEmpty()
            if (importUris.isNotEmpty()) {
                repository.importUris(importUris) { complete, total ->
                    setProgress(
                        Data.Builder()
                            .putString(KEY_PHASE, PHASE_IMPORT)
                            .putInt(KEY_COMPLETE, complete)
                            .putInt(KEY_TOTAL, total)
                            .build()
                    )
                }
            }
            val summary = repository.scan { complete, total ->
                setProgress(
                    Data.Builder()
                        .putString(KEY_PHASE, PHASE_SCAN)
                        .putInt(KEY_COMPLETE, complete)
                        .putInt(KEY_TOTAL, total)
                        .build()
                )
            }
            Result.success(
                Data.Builder()
                    .putInt(KEY_RESULT_COUNT, summary.mangaCount)
                    .putStringArray(KEY_INACCESSIBLE_ROOTS, summary.inaccessibleRoots.toTypedArray())
                    .build()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SecurityException) {
            Result.failure(Data.Builder().putString(KEY_ERROR, error.message).build())
        } catch (error: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(Data.Builder().putString(KEY_ERROR, error.message).build())
        } catch (error: IllegalArgumentException) {
            Result.failure(Data.Builder().putString(KEY_ERROR, error.message).build())
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "vivlia_local_storage"
        const val KEY_PHASE = "phase"
        const val KEY_COMPLETE = "complete"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val KEY_RESULT_COUNT = "result_count"
        const val KEY_INACCESSIBLE_ROOTS = "inaccessible_roots"
        const val PHASE_SCAN = "scan"
        const val PHASE_IMPORT = "import"

        private const val KEY_IMPORT_URIS = "import_uris"
        private const val MAX_RETRIES = 2

        fun enqueueScan(context: Context) {
            val request = OneTimeWorkRequestBuilder<LocalStorageWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImport(context: Context, uris: List<Uri>) {
            require(uris.isNotEmpty())
            val request = OneTimeWorkRequestBuilder<LocalStorageWorker>()
                .setInputData(
                    Data.Builder()
                        .putStringArray(KEY_IMPORT_URIS, uris.map(Uri::toString).toTypedArray())
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
