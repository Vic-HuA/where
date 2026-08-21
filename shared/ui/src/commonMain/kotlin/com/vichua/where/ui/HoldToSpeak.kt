package com.vichua.where.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 按住开始听、松开结束，对齐语音录入规格，避免点一下后一直停在“正在听”。
 */
fun Modifier.holdToSpeak(
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
): Modifier {
    if (!enabled) {
        return this
    }
    return pointerInput(onPress, onRelease) {
        awaitEachGesture {
            awaitFirstDown()
            onPress()
            waitForUpOrCancellation()
            onRelease()
        }
    }
}
