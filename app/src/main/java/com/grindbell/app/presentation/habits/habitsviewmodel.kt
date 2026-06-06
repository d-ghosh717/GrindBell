package com.grindbell.app.presentation.habits

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindbell.app.data.local.dao.HabitDao
import com.grindbell.app.data.local.dao.ReminderDao
import com.grindbell.app.domain.model.Habit
import com.grindbell.app.domain.model.Priority
import com.grindbell.app.domain.model.ReminderMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import javax.inject.Inject

data class HabitsState(
    val habits: List<Habit> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitDao: HabitDao,
    private val reminderDao: ReminderDao
) : ViewModel() {

    private val _state = MutableStateFlow(HabitsState())
    val state: StateFlow<HabitsState> = _state.asStateFlow()

    init { loadHabits() }

    fun loadHabits() {
        viewModelScope.launch {
            try {
                val entities = habitDao.getActiveHabits().first()
                _state.update { it.copy(habits = entities.map { e -> e.toDomain() }, isLoading = false) }
            } catch (e: Exception) { Log.e("HabitsVM", "Failed to load habits", e) }
        }
    }

    fun toggleHabitCompletion(habitId: Long) {
        viewModelScope.launch {
            try {
                val entity = habitDao.getHabitById(habitId) ?: return@launch
                val now = System.currentTimeMillis()
                val nowDateTime = LocalDateTime.now()
                val today = LocalDate.now().toString()

                // Cycle lock
                entity.lastCompletedAt?.let { lastMillis ->
                    val lastDone = Instant.ofEpochMilli(lastMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    val nextAllowed = lastDone.plusMinutes(entity.frequencyMinutes.toLong())
                    if (nowDateTime.isBefore(nextAllowed)) {
                        Log.d("HabitsVM", "Habit ${entity.name} locked until $nextAllowed")
                        return@launch
                    }
                }

                habitDao.markHabitCompleted(habitId, today, now, now)
                loadHabits()
            } catch (e: Exception) { Log.e("HabitsVM", "Failed to complete habit", e) }
        }
    }

    fun deleteHabit(habitId: Long) {
        viewModelScope.launch {
            try {
                // Clean up reminders first
                val reminders = reminderDao.getRemindersForHabit(habitId)
                reminders.forEach { /* alarmScheduler would cancel, but we need DI access */ }
                reminderDao.deleteRemindersForHabit(habitId)
                habitDao.deleteHabitById(habitId)
                loadHabits()
            } catch (e: Exception) { Log.e("HabitsVM", "Failed to delete habit", e) }
        }
    }

    fun isHabitLocked(habit: Habit): Pair<Boolean, String?> {
        habit.lastCompletedAt?.let { lastCompleted ->
            val nextAllowed = lastCompleted.plusMinutes(habit.frequencyMinutes.toLong())
            val now = LocalDateTime.now()
            if (now.isBefore(nextAllowed)) {
                val rem = Duration.between(now, nextAllowed).toMinutes()
                return true to "Next: ${nextAllowed.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))} (${rem}m)"
            }
        }
        return false to null
    }
}

private fun com.grindbell.app.data.local.entity.HabitEntity.toDomain(): Habit = Habit(
    id = id, name = name, description = description,
    dailyTargetCount = dailyTargetCount,
    frequencyMinutes = frequencyMinutes,
    startDate = startDate?.let { LocalDate.parse(it) },
    startTime = LocalTime.parse(startTime),
    endTime = LocalTime.parse(endTime),
    priority = when (priority) { "HIGH" -> Priority.HIGH; "LOW" -> Priority.LOW; else -> Priority.MEDIUM },
    reminderMode = when (reminderMode) {
        "PERSISTENT" -> ReminderMode.PERSISTENT; "AGGRESSIVE" -> ReminderMode.AGGRESSIVE
        "CRITICAL" -> ReminderMode.CRITICAL; else -> ReminderMode.NORMAL
    },
    reminderIntervalMinutes = reminderIntervalMinutes,
    isActive = isActive,
    totalCompletions = totalCompletions,
    todayCompletions = todayCompletions,
    todayMissed = todayMissed,
    lastCompletedAt = lastCompletedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) },
    lastMissedAt = lastMissedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) },
    completionDate = completionDate?.let { LocalDate.parse(it) }
)
