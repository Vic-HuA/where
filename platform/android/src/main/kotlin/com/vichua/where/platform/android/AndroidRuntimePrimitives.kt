package com.vichua.where.platform.android

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
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
