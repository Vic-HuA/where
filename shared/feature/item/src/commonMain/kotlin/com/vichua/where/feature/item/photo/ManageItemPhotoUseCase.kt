package com.vichua.where.feature.item.photo

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.MediaIntegrityStatus
import com.vichua.where.core.model.PhotoAsset
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.MediaFilePromotion
import com.vichua.where.core.platform.StorageKeys

/**
 * 编辑物品照片时需要的当前上下文。
 *
 * @property item 当前未删除物品。
 * @property photos 该物品全部照片，包含软删除记录。
 * @property currentDeviceId 本次修改来源设备。
 */
data class ItemPhotoContext(
    val item: Item,
    val photos: List<PhotoAsset>,
    val currentDeviceId: DeviceId,
)

/**
 * 追加照片后需要原子保存的聚合。
 */
data class ItemPhotoAddition(
    val newPhoto: PhotoAsset,
    val changeRecord: ChangeRecord,
)

/**
 * 更新照片集合后需要原子保存的聚合。
 */
data class ItemPhotoCollectionUpdate(
    val itemId: ItemId,
    val updatedPhotos: List<PhotoAsset>,
    val changeRecords: List<ChangeRecord>,
)

/**
 * 物品照片管理仓储契约。
 */
interface ItemPhotoRepository {
    /** 加载指定物品和全部照片。 */
    suspend fun load(itemId: ItemId): ItemPhotoContext

    /** 原子追加一张正式照片。 */
    suspend fun add(addition: ItemPhotoAddition)

    /** 原子保存用途、顺序、封面或软删除变更。 */
    suspend fun update(update: ItemPhotoCollectionUpdate)
}

/**
 * 向已有物品追加一张相册导入的照片，不改当前位置。
 */
