package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.VoiceLabelAsset

/**
 * 数据库层提供的语音名称管理上下文。
 */
data class StoredVoiceLabelContext(
    val householdId: HouseholdId,
    val locationNodeId: LocationNodeId?,
    val itemId: ItemId?,
    val labels: List<VoiceLabelAsset>,
    val currentDeviceId: DeviceId,
)

/**
 * 加载并在同一事务中保存或停用语音名称。
 */
class VoiceLabelStore(
    database: WhereDatabase,
) {
    private val database = database
    private val transactionRunner = DatabaseTransactionRunner(database)

    /**
     * 按位置加载语音名称；位置不存在时失败。
     */
    suspend fun loadForLocation(locationId: LocationNodeId): StoredVoiceLabelContext {
        val location = requireNotNull(
            database.locationNodeDao().findById(locationId.value)?.toDomain(),
        ) {
            "Cannot manage voice labels of a missing location."
        }
        require(location.deletedAt == null) { "Cannot manage voice labels of a deleted location." }
        require(!location.isHouseholdRoot) {
            "Household root cannot have a voice label."
        }
        return storedContext(
            householdId = location.householdId,
            locationNodeId = location.id,
            itemId = null,
            labels = database.voiceLabelAssetDao()
                .findAllByLocation(location.id.value)
                .map { entity -> entity.toDomain() },
        )
    }

    /**
     * 按物品加载语音名称；物品不存在时失败。
     */
    suspend fun loadForItem(itemId: ItemId): StoredVoiceLabelContext {
        val item = requireNotNull(database.itemDao().findActiveById(itemId.value)?.toDomain()) {
            "Cannot manage voice labels of a missing item."
        }
        return storedContext(
            householdId = item.householdId,
            locationNodeId = null,
            itemId = item.id,
            labels = database.voiceLabelAssetDao()
                .findAllByItem(item.id.value)
                .map { entity -> entity.toDomain() },
        )
    }

    /**
     * 写入新语音名称，并软删除同一实体已有未删除记录。
     */
    suspend fun replace(
        newLabel: VoiceLabelAsset,
        retiredLabels: List<VoiceLabelAsset>,
        changeRecords: List<ChangeRecord>,
    ) {
        require(newLabel.deletedAt == null) { "New voice label must not be soft-deleted." }
        retiredLabels.forEach { label ->
            require(label.deletedAt != null) { "Retired voice label must be soft-deleted." }
        }
        changeRecords.forEach { record ->
            require(record.entityType == ChangeEntityType.VOICE_LABEL_ASSET) {
                "Voice label change record entity type must be VOICE_LABEL_ASSET."
            }
        }
        transactionRunner.write {
            retiredLabels.forEach { label ->
                require(voiceLabelAssetDao().update(label.toEntity()) == 1) {
                    "Retired voice label update must affect exactly one row."
                }
            }
            voiceLabelAssetDao().insertAll(listOf(newLabel.toEntity()))
            if (changeRecords.isNotEmpty()) {
                changeRecordDao().insertAll(changeRecords.map(ChangeRecord::toEntity))
            }
        }
    }

    /**
     * 软删除当前语音名称。
     */
    suspend fun delete(
        updatedLabel: VoiceLabelAsset,
        changeRecord: ChangeRecord,
    ) {
        require(updatedLabel.deletedAt != null) { "Deleted voice label must be soft-deleted." }
        require(changeRecord.entityType == ChangeEntityType.VOICE_LABEL_ASSET) {
            "Voice label change record entity type must be VOICE_LABEL_ASSET."
        }
        require(changeRecord.operation == ChangeOperation.DELETE) {
            "Deleted voice label change record operation must be DELETE."
        }
        transactionRunner.write {
            require(voiceLabelAssetDao().update(updatedLabel.toEntity()) == 1) {
                "Deleted voice label update must affect exactly one row."
            }
            changeRecordDao().insert(changeRecord.toEntity())
        }
    }

    private suspend fun storedContext(
        householdId: HouseholdId,
        locationNodeId: LocationNodeId?,
        itemId: ItemId?,
        labels: List<VoiceLabelAsset>,
    ): StoredVoiceLabelContext {
        val currentDevice = requireNotNull(
            database.deviceDao().findFirstActiveByHousehold(householdId.value),
        ) {
            "Cannot manage voice labels without an active source device."
        }
        return StoredVoiceLabelContext(
            householdId = householdId,
            locationNodeId = locationNodeId,
            itemId = itemId,
            labels = labels,
            currentDeviceId = DeviceId(currentDevice.id),
        )
    }
}
