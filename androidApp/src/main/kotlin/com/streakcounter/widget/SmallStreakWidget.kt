package com.streakcounter.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.streakcounter.MainActivity
import com.streakcounter.R
import com.streakcounter.core.domain.Today
import com.streakcounter.core.presentation.format.formatCount
import com.streakcounter.core.presentation.format.formatShortDate
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.StreakType
import org.koin.core.context.GlobalContext

/**
 * One chosen streak (spec 4.5): 2×1 shows name, number, minus and plus; 2×2 adds the goal hint.
 * The streak id lives in this widget's Glance preferences ([streakIdKey]); it is an id only.
 */
class SmallStreakWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SIZE_2X1, SIZE_2X2))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Koin is started in Application.onCreate, which runs before any widget code.
        val koin = GlobalContext.get()
        val repository = koin.get<StreakRepository>()
        val today = koin.get<Today>()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val read by repository.streaks.collectAsState(initial = null)
            val date by today.date.collectAsState()
            val streakId = currentState<Preferences>()[streakIdKey]
            SmallWidgetContent(smallWidgetUi(read, streakId, date), appWidgetId)
        }
    }

    companion object {
        val SIZE_2X1 = DpSize(110.dp, 40.dp)
        val SIZE_2X2 = DpSize(110.dp, 110.dp)

        val streakIdKey = stringPreferencesKey("streak_id")
    }
}

class SmallStreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SmallStreakWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightRefresh.sync(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightRefresh.sync(context)
    }
}

internal fun setupIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, WidgetSetupActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

@Composable
internal fun SmallWidgetContent(ui: SmallWidgetUi, appWidgetId: Int) {
    val context = LocalContext.current
    val tapAction: Action = when (ui) {
        SmallWidgetUi.Removed, SmallWidgetUi.NotConfigured -> actionStartActivity(setupIntent(context, appWidgetId))
        else -> actionStartActivity<MainActivity>()
    }
    val size = LocalSize.current
    val large = size.height >= SmallStreakWidget.SIZE_2X2.height
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(WidgetDimens.widgetRadius)
            .background(ImageProvider(R.drawable.wg_bg), colorFilter = ColorFilter.tint(WidgetColors.surfaceContainer))
            .clickable(tapAction),
        contentAlignment = Alignment.Center,
    ) {
        when (ui) {
            SmallWidgetUi.Loading -> Unit
            SmallWidgetUi.Unreadable -> CenteredMessage(context.getString(R.string.widget_open_app), primary = true)
            SmallWidgetUi.NotConfigured -> CenteredMessage(context.getString(R.string.widget_pick_streak))
            SmallWidgetUi.Removed -> CenteredMessage(
                context.getString(if (large) R.string.widget_removed_large else R.string.widget_removed_small),
            )
            is SmallWidgetUi.Shown -> {
                OutlineProgressImage(ui.streak.progress, size, WidgetDimens.widgetRadius)
                if (large) Large(ui.streak) else Small(ui.streak)
                if (ui.streak.goalReached) {
                    Box(GlanceModifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopEnd) { CheckBadge() }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, primary: Boolean = false) {
    Text(
        text = text,
        style = (if (primary) WidgetType.label.copy(color = WidgetColors.primary) else WidgetType.caption)
            .copy(textAlign = TextAlign.Center),
        modifier = GlanceModifier.padding(12.dp),
    )
}

/** 2×1: name over [−  12  +]; Days since shows "18 days" and no buttons. */
@Composable
private fun Small(streak: WidgetStreak) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(context.displayName(streak), style = WidgetType.name, maxLines = 1)
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (streak.hasButtons) {
                MinusButton(streak, named = false)
                Spacer(GlanceModifier.defaultWeight())
                Text(formatCount(streak.value), style = WidgetType.numberSmall, maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                PlusButton(streak, named = false)
            } else {
                Text(formatCount(streak.value), style = WidgetType.numberSmall, maxLines = 1)
                Spacer(GlanceModifier.width(4.dp))
                Text(context.unit(streak), style = WidgetType.caption, maxLines = 1)
            }
        }
    }
}

/** 2×2: name, big number, hint ("18 to go"), then the buttons (Days since: "since 11 Oct"). */
@Composable
private fun Large(streak: WidgetStreak) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
        Text(context.displayName(streak), style = WidgetType.name, maxLines = 1)
        Text(formatCount(streak.value), style = WidgetType.numberLarge, maxLines = 1)
        Text(context.hint(streak), style = WidgetType.caption, maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        if (streak.hasButtons) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MinusButton(streak, named = false)
                Spacer(GlanceModifier.defaultWeight())
                PlusButton(streak, named = false)
            }
        } else if (streak.type == StreakType.DAYS_SINCE && streak.startDate != null) {
            Text(
                context.getString(R.string.widget_since, formatShortDate(streak.startDate, streak.today)),
                style = WidgetType.caption,
                maxLines = 1,
            )
        }
    }
}
