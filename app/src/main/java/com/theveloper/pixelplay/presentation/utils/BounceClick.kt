package com.theveloper.pixelplay.presentation.utils

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch

/**
 * A custom modifier that adds a "bounce" effect when the element is clicked.
 * The element scales down on press and scales back up on release/cancel.
 * Also provides haptic feedback for a more tactile feel.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.95f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    if (!enabled) return@composed this
    
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val hapticsConfig = LocalAppHapticsConfig.current
    val view = LocalView.current

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enabled) {
            detectTapGestures(
                onPress = {
                    scope.launch {
                        scale.animateTo(scaleDown)
                    }
                    if (hapticsConfig.enabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                    }
                    try {
                        awaitRelease()
                    } finally {
                        scope.launch {
                            scale.animateTo(1f)
                        }
                    }
                },
                onTap = {
                    onClick?.invoke()
                }
            )
        }
}

/**
 * Similar to [bounceClick] but integrates with [MutableInteractionSource] 
 * for better compatibility with existing components.
 */
fun Modifier.bounceClickable(
    interactionSource: MutableInteractionSource,
    indication: androidx.compose.foundation.Indication?,
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this
    
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val hapticsConfig = LocalAppHapticsConfig.current
    val view = LocalView.current

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enabled) {
            detectTapGestures(
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    scope.launch {
                        interactionSource.emit(press)
                        scale.animateTo(scaleDown)
                    }
                    if (hapticsConfig.enabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                    }
                    try {
                        val released = tryAwaitRelease()
                        if (released) {
                            interactionSource.emit(PressInteraction.Release(press))
                        } else {
                            interactionSource.emit(PressInteraction.Cancel(press))
                        }
                    } finally {
                        scope.launch {
                            scale.animateTo(1f)
                        }
                    }
                },
                onTap = {
                    onClick()
                }
            )
        }
        .indication(interactionSource, indication)
}

/**
 * A custom modifier that adds a "bounce" effect when the element is clicked or long-pressed.
 * Integrates with [MutableInteractionSource] and supports long-click.
 */
fun Modifier.bounceCombinedClickable(
    interactionSource: MutableInteractionSource,
    indication: androidx.compose.foundation.Indication?,
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this
    
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val hapticsConfig = LocalAppHapticsConfig.current
    val view = LocalView.current

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enabled, onLongClick, onClick) {
            detectTapGestures(
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    scope.launch {
                        interactionSource.emit(press)
                        scale.animateTo(scaleDown)
                    }
                    if (hapticsConfig.enabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
                    }
                    try {
                        val released = tryAwaitRelease()
                        if (released) {
                            interactionSource.emit(PressInteraction.Release(press))
                        } else {
                            interactionSource.emit(PressInteraction.Cancel(press))
                        }
                    } finally {
                        scope.launch {
                            scale.animateTo(1f)
                        }
                    }
                },
                onLongPress = {
                    if (onLongClick != null) {
                        if (hapticsConfig.enabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        onLongClick()
                    }
                },
                onTap = {
                    onClick()
                }
            )
        }
        .indication(interactionSource, indication)
}
