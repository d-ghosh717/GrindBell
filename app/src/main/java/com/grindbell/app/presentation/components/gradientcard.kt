package com.grindbell.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.grindbell.app.presentation.theme.*

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = GradientBlue,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(CardRoundedLarge)
            .background(
                brush = Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            content = content
        )
    }
}

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(2.dp, CardRoundedLarge, spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.04f))
            .clip(CardRoundedLarge)
            .background(SurfaceCard)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .shadow(8.dp, CardRoundedLarge, spotColor = Coral.copy(alpha = 0.2f))
            .clip(CardRoundedLarge)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E1E2E), Color(0xFF2D2D44)),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            content = content
        )
    }
}

@Composable
fun PriorityBadge(
    priority: com.grindbell.app.domain.model.Priority,
    modifier: Modifier = Modifier
) {
    val color = when (priority) {
        com.grindbell.app.domain.model.Priority.LOW -> PriorityLow
        com.grindbell.app.domain.model.Priority.MEDIUM -> PriorityMedium
        com.grindbell.app.domain.model.Priority.HIGH -> PriorityHigh
    }
    Box(
        modifier = modifier
            .clip(ChipRounded)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = priority.label,
            style = GrindBellTypography.labelSmall,
            color = color
        )
    }
}

@Composable
fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = GradientBlue
) {
    Box(
        modifier = modifier
            .height(8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(gradientColors))
        )
    }
}

@Composable
fun GradientProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Int = 120,
    strokeWidth: Float = 10f,
    gradientColors: List<Color> = GradientBlue
) {
    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxSize(),
            color = gradientColors.first(),
            trackColor = Color.White.copy(alpha = 0.15f),
            strokeWidth = strokeWidth.dp
        )
        Text(
            text = "${(progress * 100).toInt()}%",
            style = GrindBellTypography.titleLarge,
            color = TextOnGradient
        )
    }
}

@Composable
fun GreetingHeader(modifier: Modifier = Modifier) {
    val hour = java.time.LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        Text(
            text = greeting,
            style = GrindBellTypography.displaySmall,
            color = TextPrimary
        )
        Text(
            text = java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")
            ),
            style = GrindBellTypography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
fun StatChip(
    label: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier,
    color: Color = ElectricBlue
) {
    Row(
        modifier = modifier
            .clip(ChipRounded)
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = icon, style = GrindBellTypography.bodySmall)
        Text(
            text = "$value $label",
            style = GrindBellTypography.labelMedium,
            color = color
        )
    }
}
