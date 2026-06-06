package com.grindbell.app.presentation.habits

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindbell.app.data.local.dao.HabitDao
import com.grindbell.app.data.local.dao.ReminderDao
import com.grindbell.app.data.local.entity.HabitEntity
import com.grindbell.app.domain.model.Habit
import com.grindbell.app.domain.model.Priority
import com.grindbell.app.services.alarms.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import javax.inject.Inject

data class HabitDetailUiState(
    val habit: Habit? = null,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null,
    // Edit fields
    val editName: String = "",
    val editDescription: String = "",
    val editDailyTarget: String = "1",
    val editFrequency: Int = 60,
    val editStartDate: String = "",
    val editStartTime: String = "08:00",
    val editEndTime: String = "22:00",
    val editPriority: Priority = Priority.MEDIUM,
    val editIsActive: Boolean = true
)

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    private val habitDao: HabitDao,
    private val reminderDao: ReminderDao,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitDetailUiState())
    val uiState: StateFlow<HabitDetailUiState> = _uiState.asStateFlow()

    fun loadHabit(habitId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val entity = habitDao.getHabitById(habitId)
                if (entity == null) { _uiState.update { it.copy(isLoading = false, habit = null) }; return@launch }
                val habit = entity.toDomain()
                _uiState.update {
                    it.copy(habit = habit, isLoading = false,
                        editName = habit.name, editDescription = habit.description,
                        editDailyTarget = habit.dailyTargetCount.toString(),
                        editFrequency = habit.frequencyMinutes,
                        editStartDate = habit.startDate?.toString() ?: "",
                        editStartTime = habit.startTime.toString(), editEndTime = habit.endTime.toString(),
                        editPriority = habit.priority, editIsActive = habit.isActive)
                }
            } catch (e: Exception) {
                Log.e("HabitDetailVM", "Load failed", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load") }
            }
        }
    }

    fun toggleEdit() {
        val c = _uiState.value
        if (c.isEditing) {
            val h = c.habit ?: return
            _uiState.update { it.copy(isEditing = false, editName = h.name, editDescription = h.description,
                editDailyTarget = h.dailyTargetCount.toString(), editFrequency = h.frequencyMinutes,
                editStartDate = h.startDate?.toString() ?: "", editStartTime = h.startTime.toString(),
                editEndTime = h.endTime.toString(), editPriority = h.priority, editIsActive = h.isActive) }
        } else _uiState.update { it.copy(isEditing = true) }
    }

    fun onEditNameChanged(n: String) { _uiState.update { it.copy(editName = n) } }
    fun onEditDescriptionChanged(d: String) { _uiState.update { it.copy(editDescription = d) } }
    fun onEditTargetChanged(v: String) { _uiState.update { it.copy(editDailyTarget = v.filter { it.isDigit() }.ifEmpty { "1" }) } }
    fun onEditFrequencyChanged(m: Int) { _uiState.update { it.copy(editFrequency = m) } }
    fun onEditStartDateChanged(d: String) { _uiState.update { it.copy(editStartDate = d) } }
    fun onEditStartTimeChanged(t: String) { _uiState.update { it.copy(editStartTime = t) } }
    fun onEditEndTimeChanged(t: String) { _uiState.update { it.copy(editEndTime = t) } }
    fun onEditPriorityChanged(p: Priority) { _uiState.update { it.copy(editPriority = p) } }
    fun onEditActiveChanged(a: Boolean) { _uiState.update { it.copy(editIsActive = a) } }

    fun saveEdits() {
        viewModelScope.launch {
            val s = _uiState.value; val h = s.habit ?: return@launch
            if (s.editName.isBlank()) return@launch
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val now = System.currentTimeMillis()
                val target = s.editDailyTarget.toIntOrNull()?.coerceAtLeast(1) ?: 1
                habitDao.updateHabit(HabitEntity(
                    id = h.id, name = s.editName.trim(), description = s.editDescription.trim(),
                    dailyTargetCount = target, frequencyMinutes = s.editFrequency,
                    startDate = s.editStartDate.takeIf { it.isNotBlank() },
                    startTime = s.editStartTime, endTime = s.editEndTime,
                    priority = s.editPriority.name, isActive = s.editIsActive,
                    totalCompletions = h.totalCompletions, todayCompletions = h.todayCompletions,
                    todayMissed = h.todayMissed,
                    lastCompletedAt = h.lastCompletedAt?.let { it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() },
                    lastMissedAt = h.lastMissedAt?.let { it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() },
                    completionDate = h.completionDate?.toString(),
                    createdAt = h.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    updatedAt = now))
                loadHabit(h.id)
                _uiState.update { it.copy(isSaving = false, isEditing = false) }
            } catch (e: Exception) {
                Log.e("HabitDetailVM", "Save failed", e)
                _uiState.update { it.copy(isSaving = false, error = "Save failed") }
            }
        }
    }

    fun markDone() {
        viewModelScope.launch {
            val h = _uiState.value.habit ?: return@launch
            val (locked, _) = isHabitLocked(h)
            if (locked) return@launch
            val now = System.currentTimeMillis()
            habitDao.markHabitCompleted(h.id, LocalDate.now().toString(), now, now)
            loadHabit(h.id)
        }
    }

    fun deleteHabit() {
        viewModelScope.launch {
            val id = _uiState.value.habit?.id ?: return@launch
            // Cancel all reminders BEFORE deleting
            try {
                val reminders = reminderDao.getRemindersForHabit(id)
                reminders.forEach { alarmScheduler.cancelReminder(it.id) }
                reminderDao.deleteRemindersForHabit(id)
            } catch (_: Exception) {}
            habitDao.deleteHabitById(id)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun isHabitLocked(habit: Habit): Pair<Boolean, String?> {
        habit.lastCompletedAt?.let { lc ->
            val next = lc.plusMinutes(habit.frequencyMinutes.toLong())
            val now = LocalDateTime.now()
            if (now.isBefore(next)) {
                val rem = Duration.between(now, next).toMinutes()
                return true to "Next: ${next.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))} (${rem}m)"
            }
        }
        return false to null
    }
}

private fun HabitEntity.toDomain(): Habit = Habit(
    id = id, name = name, description = description, dailyTargetCount = dailyTargetCount,
    frequencyMinutes = frequencyMinutes, startDate = startDate?.let { LocalDate.parse(it) },
    startTime = LocalTime.parse(startTime), endTime = LocalTime.parse(endTime),
    priority = when (priority) { "HIGH" -> Priority.HIGH; "LOW" -> Priority.LOW; else -> Priority.MEDIUM },
    isActive = isActive, totalCompletions = totalCompletions, todayCompletions = todayCompletions,
    todayMissed = todayMissed,
    lastCompletedAt = lastCompletedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) },
    lastMissedAt = lastMissedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) },
    completionDate = completionDate?.let { LocalDate.parse(it) },
    createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), ZoneId.systemDefault())
)
