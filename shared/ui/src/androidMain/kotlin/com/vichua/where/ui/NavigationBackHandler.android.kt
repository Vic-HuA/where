package com.vichua.where.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Android 上拦截手势返回和系统 Back，避免未处理时直接退出到桌面。
 */
@Composable
actual fun NavigationBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
