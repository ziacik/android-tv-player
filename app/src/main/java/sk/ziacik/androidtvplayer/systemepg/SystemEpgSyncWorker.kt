package sk.ziacik.androidtvplayer.systemepg

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

object SystemEpgSyncSchedule {
    const val periodHours = 6L
}

class SystemEpgSyncWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result = runCatching {
        val catalog = KanalikCatalog(applicationContext).load()
        SystemEpgPublisher(applicationContext).publish(catalog)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )

    companion object {
        private const val PERIODIC_WORK = "kanalik-system-epg-periodic"
        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(context: Context) {
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.cancelUniqueWork("kanalik-system-epg-now")
            manager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SystemEpgSyncWorker>(
                    SystemEpgSyncSchedule.periodHours,
                    TimeUnit.HOURS,
                ).setConstraints(networkConstraint).build(),
            )
        }
    }
}
