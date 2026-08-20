package com.vichua.where.platform.android

import android.content.Context
import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.LocalAppPreferences
import com.vichua.where.core.model.UtcTimestamp
import com.vichua.where.feature.settings.preferences.AppPreferencesRepository

/**
 * 用设备私有键值存储保存应用开关，避免把开关写进家庭数据库。
 */
class AndroidAppPreferencesRepository(
    context: Context,
    private val database: WhereDatabase,
    private val clock: EpochMillisecondsClock,
) : AppPreferencesRepository {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    /**
     * 读取当前设备开关；没有键时按全部关闭写入。
     */
    override suspend fun load(): LocalAppPreferences {
        val deviceId = currentDeviceId()
        val storedDeviceId = preferences.getString(KEY_DEVICE_ID, null)
        if (storedDeviceId != deviceId.value || !preferences.contains(KEY_CLOUD_SPEECH_ENABLED)) {
            val created = LocalAppPreferences.disabledDefault(
                deviceId = deviceId,
                updatedAt = UtcTimestamp(clock.now()),
            )
            write(created)
            return created
        }
        return LocalAppPreferences(
            deviceId = deviceId,
            aiAssistanceEnabled = preferences.getBoolean(KEY_AI_ENABLED, false),
            cloudSpeechEnabled = preferences.getBoolean(KEY_CLOUD_SPEECH_ENABLED, false),
            aiDisclosureVersionAccepted = preferences.getString(KEY_AI_DISCLOSURE, null),
            cloudSpeechDisclosureVersionAccepted = preferences.getString(
                KEY_CLOUD_SPEECH_DISCLOSURE,
                null,
            ),
            backupReminderEnabled = preferences.getBoolean(KEY_BACKUP_REMINDER, false),
            diagnosticLoggingEnabled = preferences.getBoolean(KEY_DIAGNOSTIC_LOGGING, false),
            updatedAt = UtcTimestamp(preferences.getLong(KEY_UPDATED_AT, clock.now())),
        )
    }

    /**
     * 覆盖写入当前设备开关。
     */
    override suspend fun save(preferences: LocalAppPreferences) {
        require(preferences.deviceId == currentDeviceId()) {
            "App preferences must belong to the current active device."
        }
        write(preferences)
    }

    private fun write(value: LocalAppPreferences) {
        preferences.edit()
            .putString(KEY_DEVICE_ID, value.deviceId.value)
            .putBoolean(KEY_AI_ENABLED, value.aiAssistanceEnabled)
            .putBoolean(KEY_CLOUD_SPEECH_ENABLED, value.cloudSpeechEnabled)
            .putString(KEY_AI_DISCLOSURE, value.aiDisclosureVersionAccepted)
            .putString(KEY_CLOUD_SPEECH_DISCLOSURE, value.cloudSpeechDisclosureVersionAccepted)
            .putBoolean(KEY_BACKUP_REMINDER, value.backupReminderEnabled)
            .putBoolean(KEY_DIAGNOSTIC_LOGGING, value.diagnosticLoggingEnabled)
            .putLong(KEY_UPDATED_AT, value.updatedAt.epochMilliseconds)
            .apply()
    }

    private suspend fun currentDeviceId(): DeviceId {
        val household = database.householdDao().findFirstActive()
            ?: error("Active household is required before loading app preferences.")
        val device = database.deviceDao().findFirstActiveByHousehold(household.id)
            ?: error("Active device is required before loading app preferences.")
        return DeviceId(device.id)
    }

    private companion object {
        const val PREFERENCES_NAME = "where_app_preferences"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_AI_ENABLED = "ai_assistance_enabled"
        const val KEY_CLOUD_SPEECH_ENABLED = "cloud_speech_enabled"
        const val KEY_AI_DISCLOSURE = "ai_disclosure_version"
        const val KEY_CLOUD_SPEECH_DISCLOSURE = "cloud_speech_disclosure_version"
        const val KEY_BACKUP_REMINDER = "backup_reminder_enabled"
        const val KEY_DIAGNOSTIC_LOGGING = "diagnostic_logging_enabled"
        const val KEY_UPDATED_AT = "updated_at"
    }
}
