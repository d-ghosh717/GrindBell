package com.grindbell.app.presentation.tasks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindbell.app.data.local.dao.CategoryDao
import com.grindbell.app.data.local.dao.TaskDao
import com.grindbell.app.data.local.entity.TaskEntity
import com.grindbell.app.domain.model.Priority
import com.grindbell.app.domain.model.ReminderMode
import com.grindbell.app.services.reminders.ReminderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class TaskCreateUiState(
    val title: String = "",
    val description: String = "",
    val dueDate: LocalDate = LocalDate.now(),
    val dueTime: LocalTime = LocalTime.now().plusHours(1).withMinute(0),
    val priority: Priority = Priority.MEDIUM,
    val reminderMode: ReminderMode = ReminderMode.NORMAL,
    val reminderIntervalMinutes: Int = 30,
    val isCustomInterval: Boolean = false,
    val customIntervalText: String = "",
    val repeatUntilDone: Boolean = false,
    val isPinned: Boolean = false,
    val titleError: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class TaskCreateViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao,
    private val reminderManager: ReminderManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskCreateUiState())
    val uiState: StateFlow<TaskCreateUiState> = _uiState.asStateFlow()

    fun onTitleChanged(title: String) {
        _uiState.update { it.copy(title = title, titleError = null, error = null) }
    }

    fun onDescriptionChanged(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun onPriorityChanged(priority: Priority) {
        _uiState.update { it.copy(priority = priority) }
    }

    fun onReminderModeChanged(mode: ReminderMode) {
        _uiState.update { it.copy(reminderMode = mode) }
    }

    fun onIntervalChanged(minutes: Int) {
        _uiState.update { it.copy(reminderIntervalMinutes = minutes, isCustomInterval = false) }
    }

    fun onCustomIntervalSelected() {
        _uiState.update { it.copy(isCustomInterval = true) }
    }

    fun onCustomIntervalTextChanged(text: String) {
        _uiState.update { it.copy(customIntervalText = text.filter { it.isDigit() }) }
    }

    fun onCustomIntervalConfirmed() {
        val mins = _uiState.value.customIntervalText.toIntOrNull()
        if (mins != null && mins > 0) {
            _uiState.update { it.copy(reminderIntervalMinutes = mins, isCustomInterval = false) }
        }
    }

    fun onRepeatUntilDoneChanged(enabled: Boolean) {
        _uiState.update { it.copy(repeatUntilDone = enabled) }
    }

    fun onPinnedChanged(pinned: Boolean) {
        _uiState.update { it.copy(isPinned = pinned) }
    }

    fun onDateChanged(date: LocalDate) {
        _uiState.update { it.copy(dueDate = date) }
    }

    fun onTimeChanged(time: LocalTime) {
        _uiState.update { it.copy(dueTime = time) }
    }

    fun saveTask() {
        val state = _uiState.value

        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "Title is required") }
            return
        }

        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val dueDateTime = LocalDateTime.of(state.dueDate, state.dueTime)
                val dueMillis = dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val entity = TaskEntity(
                    title = state.title.trim(),
                    description = state.description.trim(),
                    dueDate = dueMillis,
                    priority = state.priority.name,
                    categoryId = 0L,
                    reminderMode = state.reminderMode.name,
                    reminderIntervalMinutes = state.reminderIntervalMinutes,
                    repeatUntilDone = state.repeatUntilDone,
                    isPinned = state.isPinned,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                val taskId = taskDao.insertTask(entity)

                // ALWAYS schedule a reminder — ALL modes create real notifications
                reminderManager.scheduleTaskReminder(
                    taskId = taskId,
                    title = state.title.trim(),
                    message = "Reminder: ${state.title.trim()}",
                    scheduledAt = dueMillis,
                    reminderMode = state.reminderMode,
                    intervalMinutes = state.reminderIntervalMinutes
                )

                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                Log.e("AddTaskScreen", "Failed to save task", e)
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to save: ${e.localizedMessage ?: "Unknown error"}")
                }
            }
        }
    }
}
