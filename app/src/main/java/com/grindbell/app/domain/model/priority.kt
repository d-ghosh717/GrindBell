package com.grindbell.app.domain.model

enum class Priority(val label: String, val level: Int) {
    LOW("Low", 0),
    MEDIUM("Medium", 1),
    HIGH("High", 2)
}
