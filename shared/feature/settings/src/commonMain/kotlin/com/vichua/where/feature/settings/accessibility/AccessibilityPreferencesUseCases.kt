package com.vichua.where.feature.settings.accessibility

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.model.DisplayMode
import com.vichua.where.core.model.LocalAccessibilityPreferences
import com.vichua.where.core.model.UtcTimestamp

/**
 * 当前设备适老与辅助偏好仓储契约。
 */
interface AccessibilityPreferencesRepository {
    /**
     * 读取当前设备偏好；没有记录时创建普通模式默认行。
     */
    suspend fun load(): LocalAccessibilityPreferences

    /**
     * 覆盖保存当前设备偏好，不写入变更记录。
     */
    suspend fun save(preferences: LocalAccessibilityPreferences)
}

/**
 * 读取当前设备适老与辅助偏好。
 */
class LoadAccessibilityPreferencesUseCase(
    private val repository: AccessibilityPreferencesRepository,
) {
    /**
     * 返回当前设备偏好。
     */
    suspend operator fun invoke(): LocalAccessibilityPreferences = repository.load()
}

/**
 * 更新当前设备适老与辅助偏好。
 *
 * 启用适老时默认打开高对比度；关闭适老时保留用户已单独调整的高对比度。
 */
class UpdateAccessibilityPreferencesUseCase(
    private val repository: AccessibilityPreferencesRepository,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 切换适老展示。启用时同步默认打开高对比度。
     */
    suspend fun setElderFriendly(enabled: Boolean): LocalAccessibilityPreferences {
        val current = repository.load()
        val updated = current.copy(
            displayMode = if (enabled) {
                DisplayMode.ELDER_FRIENDLY
            } else {
                DisplayMode.STANDARD
            },
            highContrastEnabled = if (enabled) {
                true
            } else {
                current.highContrastEnabled
            },
            updatedAt = UtcTimestamp(clock.now()),
        )
        repository.save(updated)
        return updated
    }

    /**
     * 单独调整高对比度，不改变当前显示模式。
     */
    suspend fun setHighContrast(enabled: Boolean): LocalAccessibilityPreferences {
        val current = repository.load()
        val updated = current.copy(
            highContrastEnabled = enabled,
            updatedAt = UtcTimestamp(clock.now()),
        )
        repository.save(updated)
        return updated
    }

    /**
     * 单独调整确认后是否自动朗读，不改变适老或高对比度。
     *
     * 关闭后详情页和搜索结果里用户主动触发的朗读仍然可用。
     */
    suspend fun setAutoReadConfirmation(enabled: Boolean): LocalAccessibilityPreferences {
        val current = repository.load()
        val updated = current.copy(
            autoReadConfirmationEnabled = enabled,
            updatedAt = UtcTimestamp(clock.now()),
        )
        repository.save(updated)
        return updated
    }

    /**
     * 单独调整触觉反馈，不改变适老、高对比度或自动朗读。
     *
     * 关闭后主要按钮和危险确认都不再震动；设备不支持震动时仍允许保存该开关。
     */
    suspend fun setHapticFeedback(enabled: Boolean): LocalAccessibilityPreferences {
        val current = repository.load()
        val updated = current.copy(
            hapticFeedbackEnabled = enabled,
            updatedAt = UtcTimestamp(clock.now()),
        )
        repository.save(updated)
        return updated
    }
}
