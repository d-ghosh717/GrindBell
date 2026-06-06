package com.grindbell.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "daily_target_count") val dailyTargetCount: Int = 1,
    @ColumnInfo(name = "frequency_minutes") val frequencyMinutes: Int = 60,
    @ColumnInfo(name = "start_date") val startDate: String? = null,
    @ColumnInfo(name = "start_time") val startTime: String = "08:00",
    @ColumnInfo(name = "end_time") val endTime: String = "22:00",
    @ColumnInfo(name = "priority") val priority: String = "MEDIUM",
    @ColumnInfo(name = "reminder_mode") val reminderMode: String = "AGGRESSIVE",
    @ColumnInfo(name = "reminder_interval_minutes") val reminderIntervalMinutes: Int = 5,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "total_completions") val totalCompletions: Int = 0,
    @ColumnInfo(name = "today_completions") val todayCompletions: Int = 0,
    @ColumnInfo(name = "today_missed") val todayMissed: Int = 0,
    @ColumnInfo(name = "last_completed_at") val lastCompletedAt: Long? = null,
    @ColumnInfo(name = "last_missed_at") val lastMissedAt: Long? = null,
    @ColumnInfo(name = "completion_date") val completionDate: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
