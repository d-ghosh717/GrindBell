package com.grindbell.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long? = null,
    @ColumnInfo(name = "habit_id") val habitId: Long? = null,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long,
    @ColumnInfo(name = "reminder_mode") val reminderMode: String = "NORMAL",
    @ColumnInfo(name = "interval_minutes") val intervalMinutes: Int = 30,
    @ColumnInfo(name = "repeat_count") val repeatCount: Int = 0,
    @ColumnInfo(name = "max_repeats") val maxRepeats: Int = 0,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "is_critical") val isCritical: Boolean = false,
    @ColumnInfo(name = "snooze_count") val snoozeCount: Int = 0,
    @ColumnInfo(name = "missed_count") val missedCount: Int = 0,
    @ColumnInfo(name = "last_triggered_at") val lastTriggeredAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
