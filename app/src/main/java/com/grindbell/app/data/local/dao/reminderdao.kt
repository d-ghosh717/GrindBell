package com.grindbell.app.data.local.dao

import androidx.room.*
import com.grindbell.app.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE is_active = 1 ORDER BY scheduled_at ASC")
    fun getActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE is_active = 1 AND scheduled_at <= :now ORDER BY scheduled_at ASC")
    suspend fun getDueReminders(now: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE is_active = 1 AND scheduled_at <= :now ORDER BY scheduled_at ASC")
    fun getDueRemindersFlow(now: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE task_id = :taskId ORDER BY scheduled_at ASC")
    suspend fun getRemindersForTask(taskId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE habit_id = :habitId ORDER BY scheduled_at ASC")
    suspend fun getRemindersForHabit(habitId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE is_active = 1 AND scheduled_at <= :endOfDay AND scheduled_at >= :startOfDay")
    suspend fun getRemindersForDay(startOfDay: Long, endOfDay: Long): List<ReminderEntity>

    @Query("SELECT COUNT(*) FROM reminders WHERE is_active = 0 AND created_at >= :startOfDay AND created_at <= :endOfDay")
    suspend fun getDeactivatedTodayCount(startOfDay: Long, endOfDay: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE task_id = :taskId")
    suspend fun deleteRemindersForTask(taskId: Long)

    @Query("DELETE FROM reminders WHERE habit_id = :habitId")
    suspend fun deleteRemindersForHabit(habitId: Long)

    @Query("UPDATE reminders SET is_active = 0 WHERE id = :id")
    suspend fun deactivateReminder(id: Long)

    @Query("UPDATE reminders SET repeat_count = repeat_count + 1, scheduled_at = :nextScheduledAt, last_triggered_at = :now WHERE id = :id")
    suspend fun rescheduleReminder(id: Long, nextScheduledAt: Long, now: Long)

    @Query("UPDATE reminders SET snooze_count = snooze_count + 1, scheduled_at = :nextScheduledAt, last_triggered_at = :now WHERE id = :id")
    suspend fun snoozeReminder(id: Long, nextScheduledAt: Long, now: Long)

    @Query("UPDATE reminders SET missed_count = missed_count + 1, last_triggered_at = :now WHERE id = :id")
    suspend fun markReminderMissed(id: Long, now: Long)

    @Query("SELECT COALESCE(SUM(missed_count), 0) FROM reminders WHERE created_at >= :startOfDay AND created_at <= :endOfDay")
    suspend fun getMissedRemindersForDay(startOfDay: Long, endOfDay: Long): Int
}
