package com.capstone.snapspot.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.compositionLocalOf

/** True when the user turned animations off in system settings ("Remove animations" / animator scale 0). */
val LocalReduceMotion = compositionLocalOf { false }

fun readReduceMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** [transition], or no transition at all when motion is reduced. */
inline fun enterOr(reduce: Boolean, transition: () -> EnterTransition): EnterTransition =
    if (reduce) EnterTransition.None else transition()

inline fun exitOr(reduce: Boolean, transition: () -> ExitTransition): ExitTransition =
    if (reduce) ExitTransition.None else transition()
