package com.grindbell.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.grindbell.app.data.local.dao.*
import com.grindbell.app.data.local.entity.*

@Database(
    entities = [
        TaskEntity::class,
        HabitEntity::class,
        CategoryEntity::class,
        ReminderEntity::class,
        AnalyticsEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun categoryDao(): CategoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun analyticsDao(): AnalyticsDao
}
