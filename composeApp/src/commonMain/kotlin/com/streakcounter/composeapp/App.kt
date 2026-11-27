package com.streakcounter.composeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.streakcounter.core.domain.Today
import org.koin.compose.koinInject

// Placeholder until the real theme lands in Task 7.
private val PlumPlaceholder = Color(0xFF5B2A56)

@Composable
fun App() {
    val today = koinInject<Today>()
    // Coming back to the app may follow a midnight, time-zone or clock change nobody told us about.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { today.refresh() }

    Box(modifier = Modifier.fillMaxSize().background(PlumPlaceholder))
}
