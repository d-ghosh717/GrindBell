package com.grindbell.app.presentation.tasks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindbell.app.data.local.dao.TaskDao
import com.grindbell.app.data.local.dao.ReminderDao
import com.grindbell.app.data.local.entity.TaskEntity
import com.grindbell.app.domain.model.Priority
import com.grindbell.app.domain.model.ReminderMode
import com.grindbell.app.domain.model.Task
import com.grindbell.app.services.reminders.ReminderManager
import com.grindbell.app.services.alarms.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import javax.inject.Inject

data class TaskDetailUiState(
    val task: Task? = null,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null,
    // Edit fields
    val editTitle: String = "",
    val editDescription: String = "",
    val editDate: LocalDate = LocalDate.now(),
    val editTime: LocalTime = LocalTime.now(),
    val editPriority: Priority = Priority.MEDIUM
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao,
    private val reminderManager: ReminderManager,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    fun loadTask(taskId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val entity = taskDao.getTaskById(taskId)
                if (entity == null) {
                    _uiState.update { it.copy(isLoading = false, task = null) }
                    return@launch
                }
                val dueDt = Instant.ofEpochMilli(entity.dueDate).atZone(ZoneId.systemDefault()).toLocalDateTime()
                val completedDt = entity.completedAt?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
                }
                val task = Task(
                    id = entity.id, title = entity.title, description = entity.description,
                    dueDate = dueDt,
                    priority = when (entity.priority) {
                        "LOW" -> Priority.LOW; "HIGH" -> Priority.HIGH; else -> Priority.MEDIUM
                    },
                    categoryId = entity.categoryId,
                    reminderMode = when (entity.reminderMode) {
                        "PERSISTENT" -> ReminderMode.PERSISTENT; "AGGRESSIVE" -> ReminderMode.AGGRESSIVE
                        "CRITICAL" -> ReminderMode.CRITICAL; else -> ReminderMode.NORMAL
                    },
                    reminderIntervalMinutes = entity.reminderIntervalMinutes,
                    repeatUntilDone = entity.repeatUntilDone,
                    isCompleted = entity.isCompleted,
                    isPinned = entity.isPinned,
                    completedAt = completedDt
                )
                _uiState.update {
                    it.copy(
                        task = task, isLoading = false,
                        editTitle = task.title, editDescription = task.description,
                        editDate = dueDt.toLocalDate(), editTime = dueDt.toLocalTime(),
                        editPriority = task.priority
                    )
                }
            } catch (e: Exception) {
                Log.e("TaskDetail", "Failed to load task", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load task") }
            }
        }
    }

    fun toggleEdit() {
        val current = _uiState.value
        if (current.isEditing) {
            val t = current.task ?: return
            _uiState.update {
                it.copy(isEditing = false,
                    editTitle = t.title, editDescription = t.description,
                    editDate = t.dueDate.toLocalDate(), editTime = t.dueDate.toLocalTime(),
                    editPriority = t.priority)
            }
        } else {
            _uiState.update { it.copy(isEditing = true) }
        }
    }

    fun onEditTitleChanged(title: String) { _uiState.update { it.copy(editTitle = title) } }
    fun onEditDescriptionChanged(desc: String) { _uiState.update { it.copy(editDescription = desc) } }
    fun onEditPriorityChanged(p: Priority) { _uiState.update { it.copy(editPriority = p) } }
    fun onDateChanged(date: LocalDate) { _uiState.update { it.copy(editDate = date) } }
    fun onTimeChanged(time: LocalTime) { _uiState.update { it.copy(editTime = time) } }

    fun saveEdits() {
        viewModelScope.launch {
            val s = _uiState.value
            val entity = s.task ?: return@launch
            if (s.editTitle.isBlank()) return@launch
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val newDueMillis = LocalDateTime.of(s.editDate, s.editTime)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                taskDao.updateTask(TaskEntity(
                    id = entity.id, title = s.editTitle.trim(), description = s.editDescription.trim(),
                    dueDate = newDueMillis, priority = s.editPriority.name,
                    categoryId = entity.categoryId,
                    reminderMode = entity.reminderMode.name,
                    reminderIntervalMinutes = entity.reminderIntervalMinutes,
                    repeatUntilDone = entity.repeatUntilDone, isCompleted = entity.isCompleted,
                    completedAt = entity.completedAt?.let {
                        it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    },
                    createdAt = entity.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    updatedAt = System.currentTimeMillis()
                ))
                loadTask(entity.id)
                _uiState.update { it.copy(isSaving = false, isEditing = false) }
            } catch (e: Exception) {
                Log.e("TaskDetail", "Failed to save", e)
                _uiState.update { it.copy(isSaving = false, error = "Save failed: ${e.localizedMessage}") }
            }
        }
    }

    fun markDone() {
        viewModelScope.launch {
            val id = _uiState.value.task?.id ?: return@launch
            val now = System.currentTimeMillis()
            taskDao.markTaskCompleted(id, now, now)
            // Cancel all active reminders for this task
            cleanupReminders(id)
            loadTask(id)
        }
    }

    fun deleteTask() {
        viewModelScope.launch {
            val id = _uiState.value.task?.id ?: return@launch
            // Clean up reminders FIRST before deletion
            cleanupReminders(id)
            taskDao.deleteTaskById(id)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    private suspend fun cleanupReminders(taskId: Long) {
        try {
            val reminders = reminderDao.getRemindersForTask(taskId)
            reminders.forEach { reminder ->
                alarmScheduler.cancelReminder(reminder.id)
                reminderDao.deactivateReminder(reminder.id)
            }
        } catch (e: Exception) {
            Log.e("TaskDetail", "Failed to cleanup reminders for task $taskId", e)
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            val task = _uiState.value.task ?: return@launch
            val entity = taskDao.getTaskById(task.id) ?: return@launch
            val updated = entity.copy(
                isPinned = !entity.isPinned,
                updatedAt = System.currentTimeMillis()
            )
            taskDao.updateTask(updated)
            loadTask(task.id)
        }
    }
}
