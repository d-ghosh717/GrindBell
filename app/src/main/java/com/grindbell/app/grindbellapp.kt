package com.grindbell.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.net.Uri
import com.grindbell.app.services.ReminderForegroundService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GrindBellApp : Application() {

    companion object {
        const val CHANNEL_REMINDERS = "grindbell_reminders_v2"
        const val CHANNEL_CRITICAL = "grindbell_critical_v2"
        const val CHANNEL_HABITS = "grindbell_habits_v2"
        const val CHANNEL_FOREGROUND = "grindbell_foreground_v2"

        // Old channel IDs — deleted on upgrade so new sound config takes effect
        private val OLD_CHANNELS = listOf(
            "grindbell_reminders", "grindbell_critical", "grindbell_habits",
            "grindbell_persistent", "grindbell_foreground"
        )
    }

    override fun onCreate() {
        super.onCreate()
        deleteOldChannels()
        createNotificationChannels()
        startForegroundService()
    }

    /**
     * Delete old channels so new ones with sound/importance config take effect.
     * Android locks channel settings on first creation — we must delete-and-recreate.
     */
    private fun deleteOldChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        OLD_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        // Alarm audio attributes — plays even in silent/DND mode
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .build()

        // ── Foreground (low importance, no sound) ──
        val foregroundChannel = NotificationChannel(
            CHANNEL_FOREGROUND, "GrindBell Service", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for background reminder delivery"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        // ── Task Reminders — behaves like an ALARM ──
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDERS, "Task Reminders", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Task reminders with ALARM sound, vibration, and full-screen popup"
            setSound(alarmSound, alarmAttrs)
            enableVibration(true)
            setBypassDnd(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        // ── Critical Alarms — MAX importance, full bypass ──
        val criticalChannel = NotificationChannel(
            CHANNEL_CRITICAL, "Critical Alarms", NotificationManager.IMPORTANCE_MAX
        ).apply {
            description = "Full-screen alarm reminders — bypasses silent/DND mode"
            setSound(alarmSound, alarmAttrs)
            enableVibration(true)
            setBypassDnd(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        // ── Habit Reminders — same alarm behavior as tasks ──
        val habitChannel = NotificationChannel(
            CHANNEL_HABITS, "Habit Reminders", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Recurring habit reminders — ALARM sound + vibration + popup"
            setSound(alarmSound, alarmAttrs)
            enableVibration(true)
            setBypassDnd(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannels(
            listOf(foregroundChannel, reminderChannel, criticalChannel, habitChannel)
        )
    }

    private fun startForegroundService() {
        val intent = Intent(this, ReminderForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
