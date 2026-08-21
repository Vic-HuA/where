package com.vichua.where.ui

import androidx.compose.runtime.Composable

/**
 * iOS 首发前不拦截系统返回；页面左上角返回仍走同一套导航栈。
 */
@Composable
actual fun NavigationBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    if (!enabled) {
        return
    }
}
