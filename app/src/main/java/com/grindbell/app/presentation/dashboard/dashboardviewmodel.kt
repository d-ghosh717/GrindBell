package com.grindbell.app.presentation.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindbell.app.data.local.dao.*
import com.grindbell.app.data.local.entity.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class DashboardState(
    val tasksCompletedToday: Int = 0,
    val tasksPendingToday: Int = 0,
    val habitsCompletedToday: Int = 0,
    val habitsTargetToday: Int = 0,
    val habitsReachedTarget: Int = 0,
    val activeHabits: List<HabitEntity> = emptyList(),
    val pinnedTasks: List<TaskEntity> = emptyList(),
    val urgentReminders: List<ReminderEntity> = emptyList(),
    val upcomingTasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val habitDao: HabitDao,
    private val reminderDao: ReminderDao,
    private val analyticsDao: AnalyticsDao
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        observeDashboard()
    }

    /**
     * Reactive dashboard using Room Flow observers.
     * Any database change (edit, complete, delete, pin) instantly updates the UI.
     * No manual refresh needed — Room Flows emit on every relevant table change.
     */
    private fun observeDashboard() {
        viewModelScope.launch {
            try {
                val today = LocalDate.now()
                val endOfDay = today.atTime(LocalTime.MAX)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val now = System.currentTimeMillis()

                combine(
                    taskDao.getAllTodayTasksFlow(endOfDay),
                    taskDao.getPinnedTasksFlow(),
                    taskDao.getUpcomingTasks(now),
                    reminderDao.getDueRemindersFlow(now),
                    habitDao.getActiveHabits()
                ) { todayTasks, pinnedTasks, upcomingTasks, dueReminders, activeHabits ->
                    val completedToday = todayTasks.count { it.isCompleted }
                    val pendingToday = todayTasks.count { !it.isCompleted }
                    val habitsCompletedSum = activeHabits.sumOf { it.todayCompletions }
                    val habitsTargetSum = activeHabits.sumOf { it.dailyTargetCount }
                    val habitsReachedTarget = activeHabits.count { it.todayCompletions >= it.dailyTargetCount }

                    DashboardState(
                        tasksCompletedToday = completedToday,
                        tasksPendingToday = pendingToday,
                        habitsCompletedToday = habitsCompletedSum,
                        habitsTargetToday = habitsTargetSum.coerceAtLeast(1),
                        habitsReachedTarget = habitsReachedTarget,
                        activeHabits = activeHabits,
                        pinnedTasks = pinnedTasks,
                        urgentReminders = dueReminders,
                        upcomingTasks = upcomingTasks.take(5),
                        isLoading = false,
                        error = null
                    )
                }.catch { e ->
                    Log.e("DashboardVM", "Flow error", e)
                    emit(DashboardState(isLoading = false, error = e.message ?: "Dashboard sync failed"))
                }.collect { state ->
                    _state.value = state
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Init failed", e)
                _state.value = DashboardState(isLoading = false, error = e.message ?: "Load failed")
            }
        }
    }

    /** Manual refresh — triggers Room re-query (rarely needed, Flow handles live updates) */
    fun refresh() {
        // Room Flows are already reactive; this is a no-op safety net
        // If for some reason the Flow stopped, restart observation
        if (_state.value.error != null) {
            observeDashboard()
        }
    }
}
