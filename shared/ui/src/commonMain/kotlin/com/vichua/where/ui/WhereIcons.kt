package com.vichua.where.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.ui.graphics.vector.ImageVector
import com.vichua.where.core.model.LocationType

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

    /** 朗读位置的扬声器图标。 */
    val ReadAloud: ImageVector = Icons.AutoMirrored.Outlined.VolumeUp

    /** 添加照片图标。 */
    val AddPhoto: ImageVector = Icons.Outlined.AddAPhoto

    /** 图片占位图标。 */
    val Image: ImageVector = Icons.Outlined.Image

    /** 添加操作图标。 */
    val Add: ImageVector = Icons.Outlined.Add

    /** 重命名图标。 */
    val Edit: ImageVector = Icons.Outlined.Edit

    /** 删除图标。 */
    val Delete: ImageVector = Icons.Outlined.Delete

    /** 展开子位置图标。 */
    val ExpandMore: ImageVector = Icons.Outlined.ExpandMore

    /** 收起子位置图标。 */
    val ExpandLess: ImageVector = Icons.Outlined.ExpandLess

    /** 返回图标。 */
    val Back: ImageVector = Icons.AutoMirrored.Outlined.ArrowBack

    /**
     * 根据冻结位置图标键返回房间图标。
     */
    fun room(iconKey: String): ImageVector = when (iconKey) {
        "room.living" -> Icons.Outlined.Weekend
        "room.bedroom" -> Icons.Outlined.Bed
        "room.kitchen" -> Icons.Outlined.Restaurant
        "room.bathroom" -> Icons.Outlined.Bathtub
        "room.study" -> Icons.AutoMirrored.Outlined.MenuBook
        "room.storage" -> Icons.Outlined.Inventory2
        else -> Icons.Outlined.LocationOn
    }

    /**
     * 根据位置类型和受控图标键返回位置图标。
     *
     * 未知键回退到类型默认图标，避免位置管理页因缺失照片或旧图标键而空白。
     */
    fun location(
        iconKey: String?,
        type: LocationType,
    ): ImageVector {
        val resolvedIconKey = iconKey.orEmpty()
        if (resolvedIconKey.startsWith("room.")) {
            return room(resolvedIconKey)
        }
        return when (resolvedIconKey) {
            "location.home" -> Home
            "location.area" -> Icons.Outlined.GridView
            "location.furniture" -> Icons.Outlined.Chair
            "location.container" -> Icons.Outlined.Inventory2
            "location.slot" -> Location
            else -> when (type) {
                LocationType.HOME -> Home
                LocationType.ROOM -> Location
                LocationType.AREA -> Icons.Outlined.GridView
                LocationType.FURNITURE -> Icons.Outlined.Chair
                LocationType.CONTAINER -> Icons.Outlined.Inventory2
                LocationType.SLOT -> Location
            }
        }
    }
}