class AddItemPhotoUseCase(
    private val repository: ItemPhotoRepository,
    private val mediaFileStore: ControlledMediaFileStore,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 把临时导入结果写成正式照片；第一张照片自动作为封面。
     */
    suspend operator fun invoke(
        itemId: ItemId,
        importedPhoto: ImportedItemPhoto,
    ) {
        val context = repository.load(itemId)
        val now = UtcTimestamp(clock.now())
        val activePhotos = context.photos.filter { photo -> photo.deletedAt == null }
        val nextSortOrder = activePhotos.maxOfOrNull { photo -> photo.sortOrder.value }?.plus(1) ?: 0
        val photoId = PhotoAssetId(idGenerator.generate())
        val extension = mimeTypeToExtension(importedPhoto.media.mimeType)
        val newPhoto = PhotoAsset(
            id = photoId,
            householdId = context.item.householdId,
            itemId = context.item.id,
            role = importedPhoto.role,
            storageKey = StorageKeys.itemOriginal(context.item.id.value, photoId.value, extension),
            thumbnailStorageKey = StorageKeys.itemThumbnail(context.item.id.value, photoId.value),
            mimeType = importedPhoto.media.mimeType,
            width = importedPhoto.media.width,
            height = importedPhoto.media.height,
            sizeBytes = importedPhoto.media.sizeBytes,
            contentHash = importedPhoto.media.contentHash,
            integrityStatus = MediaIntegrityStatus.AVAILABLE,
            lastIntegrityCheckedAt = now,
            sortOrder = SortOrder(nextSortOrder),
            isCover = activePhotos.isEmpty(),
            createdAt = now,
            updatedAt = now,
            version = EntityVersion(INITIAL_PHOTO_VERSION),
            sourceDeviceId = context.currentDeviceId,
        )
        repository.add(
            ItemPhotoAddition(
                newPhoto = newPhoto,
                changeRecord = photoChangeRecord(
                    photo = newPhoto,
                    operation = ChangeOperation.CREATE,
                    occurredAt = now,
                    recordId = idGenerator.generate(),
                ),
            ),
        )
        // 先提交数据库再转正文件，避免事务失败后留下引用不存在文件的正式记录。
        try {
            mediaFileStore.promote(
                listOf(
                    MediaFilePromotion(importedPhoto.media.tempStorageKey, newPhoto.storageKey),
                    MediaFilePromotion(
                        importedPhoto.media.thumbnailTempStorageKey,
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
 * 把指定照片设为封面，并在同一事务中取消原封面。
 */
class SetItemPhotoCoverUseCase(
    private val repository: ItemPhotoRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /** 目标必须是当前未删除照片，且当前还不是封面。 */
    suspend operator fun invoke(
        itemId: ItemId,
        photoId: PhotoAssetId,
    ) {
        val context = repository.load(itemId)
        val targetPhoto = requireActivePhoto(context, photoId)
        require(!targetPhoto.isCover) { "Selected photo is already the cover." }
        val now = UtcTimestamp(clock.now())
        applyCollectionUpdate(
            context = context,
            now = now,
            idGenerator = idGenerator,
            repository = repository,
            transform = { photo ->
                when {
                    photo.id == photoId -> photo.copy(isCover = true)
                    photo.deletedAt == null && photo.isCover -> photo.copy(isCover = false)
                    else -> photo
                }
            },
        )
    }
}

/**
 * 修改单张未删除照片的用途，不改文件和封面。
 */
class UpdateItemPhotoRoleUseCase(
    private val repository: ItemPhotoRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /** 用途没有变化时拒绝，避免产生空变更记录。 */
    suspend operator fun invoke(
        itemId: ItemId,
        photoId: PhotoAssetId,
        role: PhotoRole,
    ) {
        val context = repository.load(itemId)
        val targetPhoto = requireActivePhoto(context, photoId)
        require(targetPhoto.role != role) { "Photo role update must change the current role." }
        val now = UtcTimestamp(clock.now())
        applyCollectionUpdate(
            context = context,
            now = now,
            idGenerator = idGenerator,
            repository = repository,
            transform = { photo ->
                if (photo.id == photoId) {
                    photo.copy(role = role)
                } else {
                    photo
                }
            },
        )
    }
}

/**
 * 与相邻未删除照片交换展示顺序。
 *
 * @param offset `-1` 前移，`1` 后移。
 */
class MoveItemPhotoUseCase(
    private val repository: ItemPhotoRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /** 越出当前画廊两端时拒绝，避免产生循环顺序。 */
    suspend operator fun invoke(
        itemId: ItemId,
        photoId: PhotoAssetId,
        offset: Int,
    ) {
        require(offset == -1 || offset == 1) { "Photo move offset must be -1 or 1." }
        val context = repository.load(itemId)
        requireActivePhoto(context, photoId)
        val activePhotos = context.photos
            .filter { photo -> photo.deletedAt == null }
            .sortedWith(compareBy(PhotoAsset::sortOrder, { photo -> photo.id.value }))
        val currentIndex = activePhotos.indexOfFirst { photo -> photo.id == photoId }
        val targetIndex = currentIndex + offset
        require(targetIndex in activePhotos.indices) {
            "Photo cannot move beyond the gallery boundary."
        }
        val currentPhoto = activePhotos[currentIndex]
        val neighborPhoto = activePhotos[targetIndex]
        val now = UtcTimestamp(clock.now())
        applyCollectionUpdate(
            context = context,
            now = now,
            idGenerator = idGenerator,
            repository = repository,
            transform = { photo ->
                when (photo.id) {
                    currentPhoto.id -> photo.copy(sortOrder = neighborPhoto.sortOrder)
                    neighborPhoto.id -> photo.copy(sortOrder = currentPhoto.sortOrder)
                    else -> photo
                }
            },
        )
    }
}

/**
 * 软删除一张照片；删除封面时在同一事务中指定新封面。
 *
 * 删除最后一张照片仍保留物品档案，且不物理删除文件。
 */
class DeleteItemPhotoUseCase(
    private val repository: ItemPhotoRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /** 软删除指定照片，必要时把下一张未删除照片提升为封面。 */
    suspend operator fun invoke(
        itemId: ItemId,
        photoId: PhotoAssetId,
    ) {
        val context = repository.load(itemId)
        val targetPhoto = requireActivePhoto(context, photoId)
        val now = UtcTimestamp(clock.now())
        val remainingPhotos = context.photos
            .filter { photo -> photo.deletedAt == null && photo.id != photoId }
            .sortedWith(compareBy(PhotoAsset::sortOrder, { photo -> photo.id.value }))
        val nextCoverId = remainingPhotos.firstOrNull()?.id
        applyCollectionUpdate(
            context = context,
            now = now,
            idGenerator = idGenerator,
            repository = repository,
            transform = { photo ->
                when {
                    photo.id == photoId -> photo.copy(
                        isCover = false,
                        deletedAt = now,
                    )
                    photo.id == nextCoverId && targetPhoto.isCover -> photo.copy(isCover = true)
                    else -> photo
                }
            },
        )
    }
}

/**
 * 确认目标照片仍未删除。
 */
private fun requireActivePhoto(
    context: ItemPhotoContext,
    photoId: PhotoAssetId,
): PhotoAsset {
    val photo = requireNotNull(
        context.photos.singleOrNull { candidate -> candidate.id == photoId },
    ) {
        "Requested photo does not belong to the item."
    }
    require(photo.deletedAt == null) { "Soft-deleted photo cannot be managed." }
    return photo
}

/**
 * 把可见字段变化写成递增版本并提交完整照片集合。
 */
private suspend fun applyCollectionUpdate(
    context: ItemPhotoContext,
    now: UtcTimestamp,
    idGenerator: UniqueIdGenerator,
    repository: ItemPhotoRepository,
    transform: (PhotoAsset) -> PhotoAsset,
) {
    val updatedPhotos = context.photos.map { photo ->
        val transformedPhoto = transform(photo)
        if (hasVisiblePhotoChange(photo, transformedPhoto)) {
            transformedPhoto.copy(
                updatedAt = now,
                version = photo.version.next(),
                sourceDeviceId = context.currentDeviceId,
            )
        } else {
            photo
        }
    }
    val changeRecords = updatedPhotos.mapNotNull { updatedPhoto ->
        val storedPhoto = context.photos.single { photo -> photo.id == updatedPhoto.id }
        if (!hasVisiblePhotoChange(storedPhoto, updatedPhoto)) {
            return@mapNotNull null
        }
        val operation = if (storedPhoto.deletedAt == null && updatedPhoto.deletedAt != null) {
            ChangeOperation.DELETE
        } else {
            ChangeOperation.UPDATE
        }
        photoChangeRecord(
            photo = updatedPhoto,
            operation = operation,
            occurredAt = now,
            recordId = idGenerator.generate(),
        )
    }
    require(changeRecords.isNotEmpty()) { "Photo collection update must change at least one photo." }
    repository.update(
        ItemPhotoCollectionUpdate(
            itemId = context.item.id,
            updatedPhotos = updatedPhotos,
            changeRecords = changeRecords,
        ),
    )
}

/**
 * 判断用途、顺序、封面或删除状态是否变化。
 */
private fun hasVisiblePhotoChange(
    before: PhotoAsset,
    after: PhotoAsset,
): Boolean {
    return before.role != after.role ||
        before.sortOrder != after.sortOrder ||
        before.isCover != after.isCover ||
        before.deletedAt != after.deletedAt
}

/**
 * 生成对应照片版本的变更记录。
 */
private fun photoChangeRecord(
    photo: PhotoAsset,
    operation: ChangeOperation,
    occurredAt: UtcTimestamp,
    recordId: String,
): ChangeRecord = ChangeRecord(
    id = ChangeRecordId(recordId),
    householdId = photo.householdId,
    entityType = ChangeEntityType.PHOTO_ASSET,
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
