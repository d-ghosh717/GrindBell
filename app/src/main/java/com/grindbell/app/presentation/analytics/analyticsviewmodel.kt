package com.grindbell.app.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindbell.app.data.local.dao.AnalyticsDao
import com.grindbell.app.data.local.dao.TaskDao
import com.grindbell.app.data.local.dao.HabitDao
import com.grindbell.app.data.local.dao.ReminderDao
import com.grindbell.app.domain.model.DailyAnalytics
import com.grindbell.app.domain.model.MonthlyAnalytics
import com.grindbell.app.domain.model.WeeklyAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import javax.inject.Inject

data class AnalyticsState(
    val selectedPeriod: String = "Today",
    // Today
    val todayTasksCreated: Int = 0,
    val todayTasksCompleted: Int = 0,
    val todayTasksMissed: Int = 0,
    val todayHabitsDue: Int = 0,
    val todayHabitsCompleted: Int = 0,
    val todayHabitsMissed: Int = 0,
    val todayCompletionPercentage: Float = 0f,
    val todayMissedReminders: Int = 0,
    val mostMissedHabit: String? = null,
    val mostCompletedHabit: String? = null,
    // Week
    val weekTasksPerDay: List<Pair<String, Int>> = emptyList(),
    val weekHabitsPerDay: List<Pair<String, Int>> = emptyList(),
    val weekCompletionPercentage: Float = 0f,
    val weekMissedReminders: Int = 0,
    val bestDay: String? = null,
    val worstDay: String? = null,
    // Month
    val monthTasksPerDay: List<Pair<String, Int>> = emptyList(),
    val monthHabitsPerDay: List<Pair<String, Int>> = emptyList(),
    val monthCompletionPercentage: Float = 0f,
    val monthMissedReminders: Int = 0,
    val reminderResponseRate: Float = 0f,
    val mostSuccessfulHabit: String? = null,
    val mostIgnoredHabit: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val analyticsDao: AnalyticsDao,
    private val taskDao: TaskDao,
    private val habitDao: HabitDao,
    private val reminderDao: ReminderDao
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    init { loadAnalytics() }

    fun loadAnalytics() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (_state.value.selectedPeriod) {
                "Today" -> loadToday()
                "Week" -> loadWeek()
                "Month" -> loadMonth()
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun selectPeriod(period: String) {
        _state.update { it.copy(selectedPeriod = period) }
        loadAnalytics()
    }

    // ── TODAY ──

    private suspend fun loadToday() {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val startOfDay = today.atTime(LocalTime.MIN).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = today.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        // Tasks — straightforward DB queries
        val tasksCreated = taskDao.getCreatedTodayCount(startOfDay, endOfDay)
        val tasksCompleted = taskDao.getCompletedTodayCount(startOfDay, endOfDay)
        val tasksMissed = taskDao.getMissedTodayCount(now, startOfDay)
        val missedReminders = reminderDao.getMissedRemindersForDay(startOfDay, endOfDay)

        // Habits — load all active and compute in-memory (window-aware)
        val allActive = habitDao.getActiveHabits().first()
        val habitsDue = allActive.size

        // Completed: today_completions >= daily_target_count
        val habitsCompleted = allActive.count { it.todayCompletions >= it.dailyTargetCount }

        // Missed: window has ended AND target not reached
        val habitsMissed = allActive.count { habit ->
            if (habit.todayCompletions >= habit.dailyTargetCount) return@count false
            val endTime = try { LocalTime.parse(habit.endTime) } catch (_: Exception) { LocalTime.of(22, 0) }
            val endMillis = today.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
            now > endMillis
        }

        // Completion %: sum of today_completions / sum of daily_target_count
        val totalCompletions = allActive.sumOf { it.todayCompletions }
        val totalTargets = allActive.sumOf { it.dailyTargetCount }
        val completionPct = if (totalTargets > 0) totalCompletions.toFloat() / totalTargets else 0f

        val mostCompleted = allActive.maxByOrNull { it.todayCompletions }
        val mostMissed = allActive.maxByOrNull { it.todayMissed }

        _state.update {
            it.copy(
                todayTasksCreated = tasksCreated,
                todayTasksCompleted = tasksCompleted,
                todayTasksMissed = tasksMissed,
                todayHabitsDue = habitsDue,
                todayHabitsCompleted = habitsCompleted,
                todayHabitsMissed = habitsMissed,
                todayCompletionPercentage = completionPct,
                todayMissedReminders = missedReminders,
                mostMissedHabit = mostMissed?.name,
                mostCompletedHabit = mostCompleted?.name
            )
        }
    }

    // ── WEEK ──

    private suspend fun loadWeek() {
        val today = LocalDate.now()
        val weekStart = today.with(DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("MM-dd")
        val zone = ZoneId.systemDefault()

        val tasksPerDay = mutableListOf<Pair<String, Int>>()
        val habitsPerDay = mutableListOf<Pair<String, Int>>()
        var totalTasksCompleted = 0
        var totalHabitsCompleted = 0

        // Load all active habits once
        val allActive = habitDao.getActiveHabits().first()
        val dailyTargetSum = allActive.sumOf { it.dailyTargetCount }

        for (day in 0..6) {
            val date = weekStart.plusDays(day.toLong())
            val startOfDay = date.atTime(LocalTime.MIN).atZone(zone).toInstant().toEpochMilli()
            val endOfDay = date.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

            val dayLabel = date.format(fmt)

            // Tasks completed on this day (simple timestamp check)
            val tasksDone = taskDao.getTasksCompletedOn(startOfDay, endOfDay)

            // Habits: only count if completed on this SPECIFIC day via last_completed_at
            val habitsDone = allActive.count { habit ->
                habit.lastCompletedAt?.let { lastCompleted ->
                    lastCompleted >= startOfDay && lastCompleted <= endOfDay
                } ?: false
            }

            tasksPerDay.add(dayLabel to tasksDone)
            habitsPerDay.add(dayLabel to habitsDone)
            totalTasksCompleted += tasksDone
            totalHabitsCompleted += habitsDone
        }

        val startStr = weekStart.toString()
        val endStr = weekEnd.toString()
        val missedReminders = analyticsDao.getTotalMissedReminders(startStr, endStr)
        val best = analyticsDao.getBestDay(startStr, endStr)
        val worst = analyticsDao.getWorstDay(startStr, endStr)

        val compPct = if (totalTasksCompleted + totalHabitsCompleted > 0) {
            (totalTasksCompleted + totalHabitsCompleted).toFloat() /
                (totalTasksCompleted + totalHabitsCompleted + missedReminders).coerceAtLeast(1)
        } else 0f

        _state.update {
            it.copy(
                weekTasksPerDay = tasksPerDay,
                weekHabitsPerDay = habitsPerDay,
                weekCompletionPercentage = compPct,
                weekMissedReminders = missedReminders,
                bestDay = best?.date,
                worstDay = worst?.date
            )
        }
    }

    // ── MONTH ──

    private suspend fun loadMonth() {
        val today = LocalDate.now()
        val monthStart = today.withDayOfMonth(1)
        val fmt = DateTimeFormatter.ofPattern("MM-dd")
        val zone = ZoneId.systemDefault()

        val tasksPerDay = mutableListOf<Pair<String, Int>>()
        val habitsPerDay = mutableListOf<Pair<String, Int>>()
        var totalTasksCompleted = 0
        var totalHabitsCompleted = 0

        // Load all active habits once
        val allActive = habitDao.getActiveHabits().first()

        for (day in 0 until today.dayOfMonth) {
            val date = monthStart.plusDays(day.toLong())
            val startOfDay = date.atTime(LocalTime.MIN).atZone(zone).toInstant().toEpochMilli()
            val endOfDay = date.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

            val tasksDone = taskDao.getTasksCompletedOn(startOfDay, endOfDay)

            // Only count habits completed ON this specific day (last_completed_at range)
            val habitsDone = allActive.count { habit ->
                habit.lastCompletedAt?.let { lastCompleted ->
                    lastCompleted >= startOfDay && lastCompleted <= endOfDay
                } ?: false
            }

            tasksPerDay.add(date.format(fmt) to tasksDone)
            habitsPerDay.add(date.format(fmt) to habitsDone)
            totalTasksCompleted += tasksDone
            totalHabitsCompleted += habitsDone
        }

        val startStr = monthStart.toString()
        val endStr = today.toString()
        val missedReminders = analyticsDao.getTotalMissedReminders(startStr, endStr)
        val responseRate = analyticsDao.getAverageResponseRate(startStr, endStr)

        val mostCompleted = allActive.maxByOrNull { it.totalCompletions }
        val mostMissed = allActive.maxByOrNull { it.todayMissed }

        val compPct = if (totalTasksCompleted + totalHabitsCompleted > 0) {
            (totalTasksCompleted + totalHabitsCompleted).toFloat() /
                (totalTasksCompleted + totalHabitsCompleted + missedReminders).coerceAtLeast(1)
        } else 0f

        _state.update {
            it.copy(
                monthTasksPerDay = tasksPerDay,
                monthHabitsPerDay = habitsPerDay,
                monthCompletionPercentage = compPct,
                monthMissedReminders = missedReminders,
                reminderResponseRate = responseRate,
                mostSuccessfulHabit = mostCompleted?.name,
                mostIgnoredHabit = mostMissed?.name
            )
        }
    }
}
