package com.streakcounter.composeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Placeholder until the real theme lands in Task 7.
private val PlumPlaceholder = Color(0xFF5B2A56)

@Composable
fun App() {
    Box(modifier = Modifier.fillMaxSize().background(PlumPlaceholder))
}
