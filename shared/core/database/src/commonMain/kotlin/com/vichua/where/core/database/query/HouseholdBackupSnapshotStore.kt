package com.vichua.where.core.database.query

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.model.HouseholdBackupSnapshot

/**
 * 读取经过导出过滤的家庭快照。
 *
 * 明确不读取草稿、最近查找、设备辅助偏好和本机备份记录，避免整库复制绕过导出边界。
 */
class HouseholdBackupSnapshotStore(
    private val database: WhereDatabase,
) {
    /**
     * 加载当前未删除家庭的完整可备份数据，包括软删除的正式记录。
     */
    suspend fun load(): HouseholdBackupSnapshot {
        val household = database.householdDao().findFirstActive()?.toDomain()
            ?: error("Active household is required before creating a backup.")
        val householdId = household.id.value
        return HouseholdBackupSnapshot(
            household = household,
            devices = database.deviceDao().findAllByHousehold(householdId).map { it.toDomain() },
            locations = database.locationNodeDao().findAllByHousehold(householdId).map { it.toDomain() },
            categories = database.categoryDao().findAllByHousehold(householdId).map { it.toDomain() },
            items = database.itemDao().findAllByHousehold(householdId).map { it.toDomain() },
            aliases = database.itemAliasDao().findAllByHousehold(householdId).map { it.toDomain() },
            photos = database.photoAssetDao().findAllByHousehold(householdId).map { it.toDomain() },
            locationPhotos = database.locationPhotoAssetDao()
                .findAllByHousehold(householdId)
                .map { it.toDomain() },
            voiceLabels = database.voiceLabelAssetDao()
                .findAllByHousehold(householdId)
                .map { it.toDomain() },
            locationEvents = database.itemLocationEventDao()
                .findAllByHousehold(householdId)
                .map { it.toDomain() },
            changeRecords = database.changeRecordDao()
                .findAllByHousehold(householdId)
                .map { it.toDomain() },
            favoriteLocations = database.homeSupportDao()
                .findAllFavoriteLocationsByHousehold(householdId)
                .map { it.toDomain() },
            pinnedItems = database.homeSupportDao()
                .findAllPinnedItemsByHousehold(householdId)
                .map { it.toDomain() },
        )
    }
}
