package com.grindbell.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Analytics snapshot per day — computed from real Task, Habit, and Reminder records.
 * No gamification: no XP, levels, or streaks.
 */
@Entity(tableName = "analytics")
data class AnalyticsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: String, // yyyy-MM-dd
    @ColumnInfo(name = "tasks_created") val tasksCreated: Int = 0,
    @ColumnInfo(name = "tasks_completed") val tasksCompleted: Int = 0,
    @ColumnInfo(name = "tasks_missed") val tasksMissed: Int = 0,
    @ColumnInfo(name = "habits_due") val habitsDue: Int = 0,
    @ColumnInfo(name = "habits_completed") val habitsCompleted: Int = 0,
    @ColumnInfo(name = "habits_missed") val habitsMissed: Int = 0,
    @ColumnInfo(name = "missed_reminders") val missedReminders: Int = 0,
    @ColumnInfo(name = "most_missed_habit_id") val mostMissedHabitId: Long? = null,
    @ColumnInfo(name = "most_completed_habit_id") val mostCompletedHabitId: Long? = null,
    @ColumnInfo(name = "best_day_score") val bestDayScore: Int = 0,
    @ColumnInfo(name = "worst_day_score") val worstDayScore: Int = Int.MAX_VALUE,
    @ColumnInfo(name = "reminder_response_rate") val reminderResponseRate: Float = 0f
)
