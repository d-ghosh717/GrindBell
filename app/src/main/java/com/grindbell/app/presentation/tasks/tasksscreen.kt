package com.grindbell.app.presentation.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grindbell.app.domain.model.Task
import com.grindbell.app.presentation.components.*
import com.grindbell.app.presentation.theme.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onTaskClick: (Long) -> Unit,
    onAddTask: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadTasks() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks", style = GrindBellTypography.headlineLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight),
                actions = {
                    IconButton(onClick = onAddTask) {
                        Icon(Icons.Filled.Add, "Add Task", tint = ElectricBlue)
                    }
                }
            )
        },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Error banner
            if (state.error != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ErrorRed.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Error, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                        Text(state.error!!, style = GrindBellTypography.bodySmall, color = ErrorRed)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { viewModel.loadTasks() }) {
                            Text("Retry", style = GrindBellTypography.labelMedium, color = ElectricBlue)
                        }
                    }
                }
            }

            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
                containerColor = BackgroundLight,
                contentColor = ElectricBlue,
                edgePadding = 20.dp,
                divider = {},
                indicator = { tabPositions ->
                    if (state.selectedTab.ordinal < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab.ordinal]),
                            color = ElectricBlue
                        )
                    }
                }
            ) {
                TaskTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                tab.label,
                                style = if (state.selectedTab == tab)
                                    GrindBellTypography.labelLarge.copy(color = ElectricBlue)
                                else
                                    GrindBellTypography.labelLarge.copy(color = TextSecondary)
                            )
                        }
                    )
                }
            }

            // Content
            val taskList = when (state.selectedTab) {
                TaskTab.TODAY -> state.todayTasks
                TaskTab.UPCOMING -> state.upcomingTasks
                TaskTab.OVERDUE -> state.overdueTasks
                TaskTab.COMPLETED -> state.completedTasks
            }

            if (taskList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Inbox,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No ${state.selectedTab.label.lowercase()} tasks",
                            style = GrindBellTypography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(taskList, key = { it.id }) { entity ->
                        val task = Task(
                            id = entity.id,
                            title = entity.title,
                            description = entity.description,
                            dueDate = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(entity.dueDate), ZoneId.systemDefault()
                            ),
                            priority = when (entity.priority) {
                                "LOW" -> com.grindbell.app.domain.model.Priority.LOW
                                "HIGH" -> com.grindbell.app.domain.model.Priority.HIGH
                                else -> com.grindbell.app.domain.model.Priority.MEDIUM
                            },
                            reminderMode = when (entity.reminderMode) {
                                "PERSISTENT" -> com.grindbell.app.domain.model.ReminderMode.PERSISTENT
                                "AGGRESSIVE" -> com.grindbell.app.domain.model.ReminderMode.AGGRESSIVE
                                "CRITICAL" -> com.grindbell.app.domain.model.ReminderMode.CRITICAL
                                else -> com.grindbell.app.domain.model.ReminderMode.NORMAL
                            },
                            isCompleted = entity.isCompleted,
                            isPinned = entity.isPinned
                        )

                        val categoryGradient = state.categories
                            .find { it.id == entity.categoryId }
                            ?.let { listOf(Color(android.graphics.Color.parseColor(it.gradientStartHex)),
                                Color(android.graphics.Color.parseColor(it.gradientEndHex))) }
                            ?: GradientBlue

                        TaskCard(
                            task = task,
                            categoryGradient = categoryGradient,
                            onComplete = { viewModel.completeTask(entity.id) },
                            onClick = { onTaskClick(entity.id) },
                            onPinToggle = { viewModel.togglePin(entity.id) }
                        )
                    }
                }
            }
        }
    }
}
