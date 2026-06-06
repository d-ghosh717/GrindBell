package com.grindbell.app.presentation.habits

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grindbell.app.domain.model.Priority
import com.grindbell.app.presentation.components.*
import com.grindbell.app.presentation.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    habitId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: HabitDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(habitId) { viewModel.loadHabit(habitId) }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onDeleted() }

    val datePickerDialog = remember {
        val cal = Calendar.getInstance()
        DatePickerDialog(context, { _, year, month, dayOfMonth ->
            viewModel.onEditStartDateChanged(LocalDate.of(year, month + 1, dayOfMonth).toString())
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
    }

    fun showTimePicker(currentTime: String, onTimeSelected: (String) -> Unit) {
        val parts = currentTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(context, { _, h, m ->
            onTimeSelected(String.format("%02d:%02d", h, m))
        }, hour, minute, false).show() // false = AM/PM
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habit Details", style = GrindBellTypography.headlineLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!uiState.isEditing) {
                        IconButton(onClick = { viewModel.toggleEdit() }) {
                            Icon(Icons.Filled.Edit, "Edit", tint = ElectricBlue)
                        }
                    }
                    IconButton(onClick = { viewModel.deleteHabit(); onDeleted() }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = ErrorRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        containerColor = BackgroundLight
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HabitPurple)
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = TextTertiary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(uiState.error!!, style = GrindBellTypography.bodyLarge, color = TextSecondary)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadHabit(habitId) }, shape = ButtonRounded) { Text("Retry") }
                    }
                }
            }
            uiState.habit == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Inbox, null, tint = TextTertiary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Habit not found", style = GrindBellTypography.bodyLarge, color = TextSecondary)
                    }
                }
            }
            else -> {
                val habit = uiState.habit!!
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding)
                        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.isEditing) {
                        // ── EDIT MODE ──
                        OutlinedTextField(value = uiState.editName, onValueChange = viewModel::onEditNameChanged,
                            label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple))
                        OutlinedTextField(value = uiState.editDescription, onValueChange = viewModel::onEditDescriptionChanged,
                            label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple))

                        // Daily Target
                        Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Daily Target Count", style = GrindBellTypography.labelLarge, color = TextSecondary)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = uiState.editDailyTarget,
                                    onValueChange = viewModel::onEditTargetChanged,
                                    label = { Text("Times per day") }, singleLine = true,
                                    modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple))
                            }
                        }

                        // Date
                        Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Start Date", style = GrindBellTypography.labelLarge, color = TextSecondary)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = formatDateDisplay(uiState.editStartDate), onValueChange = {}, readOnly = true,
                                    modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                                    trailingIcon = { IconButton(onClick = { datePickerDialog.show() }) { Icon(Icons.Filled.CalendarMonth, "Pick date", tint = HabitPurple) } },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple))
                            }
                        }

                        // Time
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(value = fmtTime(uiState.editStartTime), onValueChange = {},
                                label = { Text("Start") }, readOnly = true, modifier = Modifier.weight(1f), shape = CardRoundedSmall,
                                trailingIcon = { IconButton(onClick = { showTimePicker(uiState.editStartTime) { viewModel.onEditStartTimeChanged(it) } }) { Icon(Icons.Filled.Schedule, "Time", tint = HabitPurple, modifier = Modifier.size(20.dp)) } },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple))
                            OutlinedTextField(value = fmtTime(uiState.editEndTime), onValueChange = {},
                                label = { Text("End") }, readOnly = true, modifier = Modifier.weight(1f), shape = CardRoundedSmall,
                                trailingIcon = { IconButton(onClick = { showTimePicker(uiState.editEndTime) { viewModel.onEditEndTimeChanged(it) } }) { Icon(Icons.Filled.Schedule, "Time", tint = HabitPurple, modifier = Modifier.size(20.dp)) } },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple))
                        }

                        // Frequency
                        Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Reminder Frequency (min)", style = GrindBellTypography.labelLarge, color = TextSecondary)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(15, 20, 30, 45, 60, 120, 180, 240).forEach { min ->
                                        FilterChip(selected = uiState.editFrequency == min,
                                            onClick = { viewModel.onEditFrequencyChanged(min) },
                                            label = { Text(if (min >= 60) "${min / 60}h" else "${min}m", style = GrindBellTypography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HabitPurple.copy(alpha = 0.15f)))
                                    }
                                }
                            }
                        }

                        // Priority
                        Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Priority", style = GrindBellTypography.labelLarge, color = TextSecondary)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Priority.entries.forEach { p ->
                                        FilterChip(selected = uiState.editPriority == p,
                                            onClick = { viewModel.onEditPriorityChanged(p) },
                                            label = { Text(p.label) })
                                    }
                                }
                            }
                        }

                        // Toggle active
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (uiState.editIsActive) "Active" else "Paused", style = GrindBellTypography.titleMedium)
                            Switch(checked = uiState.editIsActive, onCheckedChange = viewModel::onEditActiveChanged,
                                colors = SwitchDefaults.colors(checkedTrackColor = HabitPurple))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { viewModel.toggleEdit() }, modifier = Modifier.weight(1f).height(48.dp), shape = ButtonRounded) { Text("Cancel") }
                            Button(onClick = { viewModel.saveEdits() }, modifier = Modifier.weight(1f).height(48.dp), shape = ButtonRounded,
                                colors = ButtonDefaults.buttonColors(containerColor = HabitPurple), enabled = !uiState.isSaving) {
                                if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TextOnGradient, strokeWidth = 2.dp)
                                else Text("Save", style = GrindBellTypography.labelLarge.copy(color = TextOnGradient))
                            }
                        }
                    } else {
                        // ── VIEW MODE ──
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = {}, label = { Text(if (habit.isActive) "Active" else "Paused") },
                                leadingIcon = { Icon(if (habit.isActive) Icons.Filled.PlayArrow else Icons.Filled.Pause, null,
                                    tint = if (habit.isActive) Emerald else TextTertiary, modifier = Modifier.size(18.dp)) })
                            PriorityBadge(priority = habit.priority)
                        }

                        Text(habit.name, style = GrindBellTypography.displaySmall, color = TextPrimary)
                        if (habit.description.isNotBlank()) {
                            Text(habit.description, style = GrindBellTypography.bodyLarge, color = TextSecondary)
                        }

                        // ── TARGET PROGRESS CARD ──
                        Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedMedium,
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Today's Progress", style = GrindBellTypography.titleLarge, color = TextPrimary)
                                    Text("${habit.todayCompletions} / ${habit.dailyTargetCount}",
                                        style = GrindBellTypography.headlineMedium.copy(
                                            color = if (habit.todayCompletions >= habit.dailyTargetCount) Emerald else ElectricBlue))
                                }
                                Spacer(Modifier.height(8.dp))
                                val targetPct = (habit.todayCompletions.toFloat() / habit.dailyTargetCount).coerceIn(0f, 1f)
                                GradientProgressBar(progress = targetPct,
                                    gradientColors = if (targetPct >= 1f) listOf(Emerald, Emerald) else GradientBlue)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${(targetPct * 100).toInt()}% of daily target",
                                    style = GrindBellTypography.labelSmall, color = TextSecondary)
                            }
                        }

                        // Stats
                        Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedMedium,
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DetailRow("Daily Target", "${habit.dailyTargetCount}× per day")
                                DetailRow("Frequency", "Every ${habit.frequencyMinutes} min")
                                habit.startDate?.let { DetailRow("Start Date", it.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))) }
                                DetailRow("Active Hours", "${fmtTime(habit.startTime.toString())} — ${fmtTime(habit.endTime.toString())}")
                                DetailRow("Today", "${habit.todayCompletions} done · ${habit.todayMissed} missed")
                                habit.lastCompletedAt?.let {
                                    DetailRow("Last Done", it.format(DateTimeFormatter.ofPattern("MMM d · h:mm a")))
                                }
                            }
                        }

                        // Cycle lock
                        val (locked, lockMsg) = viewModel.isHabitLocked(habit)
                        if (locked && lockMsg != null) {
                            Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedMedium,
                                colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.1f))) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Lock, null, tint = WarningOrange, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(lockMsg, style = GrindBellTypography.bodySmall, color = WarningOrange)
                                }
                            }
                        }

                        // Quick actions
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = {}, modifier = Modifier.weight(1f).height(44.dp), shape = ButtonRounded,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HabitPurple)) { Text("Snooze") }
                            Button(onClick = { viewModel.markDone() }, modifier = Modifier.weight(1f).height(44.dp),
                                shape = ButtonRounded, colors = ButtonDefaults.buttonColors(containerColor = HabitPurple),
                                enabled = !locked) { Text("Done ✓") }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = GrindBellTypography.bodyMedium, color = TextSecondary)
        Text(value, style = GrindBellTypography.bodyMedium, color = TextPrimary)
    }
}
