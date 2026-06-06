package com.grindbell.app.services.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.grindbell.app.data.local.dao.ReminderDao
import com.grindbell.app.services.reminders.ReminderManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val reminderDao: ReminderDao,
    private val reminderManager: ReminderManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val dueReminders = reminderDao.getDueReminders(now)

        Log.d(TAG, "Worker check: ${dueReminders.size} due reminders found")

        dueReminders.take(20).forEach { reminder ->
            // Delegate to ReminderManager for proper missed handling
            // This includes: check if already done, reschedule repeat, etc.
            reminderManager.handleMissedReminder(reminder.id)
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "ReminderWorker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val request = PeriodicWorkRequestBuilder<ReminderWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "grindbell_reminder_check",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("grindbell_reminder_check")
        }
    }
}

class ReminderWorkerService : androidx.work.impl.foreground.SystemForegroundService()
