package com.vichua.where.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

/**
 * 按住开始听、松开结束，对齐语音录入规格，避免点一下后一直停在“正在听”。
 *
 * @param holdDelayMillis 按下后需持续这么久才开始听，用来过滤点一下和上滑。
 */
fun Modifier.holdToSpeak(
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    holdDelayMillis: Long = DEFAULT_HOLD_TO_SPEAK_DELAY_MILLIS,
): Modifier {
    if (!enabled) {
        return this
    }
    return pointerInput(onPress, onRelease, holdDelayMillis) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val slop = viewConfiguration.touchSlop
            var travel = Offset.Zero
            // 首页贴底栏时，上滑会超过按住延时；位移超过触控阈值就当滑动，不当按住。
            if (holdDelayMillis > 0L) {
                val cancelledBeforeHold = withTimeoutOrNull(holdDelayMillis) {
                    pointerFinishedOrSwiped(
                        pointerId = down.id,
                        slop = slop,
                        travel = { travel },
                        onTravel = { delta -> travel = delta },
                    )
                } != null
                if (cancelledBeforeHold) {
                    return@awaitEachGesture
                }
            }
            onPress()
            pointerFinishedOrSwiped(
                pointerId = down.id,
                slop = slop,
                travel = { travel },
                onTravel = { delta -> travel = delta },
            )
            onRelease()
        }
    }
}

/**
 * 等到手指抬起，或累计位移超过滑动阈值。必须挂在 AwaitPointerEventScope 上，
 * 否则手势协程不允许调用 awaitPointerEvent。
 */
private suspend fun AwaitPointerEventScope.pointerFinishedOrSwiped(
    pointerId: PointerId,
    slop: Float,
    travel: () -> Offset,
    onTravel: (Offset) -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { pointer -> pointer.id == pointerId }
            ?: return
        val nextTravel = travel() + change.positionChange()
        onTravel(nextTravel)
        if (change.changedToUpIgnoreConsumed() ||
            hypot(nextTravel.x, nextTravel.y) > slop
        ) {
            return
        }
    }
}

private const val DEFAULT_HOLD_TO_SPEAK_DELAY_MILLIS = 300L
