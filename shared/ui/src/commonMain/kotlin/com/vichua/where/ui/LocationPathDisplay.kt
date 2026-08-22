package com.vichua.where.ui

import com.vichua.where.core.model.LocationType
import kotlin.math.roundToInt

/**
 * 把存储路径里的间隔号换成原型里的层级符号，避免被看成“大于”。
 *
 * 索引和分享原文仍使用原来的间隔号，这里只改界面展示。
 */
fun visibleLocationPath(path: String): String =
    path.replace(STORED_LOCATION_PATH_SEPARATOR, VISIBLE_LOCATION_PATH_SEPARATOR)

/**
 * 把数据包大小收成用户能扫一眼的单位，避免只看到原始字节。
 */
fun visiblePackageSize(bytes: Long): String {
    require(bytes >= 0L) { "Visible package size must not be negative." }
    return when {
        bytes >= 1_000_000L -> {
            val tenths = (bytes / 100_000L)
            val whole = tenths / 10L
            val fraction = tenths % 10L
            if (fraction == 0L) "$whole MB" else "$whole.$fraction MB"
        }
        bytes >= 1_000L -> "${(bytes / 1000.0).roundToInt()} KB"
        bytes == 0L -> "空文件"
        else -> "不足 1 KB"
    }
}

/**
 * 位置类型的中文标签，供录入、更新位置和位置管理共用。
 */
fun locationTypeLabel(type: LocationType): String = when (type) {
    LocationType.HOME -> "家庭"
    LocationType.ROOM -> "房间"
    LocationType.AREA -> "区域"
    LocationType.FURNITURE -> "家具"
    LocationType.CONTAINER -> "容器"
    LocationType.SLOT -> "具体位置"
}

private const val STORED_LOCATION_PATH_SEPARATOR = " · "
private const val VISIBLE_LOCATION_PATH_SEPARATOR = " › "
