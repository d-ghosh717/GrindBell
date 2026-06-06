package com.grindbell.app.presentation.tasks

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.grindbell.app.domain.model.Priority
import com.grindbell.app.domain.model.ReminderMode
import com.grindbell.app.presentation.components.*
import com.grindbell.app.presentation.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(taskId) { viewModel.loadTask(taskId) }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) onDeleted() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    // Date picker dialog
    if (showDatePicker && uiState.task != null) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.task!!.dueDate.toLocalDate()
                .atTime(0, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.onDateChanged(date)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    // Time picker dialog
    if (showTimePicker && uiState.task != null) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.task!!.dueDate.hour,
            initialMinute = uiState.task!!.dueDate.minute,
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
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Details", style = GrindBellTypography.headlineLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!uiState.isEditing) {
                        // Pin toggle
                        IconButton(onClick = { viewModel.togglePin() }) {
                            val isPinned = uiState.task?.isPinned == true
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (isPinned) "Unpin" else "Pin",
                                tint = if (isPinned) Color(0xFFFFD700) else TextSecondary
                            )
                        }
                        IconButton(onClick = { viewModel.toggleEdit() }) {
                            Icon(Icons.Filled.Edit, "Edit", tint = ElectricBlue)
                        }
                    }
                    IconButton(onClick = { viewModel.deleteTask(); onDeleted() }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = ErrorRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        containerColor = BackgroundLight
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricBlue)
            }
        } else if (uiState.task == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = TextTertiary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Task not found", style = GrindBellTypography.bodyLarge, color = TextSecondary)
                    Button(onClick = { viewModel.loadTask(taskId) }, shape = ButtonRounded) {
                        Text("Retry")
                    }
                }
            }
        } else {
            val task = uiState.task!!

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
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, "Error", tint = ErrorRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(uiState.error!!, style = GrindBellTypography.bodySmall, color = ErrorRed)
                        }
                    }
                }

                if (uiState.isEditing) {
                    // ── EDIT MODE ──
                    OutlinedTextField(
                        value = uiState.editTitle,
                        onValueChange = viewModel::onEditTitleChanged,
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardRoundedSmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue, focusedLabelColor = ElectricBlue
                        )
                    )

                    OutlinedTextField(
                        value = uiState.editDescription,
                        onValueChange = viewModel::onEditDescriptionChanged,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        shape = CardRoundedSmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue, focusedLabelColor = ElectricBlue
                        )
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = uiState.editDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                            onValueChange = {}, readOnly = true, enabled = false,
                            label = { Text("Date") }, modifier = Modifier.weight(1f),
                            shape = CardRoundedSmall,
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Filled.CalendarToday, null, tint = ElectricBlue)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = TextPrimary, disabledBorderColor = DividerColor,
                                disabledLabelColor = TextSecondary
                            )
                        )
                        OutlinedTextField(
                            value = uiState.editTime.format(timeFormatter),
                            onValueChange = {}, readOnly = true, enabled = false,
                            label = { Text("Time") }, modifier = Modifier.weight(1f),
                            shape = CardRoundedSmall,
                            trailingIcon = {
                                IconButton(onClick = { showTimePicker = true }) {
                                    Icon(Icons.Filled.Schedule, null, tint = ElectricBlue)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = TextPrimary, disabledBorderColor = DividerColor,
                                disabledLabelColor = TextSecondary
                            )
                        )
                    }

                    // Priority
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = CardRoundedSmall,
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Priority", style = GrindBellTypography.labelLarge, color = TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Priority.entries.forEach { p ->
                                    FilterChip(
                                        selected = uiState.editPriority == p,
                                        onClick = { viewModel.onEditPriorityChanged(p) },
                                        label = { Text(p.label) }
                                    )
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.toggleEdit() },
                            modifier = Modifier.weight(1f).height(48.dp), shape = ButtonRounded
                        ) { Text("Cancel") }
                        Button(
                            onClick = { viewModel.saveEdits() },
                            modifier = Modifier.weight(1f).height(48.dp), shape = ButtonRounded,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TextOnGradient, strokeWidth = 2.dp)
                            else Text("Save Changes", style = GrindBellTypography.labelLarge.copy(color = TextOnGradient))
                        }
                    }
                } else {
                    // ── VIEW MODE ──
                    // Status + Priority
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isOverdue = task.dueDate.isBefore(java.time.LocalDateTime.now())
                        if (task.isCompleted) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Completed") },
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, null, tint = Emerald, modifier = Modifier.size(18.dp)) }
                            )
                        } else if (isOverdue) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Overdue") },
                                leadingIcon = { Icon(Icons.Filled.Warning, null, tint = Coral, modifier = Modifier.size(18.dp)) }
                            )
                        }
                        PriorityBadge(priority = task.priority)
                    }

                    // Title
                    Text(task.title, style = GrindBellTypography.displaySmall, color = TextPrimary)

                    // Description
                    if (task.description.isNotBlank()) {
                        Text(task.description, style = GrindBellTypography.bodyLarge, color = TextSecondary)
                    }

                    // Meta
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = CardRoundedMedium,
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DetailRow("Date", task.dueDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")))
                            DetailRow("Time", task.dueDate.format(timeFormatter))
                            DetailRow("Reminder Mode", task.reminderMode.label)
                            DetailRow("Repeat Until Done", if (task.repeatUntilDone) "Yes" else "No")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Actions
                    if (!task.isCompleted) {
                        Button(
                            onClick = { viewModel.markDone() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = ButtonRounded,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald)
                        ) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Mark as Done", style = GrindBellTypography.titleLarge.copy(color = Color.White))
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(), shape = CardRoundedMedium,
                            colors = CardDefaults.cardColors(containerColor = Emerald.copy(alpha = 0.08f))
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = Emerald, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Task Completed", style = GrindBellTypography.titleLarge, color = Emerald)
                                    task.completedAt?.let {
                                        Text(
                                            "Completed ${it.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))}",
                                            style = GrindBellTypography.bodySmall, color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = GrindBellTypography.bodyMedium, color = TextSecondary)
        Text(value, style = GrindBellTypography.bodyMedium, color = TextPrimary)
    }
}
