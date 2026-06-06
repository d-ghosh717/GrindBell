package com.grindbell.app.services.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.grindbell.app.domain.model.ReminderMode
import com.grindbell.app.services.ReminderForegroundService
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BroadcastReceiver triggered by AlarmManager.
 * 
 * Instead of handling notifications directly (which fails when the process
 * is dead or the BroadcastReceiver's ~10s window is insufficient),
 * this receiver immediately starts the ReminderForegroundService.
 * 
 * The ForegroundService has:
 * - High process priority (won't be killed mid-delivery)
 * - Long execution window
 * - WakeLock acquisition
 * - Full notification + popup delivery
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", 0)
        val title = intent.getStringExtra("title") ?: "GrindBell"
        val message = intent.getStringExtra("message") ?: ""
        val isCritical = intent.getBooleanExtra("is_critical", false)
        val taskId = intent.getLongExtra("task_id", -1)
        val habitId = intent.getLongExtra("habit_id", -1)

        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val isHabit = habitId >= 0
        Log.d(TAG, "[${if (isHabit) "HABIT" else "TASK"}-ALARM-FIRED] time=$timeStr reminderId=$reminderId, title=$title, habitId=$habitId, taskId=$taskId, critical=$isCritical")

        // Route everything to the ForegroundService for reliable delivery
        val serviceIntent = Intent(context, ReminderForegroundService::class.java).apply {
            action = ReminderForegroundService.ACTION_TRIGGER_REMINDER
            putExtra(ReminderForegroundService.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderForegroundService.EXTRA_TITLE, title)
            putExtra(ReminderForegroundService.EXTRA_MESSAGE, message)
            putExtra(ReminderForegroundService.EXTRA_IS_CRITICAL, isCritical)
            putExtra(ReminderForegroundService.EXTRA_TASK_ID, taskId)
            putExtra(ReminderForegroundService.EXTRA_HABIT_ID, habitId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "AlarmScheduler"
    }

    @Suppress("DEPRECATION")
    fun scheduleReminder(
        reminderId: Long,
        title: String,
        message: String,
        triggerAtMillis: Long,
        reminderMode: ReminderMode = ReminderMode.NORMAL,
        isCritical: Boolean = false,
        taskId: Long? = null,
        habitId: Long? = null
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra("title", title)
            putExtra("message", message)
            putExtra("is_critical", isCritical)
            putExtra("reminder_mode", reminderMode.name)
            taskId?.let { putExtra("task_id", it) }
            habitId?.let { putExtra("habit_id", it) }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, reminderId.toInt(), intent, flags
        )

        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(triggerAtMillis))
        Log.d(TAG, "[ALARM-SCHEDULE] reminderId=$reminderId, time=$timeStr (${triggerAtMillis}), habitId=$habitId, taskId=$taskId")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when {
                    alarmManager.canScheduleExactAlarms() -> {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                        )
                        Log.d(TAG, "[ALARM-SCHEDULE] setExactAndAllowWhileIdle for $reminderId at $timeStr")
                    }
                    else -> {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                        )
                        Log.w(TAG, "[ALARM-SCHEDULE] Exact alarms not allowed, using inexact for $reminderId")
                    }
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
                Log.d(TAG, "[ALARM-SCHEDULE] setExactAndAllowWhileIdle (pre-S) for $reminderId at $timeStr")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "[ALARM-SCHEDULE-ERR] SecurityException for $reminderId", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Exception) {
                Log.e(TAG, "[ALARM-SCHEDULE-ERR] All scheduling methods failed for $reminderId")
            }
        }
    }

    /**
     * Cancel an existing alarm WITHOUT modifying the PendingIntent's stored Intent.
     * Uses FLAG_NO_CREATE — if no PI exists yet, returns null silently.
     * CRITICAL: Does NOT use FLAG_UPDATE_CURRENT because that would replace
     * the PendingIntent's Intent extras with an empty Intent, breaking
     * subsequent re-schedule calls (the root cause of habit repeat failures).
     */
    fun cancelReminder(reminderId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val flags = PendingIntent.FLAG_NO_CREATE or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminderId.toInt(), intent, flags
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "[ALARM-CANCEL] Cancelled alarm for reminderId=$reminderId")
        } else {
            Log.d(TAG, "[ALARM-CANCEL] No existing PI for reminderId=$reminderId (already cancelled or never created)")
        }
    }

    fun cancelAll() {
        for (i in 0..9999) {
            try {
                val intent = Intent(context, AlarmReceiver::class.java)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pi = PendingIntent.getBroadcast(context, i, intent, flags)
                alarmManager.cancel(pi)
            } catch (_: Exception) { }
        }
    }
}
