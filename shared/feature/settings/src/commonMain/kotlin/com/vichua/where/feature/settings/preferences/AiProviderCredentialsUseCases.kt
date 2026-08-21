package com.vichua.where.feature.settings.preferences

import com.vichua.where.core.model.AiProviderCredentials
import com.vichua.where.core.model.AiProviderVendor

/**
 * 本机 AI 接口凭证仓储。
 *
 * 不得把 Key 写入家庭数据库、备份或诊断日志。
 */
interface AiProviderCredentialsStore {
    /**
     * 读取当前设备凭证；未保存过时返回空 Key 和默认地址。
     */
    fun load(): AiProviderCredentials

    /**
     * 覆盖保存当前设备凭证。
     */
    fun save(credentials: AiProviderCredentials)
}

/**
 * 读取本机 AI 接口凭证。
 */
class LoadAiProviderCredentialsUseCase(
    private val store: AiProviderCredentialsStore,
) {
    /**
     * 返回当前设备凭证。
     */
    operator fun invoke(): AiProviderCredentials = store.load()
}

/**
 * 保存用户填写的 AI 接口凭证。
 */
class UpdateAiProviderCredentialsUseCase(
    private val store: AiProviderCredentialsStore,
) {
    /**
     * 整理后写入本机。Key 可留空，表示先关掉实际上传。
     */
    operator fun invoke(
        vendor: AiProviderVendor,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): AiProviderCredentials {
        val credentials = AiProviderCredentials.normalized(
            vendor = vendor,
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
        )
        store.save(credentials)
        return credentials
    }
}
