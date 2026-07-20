package com.matrix.devlog.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.matrix.devlog.data.ContributionDatabase
import com.matrix.devlog.data.ContributionRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class DataRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        Log.d("DataRefreshWorker", "Starting periodic data refresh")
        val db = ContributionDatabase.getDatabase(applicationContext)
        val repository = ContributionRepository(applicationContext, db.contributionDao())
        
        try {
            val accounts = db.contributionDao().getAllAccountsFlow().first()
            for (account in accounts) {
                if (account.username.isNotBlank()) {
                    Log.d("DataRefreshWorker", "Refreshing ${account.id}...")
                    repository.refreshAccountData(account.id)
                }
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e("DataRefreshWorker", "Error refreshing data", e)
            return Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "DataRefreshWork"

        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DataRefreshWorker>(4, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d("DataRefreshWorker", "Periodic work enqueued (every 4 hours)")
        }

        fun enqueueOneTimeWork(context: Context) {
            val request = OneTimeWorkRequestBuilder<DataRefreshWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueue(request)
            Log.d("DataRefreshWorker", "One-time immediate refresh enqueued")
        }
    }
}
