package com.streakcounter.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.streakcounter.MainActivity
import com.streakcounter.R
import com.streakcounter.core.presentation.format.formatCount
import com.streakcounter.core.presentation.format.formatDate

/** Every streak in app order (spec 4.5), each row with outline, number and −/+ (Days since: number only). */
class StreakListWidget : GlanceAppWidget() {

    // Exact: the row outlines are drawn for the real width.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = widgetDependencies(context)
        val repository = deps.repository
        val today = deps.today
        // The process may have slept through midnight (the day tick doesn't run in deep sleep).
        today.refresh()
        provideContent {
            val read by remember { widgetReads(repository) }.collectAsState(initial = null)
            val date by today.date.collectAsState()
            ListWidgetContent(listWidgetUi(read, date))
        }
    }

    companion object {
        val MIN_WIDTH = 250.dp
        val MIN_HEIGHT = 110.dp
        internal val ROW_HEIGHT = 56.dp
        internal val PADDING = 12.dp
    }
}

class StreakListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakListWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightRefresh.sync(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightRefresh.sync(context)
    }
}

/** Weekday, day, short month: "Thu 27 Nov". */
private const val HEADER_DATE = "EEEdMMM"

@Composable
internal fun ListWidgetContent(ui: ListWidgetUi) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(WidgetDimens.widgetRadius)
            .background(ImageProvider(R.drawable.wg_bg), colorFilter = ColorFilter.tint(WidgetColors.surfaceContainer)),
    ) {
        when (ui) {
            ListWidgetUi.Loading -> Unit
            ListWidgetUi.Unreadable -> Box(
                GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) { OpenAppPill() }
            ListWidgetUi.Empty -> Column(
                GlanceModifier.fillMaxSize().padding(20.dp).clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(context.getString(R.string.widget_list_empty_title), style = WidgetType.emptyTitle)
                Spacer(GlanceModifier.height(12.dp))
                Text(context.getString(R.string.widget_list_empty_body), style = WidgetType.caption)
                Spacer(GlanceModifier.height(12.dp))
                OpenAppPill()
            }
            is ListWidgetUi.Shown -> Shown(ui)
        }
    }
}

@Composable
private fun Shown(ui: ListWidgetUi.Shown) {
    val context = LocalContext.current
    val rowWidth = LocalSize.current.width - StreakListWidget.PADDING * 2
    Column(GlanceModifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(context.getString(R.string.widget_list_title), style = WidgetType.label, maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            Text(formatDate(ui.today, HEADER_DATE), style = WidgetType.caption, maxLines = 1)
        }
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(ui.streaks, itemId = { it.id.toLongs { most, least -> (most xor least) and Long.MAX_VALUE } }) { streak ->
                Column {
                    StreakRow(streak, DpSize(rowWidth, StreakListWidget.ROW_HEIGHT))
                    Spacer(GlanceModifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun StreakRow(streak: WidgetStreak, size: DpSize) {
    val context = LocalContext.current
    Box(
        GlanceModifier
            .fillMaxWidth()
            .height(StreakListWidget.ROW_HEIGHT)
            .background(ImageProvider(R.drawable.wg_row_bg), colorFilter = ColorFilter.tint(WidgetColors.surface))
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        OutlineProgressImage(streak.progress, size, WidgetDimens.rowRadius)
        Row(
            GlanceModifier.fillMaxSize().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(context.displayName(streak), style = WidgetType.rowName, maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            Text(formatCount(streak.value), style = WidgetType.rowNumber, maxLines = 1)
            Spacer(GlanceModifier.width(8.dp))
            if (streak.hasButtons) {
                MinusButton(streak, named = true)
                Spacer(GlanceModifier.width(8.dp))
                PlusButton(streak, named = true)
            } else {
                // Keeps the numbers in one column with the rows that have buttons.
                Spacer(GlanceModifier.width(WidgetDimens.touch * 2 + 8.dp))
            }
        }
    }
}
