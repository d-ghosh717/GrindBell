package com.grindbell.app.data.local.database

import com.grindbell.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object DatabaseSeeder {

    val defaultCategories = listOf(
        CategoryEntity(
            name = "Personal", colorHex = "#4A6CF7",
            gradientStartHex = "#4A6CF7", gradientEndHex = "#6C8CFF",
            iconName = "person", isDefault = true, sortOrder = 0
        ),
        CategoryEntity(
            name = "Work", colorHex = "#FF6B6B",
            gradientStartHex = "#FF6B6B", gradientEndHex = "#FF8E8E",
            iconName = "work", isDefault = true, sortOrder = 1
        ),
        CategoryEntity(
            name = "Study", colorHex = "#8B5CF6",
            gradientStartHex = "#8B5CF6", gradientEndHex = "#A78BFA",
            iconName = "school", isDefault = true, sortOrder = 2
        ),
        CategoryEntity(
            name = "Health", colorHex = "#2ED573",
            gradientStartHex = "#2ED573", gradientEndHex = "#5FE69B",
            iconName = "favorite", isDefault = true, sortOrder = 3
        ),
        CategoryEntity(
            name = "Finance", colorHex = "#FF8C42",
            gradientStartHex = "#FF8C42", gradientEndHex = "#FFB088",
            iconName = "attach_money", isDefault = true, sortOrder = 4
        )
    )

    fun seed(appDatabase: AppDatabase) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existing = appDatabase.categoryDao().getAllCategories().first()
                if (existing.isEmpty()) {
                    defaultCategories.forEach { appDatabase.categoryDao().insertCategory(it) }
                }
            } catch (_: Exception) {
                // Categories may already exist
            }
        }
    }
}
