package org.solsticesw.vivlia.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.compositionLocalOf

data class VivliaMotionScheme(
    val expressiveMotionEnabled: Boolean = true,
    val reducedMotionEnabled: Boolean = false
) {
    fun <T> springSpec(
        dampingRatio: Float = Spring.DampingRatioLowBouncy,
        stiffness: Float = Spring.StiffnessMediumLow
    ): SpringSpec<T> {
        return if (reducedMotionEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            )
        } else {
            spring(
                dampingRatio = dampingRatio,
                stiffness = stiffness
            )
        }
    }
}

val LocalVivliaMotion = compositionLocalOf { VivliaMotionScheme() }
