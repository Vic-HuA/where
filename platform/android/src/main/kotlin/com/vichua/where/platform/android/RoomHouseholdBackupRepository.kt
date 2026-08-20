package com.vichua.where.platform.android

import com.vichua.where.core.database.query.HouseholdBackupSnapshotStore
import com.vichua.where.core.database.query.LocalBackupRecordStore
import com.vichua.where.core.database.transaction.HouseholdClearStore
import com.vichua.where.core.database.transaction.HouseholdRestoreStore
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdBackupSnapshot
import com.vichua.where.core.model.HouseholdDataSummary
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.ItemDraft
import com.vichua.where.core.model.LocalBackupRecord
import com.vichua.where.core.model.PhotoAsset
import com.vichua.where.feature.backup.HouseholdBackupRepository

/**
 * 使用共享 Room Store 实现 Android 家庭备份、恢复和清除仓储。
 */
class RoomHouseholdBackupRepository(
    private val snapshotStore: HouseholdBackupSnapshotStore,
    private val recordStore: LocalBackupRecordStore,
    private val restoreStore: HouseholdRestoreStore,
    private val clearStore: HouseholdClearStore,
) : HouseholdBackupRepository {
    /**
     * 加载过滤后的家庭快照。
     */
    override suspend fun loadSnapshot(): HouseholdBackupSnapshot = snapshotStore.load()

    /**
     * 返回当前有效设备。
     */
    override suspend fun currentDeviceId(): DeviceId = recordStore.currentDeviceId()

    /**
     * 返回当前未删除家庭。
     */
    override suspend fun currentHouseholdId(): HouseholdId = recordStore.currentHouseholdId()

    /**
     * 返回最近已验证备份。
     */
    override suspend fun findLatestVerified(): LocalBackupRecord? = recordStore.findLatestVerified()

    /**
     * 保存本机备份记录。
     */
    override suspend fun insertRecord(record: LocalBackupRecord) {
        recordStore.insert(record)
    }

    /**
     * 用目标快照覆盖家庭可导出实体。
     */
    override suspend fun replaceSnapshot(snapshot: HouseholdBackupSnapshot) {
        restoreStore.replaceSnapshot(snapshot, recordStore.currentDeviceId())
    }

    /**
     * 读取当前设备草稿。
     */
    override suspend fun loadDrafts(): List<ItemDraft> =
        restoreStore.loadDrafts(
            deviceId = recordStore.currentDeviceId(),
            householdId = recordStore.currentHouseholdId().value,
        )

    /**
     * 写回校验后的草稿。
     */
    override suspend fun saveDraft(draft: ItemDraft) {
        restoreStore.saveDraft(draft)
    }

    /**
     * 读取当前家庭摘要。
     */
    override suspend fun loadSummary(): HouseholdDataSummary = clearStore.loadSummary()

    /**
     * 读取当前家庭照片元数据。
     */
    override suspend fun loadPhotos(): List<PhotoAsset> = clearStore.loadPhotos()

    /**
     * 清除当前家庭可导出数据。
     */
    override suspend fun clearHousehold(clearedAtMillis: Long) {
        clearStore.clearHousehold(recordStore.currentDeviceId(), clearedAtMillis)
    }
}
