package com.grindbell.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.grindbell.app.services.reminders.ReminderManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderManager: ReminderManager
    @Inject lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val TAG = "NotificationAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", 0)
        val action = intent.getStringExtra("action") ?: return
        val taskId = intent.getLongExtra("task_id", -1)
        val habitId = intent.getLongExtra("habit_id", -1)

        Log.d(TAG, "Action: $action reminderId=$reminderId taskId=$taskId habitId=$habitId")

        when (action) {
            "done" -> {
                // 1. IMMEDIATE: Cancel the notification (stops notification sound)
                notificationHelper.cancelReminderNotification(reminderId)

                // 2. Tell ForegroundService to stop its vibrator
                sendStopToForegroundService(context, reminderId)

                // 3. Delegate DB + alarm cleanup
                val actualTaskId = if (taskId >= 0) taskId else null
                val actualHabitId = if (habitId >= 0) habitId else null
                reminderManager.markReminderDone(reminderId, actualTaskId, actualHabitId)
            }
            "snooze" -> {
                notificationHelper.cancelReminderNotification(reminderId)
                sendStopToForegroundService(context, reminderId)
                reminderManager.snoozeReminder(reminderId, 15)
            }
        }
    }

    private fun sendStopToForegroundService(context: Context, reminderId: Long) {
        try {
            val stopIntent = Intent(context, ReminderForegroundService::class.java).apply {
                action = ReminderForegroundService.ACTION_STOP_ALL_EFFECTS
                putExtra(ReminderForegroundService.EXTRA_REMINDER_ID, reminderId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(stopIntent)
            } else {
                context.startService(stopIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send stop", e)
        }
    }
}
