package com.vichua.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 按绝对路径展示私有目录中的本地图片，文件缺失时回退占位内容。
 *
 * 使用 expect/actual 是为了让共享页面只依赖路径，不直接解码平台 Bitmap。
 */
@Composable
expect fun LocalStorageImage(
    absolutePath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
)
