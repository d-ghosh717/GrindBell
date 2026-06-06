package com.grindbell.app.data.local.dao

import androidx.room.*
import com.grindbell.app.data.local.entity.AnalyticsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {

    @Query("SELECT * FROM analytics WHERE date = :date")
    suspend fun getAnalyticsForDate(date: String): AnalyticsEntity?

    @Query("SELECT * FROM analytics WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getAnalyticsForRange(startDate: String, endDate: String): List<AnalyticsEntity>

    @Query("SELECT * FROM analytics WHERE date BETWEEN :startDate AND :endDate ORDER BY tasks_completed DESC LIMIT 1")
    suspend fun getBestDay(startDate: String, endDate: String): AnalyticsEntity?

    @Query("SELECT * FROM analytics WHERE date BETWEEN :startDate AND :endDate ORDER BY tasks_completed ASC LIMIT 1")
    suspend fun getWorstDay(startDate: String, endDate: String): AnalyticsEntity?

    @Query("SELECT COALESCE(SUM(tasks_completed), 0) FROM analytics WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getTotalTasksCompleted(startDate: String, endDate: String): Int

    @Query("SELECT COALESCE(SUM(habits_completed), 0) FROM analytics WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getTotalHabitsCompleted(startDate: String, endDate: String): Int

    @Query("SELECT COALESCE(SUM(missed_reminders), 0) FROM analytics WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getTotalMissedReminders(startDate: String, endDate: String): Int

    @Query("SELECT COALESCE(AVG(reminder_response_rate), 0) FROM analytics WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getAverageResponseRate(startDate: String, endDate: String): Float

    @Query("SELECT * FROM analytics WHERE date LIKE :monthPrefix ORDER BY date ASC")
    fun getAnalyticsForMonth(monthPrefix: String): Flow<List<AnalyticsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAnalytics(analytics: AnalyticsEntity)

    @Query("DELETE FROM analytics")
    suspend fun clearAll()
}

data class MonthlyTuple(
    val date: String,
    val tasksCompleted: Int = 0,
    val habitsCompleted: Int = 0
)
