package com.grindbell.app.presentation.notifications

import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
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

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var reminderManager: ReminderManager

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

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
            "GrindBell:CriticalAlarm"
        )
        wakeLock?.acquire(5 * 60 * 1000L)

        val reminderId = intent.getLongExtra("reminder_id", 0)
        val title = intent.getStringExtra("title") ?: "Critical Reminder"
        val message = intent.getStringExtra("message") ?: ""
        val taskId = intent.getLongExtra("task_id", -1)
        val habitId = intent.getLongExtra("habit_id", -1)

        // Init vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java) as Vibrator
        }

        // Start looping alarm sound
        val ringtone = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        mediaPlayer = MediaPlayer.create(this, ringtone)?.apply {
            isLooping = true
            start()
        }

        // Start continuous vibration
        startContinuousVibration()

        setContent {
            GrindBellTheme {
                CriticalReminderScreen(
                    title = title, message = message,
                    onDone = {
                        stopAllLocalEffects()
                        broadcastStopToService(reminderId)
                        val actualTaskId = if (taskId >= 0) taskId else null
                        val actualHabitId = if (habitId >= 0) habitId else null
                        reminderManager.markReminderDone(reminderId, actualTaskId, actualHabitId)
                        finish()
                    },
                    onSnooze = {
                        stopAllLocalEffects()
                        broadcastStopToService(reminderId)
                        reminderManager.snoozeReminder(reminderId, 15)
                        finish()
                    }
                )
            }
        }

        lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
                    stopAllLocalEffects()
                }
            }
        )
    }

    /**
     * Broadcast ACTION_STOP_ALL_EFFECTS to ForegroundService.
     * Kills notification sound + service vibrator synchronously.
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
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startContinuousVibration() {
        try {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 250, 500, 250, 1000, 250, 1500), 0
                )
            )
        } catch (_: Exception) {}
    }
}

@Composable
fun CriticalReminderScreen(
    title: String, message: String, onDone: () -> Unit, onSnooze: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "bgPulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Coral.copy(alpha = 0.3f), Color(0xFF1E1E2E), Color(0xFF1A1A2E), Color(0xFF0F0F1A)))
        ).graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Coral.copy(alpha = glowAlpha)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Alarm, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = Coral.copy(alpha = 0.2f)) {
                Text("⚠️ CRITICAL REMINDER", style = GrindBellTypography.labelLarge.copy(color = Coral),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = GrindBellTypography.displayMedium.copy(color = Color.White, textAlign = TextAlign.Center))
            Spacer(Modifier.height(8.dp))
            Text(message, style = GrindBellTypography.bodyLarge.copy(color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center),
                modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(48.dp))

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.buttonColors(containerColor = Emerald)) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Mark as Done", style = GrindBellTypography.titleLarge.copy(color = Color.White))
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(30.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Icon(Icons.Filled.Snooze, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Snooze 15 min", style = GrindBellTypography.titleMedium.copy(color = Color.White.copy(alpha = 0.8f)))
            }
        }
    }
}
