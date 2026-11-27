package com.streakcounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.streakcounter.composeapp.App
import com.streakcounter.composeapp.TimeChangeObserver
import com.streakcounter.core.domain.Today
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val today: Today by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars with icons that follow the theme (light icons on dark only).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(TimeChangeObserver(this, today))
        setContent { App() }
    }
}
