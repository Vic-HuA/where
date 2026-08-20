package com.vichua.where.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 当前设备适老与辅助偏好的 Room 持久化结构。
 *
 * 按设备隔离，避免把本机字号或对比度写进家庭数据包。
 *
 * @property deviceId 当前设备 ID，同时作为主键。
 * @property displayMode 显示模式枚举名。
 * @property followSystemFontScale 是否跟随系统字体缩放。
 * @property highContrastEnabled 是否启用应用内高对比度。
 * @property autoReadConfirmationEnabled 是否自动朗读确认内容。
 * @property hapticFeedbackEnabled 是否启用主要操作触觉反馈。
 * @property speechRate 本地文字朗读速度。
 * @property volumeHintEnabled 朗读前是否显示音量提示。
 * @property updatedAt 最近更新时间。
 */
@Entity(
    tableName = "local_accessibility_preferences",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
)
data class LocalAccessibilityPreferencesEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "display_mode")
    val displayMode: String,
    @ColumnInfo(name = "follow_system_font_scale")
    val followSystemFontScale: Boolean,
    @ColumnInfo(name = "high_contrast_enabled")
    val highContrastEnabled: Boolean,
    @ColumnInfo(name = "auto_read_confirmation_enabled")
    val autoReadConfirmationEnabled: Boolean,
    @ColumnInfo(name = "haptic_feedback_enabled")
    val hapticFeedbackEnabled: Boolean,
    @ColumnInfo(name = "speech_rate")
    val speechRate: Float,
    @ColumnInfo(name = "volume_hint_enabled")
    val volumeHintEnabled: Boolean,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
