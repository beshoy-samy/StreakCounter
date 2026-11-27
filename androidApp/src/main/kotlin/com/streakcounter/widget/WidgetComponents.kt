package com.streakcounter.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.streakcounter.R
import com.streakcounter.core.presentation.format.formatCount
import com.streakcounter.feature.streaks.domain.StreakType

internal fun Context.displayName(streak: WidgetStreak): String = streak.name ?: getString(
    when (streak.type) {
        StreakType.CHECK_IN -> R.string.widget_unnamed_check_in
        StreakType.DAYS_SINCE -> R.string.widget_unnamed_days_since
        StreakType.TIMES -> R.string.widget_unnamed_times
    },
)

/** "days" / "today" (Times resetting at midnight) / "times", as in the app card. */
internal fun Context.unit(streak: WidgetStreak): String = when {
    streak.type == StreakType.TIMES && streak.autoReset -> getString(R.string.widget_unit_today)
    streak.type == StreakType.TIMES -> resources.getQuantityString(R.plurals.widget_unit_times, streak.value)
    else -> resources.getQuantityString(R.plurals.widget_unit_days, streak.value)
}

/** The 2×2 line under the number: "Goal reached", "18 to go", or the unit without a goal. */
internal fun Context.hint(streak: WidgetStreak): String {
    val goal = streak.goal
    return when {
        goal != null && streak.goalReached -> getString(R.string.widget_goal_reached)
        goal != null -> getString(R.string.widget_to_go, formatCount(goal - streak.value))
        else -> unit(streak)
    }
}

private fun stepAction(streak: WidgetStreak, op: String): Action = actionRunCallback<StreakWidgetAction>(
    actionParametersOf(
        StreakWidgetAction.streakIdKey to streak.id.toString(),
        StreakWidgetAction.opKey to op,
    ),
)

/**
 * Minus (outlined) and plus (filled) circles, 48 dp touch targets. [named] adds the streak name to
 * the spoken labels (list rows). A check-in done today shows a check and does nothing; minus at 0
 * does nothing.
 */
@Composable
internal fun MinusButton(streak: WidgetStreak, named: Boolean) {
    val context = LocalContext.current
    val label = if (named) context.getString(R.string.widget_take_one_from, context.displayName(streak))
    else context.getString(R.string.widget_take_one)
    CircleButton(
        background = R.drawable.wg_circle_outline,
        backgroundColor = WidgetColors.outline,
        icon = R.drawable.wg_ic_remove,
        iconColor = if (streak.minusEnabled) WidgetColors.onSurface else WidgetColors.outline,
        label = label,
        action = if (streak.minusEnabled) stepAction(streak, StreakWidgetAction.OP_DEC) else null,
    )
}

@Composable
internal fun PlusButton(streak: WidgetStreak, named: Boolean) {
    val context = LocalContext.current
    val name = context.displayName(streak)
    if (streak.plusDone) {
        CircleButton(
            background = R.drawable.wg_circle,
            backgroundColor = WidgetColors.primaryContainer,
            icon = R.drawable.wg_ic_check,
            iconColor = WidgetColors.onPrimaryContainer,
            label = if (named) context.getString(R.string.widget_checked_in_named, name)
            else context.getString(R.string.widget_checked_in),
            action = null,
        )
    } else {
        CircleButton(
            background = R.drawable.wg_circle,
            backgroundColor = WidgetColors.primary,
            icon = R.drawable.wg_ic_add,
            iconColor = WidgetColors.onPrimary,
            label = if (named) context.getString(R.string.widget_add_one_to, name)
            else context.getString(R.string.widget_add_one),
            action = stepAction(streak, StreakWidgetAction.OP_INC),
        )
    }
}

@Composable
private fun CircleButton(
    @DrawableRes background: Int,
    backgroundColor: ColorProvider,
    @DrawableRes icon: Int,
    iconColor: ColorProvider,
    label: String,
    action: Action?,
) {
    var modifier = GlanceModifier
        .size(WidgetDimens.touch)
        .background(ImageProvider(background), colorFilter = ColorFilter.tint(backgroundColor))
        .semantics { contentDescription = label }
    if (action != null) modifier = modifier.clickable(action)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconColor),
            modifier = GlanceModifier.size(WidgetDimens.icon),
        )
    }
}

/** Goal reached: a small primary circle with a check, top-end. */
@Composable
internal fun CheckBadge() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .size(WidgetDimens.badge)
            .background(ImageProvider(R.drawable.wg_circle), colorFilter = ColorFilter.tint(WidgetColors.primary))
            .semantics { contentDescription = context.getString(R.string.widget_goal_reached) },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.wg_ic_check),
            contentDescription = null,
            colorFilter = ColorFilter.tint(WidgetColors.onPrimary),
            modifier = GlanceModifier.size(16.dp),
        )
    }
}

/** "Open StreakCounter" as a filled pill (the design's button). The caller sets the click. */
@Composable
internal fun OpenAppPill(modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .background(ImageProvider(R.drawable.wg_pill), colorFilter = ColorFilter.tint(WidgetColors.primary))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(R.string.widget_open_app),
            style = WidgetType.label.copy(color = WidgetColors.onPrimary),
            maxLines = 1,
        )
    }
}
