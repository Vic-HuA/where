package com.vichua.where.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * 在 Android 上按路径解码本地缩略图；解码失败时不抛到界面，改用占位。
 */
@Composable
actual fun LocalStorageImage(
    absolutePath: String?,
    contentDescription: String?,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
) {
    val imageBitmap = remember(absolutePath) {
        absolutePath
            ?.takeIf(String::isNotBlank)
            ?.let { path -> BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    if (imageBitmap == null) {
        fallback()
    } else {
        Image(
            modifier = modifier,
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
        )
    }
}
