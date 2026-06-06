package com.grindbell.app.presentation.notifications

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grindbell.app.presentation.theme.*
import com.grindbell.app.services.ReminderForegroundService
import com.grindbell.app.services.reminders.ReminderManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Full-screen popup for non-critical reminders (tasks + habits).
 * Back button disabled — user MUST tap Done or Snooze.
 *
 * On Done/Snooze: immediately broadcasts stop to ForegroundService
 * (kills sound + vibration instantly), then delegates to ReminderManager
 * for DB cleanup and alarm rescheduling.
 *
 * Implements onNewIntent() so repeat alarms update the popup content
 * instead of being silently ignored (singleInstance launch mode).
 */
@AndroidEntryPoint
class ReminderPopupActivity : ComponentActivity() {

    @Inject lateinit var reminderManager: ReminderManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var currentReminderId: Long = 0
    private var currentTaskId: Long = -1
    private var currentHabitId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "GrindBell:ReminderPopup"
        )
        wakeLock?.acquire(3 * 60 * 1000L)

        initVibrator()

        lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
                    stopVibrator()
                    releaseWakeLock()
                }
            }
        )

        processIntent(intent, isFirstLaunch = true)
    }

    /**
     * Called when a repeat alarm fires while the popup is already on screen.
     * Without this, the second+ popup shows stale content from the first alarm.
     * singleInstance launch mode routes subsequent startActivity calls here.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("ReminderPopup", "[POPUP-REPEAT] onNewIntent triggered — refreshing popup content")
        processIntent(intent, isFirstLaunch = false)
        // Re-trigger vibration on repeat
        vibratePattern(longArrayOf(0, 400, 200, 400, 200, 600))
        Log.d("ReminderPopup", "[POPUP-REPEAT] Vibration re-triggered, content refreshed")
    }

    private fun processIntent(intent: Intent, isFirstLaunch: Boolean) {
        currentReminderId = intent.getLongExtra("reminder_id", 0)
        val title = intent.getStringExtra("title") ?: "Reminder"
        val message = intent.getStringExtra("message") ?: ""
        currentTaskId = intent.getLongExtra("task_id", -1)
        currentHabitId = intent.getLongExtra("habit_id", -1)

        Log.d("ReminderPopup", "[POPUP-SHOW] reminderId=$currentReminderId title=$title habitId=$currentHabitId firstLaunch=$isFirstLaunch")

        // Vibrate on first open
        if (isFirstLaunch) {
            vibratePattern(longArrayOf(0, 400, 200, 400, 200, 600))
        }

        setContent {
            GrindBellTheme {
                ReminderPopupScreen(
                    title = title,
                    message = message,
                    isHabit = currentHabitId >= 0,
                    onDone = { handleDone(currentReminderId, currentTaskId, currentHabitId) },
                    onSnooze = { handleSnooze(currentReminderId) }
                )
            }
        }
    }

    private fun handleDone(reminderId: Long, taskId: Long, habitId: Long) {
        Log.d("ReminderPopup", "[POPUP-DONE] reminderId=$reminderId taskId=$taskId habitId=$habitId")
        // 1. Stop all effects IMMEDIATELY (sound + vibration)
        stopAllLocalEffects()
        broadcastStopToService(reminderId)

        // 2. Delegate DB cleanup to ReminderManager (async)
        val actualTaskId = if (taskId >= 0) taskId else null
        val actualHabitId = if (habitId >= 0) habitId else null
        reminderManager.markReminderDone(reminderId, actualTaskId, actualHabitId)

        finish()
    }

    private fun handleSnooze(reminderId: Long) {
        Log.d("ReminderPopup", "[POPUP-SNOOZE] reminderId=$reminderId")
        stopAllLocalEffects()
        broadcastStopToService(reminderId)
        reminderManager.snoozeReminder(reminderId, 15)
        finish()
    }

    /**
     * Broadcast ACTION_STOP_ALL_EFFECTS to the ForegroundService.
     * This kills notification sound + service vibrator IMMEDIATELY.
     * Critical: happens BEFORE ReminderManager cleanup to prevent sound from lingering.
     */
    private fun broadcastStopToService(reminderId: Long) {
        try {
            val stopIntent = Intent(this, ReminderForegroundService::class.java).apply {
                action = ReminderForegroundService.ACTION_STOP_ALL_EFFECTS
                putExtra(ReminderForegroundService.EXTRA_REMINDER_ID, reminderId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(stopIntent)
            } else {
                startService(stopIntent)
            }
        } catch (_: Exception) {}
    }

    private fun stopAllLocalEffects() {
        stopVibrator()
        releaseWakeLock()
    }

    override fun onBackPressed() {
        // Disabled — user must explicitly act
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java) as Vibrator
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (_: Exception) {}
    }

    private fun stopVibrator() {
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}

@Composable
fun ReminderPopupScreen(
    title: String,
    message: String,
    isHabit: Boolean,
    onDone: () -> Unit,
    onSnooze: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "popup_pulse")
    val bgAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgPulse"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    val accentColor = if (isHabit) HabitPurple else ElectricBlue

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1E1E2E), Color(0xFF1A1A2E).copy(alpha = bgAlpha), Color(0xFF0F0F1A)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                modifier = Modifier.size(80.dp).graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                    .clip(CircleShape).background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.NotificationsActive, null, tint = accentColor, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(20.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = accentColor.copy(alpha = 0.15f)) {
                Text(
                    if (isHabit) "🔔 HABIT REMINDER" else "🔔 TASK REMINDER",
                    style = GrindBellTypography.labelLarge.copy(color = accentColor),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = GrindBellTypography.displayMedium.copy(color = Color.White, textAlign = TextAlign.Center))
            Spacer(Modifier.height(8.dp))
            Text(message, style = GrindBellTypography.bodyLarge.copy(color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(40.dp))

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = Emerald)) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Done ✓", style = GrindBellTypography.titleLarge.copy(color = Color.White))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Icon(Icons.Filled.Snooze, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Snooze 15 min", style = GrindBellTypography.titleMedium.copy(color = Color.White.copy(alpha = 0.8f)))
            }
            Spacer(Modifier.height(20.dp))
            Text("Reminder will repeat until you mark it Done",
                style = GrindBellTypography.labelSmall.copy(color = Color.White.copy(alpha = 0.35f), textAlign = TextAlign.Center))
        }
    }
}
