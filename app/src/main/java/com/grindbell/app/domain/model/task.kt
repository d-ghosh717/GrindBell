package com.grindbell.app.domain.model

import java.time.LocalDateTime

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueDate: LocalDateTime,
    val priority: Priority = Priority.MEDIUM,
    val categoryId: Long = 0,
    val reminderMode: ReminderMode = ReminderMode.NORMAL,
    val reminderIntervalMinutes: Int = 30,
    val repeatUntilDone: Boolean = false,
    val isCompleted: Boolean = false,
    val isPinned: Boolean = false,
    val completedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
