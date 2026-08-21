package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 仅保存在当前设备上的应用功能开关。
 *
 * 不写入变更记录，也不进入家庭导出或备份。
 *
 * @property deviceId 当前设备 ID。
 * @property aiAssistanceEnabled AI 辅助是否开启。
 * @property cloudSpeechEnabled 云端语音识别是否开启。
 * @property aiDisclosureVersionAccepted 最近确认的 AI 披露版本；未确认时为空。
 * @property cloudSpeechDisclosureVersionAccepted 最近确认的云端语音披露版本；未确认时为空。
 * @property backupReminderEnabled 是否在尚未成功备份时于首页提醒。
 * @property diagnosticLoggingEnabled 是否写入不含敏感内容的本机诊断事件。
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
     * 只有开关打开且披露版本仍是当前版本时，才允许把这次主动选中的照片或文字交给 AI。
     */
    val canUseAiAssistance: Boolean
        get() = aiAssistanceEnabled &&
            aiDisclosureVersionAccepted == AiAssistanceDisclosure.VERSION

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
 * AI 辅助披露的冻结文案和版本。
 *
 * 版本变化后必须重新确认。未填写本机 Key 时不会上传。
 */
object AiAssistanceDisclosure {
    /** 当前披露版本。 */
    const val VERSION = "ai-assistance-v2"

    /** 将处理的数据类型。 */
    const val DATA_TYPE = "你为当前这次记录主动选择的照片（最多 6 张）以及你主动提交的文字"

    /** 服务供应商说明。 */
    const val VENDOR = "你填写的 OpenAI 兼容视觉接口"

    /** 处理用途。 */
    const val PURPOSE = "根据选中内容建议物品名称、分类和位置描述，经你确认后才写入"
}

/**
 * 本机 AI 接口凭证。
 *
 * 只存在当前设备，不进入家庭备份、导出或诊断日志。
 *
 * @property apiKey 用户填写的接口密钥；为空表示尚未配置。
 * @property baseUrl HTTPS 接口根地址，默认官方 OpenAI。
 */
data class AiProviderCredentials(
    val apiKey: String,
    val baseUrl: String,
) {
    init {
        require(apiKey.isEmpty() || apiKey.isNotBlank()) {
            "AI API key must be empty or non-blank."
        }
        require(baseUrl.isNotBlank()) { "AI provider base URL must not be blank." }
        require(baseUrl.startsWith("https://")) { "AI provider base URL must use HTTPS." }
        require(' ' !in baseUrl) { "AI provider base URL must not contain spaces." }
    }

    /** 有 Key 时才允许真正发起识别。 */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    companion object {
        /** 未填写时使用的官方兼容根地址。 */
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"

        /** 默认视觉模型；兼容接口通常接受同一名称。 */
        const val DEFAULT_MODEL = "gpt-4o-mini"

        /**
         * 整理用户输入。地址留空时回落到默认官方根地址。
         */
        fun normalized(
            apiKey: String,
            baseUrl: String,
        ): AiProviderCredentials {
            val trimmedUrl = baseUrl.trim().trimEnd('/')
            return AiProviderCredentials(
                apiKey = apiKey.trim(),
                baseUrl = trimmedUrl.ifEmpty { DEFAULT_BASE_URL },
            )
        }
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
