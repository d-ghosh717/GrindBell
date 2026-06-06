package com.grindbell.app.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grindbell.app.presentation.components.*
import com.grindbell.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadAnalytics() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics", style = GrindBellTypography.headlineLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        containerColor = BackgroundLight
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Period tabs
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Today", "Week", "Month").forEach { period ->
                            FilterChip(
                                selected = period == state.selectedPeriod,
                                onClick = { viewModel.selectPeriod(period) },
                                label = { Text(period) },
                                shape = ChipRounded
                            )
                        }
                    }
                }

                item {
                    when (state.selectedPeriod) {
                        "Today" -> { TodayAnalyticsContent(state) }
                        "Week" -> { WeekAnalyticsContent(state) }
                        "Month" -> { MonthAnalyticsContent(state) }
                    }
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun TodayAnalyticsContent(state: AnalyticsState) {
    TodayStats(state)
}

@Composable
private fun WeekAnalyticsContent(state: AnalyticsState) {
    WeekStats(state)
}

@Composable
private fun MonthAnalyticsContent(state: AnalyticsState) {
    MonthStats(state)
}

@Composable
private fun TodayStats(state: AnalyticsState) {
    // Overview cards
    GradientCard(
        modifier = Modifier.fillMaxWidth(),
        gradientColors = GradientDark
    ) {
        Text("Today Overview", style = GrindBellTypography.titleLarge.copy(color = TextOnGradient))
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Tasks Done", "${state.todayTasksCompleted}", ElectricBlue)
            StatItem("Habits Done", "${state.todayHabitsCompleted}", HabitPurple)
            StatItem("Missed", "${state.todayMissedReminders}", Coral)
        }
    }

    // Tasks breakdown
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRoundedMedium,
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Tasks", style = GrindBellTypography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricBox("Created", state.todayTasksCreated, ElectricBlue)
                MetricBox("Completed", state.todayTasksCompleted, Emerald)
                MetricBox("Missed", state.todayTasksMissed, Coral)
            }
        }
    }

    // Habits breakdown
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRoundedMedium,
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Habits", style = GrindBellTypography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricBox("Due", state.todayHabitsDue, Violet)
                MetricBox("Completed", state.todayHabitsCompleted, Emerald)
                MetricBox("Missed", state.todayHabitsMissed, Coral)
            }
        }
    }

    // Completion %
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRoundedMedium,
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Completion %", style = GrindBellTypography.titleLarge)
                Text(
                    "${(state.todayCompletionPercentage * 100).toInt()}%",
                    style = GrindBellTypography.headlineLarge.copy(
                        color = if (state.todayCompletionPercentage >= 0.5f) Emerald else Coral
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            GradientProgressBar(
                progress = state.todayCompletionPercentage,
                gradientColors = listOf(ElectricBlue, Emerald)
            )
        }
    }

    // Most Missed / Completed Habit
    if (state.mostMissedHabit != null || state.mostCompletedHabit != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardRoundedMedium,
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.mostCompletedHabit?.let {
                    InsightRow("Most Completed Habit", it, Emerald)
                }
                state.mostMissedHabit?.let {
                    InsightRow("Most Missed Habit", it, Coral)
                }
            }
        }
    }
}

@Composable
private fun WeekStats(state: AnalyticsState) {
    // Tasks per day summary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRoundedMedium,
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Tasks Completed Per Day", style = GrindBellTypography.titleLarge)
            Spacer(Modifier.height(12.dp))
            state.weekTasksPerDay.forEach { (day, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(day, style = GrindBellTypography.bodySmall, color = TextSecondary, modifier = Modifier.width(50.dp))
                    Box(modifier = Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE5E7EB))) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (count.toFloat() / (state.weekTasksPerDay.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)).coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(ElectricBlue)
                        )
                    }
                    Text(
                        "$count",
                        style = GrindBellTypography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.width(30.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }

    // Habits per day
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRoundedMedium,
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Habits Completed Per Day", style = GrindBellTypography.titleLarge)
            Spacer(Modifier.height(12.dp))
            state.weekHabitsPerDay.forEach { (day, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(day, style = GrindBellTypography.bodySmall, color = TextSecondary, modifier = Modifier.width(50.dp))
                    Box(modifier = Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE5E7EB))) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (count.toFloat() / (state.weekHabitsPerDay.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)).coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(HabitPurple)
                        )
                    }
                    Text("$count", style = GrindBellTypography.bodySmall, color = TextPrimary, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                }
            }
        }
    }

    // Week summary stats
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = CardRoundedMedium,
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(state.weekCompletionPercentage * 100).toInt()}%", style = GrindBellTypography.headlineMedium.copy(color = ElectricBlue))
                Text("Completion", style = GrindBellTypography.labelSmall, color = TextSecondary)
            }
        }
        Card(
            modifier = Modifier.weight(1f),
            shape = CardRoundedMedium,
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.weekMissedReminders}", style = GrindBellTypography.headlineMedium.copy(color = Coral))
                Text("Missed", style = GrindBellTypography.labelSmall, color = TextSecondary)
            }
        }
    }

    // Best/Worst day
    if (state.bestDay != null || state.worstDay != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardRoundedMedium,
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.bestDay?.let { InsightRow("Best Day", it, Emerald) }
                state.worstDay?.let { InsightRow("Worst Day", it, Coral) }
            }
        }
    }
}

@Composable
private fun MonthStats(state: AnalyticsState) {
    // Trend summary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRoundedMedium,
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Task Completion Trend", style = GrindBellTypography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val maxVal = state.monthTasksPerDay.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                state.monthTasksPerDay.takeLast(30).forEach { (_, count) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction = (count.toFloat() / maxVal).coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(ElectricBlue)
                    )
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardRoundedMedium,
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Habit Completion Trend", style = GrindBellTypography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val maxVal = state.monthHabitsPerDay.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                state.monthHabitsPerDay.takeLast(30).forEach { (_, count) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction = (count.toFloat() / maxVal).coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(HabitPurple)
                    )
                }
            }
        }
    }

    // Summary stats
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = CardRoundedMedium,
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(state.monthCompletionPercentage * 100).toInt()}%", style = GrindBellTypography.headlineMedium.copy(color = ElectricBlue))
                Text("Completion", style = GrindBellTypography.labelSmall, color = TextSecondary)
            }
        }
        Card(
            modifier = Modifier.weight(1f),
            shape = CardRoundedMedium,
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(state.reminderResponseRate * 100).toInt()}%", style = GrindBellTypography.headlineMedium.copy(color = Violet))
                Text("Response Rate", style = GrindBellTypography.labelSmall, color = TextSecondary)
            }
        }
    }

    // Most successful / ignored habits
    if (state.mostSuccessfulHabit != null || state.mostIgnoredHabit != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardRoundedMedium,
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.mostSuccessfulHabit?.let { InsightRow("Most Successful Habit", it, Emerald) }
                state.mostIgnoredHabit?.let { InsightRow("Most Ignored Habit", it, Coral) }
            }
        }
    }
}

// ── Reusable components ──

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = GrindBellTypography.headlineLarge.copy(color = TextOnGradient))
        Text(label, style = GrindBellTypography.labelSmall.copy(color = TextOnGradient.copy(alpha = 0.7f)))
    }
}

@Composable
private fun MetricBox(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$value", style = GrindBellTypography.headlineMedium.copy(color = color))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = GrindBellTypography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun InsightRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = GrindBellTypography.bodyMedium, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(value, style = GrindBellTypography.titleMedium, color = color)
        }
    }
}
