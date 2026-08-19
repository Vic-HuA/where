package com.vichua.where.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 将稳定 `iconKey` 映射到共享 Compose 线性矢量图标。
 *
 * 未知位置键安全回退到位置图标，不加载外部文件或网络资源。
 */
object WhereIcons {
    /** 首页图标。 */
    val Home: ImageVector = Icons.Outlined.Home

    /** 位置图标。 */
    val Location: ImageVector = Icons.Outlined.LocationOn

    /** 设置图标。 */
    val Settings: ImageVector = Icons.Outlined.Settings

    /** 搜索图标。 */
    val Search: ImageVector = Icons.Outlined.Search

    /** 语音图标。 */
    val Microphone: ImageVector = Icons.Outlined.Mic

    /** 切换到文字输入的键盘图标。 */
    val Keyboard: ImageVector = Icons.Outlined.Keyboard

    /** 拍照图标。 */
    val Camera: ImageVector = Icons.Outlined.PhotoCamera

    /** 添加照片图标。 */
    val AddPhoto: ImageVector = Icons.Outlined.AddAPhoto

    /** 图片占位图标。 */
    val Image: ImageVector = Icons.Outlined.Image

    /** 添加操作图标。 */
    val Add: ImageVector = Icons.Outlined.Add

    /** 返回图标。 */
    val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack

    /**
     * 根据冻结位置图标键返回房间图标。
     */
    fun room(iconKey: String): ImageVector = when (iconKey) {
        "room.living" -> Icons.Outlined.Weekend
        "room.bedroom" -> Icons.Outlined.Bed
        "room.kitchen" -> Icons.Outlined.Kitchen
        "room.bathroom" -> Icons.Outlined.Bathtub
        "room.study" -> Icons.AutoMirrored.Outlined.MenuBook
        "room.storage" -> Icons.Outlined.Inventory2
        else -> Icons.Outlined.LocationOn
    }
}
