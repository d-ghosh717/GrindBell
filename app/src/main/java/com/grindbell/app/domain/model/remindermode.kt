package com.grindbell.app.domain.model

enum class ReminderMode(val label: String) {
    NORMAL("Normal"),
    PERSISTENT("Persistent"),
    AGGRESSIVE("Aggressive"),
    CRITICAL("Critical Alarm")
}
