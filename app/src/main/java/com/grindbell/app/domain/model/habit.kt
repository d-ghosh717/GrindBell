package com.grindbell.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class Habit(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val dailyTargetCount: Int = 1,
    val frequencyMinutes: Int = 60,
    val startDate: LocalDate? = null,
    val startTime: LocalTime = LocalTime.of(8, 0),
    val endTime: LocalTime = LocalTime.of(22, 0),
    val priority: Priority = Priority.MEDIUM,
    val reminderMode: ReminderMode = ReminderMode.AGGRESSIVE,
    val reminderIntervalMinutes: Int = 5,
    val isActive: Boolean = true,
    val totalCompletions: Int = 0,
    val todayCompletions: Int = 0,
    val todayMissed: Int = 0,
    val lastCompletedAt: java.time.LocalDateTime? = null,
    val lastMissedAt: java.time.LocalDateTime? = null,
    val completionDate: LocalDate? = null,
    val createdAt: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    val updatedAt: java.time.LocalDateTime = java.time.LocalDateTime.now()
)
