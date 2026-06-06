package com.grindbell.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "due_date") val dueDate: Long, // epoch millis
    @ColumnInfo(name = "priority") val priority: String = "MEDIUM",
    @ColumnInfo(name = "category_id") val categoryId: Long = 0,
    @ColumnInfo(name = "reminder_mode") val reminderMode: String = "NORMAL",
    @ColumnInfo(name = "reminder_interval_minutes") val reminderIntervalMinutes: Int = 30,
    @ColumnInfo(name = "repeat_until_done") val repeatUntilDone: Boolean = false,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
