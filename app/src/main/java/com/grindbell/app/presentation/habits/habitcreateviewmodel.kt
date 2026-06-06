package com.grindbell.app.presentation.habits

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindbell.app.data.local.dao.HabitDao
import com.grindbell.app.data.local.dao.ReminderDao
import com.grindbell.app.data.local.entity.HabitEntity
import com.grindbell.app.domain.model.Priority
import com.grindbell.app.domain.model.ReminderMode
import com.grindbell.app.services.reminders.ReminderManager
import com.grindbell.app.services.alarms.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.*
import javax.inject.Inject

data class HabitCreateUiState(
    val name: String = "",
    val description: String = "",
    val dailyTargetCount: String = "1",
    val frequencyMinutes: Int = 60,
    val isCustomFrequency: Boolean = false,
    val customFrequencyText: String = "",
    val startDate: String = LocalDate.now().toString(),
    val startTime: String = "08:00",
    val endTime: String = "22:00",
    val priority: Priority = Priority.MEDIUM,
    val reminderMode: ReminderMode = ReminderMode.AGGRESSIVE,
    val nameError: String? = null,
    val targetError: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class HabitCreateViewModel @Inject constructor(
    private val habitDao: HabitDao,
    private val reminderDao: ReminderDao,
    private val reminderManager: ReminderManager,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitCreateUiState())
    val uiState: StateFlow<HabitCreateUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) { _uiState.update { it.copy(name = name, nameError = null, error = null) } }
    fun onDescriptionChanged(desc: String) { _uiState.update { it.copy(description = desc) } }
    fun onTargetCountChanged(value: String) {
        _uiState.update { it.copy(dailyTargetCount = value.filter { it.isDigit() }.ifEmpty { "1" }, targetError = null) }
    }
    fun onFrequencyChanged(min: Int) { _uiState.update { it.copy(frequencyMinutes = min, isCustomFrequency = false) } }
    fun onCustomFrequencySelected() { _uiState.update { it.copy(isCustomFrequency = true) } }
    fun onCustomFrequencyTextChanged(text: String) {
        _uiState.update { it.copy(customFrequencyText = text.filter { it.isDigit() }) }
    }
    fun onCustomFrequencyConfirmed() {
        val mins = _uiState.value.customFrequencyText.toIntOrNull()
        if (mins != null && mins > 0) _uiState.update { it.copy(frequencyMinutes = mins, isCustomFrequency = false) }
    }
    fun onStartDateChanged(date: String) { _uiState.update { it.copy(startDate = date) } }
    fun onStartTimeChanged(time: String) { _uiState.update { it.copy(startTime = time) } }
    fun onEndTimeChanged(time: String) { _uiState.update { it.copy(endTime = time) } }
    fun onPriorityChanged(p: Priority) { _uiState.update { it.copy(priority = p) } }
    fun onReminderModeChanged(mode: ReminderMode) { _uiState.update { it.copy(reminderMode = mode) } }

    fun saveHabit() {
        val state = _uiState.value
        if (state.name.isBlank()) { _uiState.update { it.copy(nameError = "Name required") }; return }
        val target = state.dailyTargetCount.toIntOrNull()?.coerceAtLeast(1) ?: 1
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val now = System.currentTimeMillis()
                val entity = HabitEntity(
                    name = state.name.trim(), description = state.description.trim(),
                    dailyTargetCount = target, frequencyMinutes = state.frequencyMinutes,
                    startDate = state.startDate, startTime = state.startTime, endTime = state.endTime,
                    priority = state.priority.name, reminderMode = state.reminderMode.name,
                    reminderIntervalMinutes = if (state.reminderMode == ReminderMode.AGGRESSIVE) 5 else state.frequencyMinutes,
                    isActive = true,
                    createdAt = now, updatedAt = now
                )
                val habitId = habitDao.insertHabit(entity)

                // ── SCHEDULE HABIT REMINDER via AlarmManager ──
                // Calculate first reminder time from start_time + start_date
                val startTime = LocalTime.parse(state.startTime)
                val startDate = try { LocalDate.parse(state.startDate) } catch (_: Exception) { LocalDate.now() }
                val firstReminderMillis = startDate.atTime(startTime)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                reminderManager.scheduleHabitReminder(
                    habitId = habitId,
                    title = state.name.trim(),
                    message = "Time for: ${state.name.trim()}",
                    scheduledAt = firstReminderMillis,
                    intervalMinutes = state.frequencyMinutes,
                    reminderMode = state.reminderMode
                )

                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                Log.e("HabitCreateVM", "Failed to save habit", e)
                _uiState.update { it.copy(isSaving = false, error = "Save failed: ${e.localizedMessage}") }
            }
        }
    }
}
