package com.grindbell.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.grindbell.app.domain.model.Task
import com.grindbell.app.domain.model.Habit
import com.grindbell.app.presentation.theme.*
import kotlin.math.roundToInt

@Composable
fun TaskCard(
    task: Task,
    categoryGradient: List<Color> = GradientBlue,
    onComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPinToggle: (() -> Unit)? = null
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }

    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 4f else 8f,
        animationSpec = tween(200),
        label = "cardElevation"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .shadow(elevation = elevation.dp, shape = CardRoundedMedium,
                spotColor = categoryGradient.first().copy(alpha = 0.2f))
            .clip(CardRoundedMedium)
            .background(brush = Brush.linearGradient(
                colors = if (task.isCompleted) listOf(Color(0xFFE5E7EB), Color(0xFFD1D5DB)) else categoryGradient,
                start = Offset(0f, 0f), end = Offset(1000f, 1000f)))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (offsetX > 200f) onComplete(); offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        if (!task.isCompleted) offsetX = (offsetX + dragAmount).coerceIn(0f, 300f)
                    })
            }
            .clickable { isPressed = true; onClick() }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight()
                .background(Brush.verticalGradient(categoryGradient)))
            Row(modifier = Modifier.weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // ── STAR / PIN BUTTON ──
                        if (onPinToggle != null) {
                            IconButton(
                                onClick = onPinToggle,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (task.isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (task.isPinned) "Unpin" else "Pin",
                                    tint = if (task.isPinned)
                                        Color(0xFFFFD700) // Gold star
                                    else
                                        TextOnGradient.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(text = task.title,
                            style = GrindBellTypography.titleLarge.copy(
                                color = if (task.isCompleted) TextSecondary else TextOnGradient,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        PriorityBadge(priority = task.priority)
                    }
                    if (task.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = task.description, style = GrindBellTypography.bodySmall,
                            color = if (task.isCompleted) TextTertiary else TextOnGradient.copy(alpha = 0.7f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val tf = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                        Text(text = task.dueDate.format(tf), style = GrindBellTypography.labelMedium,
                            color = if (task.isCompleted) TextTertiary else TextOnGradient.copy(alpha = 0.8f))
                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(text = task.reminderMode.label, style = GrindBellTypography.labelSmall,
                                color = if (task.isCompleted) TextTertiary else TextOnGradient.copy(alpha = 0.9f))
                        }
                    }
                }
                Icon(imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (task.isCompleted) "Completed" else "Not completed",
                    tint = TextOnGradient.copy(alpha = if (task.isCompleted) 0.7f else 0.4f),
                    modifier = Modifier.size(24.dp))
            }
            if (offsetX > 50f && !task.isCompleted) {
                Box(modifier = Modifier.fillMaxHeight().width((offsetX / 300 * 80).dp).background(SuccessGreen),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.CheckCircle, "Complete", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
fun HabitCard(
    habit: Habit,
    onMarkDone: () -> Unit,
    onSnooze: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false,
    lockMessage: String? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val elevation by animateFloatAsState(targetValue = if (isPressed) 3f else 6f, label = "habElevation")

    val targetPct = (habit.todayCompletions.toFloat() / habit.dailyTargetCount.coerceAtLeast(1)).coerceIn(0f, 1f)
    val targetReached = habit.todayCompletions >= habit.dailyTargetCount

    Box(
        modifier = modifier
            .shadow(elevation = elevation.dp, shape = CardRoundedMedium,
                spotColor = HabitPurple.copy(alpha = 0.15f))
            .clip(CardRoundedMedium)
            .background(brush = Brush.linearGradient(
                colors = if (isLocked) listOf(Color(0xFF3D2A5C), Color(0xFF4A3370)) else GradientPurple,
                start = Offset(0f, 0f), end = Offset(1000f, 1000f)))
            .clickable { isPressed = true; onClick() }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight()
                .background(Brush.verticalGradient(if (targetReached) GradientEmerald else GradientPurple)))

            Column(modifier = Modifier.weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = habit.name,
                            style = GrindBellTypography.titleLarge.copy(color = TextOnGradient),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "Every ${habit.frequencyMinutes} min · ${fmtTimeStr(habit.startTime.toString())} - ${fmtTimeStr(habit.endTime.toString())}",
                            style = GrindBellTypography.bodySmall, color = TextOnGradient.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── TARGET PROGRESS BAR ──
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${habit.todayCompletions} / ${habit.dailyTargetCount}",
                        style = GrindBellTypography.labelLarge.copy(
                            color = if (targetReached) Emerald else TextOnGradient))
                    Text("${(targetPct * 100).toInt()}%",
                        style = GrindBellTypography.labelLarge.copy(
                            color = if (targetReached) Emerald else TextOnGradient.copy(alpha = 0.8f)))
                }
                Spacer(Modifier.height(4.dp))
                GradientProgressBar(progress = targetPct,
                    gradientColors = if (targetReached) GradientEmerald else listOf(TextOnGradient, TextOnGradient.copy(alpha = 0.5f)))

                Spacer(modifier = Modifier.height(10.dp))

                // Stats + lock
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Today: ${habit.todayCompletions} done${if (habit.todayMissed > 0) " · ${habit.todayMissed} missed" else ""}",
                            style = GrindBellTypography.labelLarge.copy(
                                color = if (targetReached) Emerald else TextOnGradient))
                        Text("Total: ${habit.totalCompletions}×",
                            style = GrindBellTypography.labelSmall.copy(color = TextOnGradient.copy(alpha = 0.6f)))
                    }
                    if (isLocked && lockMessage != null) {
                        Text(lockMessage, style = GrindBellTypography.labelSmall.copy(color = WarningOrange.copy(alpha = 0.9f)))
                    } else if (targetReached) {
                        Text("✓ Target reached!", style = GrindBellTypography.labelSmall.copy(color = Emerald))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onSnooze, modifier = Modifier.weight(1f).height(40.dp), shape = ButtonRounded,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))) {
                        Text("Snooze", style = GrindBellTypography.labelMedium, color = TextOnGradient)
                    }
                    Button(onClick = onMarkDone, modifier = Modifier.weight(1f).height(40.dp), shape = ButtonRounded,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLocked) Color.White.copy(alpha = 0.3f) else TextOnGradient),
                        enabled = !isLocked) {
                        Text("Done ✓", style = GrindBellTypography.labelMedium,
                            color = if (isLocked) TextOnGradient.copy(alpha = 0.4f) else HabitPurple)
                    }
                }
            }
        }
    }
}

@Composable
private fun animateFloatAsState(targetValue: Float, animationSpec: AnimationSpec<Float> = spring(), label: String): State<Float> {
    val animatable = remember { Animatable(targetValue) }
    LaunchedEffect(targetValue) { animatable.animateTo(targetValue, animationSpec) }
    return animatable.asState()
}

private fun fmtTimeStr(timeStr: String): String {
    return try {
        java.time.LocalTime.parse(timeStr).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    } catch (_: Exception) { timeStr }
}
