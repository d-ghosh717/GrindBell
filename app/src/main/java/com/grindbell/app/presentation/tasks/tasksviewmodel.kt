package com.grindbell.app.presentation.tasks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindbell.app.data.local.dao.*
import com.grindbell.app.data.local.entity.TaskEntity
import com.grindbell.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class TasksState(
    val todayTasks: List<TaskEntity> = emptyList(),
    val upcomingTasks: List<TaskEntity> = emptyList(),
    val overdueTasks: List<TaskEntity> = emptyList(),
    val completedTasks: List<TaskEntity> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedTab: TaskTab = TaskTab.TODAY,
    val isLoading: Boolean = true,
    val error: String? = null
)

enum class TaskTab(val label: String) {
    TODAY("Today"),
    UPCOMING("Upcoming"),
    OVERDUE("Overdue"),
    COMPLETED("Completed")
}

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _state = MutableStateFlow(TasksState())
    val state: StateFlow<TasksState> = _state.asStateFlow()

    init {
        loadTasks()
    }

    fun loadTasks() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val now = System.currentTimeMillis()
                val today = LocalDate.now()
                val endOfDay = today.atTime(LocalTime.MAX)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val todayTasks = taskDao.getTodayTasks(endOfDay).first()
                val upcoming = taskDao.getUpcomingTasks(now).first()
                val overdue = taskDao.getOverdueTasks(now).first()
                val completed = taskDao.getCompletedTasks().first()

                val cats = categoryDao.getAllCategories().first().map { entity ->
                    Category(
                        id = entity.id, name = entity.name, colorHex = entity.colorHex,
                        gradientStartHex = entity.gradientStartHex,
                        gradientEndHex = entity.gradientEndHex,
                        isDefault = entity.isDefault
                    )
                }

                _state.update {
                    it.copy(
                        todayTasks = todayTasks, upcomingTasks = upcoming,
                        overdueTasks = overdue, completedTasks = completed,
                        categories = cats, isLoading = false, error = null
                    )
                }
            } catch (e: Exception) {
                Log.e("TasksVM", "Failed to load tasks", e)
                _state.update { it.copy(isLoading = false, error = "Failed to load tasks") }
            }
        }
    }

    fun selectTab(tab: TaskTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun completeTask(taskId: Long) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                taskDao.markTaskCompleted(taskId, now, now)
                loadTasks()
            } catch (e: Exception) {
                Log.e("TasksVM", "Failed to complete task", e)
            }
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            try {
                taskDao.deleteTaskById(taskId)
                loadTasks()
            } catch (e: Exception) {
                Log.e("TasksVM", "Failed to delete task", e)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun togglePin(taskId: Long) {
        viewModelScope.launch {
            try {
                val entity = taskDao.getTaskById(taskId) ?: return@launch
                val updated = entity.copy(
                    isPinned = !entity.isPinned,
                    updatedAt = System.currentTimeMillis()
                )
                taskDao.updateTask(updated)
                loadTasks()
            } catch (e: Exception) {
                Log.e("TasksVM", "Failed to toggle pin", e)
            }
        }
    }
}
