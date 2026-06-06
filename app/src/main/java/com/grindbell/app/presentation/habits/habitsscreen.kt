package com.grindbell.app.presentation.habits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grindbell.app.presentation.components.*
import com.grindbell.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    onHabitClick: (Long) -> Unit,
    onAddHabit: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHabits() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habits", style = GrindBellTypography.headlineLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight),
                actions = { IconButton(onClick = onAddHabit) { Icon(Icons.Filled.Add, "Add Habit", tint = HabitPurple) } }
            )
        },
        containerColor = BackgroundLight
    ) { padding ->
        if (state.habits.isEmpty() && !state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Repeat, contentDescription = null,
                        tint = HabitPurple.copy(alpha = 0.3f), modifier = Modifier.size(72.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No habits yet", style = GrindBellTypography.headlineSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Create your first habit to build consistency",
                        style = GrindBellTypography.bodyMedium, color = TextTertiary)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onAddHabit, shape = ButtonRounded,
                        colors = ButtonDefaults.buttonColors(containerColor = HabitPurple)) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Habit")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedMedium,
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${state.habits.size}",
                                    style = GrindBellTypography.displayMedium.copy(color = HabitPurple))
                                Text("Active Habits", style = GrindBellTypography.bodySmall, color = TextSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val totalDone = state.habits.sumOf { it.todayCompletions }
                                val totalTarget = state.habits.sumOf { it.dailyTargetCount }
                                Text("${totalDone}/${totalTarget}",
                                    style = GrindBellTypography.displayMedium.copy(color = Emerald))
                                Text("Today's Progress", style = GrindBellTypography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }

                items(state.habits, key = { it.id }) { habit ->
                    val (locked, lockMsg) = viewModel.isHabitLocked(habit)
                    HabitCard(
                        habit = habit,
                        onMarkDone = { viewModel.toggleHabitCompletion(habit.id) },
                        onSnooze = {},
                        onClick = { onHabitClick(habit.id) },
                        isLocked = locked,
                        lockMessage = lockMsg
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
