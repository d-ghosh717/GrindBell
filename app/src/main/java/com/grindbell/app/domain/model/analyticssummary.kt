package com.grindbell.app.domain.model

data class AnalyticsSummary(
    val tasksCreated: Int = 0,
    val tasksCompleted: Int = 0,
    val tasksMissed: Int = 0,
    val habitsDue: Int = 0,
    val habitsCompleted: Int = 0,
    val habitsMissed: Int = 0,
    val completionPercentage: Float = 0f,
    val missedReminders: Int = 0,
    val mostMissedHabit: String? = null,
    val mostCompletedHabit: String? = null
)

data class DailyAnalytics(
    val date: String,
    val tasksCompleted: Int = 0,
    val tasksCreated: Int = 0,
    val tasksMissed: Int = 0,
    val habitsDue: Int = 0,
    val habitsCompleted: Int = 0,
    val habitsMissed: Int = 0,
    val completionPercentage: Float = 0f,
    val missedReminders: Int = 0,
    val mostMissedHabit: String? = null,
    val mostCompletedHabit: String? = null
)

data class WeeklyAnalytics(
    val weekStart: String,
    val dailyBreakdown: List<DailyAnalytics> = emptyList(),
    val totalTasksCompleted: Int = 0,
    val totalHabitsCompleted: Int = 0,
    val missedReminders: Int = 0,
    val completionPercentage: Float = 0f,
    val bestDay: String? = null,
    val worstDay: String? = null
)

data class MonthlyAnalytics(
    val month: String,
    val dailyBreakdown: List<DailyAnalytics> = emptyList(),
    val totalTasksCompleted: Int = 0,
    val totalHabitsCompleted: Int = 0,
    val missedReminders: Int = 0,
    val reminderResponseRate: Float = 0f,
    val mostSuccessfulHabit: String? = null,
    val mostIgnoredHabit: String? = null,
    val completionPercentage: Float = 0f
)
