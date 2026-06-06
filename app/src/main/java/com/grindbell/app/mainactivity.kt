package com.grindbell.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.grindbell.app.presentation.GrindBellNavigation
import com.grindbell.app.presentation.theme.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "POST_NOTIFICATIONS granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Request POST_NOTIFICATIONS runtime permission (Android 13+) ──
        // Without this, NotificationManager.notify() silently drops ALL notifications.
        // Foreground service notifications are exempt, which is why vibration works
        // but reminder notifications + sound don't.
        requestNotificationPermission()

        // ── Request exact alarm permission (Android 12+) ──
        // Without this, AlarmManager.setExactAndAllowWhileIdle() falls back to
        // inexact alarms which may fire late or not at all.
        requestExactAlarmPermission()

        // ── Request battery optimization exemption ──
        // Without this, Doze mode may delay or suppress alarms.
        requestBatteryOptimizationExemption()

        setContent {
            GrindBellTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundLight
                ) {
                    GrindBellNavigation()
                }
            }
        }
    }

    /**
     * Android 13+ (API 33): POST_NOTIFICATIONS is a runtime permission.
     * Without it, ALL notifications from this app are silently dropped by the system.
     * This is the #1 cause of "no notification, no sound" on modern Android devices.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale, then request
                    Log.d(TAG, "Showing notification permission rationale")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // First request — just ask
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    /**
     * Android 12+ (API 31): SCHEDULE_EXACT_ALARM is no longer granted by default.
     * Users can revoke it in Settings > Apps > Special app access > Alarms & reminders.
     * We request it here and the user must grant it for reliable alarm delivery.
     */
    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarm permission not granted — requesting")
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open exact alarm settings", e)
                }
            }
        }
    }

    /**
     * Request exemption from battery optimization.
     * Without this, Doze mode may delay alarms by minutes or hours.
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request battery exemption", e)
                }
            }
        }
    }
}
