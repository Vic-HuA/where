package com.vichua.where.feature.settings.preferences

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.model.AiAssistanceDisclosure
import com.vichua.where.core.model.CloudSpeechDisclosure
import com.vichua.where.core.model.LocalAppPreferences
import com.vichua.where.core.model.UtcTimestamp

/**
 * 当前设备应用开关仓储契约。
 */
interface AppPreferencesRepository {
    /**
     * 读取当前设备开关；没有记录时创建全部关闭的默认值。
     */
    suspend fun load(): LocalAppPreferences

    /**
     * 覆盖保存当前设备开关，不写入变更记录。
     */
    suspend fun save(preferences: LocalAppPreferences)
}

/**
 * 读取当前设备应用开关。
 */
class LoadAppPreferencesUseCase(
    private val repository: AppPreferencesRepository,
) {
    /**
     * 返回当前设备开关。
     */
    suspend operator fun invoke(): LocalAppPreferences = repository.load()
}

/**
 * 更新当前设备应用开关。
 *
 * 开启 AI 或云端语音前必须先确认当前披露版本。
 */
class UpdateAppPreferencesUseCase(
    private val repository: AppPreferencesRepository,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 关闭 AI 辅助，不删除已保存的物品数据。
     */
    suspend fun disableAiAssistance(): LocalAppPreferences {
        val current = repository.load()
        val updated = current.copy(
            aiAssistanceEnabled = false,
            updatedAt = UtcTimestamp(clock.now()),
        )
        repository.save(updated)
        return updated
    }

    /**
     * 在用户确认当前披露后开启 AI 辅助。
     */
    suspend fun enableAiAssistanceAfterDisclosure(): LocalAppPreferences {
        val current = repository.load()
        val updated = current.copy(
            aiAssistanceEnabled = true,
            aiDisclosureVersionAccepted = AiAssistanceDisclosure.VERSION,
            updatedAt = UtcTimestamp(clock.now()),
        )
        repository.save(updated)
        return updated
    }

    /**
     * 关闭云端语音识别，不删除已保存的物品数据。
     */
    suspend fun disableCloudSpeech(): LocalAppPreferences {
        val current = repository.load()
        val updated = current.copy(
            cloudSpeechEnabled = false,
            updatedAt = UtcTimestamp(clock.now()),
        )
        repository.save(updated)
        return updated
    }

    /**
     * 在用户确认当前披露后开启云端语音识别。
     */
    suspend fun enableCloudSpeechAfterDisclosure(): LocalAppPreferences {
        val current = repository.load()
        val updated = current.copy(
            cloudSpeechEnabled = true,
            cloudSpeechDisclosureVersionAccepted = CloudSpeechDisclosure.VERSION,
            updatedAt = UtcTimestamp(clock.now()),
        )
        repository.save(updated)
        return updated
    }
}
