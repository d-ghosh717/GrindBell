package com.grindbell.app.presentation.tasks

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grindbell.app.domain.model.Priority
import com.grindbell.app.domain.model.ReminderMode
import com.grindbell.app.presentation.components.*
import com.grindbell.app.presentation.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskScreen(
    onTaskCreated: () -> Unit,
    onCancel: () -> Unit,
    viewModel: TaskCreateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onTaskCreated()
    }

    // ── Date Picker Dialog ──
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.dueDate
                .atTime(0, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        viewModel.onDateChanged(date)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Time Picker Dialog ──
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.dueTime.hour,
            initialMinute = uiState.dueTime.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onTimeChanged(LocalTime.of(timePickerState.hour, timePickerState.minute))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Task", style = GrindBellTypography.headlineLarge) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner
            if (uiState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardRoundedMedium,
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Error, "Error", tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Text(uiState.error!!, style = GrindBellTypography.bodySmall, color = ErrorRed)
                    }
                }
            }

            // Title
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChanged,
                label = { Text("Task Title *") },
                placeholder = { Text("What needs to be done?") },
                modifier = Modifier.fillMaxWidth(),
                shape = CardRoundedSmall,
                isError = uiState.titleError != null,
                supportingText = uiState.titleError?.let { { Text(it) } },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    focusedLabelColor = ElectricBlue
                )
            )

            // Description
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChanged,
                label = { Text("Description") },
                placeholder = { Text("Add details (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = CardRoundedSmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    focusedLabelColor = ElectricBlue
                )
            )

            // Date & Time row — NOW WITH CLICKABLE PICKERS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.dueDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                    onValueChange = {},
                    label = { Text("Due Date") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    shape = CardRoundedSmall,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.CalendarToday, "Pick date", tint = ElectricBlue)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        focusedLabelColor = ElectricBlue,
                        disabledTextColor = TextPrimary,
                        disabledBorderColor = DividerColor,
                        disabledLabelColor = TextSecondary
                    )
                )

                OutlinedTextField(
                    value = uiState.dueTime.format(timeFormatter),
                    onValueChange = {},
                    label = { Text("Due Time") },
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    shape = CardRoundedSmall,
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker = true }) {
                            Icon(Icons.Filled.Schedule, "Pick time", tint = ElectricBlue)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        focusedLabelColor = ElectricBlue,
                        disabledTextColor = TextPrimary,
                        disabledBorderColor = DividerColor,
                        disabledLabelColor = TextSecondary
                    )
                )
            }

            // Priority selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardRoundedSmall,
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Priority", style = GrindBellTypography.labelLarge, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Priority.entries.forEach { priority ->
                            FilterChip(
                                selected = uiState.priority == priority,
                                onClick = { viewModel.onPriorityChanged(priority) },
                                label = { Text(priority.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (priority) {
                                        Priority.LOW -> PriorityLow.copy(alpha = 0.2f)
                                        Priority.MEDIUM -> PriorityMedium.copy(alpha = 0.2f)
                                        Priority.HIGH -> PriorityHigh.copy(alpha = 0.2f)
                                    }
                                )
                            )
                        }
                    }
                }
            }

            // Reminder mode
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardRoundedSmall,
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Reminder Mode", style = GrindBellTypography.labelLarge, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReminderMode.entries.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.reminderMode == mode,
                                    onClick = { viewModel.onReminderModeChanged(mode) },
                                    colors = RadioButtonDefaults.colors(selectedColor = ElectricBlue)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(mode.label, style = GrindBellTypography.titleMedium)
                                    Text(
                                        text = when (mode) {
                                            ReminderMode.NORMAL -> "Single notification at due time"
                                            ReminderMode.PERSISTENT -> "Every few minutes until you complete"
                                            ReminderMode.AGGRESSIVE -> "Escalating frequency — harder to ignore"
                                            ReminderMode.CRITICAL -> "Full-screen alarm with sound + vibration"
                                        },
                                        style = GrindBellTypography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── REMINDER FREQUENCY ──
            if (uiState.reminderMode != ReminderMode.NORMAL || uiState.repeatUntilDone) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardRoundedSmall,
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Reminder Frequency", style = GrindBellTypography.labelLarge, color = TextSecondary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5 to "5m", 10 to "10m", 15 to "15m", 20 to "20m").forEach { (min, label) ->
                                FilterChip(
                                    selected = !uiState.isCustomInterval && uiState.reminderIntervalMinutes == min,
                                    onClick = { viewModel.onIntervalChanged(min) },
                                    label = { Text(label, style = GrindBellTypography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricBlue.copy(alpha = 0.15f)),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(30 to "30m", 45 to "45m", 60 to "1h", 120 to "2h").forEach { (min, label) ->
                                FilterChip(
                                    selected = !uiState.isCustomInterval && uiState.reminderIntervalMinutes == min,
                                    onClick = { viewModel.onIntervalChanged(min) },
                                    label = { Text(label, style = GrindBellTypography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricBlue.copy(alpha = 0.15f)),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = uiState.isCustomInterval,
                                onClick = { viewModel.onCustomIntervalSelected() },
                                label = { Text("Custom", style = GrindBellTypography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ElectricBlue.copy(alpha = 0.15f))
                            )
                            if (!uiState.isCustomInterval) {
                                Text("Every ${uiState.reminderIntervalMinutes} min",
                                    style = GrindBellTypography.labelSmall.copy(color = ElectricBlue),
                                    modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }
                        if (uiState.isCustomInterval) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = uiState.customIntervalText,
                                    onValueChange = viewModel::onCustomIntervalTextChanged,
                                    label = { Text("Minutes") }, placeholder = { Text("e.g. 7, 12, 25, 90") },
                                    modifier = Modifier.weight(1f), shape = CardRoundedSmall, singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricBlue, focusedLabelColor = ElectricBlue)
                                )
                                Button(onClick = viewModel::onCustomIntervalConfirmed, shape = ButtonRounded,
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    enabled = (uiState.customIntervalText.toIntOrNull() ?: 0) > 0) {
                                    Text("Set")
                                }
                            }
                        }
                    }
                }
            }

            // Repeat Until Done toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Repeat Until Done", style = GrindBellTypography.titleMedium)
                    Text(
                        "Keep reminding until you mark this complete",
                        style = GrindBellTypography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = uiState.repeatUntilDone,
                    onCheckedChange = viewModel::onRepeatUntilDoneChanged,
                    colors = SwitchDefaults.colors(checkedTrackColor = ElectricBlue)
                )
            }

            // Pin / Star toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = if (uiState.isPinned) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = if (uiState.isPinned) Color(0xFFFFD700) else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Pin to Dashboard", style = GrindBellTypography.titleMedium)
                    }
                    Text(
                        "Always visible at the top of your dashboard",
                        style = GrindBellTypography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = uiState.isPinned,
                    onCheckedChange = viewModel::onPinnedChanged,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFFD700))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save button
            Button(
                onClick = { viewModel.saveTask() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = ButtonRounded,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = TextOnGradient,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Task", style = GrindBellTypography.titleLarge.copy(color = TextOnGradient))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
