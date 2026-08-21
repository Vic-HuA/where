package com.vichua.where.platform.android

import android.content.Context
import com.vichua.where.core.model.AiProviderCredentials
import com.vichua.where.core.model.AiProviderVendor
import com.vichua.where.feature.settings.preferences.AiProviderCredentialsStore

/**
 * 用设备私有键值存储保存 AI 接口凭证，不写入家庭数据库。
 *
 * 应用已关闭系统备份；这里仍单独存放，避免和开关偏好混在一起被误导出。
 */
class AndroidAiProviderCredentialsStore(
    context: Context,
) : AiProviderCredentialsStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    /**
     * 读取本机凭证；旧数据没有供应商或模型时按地址推断并补默认模型。
     */
    override fun load(): AiProviderCredentials {
        val storedUrl = preferences.getString(KEY_BASE_URL, "").orEmpty()
        val vendor = storedVendor(storedUrl)
        val presets = AiProviderCredentials.presetsFor(vendor)
        return AiProviderCredentials.normalized(
            vendor = vendor,
            apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
            baseUrl = storedUrl.ifBlank { presets.first },
            model = preferences.getString(KEY_MODEL, "").orEmpty().ifBlank { presets.second },
        )
    }

    /**
     * 覆盖写入本机凭证。
     */
    override fun save(credentials: AiProviderCredentials) {
        preferences.edit()
            .putString(KEY_VENDOR, credentials.vendor.name)
            .putString(KEY_API_KEY, credentials.apiKey)
            .putString(KEY_BASE_URL, credentials.baseUrl)
            .putString(KEY_MODEL, credentials.model)
            .apply()
    }

    private fun storedVendor(storedUrl: String): AiProviderVendor {
        val stored = preferences.getString(KEY_VENDOR, "").orEmpty()
        val parsed = stored.takeIf(String::isNotBlank)?.let { name ->
            runCatching { AiProviderVendor.valueOf(name) }.getOrNull()
        }
        return parsed ?: when {
            storedUrl.contains("anthropic", ignoreCase = true) -> AiProviderVendor.ANTHROPIC
            storedUrl.isBlank() || storedUrl.contains("openai", ignoreCase = true) ->
                AiProviderVendor.OPENAI
            else -> AiProviderVendor.CUSTOM
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "where_ai_provider_secrets"
        const val KEY_VENDOR = "vendor"
        const val KEY_API_KEY = "api_key"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
    }
}
