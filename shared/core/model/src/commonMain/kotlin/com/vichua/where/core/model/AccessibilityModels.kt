package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 当前设备的信息密度模式。
 *
 * 只影响展示和引导，不改变家庭数据、搜索结果或权限边界。
 */
@Serializable
enum class DisplayMode {
    /** 普通信息密度，保留搜索框和完整次要操作。 */
    STANDARD,

    /** 适老展示，首页收敛主入口并把次要信息折叠。 */
    ELDER_FRIENDLY,
}

/**
 * 仅保存在当前设备上的适老与辅助偏好。
 *
 * 不写入变更记录，也不进入家庭导出或备份；恢复家庭数据后继续使用目标设备当前设置。
 *
 * @property deviceId 当前设备 ID，同时作为主键。
 * @property displayMode 普通或适老展示。
 * @property followSystemFontScale 是否跟随系统字体缩放。
 * @property highContrastEnabled 是否启用应用内高对比度增强。
 * @property autoReadConfirmationEnabled 是否自动朗读确认内容；本步只持久化，不触发行为。
 * @property hapticFeedbackEnabled 是否为主要操作提供触觉反馈；本步只持久化。
 * @property speechRate 本地文字朗读速度。
 * @property volumeHintEnabled 朗读前是否显示音量提示；本步只持久化。
 * @property updatedAt 最近更新时间。
 */
@Serializable
data class LocalAccessibilityPreferences(
    val deviceId: DeviceId,
    val displayMode: DisplayMode,
    val followSystemFontScale: Boolean,
    val highContrastEnabled: Boolean,
    val autoReadConfirmationEnabled: Boolean,
    val hapticFeedbackEnabled: Boolean,
    val speechRate: Float,
    val volumeHintEnabled: Boolean,
    val updatedAt: UtcTimestamp,
) {
    init {
        require(speechRate in MIN_SPEECH_RATE..MAX_SPEECH_RATE) {
            "Speech rate must stay within the supported range."
        }
    }

    /** 当前是否使用适老展示。 */
    val elderFriendly: Boolean
        get() = displayMode == DisplayMode.ELDER_FRIENDLY

    companion object {
        /** 平台可接受的最低朗读速度。 */
        const val MIN_SPEECH_RATE = 0.5f

        /** 平台可接受的最高朗读速度。 */
        const val MAX_SPEECH_RATE = 2.0f

        /** 未调整过的默认朗读速度。 */
        const val DEFAULT_SPEECH_RATE = 1.0f

        /**
         * 为当前设备创建默认普通模式偏好。
         *
         * 现有用户升级后首次读取时使用，避免把适老模式强加给已经在用的家庭。
         */
        fun standardDefault(
            deviceId: DeviceId,
            updatedAt: UtcTimestamp,
        ): LocalAccessibilityPreferences = LocalAccessibilityPreferences(
            deviceId = deviceId,
            displayMode = DisplayMode.STANDARD,
            followSystemFontScale = true,
            highContrastEnabled = false,
            autoReadConfirmationEnabled = false,
            hapticFeedbackEnabled = true,
            speechRate = DEFAULT_SPEECH_RATE,
            volumeHintEnabled = false,
            updatedAt = updatedAt,
        )
    }
}
