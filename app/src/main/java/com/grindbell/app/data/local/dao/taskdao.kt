package com.grindbell.app.data.local.dao

import androidx.room.*
import com.grindbell.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY due_date ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND is_pinned = 1 ORDER BY due_date ASC")
    suspend fun getPinnedTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND is_pinned = 1 ORDER BY due_date ASC")
    fun getPinnedTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date <= :endOfDay ORDER BY is_pinned DESC, due_date ASC")
    fun getTodayTasks(endOfDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE due_date <= :endOfDay ORDER BY due_date ASC")
    fun getAllTodayTasksFlow(endOfDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date < :now ORDER BY due_date ASC")
    fun getOverdueTasks(now: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 0 AND due_date > :now ORDER BY due_date ASC")
    fun getUpcomingTasks(now: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE is_completed = 1 ORDER BY completed_at DESC")
    fun getCompletedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 1 AND completed_at >= :startOfDay AND completed_at <= :endOfDay")
    suspend fun getCompletedTodayCount(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 0 AND due_date <= :endOfDay")
    suspend fun getPendingTodayCount(endOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE created_at >= :startOfDay AND created_at <= :endOfDay")
    suspend fun getCreatedTodayCount(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 0 AND due_date < :now AND due_date >= :startOfDay")
    suspend fun getMissedTodayCount(now: Long, startOfDay: Long): Int

    // Per-day analytics
    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 1 AND completed_at >= :startOfDay AND completed_at <= :endOfDay")
    suspend fun getTasksCompletedOn(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE is_completed = 0 AND due_date <= :endOfDay")
    suspend fun getTasksPendingAt(endOfDay: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("UPDATE tasks SET is_completed = 1, completed_at = :completedAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun markTaskCompleted(id: Long, completedAt: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTotalTaskCount(): Int
}
