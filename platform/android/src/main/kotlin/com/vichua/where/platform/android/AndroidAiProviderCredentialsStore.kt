package com.vichua.where.platform.android

import android.content.Context
import com.vichua.where.core.model.AiProviderCredentials
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
     * 读取本机凭证；没有 Key 时仍返回默认 HTTPS 根地址。
     */
    override fun load(): AiProviderCredentials {
        return AiProviderCredentials.normalized(
            apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
            baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
        )
    }

    /**
     * 覆盖写入本机凭证。
     */
    override fun save(credentials: AiProviderCredentials) {
        preferences.edit()
            .putString(KEY_API_KEY, credentials.apiKey)
            .putString(KEY_BASE_URL, credentials.baseUrl)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "where_ai_provider_secrets"
        const val KEY_API_KEY = "api_key"
        const val KEY_BASE_URL = "base_url"
    }
}
