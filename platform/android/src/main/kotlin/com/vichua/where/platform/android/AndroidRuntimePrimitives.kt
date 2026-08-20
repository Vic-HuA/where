package com.vichua.where.platform.android

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.common.VisibleDateTimeFormatter
import java.util.Calendar
import java.util.UUID

/**
 * 使用 Android/JVM 系统 UUID 生成器创建不含业务信息的随机标识。
 */
class AndroidUniqueIdGenerator : UniqueIdGenerator {
    /**
     * 生成随机 UUID 文本。
     */
    override fun generate(): String = UUID.randomUUID().toString()
}

/**
 * 使用系统 UTC Epoch 毫秒时间的 Android 时钟。
 */
object AndroidEpochMillisecondsClock : EpochMillisecondsClock {
    /**
     * 返回系统当前 UTC Epoch 毫秒值。
     */
    override fun now(): Long = System.currentTimeMillis()
}

/**
 * 使用设备本地时区格式化用户可见的中文日期时间。
 */
object AndroidVisibleDateTimeFormatter : VisibleDateTimeFormatter {
    /**
     * 按设备时区输出朗读和分享共用的短日期时间。
     */
    override fun format(epochMilliseconds: Long): String {
        require(epochMilliseconds >= 0L) { "Visible date time must not be negative." }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = epochMilliseconds
        }
        return buildString {
            append(calendar.get(Calendar.YEAR))
            append('年')
            append(calendar.get(Calendar.MONTH) + 1)
            append('月')
            append(calendar.get(Calendar.DAY_OF_MONTH))
            append('日')
            append(' ')
            append(calendar.get(Calendar.HOUR_OF_DAY))
            append('点')
            append(calendar.get(Calendar.MINUTE))
            append('分')
        }
    }
}
