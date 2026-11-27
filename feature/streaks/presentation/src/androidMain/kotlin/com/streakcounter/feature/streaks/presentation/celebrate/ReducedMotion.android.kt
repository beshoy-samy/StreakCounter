package com.streakcounter.feature.streaks.presentation.celebrate

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    // "Remove animations" (and the developer option set to off) both write a 0 scale.
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
