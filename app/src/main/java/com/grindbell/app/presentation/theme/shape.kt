package com.grindbell.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val GrindBellShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

// Card corners
val CardRoundedLarge = RoundedCornerShape(28.dp)
val CardRoundedMedium = RoundedCornerShape(24.dp)
val CardRoundedSmall = RoundedCornerShape(16.dp)

// Button corners
val ButtonRounded = RoundedCornerShape(24.dp)
val PillShape = RoundedCornerShape(50)

// Chip corners
val ChipRounded = RoundedCornerShape(20.dp)
