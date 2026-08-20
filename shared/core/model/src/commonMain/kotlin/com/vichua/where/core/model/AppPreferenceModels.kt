package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 仅保存在当前设备上的应用功能开关。
 *
 * 不写入变更记录，也不进入家庭导出或备份。
 *
 * @property deviceId 当前设备 ID。
 * @property aiAssistanceEnabled AI 辅助是否开启；本步只持久化，不发起请求。
 * @property cloudSpeechEnabled 云端语音识别是否开启。
 * @property aiDisclosureVersionAccepted 最近确认的 AI 披露版本；未确认时为空。
 * @property cloudSpeechDisclosureVersionAccepted 最近确认的云端语音披露版本；未确认时为空。
 * @property backupReminderEnabled 是否启用备份提醒；本步只持久化。
 * @property diagnosticLoggingEnabled 是否启用不含敏感内容的诊断日志；本步只持久化。
 * @property updatedAt 最近更新时间。
 */
@Serializable
data class LocalAppPreferences(
    val deviceId: DeviceId,
    val aiAssistanceEnabled: Boolean,
    val cloudSpeechEnabled: Boolean,
    val aiDisclosureVersionAccepted: String?,
    val cloudSpeechDisclosureVersionAccepted: String?,
    val backupReminderEnabled: Boolean,
    val diagnosticLoggingEnabled: Boolean,
    val updatedAt: UtcTimestamp,
) {
    init {
        require(aiDisclosureVersionAccepted == null || aiDisclosureVersionAccepted.isNotBlank()) {
            "AI disclosure version must be null or non-blank."
        }
        require(
            cloudSpeechDisclosureVersionAccepted == null ||
                cloudSpeechDisclosureVersionAccepted.isNotBlank(),
        ) {
            "Cloud speech disclosure version must be null or non-blank."
        }
    }

    /**
     * 只有开关打开且披露版本仍是当前版本时，才允许把这次主动录音交给系统识别服务。
     */
    val canUseCloudSpeech: Boolean
        get() = cloudSpeechEnabled &&
            cloudSpeechDisclosureVersionAccepted == CloudSpeechDisclosure.VERSION

    companion object {
        /**
         * 为当前设备创建全部关闭的默认开关。
         */
        fun disabledDefault(
            deviceId: DeviceId,
            updatedAt: UtcTimestamp,
        ): LocalAppPreferences = LocalAppPreferences(
            deviceId = deviceId,
            aiAssistanceEnabled = false,
            cloudSpeechEnabled = false,
            aiDisclosureVersionAccepted = null,
            cloudSpeechDisclosureVersionAccepted = null,
            backupReminderEnabled = false,
            diagnosticLoggingEnabled = false,
            updatedAt = updatedAt,
        )
    }
}

/**
 * 云端语音识别披露的冻结文案和版本。
 *
 * 版本变化后必须重新确认，不能沿用旧同意继续上传。
 */
object CloudSpeechDisclosure {
    /** 当前披露版本。 */
    const val VERSION = "cloud-speech-v1"

    /** 将上传的数据类型。 */
    const val DATA_TYPE = "你这次主动说出的语音"

    /** 服务供应商说明。 */
    const val VENDOR = "系统语音识别（设备厂商或 Google）"

    /** 处理用途。 */
    const val PURPOSE = "把说话转成文字，再交给本地查找或填写物品名称"
}
