package com.grindbell.app.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.grindbell.app.GrindBellApp
import com.grindbell.app.presentation.notifications.AlarmActivity
import com.grindbell.app.presentation.notifications.ReminderPopupActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val TAG = "NotificationHelper"
    }

    /**
     * Tracks the last notification ID used for each reminder.
     * When a repeat fires, we cancel the old notification and post a NEW one
     * with a unique ID — this ensures Android plays sound + vibration each time
     * instead of treating it as a silent update.
     */
    private val activeNotificationIds = java.util.concurrent.ConcurrentHashMap<Long, Int>()
    private var idCounter = 0

    fun showReminderNotification(
        callerContext: Context,
        reminderId: Long,
        title: String,
        message: String,
        isCritical: Boolean = false,
        taskId: Long? = null,
        habitId: Long? = null
    ) {
        val channelId = when {
            isCritical -> GrindBellApp.CHANNEL_CRITICAL
            habitId != null -> GrindBellApp.CHANNEL_HABITS
            else -> GrindBellApp.CHANNEL_REMINDERS
        }

        // ── CANCEL the OLD notification for this reminder ──
        // Without this, Android treats notify() with a new ID as a separate
        // notification, stacking duplicates. We want exactly ONE per reminder.
        activeNotificationIds.remove(reminderId)?.let { oldId ->
            notificationManager.cancel(oldId)
        }

        // ── Generate a UNIQUE notification ID ──
        // Using a fresh ID each time ensures Android treats this as a NEW
        // notification: sound plays, vibration fires, heads-up popup shows.
        // This is the fix for "no repeated sound/vibration/popup" on habits.
        val notificationId = ((reminderId.toInt() and 0xFFFF) shl 16) or (idCounter++ and 0xFFFF)
        activeNotificationIds[reminderId] = notificationId

        // ── Done action ──
        val donePending = PendingIntent.getBroadcast(
            context, notificationId * 10 + 1,
            createActionIntent(reminderId, "done", taskId, habitId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Snooze action ──
        val snoozePending = PendingIntent.getBroadcast(
            context, notificationId * 10 + 2,
            createActionIntent(reminderId, "snooze", taskId, habitId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Tap notification → opens detail/popup ──
        val contentIntent = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, ReminderPopupActivity::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("title", title)
                putExtra("message", message)
                putExtra("task_id", taskId ?: -1)
                putExtra("habit_id", habitId ?: -1)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Full-screen intent: makes notification pop up over any app ──
        val fullScreenIntent = PendingIntent.getActivity(
            context, notificationId * 100,
            Intent(context, ReminderPopupActivity::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("title", title)
                putExtra("message", message)
                putExtra("task_id", taskId ?: -1)
                putExtra("habit_id", habitId ?: -1)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = if (isCritical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH
        val vibrationPattern = if (isCritical)
            longArrayOf(0, 500, 250, 500, 250, 1000, 250, 1500)
        else
            longArrayOf(0, 400, 200, 400, 200, 600)

        // Build notification — with full-screen intent for guaranteed popup delivery
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (isCritical) "🚨 $title" else "🔔 $title")
            .setContentText(message)
            .setPriority(priority)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .addAction(android.R.drawable.ic_media_pause, "Snooze 15m", snoozePending)
            .addAction(android.R.drawable.ic_input_add, "Done ✓", donePending)
            .setAutoCancel(false)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(vibrationPattern)

        notificationManager.notify(notificationId, builder.build())

        Log.d(TAG, "[NOTIFICATION-SHOW] Posted NEW notification: id=$notificationId channel=$channelId title=$title (old notification cancelled)")
    }

    fun updateNotificationContent(
        reminderId: Long, newTitle: String, newMessage: String, taskId: Long?, habitId: Long?
    ) {
        val channelId = if (habitId != null) GrindBellApp.CHANNEL_HABITS else GrindBellApp.CHANNEL_REMINDERS

        // Get the CURRENT active notification ID for this reminder
        val currentId = activeNotificationIds[reminderId]
        if (currentId == null) {
            Log.d(TAG, "[UPDATE-SKIP] No active notification for reminder $reminderId, skipping update")
            return
        }

        // Cancel old, generate new unique ID
        notificationManager.cancel(currentId)
        val notificationId = ((reminderId.toInt() and 0xFFFF) shl 16) or (idCounter++ and 0xFFFF)
        activeNotificationIds[reminderId] = notificationId

        val contentIntent = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, ReminderPopupActivity::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("title", newTitle)
                putExtra("message", newMessage)
                putExtra("task_id", taskId ?: -1)
                putExtra("habit_id", habitId ?: -1)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val donePending = PendingIntent.getBroadcast(
            context, notificationId * 10 + 1,
            createActionIntent(reminderId, "done", taskId, habitId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePending = PendingIntent.getBroadcast(
            context, notificationId * 10 + 2,
            createActionIntent(reminderId, "snooze", taskId, habitId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 $newTitle")
            .setContentText(newMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_pause, "Snooze 15m", snoozePending)
            .addAction(android.R.drawable.ic_input_add, "Done ✓", donePending)
            .setAutoCancel(false)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        notificationManager.notify(notificationId, builder.build())
        Log.d(TAG, "[UPDATE-CONTENT] Updated notification for reminder $reminderId → id=$notificationId: $newMessage")
    }

    /**
     * Cancel ONLY the specific reminder notification (all instances).
     * Does NOT touch the foreground service notification.
     */
    fun cancelReminderNotification(reminderId: Long) {
        // Cancel currently tracked notification
        activeNotificationIds.remove(reminderId)?.let { notificationManager.cancel(it) }
        Log.d(TAG, "[CANCEL] Notification cancelled for reminder $reminderId")
    }

    /**
     * Cancel ALL notifications — use only when shutting down the entire service.
     */
    fun cancelAll() {
        activeNotificationIds.values.forEach { notificationManager.cancel(it) }
        activeNotificationIds.clear()
        notificationManager.cancelAll()
    }

    private fun createActionIntent(
        reminderId: Long, action: String, taskId: Long?, habitId: Long?
    ): Intent = Intent(context, NotificationActionReceiver::class.java).apply {
        putExtra("reminder_id", reminderId)
        putExtra("action", action)
        taskId?.let { putExtra("task_id", it) }
        habitId?.let { putExtra("habit_id", it) }
    }
}
