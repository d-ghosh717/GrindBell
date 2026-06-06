package com.grindbell.app.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grindbell.app.presentation.components.*
import com.grindbell.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToTasks: () -> Unit,
    onNavigateToHabits: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onAddTask: () -> Unit,
    onReminderTap: (taskId: Long?, habitId: Long?) -> Unit,
    onTaskClick: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Room Flows are reactive — dashboard updates instantly on any DB change.
    // No manual refresh needed.

    Box(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    GreetingHeader(modifier = Modifier.weight(1f))
                    IconButton(onClick = onNavigateToAnalytics) {
                        Icon(Icons.Outlined.BarChart, "Analytics", tint = TextSecondary, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // ── TODAY PROGRESS ──
            item {
                val totalDone = state.tasksCompletedToday + state.habitsCompletedToday
                val totalPossible = (state.tasksCompletedToday + state.tasksPendingToday + state.habitsTargetToday).coerceAtLeast(1)
                val pct = (totalDone.toFloat() / totalPossible * 100).toInt()
                GradientCard(
                    modifier = Modifier.fillMaxWidth().shadow(12.dp, CardRoundedLarge, spotColor = ElectricBlue.copy(alpha = 0.15f)).clip(CardRoundedLarge),
                    gradientColors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF1A1A2E), Color(0xFF0F3460))
                ) {
                    Text("Today's Progress", style = GrindBellTypography.titleLarge.copy(color = TextOnGradient.copy(alpha = 0.7f)))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("$pct%", style = GrindBellTypography.displayLarge.copy(color = TextOnGradient))
                    Text("$totalDone / $totalPossible done · ${state.habitsReachedTarget} habits on target",
                        style = GrindBellTypography.labelSmall.copy(color = TextOnGradient.copy(alpha = 0.6f)))
                    Spacer(modifier = Modifier.height(12.dp))
                    GradientProgressBar(progress = totalDone.toFloat() / totalPossible, gradientColors = listOf(ElectricBlue, Emerald))
                }
            }

            // ── URGENT REMINDER ──
            if (state.urgentReminders.isNotEmpty()) {
                val urgent = state.urgentReminders.first()
                val overdueMin = (System.currentTimeMillis() - urgent.scheduledAt) / 60000
                item {
                    PulseCard(modifier = Modifier.fillMaxWidth().clickable { onReminderTap(urgent.taskId, urgent.habitId) }, isActive = true) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.NotificationsActive, null, tint = Coral, modifier = Modifier.size(18.dp))
                                    Text("Urgent Reminder", style = GrindBellTypography.labelLarge.copy(color = Coral))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(urgent.title, style = GrindBellTypography.headlineSmall.copy(color = TextOnGradient))
                                Text("Overdue: ${overdueMin} min · Repeats every ${urgent.intervalMinutes} min",
                                    style = GrindBellTypography.bodySmall.copy(color = TextOnGradient.copy(alpha = 0.7f)))
                            }
                            Icon(Icons.Filled.ChevronRight, null, tint = TextOnGradient.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // ── PINNED TASKS ──
            if (state.pinnedTasks.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⭐", style = GrindBellTypography.headlineSmall)
                        Text("Pinned Tasks", style = GrindBellTypography.headlineSmall, color = TextPrimary)
                    }
                }
                items(state.pinnedTasks) { task ->
                    GlassmorphicCard(modifier = Modifier.fillMaxWidth().clickable { onTaskClick(task.id) }
                        .shadow(4.dp, CardRoundedMedium, spotColor = Color(0xFFFBBF24).copy(alpha = 0.2f))) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("⭐", style = GrindBellTypography.bodySmall)
                                    Text(task.title, style = GrindBellTypography.titleLarge, color = TextPrimary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(java.time.Instant.ofEpochMilli(task.dueDate)
                                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                                        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a · MMM d")),
                                        style = GrindBellTypography.bodySmall, color = TextSecondary)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when (task.priority) {
                                                    "HIGH" -> PriorityHigh.copy(alpha = 0.15f)
                                                    "LOW" -> PriorityLow.copy(alpha = 0.15f)
                                                    else -> PriorityMedium.copy(alpha = 0.15f)
                                                }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = task.priority,
                                            style = GrindBellTypography.labelSmall,
                                            color = when (task.priority) {
                                                "HIGH" -> PriorityHigh
                                                "LOW" -> PriorityLow
                                                else -> PriorityMedium
                                            }
                                        )
                                    }
                                }
                            }
                            // Show reminder mode badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFBBF24).copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = when (task.reminderMode) {
                                        "CRITICAL" -> "🚨 Critical"
                                        "AGGRESSIVE" -> "⚡ Aggressive"
                                        "PERSISTENT" -> "🔁 Persistent"
                                        else -> "Normal"
                                    },
                                    style = GrindBellTypography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ── SUMMARY CARDS ──
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassmorphicCard(modifier = Modifier.weight(1f).shadow(4.dp, CardRoundedMedium, spotColor = ElectricBlue.copy(alpha = 0.06f))) {
                        Text("Tasks", style = GrindBellTypography.labelMedium.copy(color = TextSecondary))
                        Spacer(Modifier.height(8.dp))
                        Text("${state.tasksCompletedToday}/${state.tasksCompletedToday + state.tasksPendingToday}",
                            style = GrindBellTypography.displayMedium.copy(color = ElectricBlue))
                        Text("done today", style = GrindBellTypography.labelSmall.copy(color = TextTertiary))
                        Spacer(Modifier.height(8.dp))
                        GradientProgressBar(
                            progress = if (state.tasksCompletedToday + state.tasksPendingToday > 0)
                                state.tasksCompletedToday.toFloat() / (state.tasksCompletedToday + state.tasksPendingToday) else 0f,
                            gradientColors = GradientBlue)
                    }
                    GlassmorphicCard(modifier = Modifier.weight(1f).shadow(4.dp, CardRoundedMedium, spotColor = HabitPurple.copy(alpha = 0.06f))) {
                        Text("Habits", style = GrindBellTypography.labelMedium.copy(color = TextSecondary))
                        Spacer(Modifier.height(8.dp))
                        Text("${state.habitsReachedTarget}/${state.activeHabits.size}",
                            style = GrindBellTypography.displayMedium.copy(color = HabitPurple))
                        Text("on target", style = GrindBellTypography.labelSmall.copy(color = TextTertiary))
                        Spacer(Modifier.height(8.dp))
                        GradientProgressBar(
                            progress = if (state.activeHabits.isNotEmpty())
                                state.habitsReachedTarget.toFloat() / state.activeHabits.size else 0f,
                            gradientColors = GradientPurple)
                    }
                }
            }

            // ── QUICK ACTIONS ──
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onNavigateToTasks, modifier = Modifier.weight(1f).height(48.dp), shape = ButtonRounded,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue)) {
                        Icon(Icons.Filled.Checklist, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("View Tasks", style = GrindBellTypography.labelLarge)
                    }
                    OutlinedButton(onClick = onNavigateToHabits, modifier = Modifier.weight(1f).height(48.dp), shape = ButtonRounded,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HabitPurple)) {
                        Icon(Icons.Filled.Repeat, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("View Habits", style = GrindBellTypography.labelLarge)
                    }
                }
            }

            // ── MORE URGENT ──
            if (state.urgentReminders.size > 1) {
                item { Text("⚡ More Reminders", style = GrindBellTypography.headlineSmall, color = TextPrimary) }
                items(state.urgentReminders.drop(1).take(3)) { r ->
                    GlassmorphicCard(modifier = Modifier.fillMaxWidth().clickable { onReminderTap(r.taskId, r.habitId) }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.title, style = GrindBellTypography.titleLarge, color = TextPrimary)
                                Text("${(System.currentTimeMillis() - r.scheduledAt) / 60000}m overdue",
                                    style = GrindBellTypography.bodySmall, color = Coral)
                            }
                            PriorityBadge(priority = com.grindbell.app.domain.model.Priority.HIGH)
                        }
                    }
                }
            }

            // ── UPCOMING ──
            item { Text("📋 Upcoming", style = GrindBellTypography.headlineSmall, color = TextPrimary) }
            if (state.upcomingTasks.isEmpty()) {
                item { GlassmorphicCard(modifier = Modifier.fillMaxWidth()) { Text("No upcoming tasks", style = GrindBellTypography.bodyMedium, color = TextSecondary) } }
            } else {
                items(state.upcomingTasks) { task ->
                    GlassmorphicCard(modifier = Modifier.fillMaxWidth().clickable { onTaskClick(task.id) }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.title, style = GrindBellTypography.titleLarge, color = TextPrimary)
                                Text(java.time.Instant.ofEpochMilli(task.dueDate)
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")),
                                    style = GrindBellTypography.bodySmall, color = TextSecondary)
                            }
                            PriorityBadge(priority = when (task.priority) {
                                "LOW" -> com.grindbell.app.domain.model.Priority.LOW
                                "HIGH" -> com.grindbell.app.domain.model.Priority.HIGH
                                else -> com.grindbell.app.domain.model.Priority.MEDIUM
                            })
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }

        FloatingActionButton(onClick = onAddTask,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 100.dp).size(60.dp)
                .shadow(8.dp, CircleShape, spotColor = ElectricBlue.copy(alpha = 0.3f)),
            shape = CircleShape, containerColor = ElectricBlue, contentColor = TextOnGradient) {
            Icon(Icons.Filled.Add, "Add", modifier = Modifier.size(28.dp))
        }
    }
}
