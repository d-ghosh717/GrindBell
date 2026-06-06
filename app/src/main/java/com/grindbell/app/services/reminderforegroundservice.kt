package com.grindbell.app.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.grindbell.app.GrindBellApp
import com.grindbell.app.MainActivity
import com.grindbell.app.presentation.notifications.AlarmActivity
import com.grindbell.app.presentation.notifications.ReminderPopupActivity
import com.grindbell.app.services.reminders.ReminderManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReminderForegroundService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var reminderManager: ReminderManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var vibrationTimeout: Runnable? = null

    companion object {
        const val TAG = "ReminderFgService"

        const val ACTION_TRIGGER_REMINDER = "com.grindbell.action.TRIGGER_REMINDER"
        const val ACTION_STOP_ALL_EFFECTS = "com.grindbell.action.STOP_ALL_EFFECTS"
        const val ACTION_SHUTDOWN = "com.grindbell.action.SHUTDOWN"

        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_IS_CRITICAL = "is_critical"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_HABIT_ID = "habit_id"

        const val FOREGROUND_NOTIFICATION_ID = 999000
        private const val VIBRATION_TIMEOUT_MS = 120_000L // 2 min safety cutoff
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        initVibrator()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_REMINDER -> {
                val rId = intent.getLongExtra(EXTRA_REMINDER_ID, 0)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "GrindBell"
                val msg = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
                val critical = intent.getBooleanExtra(EXTRA_IS_CRITICAL, false)
                val tId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                val hId = intent.getLongExtra(EXTRA_HABIT_ID, -1)

                acquireWakeLock()
                deliverReminder(rId, title, msg, critical, tId, hId)
            }

            ACTION_STOP_ALL_EFFECTS -> {
                val rId = intent.getLongExtra(EXTRA_REMINDER_ID, 0)
                stopReminderEffects(rId)
            }

            ACTION_SHUTDOWN -> {
                Log.d(TAG, "Shutdown")
                stopReminderEffects(-1)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelVibrationTimeout()
        stopVibrator()
        releaseWakeLock()
        super.onDestroy()
    }

    // ── FOREGROUND ──

    private fun startForegroundNotification() {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, GrindBellApp.CHANNEL_FOREGROUND)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 GrindBell Active")
            .setContentText("Watching your reminders")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        startForeground(FOREGROUND_NOTIFICATION_ID, n)
    }

    // ── VIBRATOR ──

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java) as Vibrator
        }
    }

    private fun startVibration(isCritical: Boolean) {
        try {
            val pattern = if (isCritical)
                longArrayOf(0, 500, 250, 500, 250, 1000, 250, 1500)
            else
                longArrayOf(0, 400, 200, 400, 200, 600)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            scheduleVibrationTimeout()
        } catch (_: Exception) {}
    }

    private fun stopVibrator() {
        try { vibrator?.cancel() } catch (_: Exception) {}
    }

    private fun scheduleVibrationTimeout() {
        cancelVibrationTimeout()
        vibrationTimeout = Runnable {
            Log.w(TAG, "Vibration auto-stop after ${VIBRATION_TIMEOUT_MS}ms")
            stopVibrator()
        }
        mainHandler.postDelayed(vibrationTimeout!!, VIBRATION_TIMEOUT_MS)
    }

    private fun cancelVibrationTimeout() {
        vibrationTimeout?.let { mainHandler.removeCallbacks(it) }
        vibrationTimeout = null
    }

    // ── WAKE LOCK ──

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "GrindBell:FgServiceWakeLock"
        )
        wakeLock?.acquire(30_000L)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ── REMINDER DELIVERY ──

    private fun deliverReminder(
        reminderId: Long, title: String, message: String,
        isCritical: Boolean, taskId: Long, habitId: Long
    ) {
        val actualTaskId = if (taskId >= 0) taskId else null
        val actualHabitId = if (habitId >= 0) habitId else null
        val isHabit = actualHabitId != null
        val now = System.currentTimeMillis()
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(now))

        Log.d(TAG, "[${if (isHabit) "HABIT" else "TASK"}-ALARM-FIRED] time=$timeStr reminderId=$reminderId habitId=$actualHabitId taskId=$actualTaskId")

        // ── STEP 1 (CRITICAL): Schedule next alarm FIRST ──
        // This MUST happen before any UI operations. If notification/popup
        // throw (e.g. PendingIntent creation fails under load), the chain
        // still continues because the next alarm is already scheduled.
        scheduleNextReminder(reminderId)
        Log.d(TAG, "[${if (isHabit) "HABIT" else "TASK"}-SCHEDULING-NEXT] time=$timeStr Next alarm scheduling launched for reminder $reminderId")

        // ── STEP 2: Post notification (best-effort, must not block chain) ──
        try {
            notificationHelper.showReminderNotification(
                callerContext = this,
                reminderId = reminderId,
                title = title,
                message = message,
                isCritical = isCritical,
                taskId = actualTaskId,
                habitId = actualHabitId
            )
            Log.d(TAG, "[${if (isHabit) "HABIT" else "TASK"}-NOTIFICATION-SHOWN] Notification posted for reminder $reminderId")
        } catch (e: Exception) {
            Log.e(TAG, "[${if (isHabit) "HABIT" else "TASK"}-NOTIFICATION-ERR] Notification failed (chain continues): ${e.message}", e)
        }

        // ── STEP 3: Vibration ──
        startVibration(isCritical)
        Log.d(TAG, "[${if (isHabit) "HABIT" else "TASK"}-VIBRATION-START] Vibration started for reminder $reminderId, critical=$isCritical")

        // ── STEP 4: Popup (best-effort) ──
        launchPopup(reminderId, title, message, isCritical, taskId, habitId)
        Log.d(TAG, "[${if (isHabit) "HABIT" else "TASK"}-POPUP-SHOWN] Popup launched for reminder $reminderId")
    }

    /**
     * Schedule the NEXT occurrence of this reminder.
     * Without this call, repeat reminders fire once and never again.
     * Handled by ReminderManager which manages the repeat chain in the database.
     */
    private fun scheduleNextReminder(reminderId: Long) {
        try {
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            Log.d(TAG, "[NEXT-SCHEDULE] time=$timeStr Calling handleMissedReminder for chain $reminderId")
            reminderManager.handleMissedReminder(reminderId)
            Log.d(TAG, "[NEXT-SCHEDULE] time=$timeStr handleMissedReminder launched for chain $reminderId")
        } catch (e: Exception) {
            Log.e(TAG, "[NEXT-SCHEDULE-ERR] Failed to schedule next reminder for $reminderId", e)
        }
    }

    private fun launchPopup(
        reminderId: Long, title: String, message: String,
        isCritical: Boolean, taskId: Long, habitId: Long
    ) {
        val cls = if (isCritical) AlarmActivity::class.java else ReminderPopupActivity::class.java
        try {
            startActivity(Intent(this, cls).apply {
                putExtra("reminder_id", reminderId)
                putExtra("title", title)
                putExtra("message", message)
                putExtra("task_id", taskId)
                putExtra("habit_id", habitId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            })
            Log.d(TAG, "Popup launched: ${cls.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch popup", e)
        }
    }

    // ── STOP EFFECTS ──

    /**
     * Stop vibration + wake lock for a specific reminder.
     * Does NOT cancel ALL notifications — only the specific reminder.
     * Does NOT touch the foreground service notification.
     */
    private fun stopReminderEffects(reminderId: Long) {
        Log.d(TAG, "Stopping effects for reminder $reminderId")
        cancelVibrationTimeout()
        stopVibrator()
        releaseWakeLock()
    }
}
