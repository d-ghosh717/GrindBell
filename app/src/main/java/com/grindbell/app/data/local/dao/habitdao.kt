package com.grindbell.app.data.local.dao

import androidx.room.*
import com.grindbell.app.data.local.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY created_at ASC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE is_active = 1 ORDER BY created_at ASC")
    fun getActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: Long): HabitEntity?

    @Query("SELECT COUNT(*) FROM habits WHERE is_active = 1")
    suspend fun getActiveHabitCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitById(id: Long)

    @Query("UPDATE habits SET total_completions = total_completions + 1, today_completions = CASE WHEN completion_date = :today THEN today_completions + 1 ELSE 1 END, completion_date = :today, last_completed_at = :completedAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun markHabitCompleted(id: Long, today: String, completedAt: Long, updatedAt: Long)

    @Query("UPDATE habits SET today_missed = CASE WHEN completion_date = :today THEN today_missed + 1 ELSE 1 END, completion_date = :today, last_missed_at = :missedAt, updated_at = :missedAt WHERE id = :id")
    suspend fun markHabitMissed(id: Long, today: String, missedAt: Long)

    // Analytics queries
    @Query("SELECT COUNT(*) FROM habits WHERE is_active = 1 AND last_completed_at >= :startOfDay AND last_completed_at <= :endOfDay")
    suspend fun getHabitsWithCompletionToday(startOfDay: Long, endOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM habits WHERE is_active = 1")
    suspend fun getTotalActiveHabits(): Int

    @Query("SELECT * FROM habits WHERE is_active = 1 ORDER BY today_completions DESC LIMIT 1")
    suspend fun getMostCompletedHabitToday(): HabitEntity?

    @Query("SELECT * FROM habits WHERE is_active = 1 AND (completion_date != :today OR (completion_date = :today AND today_completions = 0)) ORDER BY today_missed DESC LIMIT 1")
    suspend fun getMostMissedHabitToday(today: String): HabitEntity?

    @Query("SELECT COALESCE(SUM(today_completions), 0) FROM habits WHERE is_active = 1")
    suspend fun getTotalCompletionsToday(): Int

    @Query("SELECT COUNT(*) FROM habits WHERE is_active = 1 AND today_completions >= daily_target_count")
    suspend fun getHabitsReachedTargetToday(): Int

    @Query("SELECT COALESCE(SUM(daily_target_count), 0) FROM habits WHERE is_active = 1")
    suspend fun getTotalTargetCount(): Int
}
