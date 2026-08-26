package com.vichua.where.feature.location.voice

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.MediaIntegrityStatus
import com.vichua.where.core.model.UtcTimestamp
import com.vichua.where.core.model.VoiceLabelAsset
import com.vichua.where.core.model.VoiceLabelAssetId
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.ImportedAudioFile
import com.vichua.where.core.platform.MediaFilePromotion
import com.vichua.where.core.platform.StorageKeys

/**
 * 编辑语音名称时需要的当前上下文。
 */
data class VoiceLabelContext(
    val householdId: HouseholdId,
    val locationNodeId: LocationNodeId?,
    val itemId: ItemId?,
    val labels: List<VoiceLabelAsset>,
    val currentDeviceId: DeviceId,
)

/**
 * 语音名称管理仓储契约。
 */
interface VoiceLabelRepository {
    /** 按位置加载语音名称。 */
    suspend fun loadForLocation(locationId: LocationNodeId): VoiceLabelContext

    /** 按物品加载语音名称。 */
    suspend fun loadForItem(itemId: ItemId): VoiceLabelContext

    /** 写入新语音名称并停用旧记录。 */
    suspend fun replace(
        newLabel: VoiceLabelAsset,
        retiredLabels: List<VoiceLabelAsset>,
        changeRecords: List<ChangeRecord>,
    )

    /** 软删除当前语音名称。 */
    suspend fun delete(
        updatedLabel: VoiceLabelAsset,
        changeRecord: ChangeRecord,
    )
}

/**
 * 把用户确认保存的短录音写入临时目录。
 */
class ImportVoiceLabelUseCase(
    private val mediaFileStore: ControlledMediaFileStore,
) {
    /**
     * 校验时长后写入临时音频。
     */
    suspend operator fun invoke(imported: ImportedAudioFile): ImportedAudioFile {
        require(imported.durationMillis >= MIN_DURATION_MILLIS) {
            "Voice label is too short."
        }
        return imported
    }

    /**
     * 把录制字节导入临时目录。
     */
    suspend fun import(
        bytes: ByteArray,
        mimeType: String?,
        durationMillis: Long,
    ): ImportedAudioFile {
        require(bytes.isNotEmpty()) { "Imported voice label bytes must not be empty." }
        require(durationMillis >= MIN_DURATION_MILLIS) { "Voice label is too short." }
        return mediaFileStore.importAudio(bytes, mimeType, durationMillis)
    }

    companion object {
        const val MIN_DURATION_MILLIS = 400L
    }
}

/**
 * 为位置或物品保存语音名称；已有未删除记录会被软删除。
 */
class SaveVoiceLabelUseCase(
    private val repository: VoiceLabelRepository,
    private val mediaFileStore: ControlledMediaFileStore,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 保存位置语音名称。
     */
    suspend fun saveForLocation(
        locationId: LocationNodeId,
        imported: ImportedAudioFile,
    ) {
        save(repository.loadForLocation(locationId), imported)
    }

    /**
     * 保存物品语音名称。
     */
    suspend fun saveForItem(
        itemId: ItemId,
        imported: ImportedAudioFile,
    ) {
        save(repository.loadForItem(itemId), imported)
    }

    private suspend fun save(
        context: VoiceLabelContext,
        imported: ImportedAudioFile,
    ) {
        val now = UtcTimestamp(clock.now())
        val labelId = VoiceLabelAssetId(idGenerator.generate())
        val storageKey = if (context.locationNodeId != null) {
            StorageKeys.locationVoice(context.locationNodeId.value, labelId.value)
        } else {
            StorageKeys.itemVoice(requireNotNull(context.itemId).value, labelId.value)
        }
        val newLabel = VoiceLabelAsset(
            id = labelId,
            householdId = context.householdId,
            locationNodeId = context.locationNodeId,
            itemId = context.itemId,
            storageKey = storageKey,
            mimeType = imported.mimeType,
            durationMillis = imported.durationMillis,
            sizeBytes = imported.sizeBytes,
            contentHash = imported.contentHash,
            integrityStatus = MediaIntegrityStatus.AVAILABLE,
            lastIntegrityCheckedAt = now,
            createdAt = now,
            updatedAt = now,
            version = EntityVersion(INITIAL_VERSION),
            sourceDeviceId = context.currentDeviceId,
        )
        val retiredLabels = context.labels
            .filter { label -> label.deletedAt == null }
            .map { label ->
                label.copy(
                    deletedAt = now,
                    updatedAt = now,
                    version = label.version.next(),
                    sourceDeviceId = context.currentDeviceId,
                )
            }
        val changeRecords = buildList {
            retiredLabels.forEach { label ->
                add(voiceLabelChangeRecord(label, ChangeOperation.DELETE, now, idGenerator.generate()))
            }
            add(voiceLabelChangeRecord(newLabel, ChangeOperation.CREATE, now, idGenerator.generate()))
        }
        repository.replace(
            newLabel = newLabel,
            retiredLabels = retiredLabels,
            changeRecords = changeRecords,
        )
        try {
            mediaFileStore.promote(
                listOf(MediaFilePromotion(imported.tempStorageKey, newLabel.storageKey)),
            )
        } catch (_: Exception) {
            // Intentionally keep the voice label record after a failed file promotion.
        }
    }

    private companion object {
        const val INITIAL_VERSION = 1L
    }
}

/**
 * 删除位置或物品当前语音名称。
 */
class DeleteVoiceLabelUseCase(
    private val repository: VoiceLabelRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /** 删除位置当前语音名称。 */
    suspend fun deleteForLocation(locationId: LocationNodeId) {
        delete(repository.loadForLocation(locationId))
    }

    /** 删除物品当前语音名称。 */
    suspend fun deleteForItem(itemId: ItemId) {
        delete(repository.loadForItem(itemId))
    }

    private suspend fun delete(context: VoiceLabelContext) {
        val activeLabel = context.labels.singleOrNull { label -> label.deletedAt == null }
        require(activeLabel != null) { "Entity does not have an active voice label." }
        val now = UtcTimestamp(clock.now())
        val updatedLabel = activeLabel.copy(
            deletedAt = now,
            updatedAt = now,
            version = activeLabel.version.next(),
            sourceDeviceId = context.currentDeviceId,
        )
        repository.delete(
            updatedLabel = updatedLabel,
            changeRecord = voiceLabelChangeRecord(
                label = updatedLabel,
                operation = ChangeOperation.DELETE,
                occurredAt = now,
                recordId = idGenerator.generate(),
            ),
        )
    }
}

private fun voiceLabelChangeRecord(
    label: VoiceLabelAsset,
    operation: ChangeOperation,
    occurredAt: UtcTimestamp,
    recordId: String,
): ChangeRecord = ChangeRecord(
    id = ChangeRecordId(recordId),
    householdId = label.householdId,
    entityType = ChangeEntityType.VOICE_LABEL_ASSET,
    entityId = label.id.value,
    operation = operation,
    entityVersion = label.version,
    sourceDeviceId = label.sourceDeviceId,
    occurredAt = occurredAt,
)
