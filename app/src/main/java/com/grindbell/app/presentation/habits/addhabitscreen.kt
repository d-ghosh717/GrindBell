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
import com.grindbell.app.presentation.components.*
import com.grindbell.app.presentation.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitScreen(
    onHabitCreated: () -> Unit,
    onCancel: () -> Unit,
    viewModel: HabitCreateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onHabitCreated()
    }

    val datePickerDialog = remember {
        val cal = Calendar.getInstance()
        DatePickerDialog(context, { _, year, month, dayOfMonth ->
            viewModel.onStartDateChanged(LocalDate.of(year, month + 1, dayOfMonth).toString())
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
    }

    fun showTimePicker(currentTime: String, onTimeSelected: (String) -> Unit) {
        val parts = currentTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(context, { _, h, m ->
            onTimeSelected(String.format("%02d:%02d", h, m))
        }, hour, minute, false) // false = AM/PM mode
            .show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Habit", style = GrindBellTypography.headlineLarge) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, "Cancel")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner
            if (uiState.error != null) {
                Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedMedium,
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.08f))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Error, "Error", tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Text(uiState.error!!, style = GrindBellTypography.bodySmall, color = ErrorRed)
                    }
                }
            }

            // Name
            OutlinedTextField(
                value = uiState.name, onValueChange = viewModel::onNameChanged,
                label = { Text("Habit Name *") },
                placeholder = { Text("e.g. Drink Water, Meditate, Stretch") },
                modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { { Text(it) } },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple)
            )

            // Description
            OutlinedTextField(
                value = uiState.description, onValueChange = viewModel::onDescriptionChanged,
                label = { Text("Description") }, placeholder = { Text("Optional") },
                modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple)
            )

            // ── DAILY TARGET COUNT ──
            Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Daily Target", style = GrindBellTypography.labelLarge, color = TextSecondary)
                    Text("How many times per day should this habit be completed?",
                        style = GrindBellTypography.bodySmall, color = TextTertiary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.dailyTargetCount,
                        onValueChange = viewModel::onTargetCountChanged,
                        label = { Text("Target Count") },
                        placeholder = { Text("e.g. 8 glasses of water") },
                        modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                        singleLine = true,
                        isError = uiState.targetError != null,
                        supportingText = uiState.targetError?.let { { Text(it) } },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple)
                    )
                }
            }

            // ── START DATE ──
            Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Start Date", style = GrindBellTypography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = formatDateDisplay(uiState.startDate), onValueChange = {}, readOnly = true,
                        modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(Icons.Filled.CalendarMonth, "Pick date", tint = HabitPurple)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple)
                    )
                }
            }

            // ── START TIME / END TIME ──
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fmtTime(uiState.startTime), onValueChange = {},
                    label = { Text("Start Time") }, readOnly = true,
                    modifier = Modifier.weight(1f), shape = CardRoundedSmall,
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker(uiState.startTime) { viewModel.onStartTimeChanged(it) } }) {
                            Icon(Icons.Filled.Schedule, "Pick time", tint = HabitPurple, modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple)
                )
                OutlinedTextField(
                    value = fmtTime(uiState.endTime), onValueChange = {},
                    label = { Text("End Time") }, readOnly = true,
                    modifier = Modifier.weight(1f), shape = CardRoundedSmall,
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker(uiState.endTime) { viewModel.onEndTimeChanged(it) } }) {
                            Icon(Icons.Filled.Schedule, "Pick time", tint = HabitPurple, modifier = Modifier.size(20.dp))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple)
                )
            }

            // ── FREQUENCY ──
            Card(modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Reminder Frequency", style = GrindBellTypography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15 to "15m", 30 to "30m", 45 to "45m", 60 to "1h").forEach { (min, label) ->
                            FilterChip(
                                selected = !uiState.isCustomFrequency && uiState.frequencyMinutes == min,
                                onClick = { viewModel.onFrequencyChanged(min) },
                                label = { Text(label, style = GrindBellTypography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HabitPurple.copy(alpha = 0.15f)),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(120 to "2h", 180 to "3h", 240 to "4h").forEach { (min, label) ->
                            FilterChip(
                                selected = !uiState.isCustomFrequency && uiState.frequencyMinutes == min,
                                onClick = { viewModel.onFrequencyChanged(min) },
                                label = { Text(label, style = GrindBellTypography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HabitPurple.copy(alpha = 0.15f)),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        FilterChip(
                            selected = uiState.isCustomFrequency,
                            onClick = { viewModel.onCustomFrequencySelected() },
                            label = { Text("Custom", style = GrindBellTypography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HabitPurple.copy(alpha = 0.15f)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (uiState.isCustomFrequency) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.customFrequencyText,
                                onValueChange = viewModel::onCustomFrequencyTextChanged,
                                label = { Text("Minutes") }, placeholder = { Text("e.g. 10, 25, 90") },
                                modifier = Modifier.weight(1f), shape = CardRoundedSmall, singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HabitPurple, focusedLabelColor = HabitPurple)
                            )
                            Button(onClick = viewModel::onCustomFrequencyConfirmed, shape = ButtonRounded,
                                colors = ButtonDefaults.buttonColors(containerColor = HabitPurple),
                                enabled = (uiState.customFrequencyText.toIntOrNull() ?: 0) > 0) {
                                Text("Set")
                            }
                        }
                    }
                    if (!uiState.isCustomFrequency) {
                        Spacer(Modifier.height(4.dp))
                        Text("Every ${uiState.frequencyMinutes} minutes", style = GrindBellTypography.labelSmall.copy(color = HabitPurple))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save
            Button(onClick = viewModel::saveHabit,
                modifier = Modifier.fillMaxWidth().height(56.dp), shape = ButtonRounded,
                colors = ButtonDefaults.buttonColors(containerColor = HabitPurple),
                enabled = !uiState.isSaving) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextOnGradient, strokeWidth = 2.dp)
                } else {
                    Text("Create Habit", style = GrindBellTypography.titleLarge.copy(color = TextOnGradient))
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

internal fun formatDateDisplay(dateStr: String): String {
    return try {
        LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (_: Exception) { dateStr }
}

internal fun fmtTime(timeStr: String): String {
    return try {
        LocalTime.parse(timeStr).format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (_: Exception) { timeStr }
}
