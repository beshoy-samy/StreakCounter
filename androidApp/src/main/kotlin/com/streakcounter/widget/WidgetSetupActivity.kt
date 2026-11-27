package com.streakcounter.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.streakcounter.MainActivity
import com.streakcounter.core.designsystem.theme.StreakTheme
import com.streakcounter.core.domain.Result
import com.streakcounter.core.domain.Today
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.presentation.widget.WidgetPickerScreen
import com.streakcounter.feature.streaks.presentation.widget.WidgetPickerState
import com.streakcounter.startAppKoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

/**
 * The small widget's configure screen (design `widgets/setup` (pick, none)): shown when the widget is placed,
 * on reconfigure, and from "Streak removed. Tap to pick another.". Stores only the streak id in
 * the widget's Glance state.
 */
class WidgetSetupActivity : ComponentActivity() {
    private val repository: StreakRepository by inject()
    private val today: Today by inject()

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before the first inject() read; a no-op once Application.onCreate has started Koin.
        startAppKoin(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves a newly placed widget unadded.
        setResult(RESULT_CANCELED, resultIntent())
        // The activity is exported for the launcher: only act on our own small widgets.
        if (!isOurSmallWidget(appWidgetId)) {
            finish()
            return
        }
        today.refresh()

        setContent {
            val read by remember { repository.streaks }.collectAsState(initial = null)
            val date by today.date.collectAsState()
            var selected by rememberSaveable { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                // Reconfiguring: start on the current choice, unless the user already picked.
                val current = currentChoice()
                if (selected == null) selected = current
            }
            val state = when (val r = read) {
                null -> WidgetPickerState.Opening
                is Result.Error -> WidgetPickerState.Unreadable
                is Result.Success -> WidgetPickerState.Ready(r.data, date)
            }
            StreakTheme {
                WidgetPickerScreen(
                    state = state,
                    selectedId = selected?.let { Uuid.parseOrNull(it) },
                    onSelect = { selected = it.toString() },
                    onAddWidget = { selected?.let(::confirm) },
                    onCancel = ::finish,
                    onNewStreak = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    private suspend fun currentChoice(): String? = try {
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        getAppWidgetState<Preferences>(this, PreferencesGlanceStateDefinition, glanceId)[SmallStreakWidget.streakIdKey]
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun confirm(streakId: String) {
        lifecycleScope.launch {
            val result = storeWidgetChoice {
                val glanceId = GlanceAppWidgetManager(this@WidgetSetupActivity).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@WidgetSetupActivity, glanceId) { prefs ->
                    prefs[SmallStreakWidget.streakIdKey] = streakId
                }
                SmallStreakWidget().update(this@WidgetSetupActivity, glanceId)
            }
            setResult(result, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun isOurSmallWidget(id: Int): Boolean {
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider ?: return false
        return provider == ComponentName(this, SmallStreakWidgetReceiver::class.java)
    }
}

/**
 * Runs [store] and returns the activity result. RESULT_CANCELED when it fails: the widget was
 * removed while the screen was open (getGlanceIdBy throws IllegalArgumentException) or its state
 * couldn't be written (IOException). An uncaught throw here would crash the setup screen.
 */
internal suspend fun storeWidgetChoice(store: suspend () -> Unit): Int = try {
    store()
    Activity.RESULT_OK
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Activity.RESULT_CANCELED
}
