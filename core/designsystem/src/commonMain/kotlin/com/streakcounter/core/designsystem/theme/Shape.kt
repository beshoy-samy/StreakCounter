package com.streakcounter.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radii from tokens.json `radius`. */
object Radius {
    val sm = 12.dp
    val md = 18.dp
    val lg = 28.dp
    val xl = 32.dp
}

val StreakShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/** `radius.full`. */
val FullShape = CircleShape
