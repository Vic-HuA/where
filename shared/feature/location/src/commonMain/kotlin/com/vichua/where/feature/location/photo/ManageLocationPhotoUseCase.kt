package com.vichua.where.feature.location.photo

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationPhotoAsset
import com.vichua.where.core.model.LocationPhotoAssetId
import com.vichua.where.core.model.MediaIntegrityStatus
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.ImportedMediaFile
import com.vichua.where.core.platform.MediaFilePromotion
import com.vichua.where.core.platform.StorageKeys

/**
 * 编辑位置照片时需要的当前上下文。
 */
data class LocationPhotoContext(
    val location: LocationNode,
    val photos: List<LocationPhotoAsset>,
    val currentDeviceId: DeviceId,
)

/**
 * 位置照片管理仓储契约。
 */
interface LocationPhotoRepository {
    /** 加载指定位置和全部照片。 */
    suspend fun load(locationId: LocationNodeId): LocationPhotoContext

    /** 写入新代表照并停用旧照。 */
    suspend fun replaceCover(
        newPhoto: LocationPhotoAsset,
        retiredPhotos: List<LocationPhotoAsset>,
        changeRecords: List<ChangeRecord>,
    )

    /** 软删除当前代表照。 */
    suspend fun deleteCover(
        updatedPhoto: LocationPhotoAsset,
        changeRecord: ChangeRecord,
    )
}

/**
 * 把用户选择的图片导入应用私有临时目录，供位置代表照使用。
 */
class ImportLocationPhotoUseCase(
    private val mediaFileStore: ControlledMediaFileStore,
) {
    /**
     * 写入临时原图和缩略图；正式位置照片 ID 在保存时再确定。
     */
    suspend operator fun invoke(
        bytes: ByteArray,
        sourceMimeType: String?,
    ): ImportedMediaFile {
        require(bytes.isNotEmpty()) { "Imported location photo bytes must not be empty." }
        return mediaFileStore.importImage(bytes, sourceMimeType)
    }
}

/**
 * 为位置写入或更换代表照。已有未删除照片会被软删除，保证每个位置只有一张封面。
 */
class AddLocationPhotoUseCase(
    private val repository: LocationPhotoRepository,
    private val mediaFileStore: ControlledMediaFileStore,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 把临时导入结果写成正式代表照。
     */
    suspend operator fun invoke(
        locationId: LocationNodeId,
        importedPhoto: ImportedMediaFile,
    ) {
        val context = repository.load(locationId)
        val now = UtcTimestamp(clock.now())
        val photoId = LocationPhotoAssetId(idGenerator.generate())
        val extension = mimeTypeToExtension(importedPhoto.mimeType)
        val newPhoto = LocationPhotoAsset(
            id = photoId,
            householdId = context.location.householdId,
            locationNodeId = context.location.id,
            storageKey = StorageKeys.locationOriginal(
                context.location.id.value,
                photoId.value,
                extension,
            ),
            thumbnailStorageKey = StorageKeys.locationThumbnail(
                context.location.id.value,
                photoId.value,
            ),
            mimeType = importedPhoto.mimeType,
            width = importedPhoto.width,
            height = importedPhoto.height,
            sizeBytes = importedPhoto.sizeBytes,
            contentHash = importedPhoto.contentHash,
            integrityStatus = MediaIntegrityStatus.AVAILABLE,
            lastIntegrityCheckedAt = now,
            sortOrder = SortOrder(0),
            isCover = true,
            createdAt = now,
            updatedAt = now,
            version = EntityVersion(INITIAL_PHOTO_VERSION),
            sourceDeviceId = context.currentDeviceId,
        )
        val retiredPhotos = context.photos
            .filter { photo -> photo.deletedAt == null }
            .map { photo ->
                photo.copy(
                    deletedAt = now,
                    isCover = false,
                    updatedAt = now,
                    version = photo.version.next(),
                    sourceDeviceId = context.currentDeviceId,
                )
            }
        val changeRecords = buildList {
            retiredPhotos.forEach { photo ->
                add(
                    locationPhotoChangeRecord(
                        photo = photo,
                        operation = ChangeOperation.DELETE,
                        occurredAt = now,
                        recordId = idGenerator.generate(),
                    ),
                )
            }
            add(
                locationPhotoChangeRecord(
                    photo = newPhoto,
                    operation = ChangeOperation.CREATE,
                    occurredAt = now,
                    recordId = idGenerator.generate(),
                ),
            )
        }
        repository.replaceCover(
            newPhoto = newPhoto,
            retiredPhotos = retiredPhotos,
            changeRecords = changeRecords,
        )
        // 先提交数据库再转正文件，避免事务失败后留下引用不存在文件的正式记录。
        try {
            mediaFileStore.promote(
                listOf(
                    MediaFilePromotion(importedPhoto.tempStorageKey, newPhoto.storageKey),
                    MediaFilePromotion(
                        importedPhoto.thumbnailTempStorageKey,
                        newPhoto.thumbnailStorageKey,
                    ),
                ),
            )
        } catch (_: Exception) {
            // Intentionally keep the photo record after a failed file promotion.
        }
    }

    private companion object {
        const val INITIAL_PHOTO_VERSION = 1L
    }
}

/**
 * 删除位置当前代表照。
 */
class DeleteLocationPhotoUseCase(
    private val repository: LocationPhotoRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 软删除当前未删除代表照；没有照片时拒绝。
     */
    suspend operator fun invoke(locationId: LocationNodeId) {
        val context = repository.load(locationId)
        val activePhoto = context.photos.singleOrNull { photo -> photo.deletedAt == null }
        require(activePhoto != null) { "Location does not have an active photo." }
        val now = UtcTimestamp(clock.now())
        val updatedPhoto = activePhoto.copy(
            deletedAt = now,
            isCover = false,
            updatedAt = now,
            version = activePhoto.version.next(),
            sourceDeviceId = context.currentDeviceId,
        )
        repository.deleteCover(
            updatedPhoto = updatedPhoto,
            changeRecord = locationPhotoChangeRecord(
                photo = updatedPhoto,
                operation = ChangeOperation.DELETE,
                occurredAt = now,
                recordId = idGenerator.generate(),
            ),
        )
    }
}

/**
 * 生成对应位置照片版本的变更记录。
 */
private fun locationPhotoChangeRecord(
    photo: LocationPhotoAsset,
    operation: ChangeOperation,
    occurredAt: UtcTimestamp,
    recordId: String,
): ChangeRecord = ChangeRecord(
    id = ChangeRecordId(recordId),
    householdId = photo.householdId,
    entityType = ChangeEntityType.LOCATION_PHOTO_ASSET,
    entityId = photo.id.value,
    operation = operation,
    entityVersion = photo.version,
    sourceDeviceId = photo.sourceDeviceId,
    occurredAt = occurredAt,
)

/**
 * 把已校验 MIME 映射为受控扩展名。
 */
private fun mimeTypeToExtension(mimeType: String): String {
    return when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> error("Unsupported photo MIME type.")
    }
}
