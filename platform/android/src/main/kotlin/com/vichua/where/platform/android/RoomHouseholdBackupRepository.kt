package com.vichua.where.platform.android

import com.vichua.where.core.database.query.HouseholdBackupSnapshotStore
import com.vichua.where.core.database.query.LocalBackupRecordStore
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdBackupSnapshot
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.LocalBackupRecord
import com.vichua.where.feature.backup.HouseholdBackupRepository

/**
 * 使用共享 Room Store 实现 Android 家庭备份仓储。
 */
class RoomHouseholdBackupRepository(
    private val snapshotStore: HouseholdBackupSnapshotStore,
    private val recordStore: LocalBackupRecordStore,
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
}
