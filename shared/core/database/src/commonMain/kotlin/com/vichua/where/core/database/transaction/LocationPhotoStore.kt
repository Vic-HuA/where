package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationPhotoAsset

/**
 * 数据库层提供的位置照片管理上下文。
 *
 * @property location 当前未删除位置。
 * @property photos 该位置全部照片，包含软删除记录，便于更换代表照。
 * @property currentDeviceId 当前有效设备，用于记录本次修改来源。
 */
data class StoredLocationPhotoContext(
    val location: LocationNode,
    val photos: List<LocationPhotoAsset>,
    val currentDeviceId: DeviceId,
)

/**
 * 加载位置照片并在同一事务中追加或停用代表照。
 */
class LocationPhotoStore(
    database: WhereDatabase,
) {
    private val database = database
    private val transactionRunner = DatabaseTransactionRunner(database)

    /**
     * 读取指定位置和全部照片；位置不存在时失败。
     */
    suspend fun load(locationId: LocationNodeId): StoredLocationPhotoContext {
        val location = requireNotNull(
            database.locationNodeDao().findById(locationId.value)?.toDomain(),
        ) {
            "Cannot manage photos of a missing location."
        }
        require(location.deletedAt == null) { "Cannot manage photos of a deleted location." }
        require(!location.isHouseholdRoot) {
            "Household root cannot have a location photo."
        }
        val currentDevice = requireNotNull(
            database.deviceDao().findFirstActiveByHousehold(location.householdId.value),
        ) {
            "Cannot manage location photos without an active source device."
        }
        return StoredLocationPhotoContext(
            location = location,
            photos = database.locationPhotoAssetDao()
                .findAllByLocation(location.id.value)
                .map { entity -> entity.toDomain() },
            currentDeviceId = DeviceId(currentDevice.id),
        )
    }

    /**
     * 写入新代表照，并软删除该位置已有未删除照片，保证封面唯一。
     */
    suspend fun replaceCover(
        newPhoto: LocationPhotoAsset,
        retiredPhotos: List<LocationPhotoAsset>,
        changeRecords: List<ChangeRecord>,
    ) {
        require(newPhoto.deletedAt == null) { "New location photo must not be soft-deleted." }
        require(newPhoto.isCover) { "New location photo must be the cover." }
        retiredPhotos.forEach { photo ->
            require(photo.locationNodeId == newPhoto.locationNodeId) {
                "Retired location photo must belong to the same location."
            }
            require(photo.deletedAt != null) { "Retired location photo must be soft-deleted." }
        }
        changeRecords.forEach { record ->
            require(record.entityType == ChangeEntityType.LOCATION_PHOTO_ASSET) {
                "Location photo change record entity type must be LOCATION_PHOTO_ASSET."
            }
        }

        transactionRunner.write {
            val location = locationNodeDao().findById(newPhoto.locationNodeId.value)?.toDomain()
            require(location != null && location.deletedAt == null) {
                "Location photo location does not exist."
            }
            require(location.householdId == newPhoto.householdId) {
                "Location photo must belong to the same household as the location."
            }
            require(!location.isHouseholdRoot) {
                "Household root cannot have a location photo."
            }
            retiredPhotos.forEach { photo ->
                require(locationPhotoAssetDao().update(photo.toEntity()) == 1) {
                    "Retired location photo update must affect exactly one row."
                }
            }
            locationPhotoAssetDao().insertAll(listOf(newPhoto.toEntity()))
            if (changeRecords.isNotEmpty()) {
                changeRecordDao().insertAll(changeRecords.map(ChangeRecord::toEntity))
            }
        }
    }

    /**
     * 软删除当前代表照。
     */
    suspend fun deleteCover(
        updatedPhoto: LocationPhotoAsset,
        changeRecord: ChangeRecord,
    ) {
        require(updatedPhoto.deletedAt != null) { "Deleted location photo must be soft-deleted." }
        require(changeRecord.entityType == ChangeEntityType.LOCATION_PHOTO_ASSET) {
            "Location photo change record entity type must be LOCATION_PHOTO_ASSET."
        }
        require(changeRecord.operation == ChangeOperation.DELETE) {
            "Deleted location photo change record operation must be DELETE."
        }
        require(changeRecord.entityId == updatedPhoto.id.value) {
            "Deleted location photo change record must reference the photo."
        }

        transactionRunner.write {
            require(locationPhotoAssetDao().update(updatedPhoto.toEntity()) == 1) {
                "Deleted location photo update must affect exactly one row."
            }
            changeRecordDao().insert(changeRecord.toEntity())
        }
    }
}
