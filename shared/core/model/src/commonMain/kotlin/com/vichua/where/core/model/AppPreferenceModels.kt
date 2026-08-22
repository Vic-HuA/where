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
    const val VERSION = "ai-assistance-v3"

    /** 将处理的数据类型。 */
    const val DATA_TYPE = "你为当前这次记录主动选择的照片（最多 6 张）以及你主动提交的文字"

    /** 服务供应商说明。 */
    const val VENDOR = "你选择的 OpenAI 兼容或 Anthropic 兼容视觉接口"

    /** 处理用途。 */
    const val PURPOSE = "根据选中内容建议物品名称、分类和位置描述，经你确认后才写入"
}

/**
 * 本机 AI 接口协议。
 *
 * 只区分请求格式，不绑定某一家官方账号。旧的 CUSTOM 按 OpenAI 兼容处理。
 */
enum class AiProviderVendor {
    OPENAI,
    ANTHROPIC,
    CUSTOM,
    ;

    companion object {
        /** 界面只提供两种兼容协议；旧的自定义按 OpenAI 兼容展示。 */
        fun protocolChoices(): List<AiProviderVendor> = listOf(OPENAI, ANTHROPIC)

        fun displayProtocol(vendor: AiProviderVendor): AiProviderVendor = when (vendor) {
            ANTHROPIC -> ANTHROPIC
            OPENAI,
            CUSTOM,
            -> OPENAI
        }
    }
}

/**
 * 本机 AI 接口凭证。
 *
 * 只存在当前设备，不进入家庭备份、导出或诊断日志。
 *
 * @property vendor 决定请求格式；自定义中转归入 OpenAI 兼容。
 * @property apiKey 用户填写的接口密钥；为空表示尚未配置。
 * @property baseUrl 必填 HTTPS 接口根地址。
 * @property model 必填模型名。
 */
data class AiProviderCredentials(
    val vendor: AiProviderVendor,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
) {
    init {
        require(apiKey.isEmpty() || apiKey.isNotBlank()) {
            "AI API key must be empty or non-blank."
        }
        require(baseUrl.isNotBlank()) { "AI provider base URL must not be blank." }
        require(baseUrl.startsWith("https://")) { "AI provider base URL must use HTTPS." }
        require(' ' !in baseUrl) { "AI provider base URL must not contain spaces." }
        require(model.isNotBlank()) { "AI model must not be blank." }
        require(' ' !in model) { "AI model must not contain spaces." }
    }

    /** Key、地址和模型都齐了才允许真正发起识别。 */
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        const val OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val ANTHROPIC_BASE_URL = "https://api.anthropic.com"
        const val DEFAULT_BASE_URL = OPENAI_BASE_URL
        const val OPENAI_DEFAULT_MODEL = "gpt-4o-mini"
        const val ANTHROPIC_DEFAULT_MODEL = "claude-sonnet-4-0"
        const val DEFAULT_MODEL = OPENAI_DEFAULT_MODEL

        /** 选择协议后带入的常用官方地址和视觉模型，用户仍可改成自己的中转地址。 */
        fun presetsFor(vendor: AiProviderVendor): Pair<String, String> = when (vendor) {
            AiProviderVendor.OPENAI,
            AiProviderVendor.CUSTOM,
            -> OPENAI_BASE_URL to OPENAI_DEFAULT_MODEL
            AiProviderVendor.ANTHROPIC -> ANTHROPIC_BASE_URL to ANTHROPIC_DEFAULT_MODEL
        }

        /**
         * 整理用户输入。地址和模型必须由界面填齐，不再偷偷回落到官方地址。
         */
        fun normalized(
            vendor: AiProviderVendor,
            apiKey: String,
            baseUrl: String,
            model: String,
        ): AiProviderCredentials {
            return AiProviderCredentials(
                vendor = vendor,
                apiKey = apiKey.trim(),
                baseUrl = baseUrl.trim().trimEnd('/'),
                model = model.trim(),
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
