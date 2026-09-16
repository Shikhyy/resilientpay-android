package com.resilientpay.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ResilientColorScheme = lightColorScheme(
    primary = ResilientTeal,
    onPrimary = ResilientPaper,
    secondary = ResilientBlue,
    onSecondary = ResilientPaper,
    tertiary = ResilientOrange,
    background = ResilientPaper,
    onBackground = ResilientInk,
    surface = ResilientSurface,
    onSurface = ResilientInk,
    outline = ResilientRule,
    error = ResilientRed,
    onError = ResilientPaper,
)

// Sharp geometry per DESIGN_TOKENS.md: Default radius: 0px. Max: 2px.
private val ResilientShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(2.dp),
    extraLarge = RoundedCornerShape(2.dp),
)

@Composable
fun ResilientPayTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ResilientColorScheme,
        shapes = ResilientShapes,
        content = content
    )
}
