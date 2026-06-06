package com.grindbell.app.domain.model

data class Category(
    val id: Long = 0,
    val name: String,
    val colorHex: String,
    val iconName: String = "folder",
    val gradientStartHex: String = "#4A6CF7",
    val gradientEndHex: String = "#6C8CFF",
    val isDefault: Boolean = false,
    val sortOrder: Int = 0
)
