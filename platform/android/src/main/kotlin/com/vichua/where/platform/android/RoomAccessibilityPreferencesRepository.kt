package com.vichua.where.platform.android

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.database.query.AccessibilityPreferencesStore
import com.vichua.where.core.model.LocalAccessibilityPreferences
import com.vichua.where.feature.settings.accessibility.AccessibilityPreferencesRepository

/**
 * 使用共享 Room 查询 Store 实现 Android 设备辅助偏好仓储。
 */
class RoomAccessibilityPreferencesRepository(
    private val store: AccessibilityPreferencesStore,
    private val clock: EpochMillisecondsClock,
) : AccessibilityPreferencesRepository {
    /**
     * 读取当前设备偏好，没有行时按当前时间写入默认普通模式。
     */
    override suspend fun load(): LocalAccessibilityPreferences =
        store.loadOrCreate(clock.now())

    /**
     * 覆盖保存当前设备偏好。
     */
    override suspend fun save(preferences: LocalAccessibilityPreferences) {
        store.save(preferences)
    }
}
