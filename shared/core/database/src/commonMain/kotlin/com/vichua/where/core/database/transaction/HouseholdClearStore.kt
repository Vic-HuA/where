package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdDataSummary
import com.vichua.where.core.model.ItemDraft
import com.vichua.where.core.model.PhotoAsset

/**
 * 清除当前家庭可导出数据，并保留设备本地偏好、草稿和备份记录。
 */
class HouseholdClearStore(
    database: WhereDatabase,
) {
    private val database = database
    private val transactionRunner = DatabaseTransactionRunner(database)

    /**
     * 读取清除前需要向用户展示的当前家庭摘要。
     */
    suspend fun loadSummary(): HouseholdDataSummary {
        val household = database.householdDao().findFirstActive()
            ?: error("Active household is required before clearing household data.")
        val householdId = household.id
        return HouseholdDataSummary(
            itemCount = database.itemDao().findAllByHousehold(householdId).size,
            locationCount = database.locationNodeDao().findAllByHousehold(householdId).size,
            photoCount = database.photoAssetDao().findAllByHousehold(householdId).size,
        )
    }

    /**
     * 返回当前家庭全部照片元数据，供清除后删除受控原图。
     */
    suspend fun loadPhotos(): List<PhotoAsset> {
        val household = database.householdDao().findFirstActive()
            ?: error("Active household is required before clearing household data.")
        return database.photoAssetDao().findAllByHousehold(household.id).map { it.toDomain() }
    }

    /**
     * 读取当前设备草稿，供清除后清空失效位置引用。
     */
    suspend fun loadDrafts(deviceId: DeviceId): List<ItemDraft> {
        val household = database.householdDao().findFirstActive()
            ?: error("Active household is required before clearing household data.")
        return database.itemDraftDao()
            .findAllByDeviceAndHousehold(deviceId.value, household.id)
            .map { entity -> entity.toDomain() }
    }

    /**
     * 在单个事务中删除家庭实体并软删除家庭根记录。
     *
     * 当前设备、适老偏好、最近查找、本机备份记录和草稿行予以保留。
     */
    suspend fun clearHousehold(
        currentDeviceId: DeviceId,
        clearedAtMillis: Long,
    ) {
        val household = database.householdDao().findFirstActive()
            ?: error("Active household is required before clearing household data.")
        require(database.deviceDao().findById(currentDeviceId.value) != null) {
            "Current device is required before clearing household data."
        }
        val householdId = household.id
        transactionRunner.write {
            itemLocationEventDao().deleteByHousehold(householdId)
            photoAssetDao().deleteByHousehold(householdId)
            itemAliasDao().deleteByHousehold(householdId)
            itemDao().findAllByHousehold(householdId).forEach { item ->
                itemSearchDao().deleteByItemId(item.id)
            }
            itemDao().deleteByHousehold(householdId)
            homeSupportDao().deleteFavoriteLocationsByHousehold(householdId)
            changeRecordDao().deleteByHousehold(householdId)
            categoryDao().deleteByHousehold(householdId)
            locationNodeDao().deleteByHousehold(householdId)
            deviceDao().deleteByHouseholdExcept(householdId, currentDeviceId.value)
            householdDao().update(household.copy(deletedAt = clearedAtMillis))
        }
    }
}
