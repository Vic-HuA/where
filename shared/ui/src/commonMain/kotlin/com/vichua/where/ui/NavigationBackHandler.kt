package com.vichua.where.ui

import androidx.compose.runtime.Composable

/**
 * 拦截系统返回，交给当前页面的上一层导航，而不是直接结束界面。
 *
 * @param enabled 为 false 时把返回交给系统，例如首页可以回到桌面。
 * @param onBack 当前页应执行的返回动作。
 */
@Composable
expect fun NavigationBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
