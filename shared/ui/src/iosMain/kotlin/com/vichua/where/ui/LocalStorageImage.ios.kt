package com.vichua.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * iOS 目标暂未接入本地文件解码，统一回退占位，避免共享页面为空实现。
 */
@Composable
actual fun LocalStorageImage(
    absolutePath: String?,
    contentDescription: String?,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
) {
    fallback()
}
