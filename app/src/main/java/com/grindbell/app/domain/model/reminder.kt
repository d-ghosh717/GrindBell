package com.grindbell.app.domain.model

import java.time.LocalDateTime

data class Reminder(
    val id: Long = 0,
    val taskId: Long? = null,
    val habitId: Long? = null,
    val title: String,
    val message: String,
    val scheduledAt: LocalDateTime,
    val reminderMode: ReminderMode = ReminderMode.NORMAL,
    val intervalMinutes: Int = 30,
    val repeatCount: Int = 0,
    val maxRepeats: Int = 0,
    val isActive: Boolean = true,
    val isCritical: Boolean = false,
    val snoozeCount: Int = 0,
    val missedCount: Int = 0,
    val lastTriggeredAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
