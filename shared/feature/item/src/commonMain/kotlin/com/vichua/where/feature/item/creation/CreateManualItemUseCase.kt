package com.vichua.where.feature.item.creation

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.TextNormalizer
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.CategoryId
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemAlias
import com.vichua.where.core.model.ItemAliasId
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.ItemLocationEventId
import com.vichua.where.core.model.ItemLocationReason
import com.vichua.where.core.model.ItemStatus
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.feature.item.profile.ItemCategoryOption
import com.vichua.where.feature.item.profile.parseItemAliasInputs
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.MediaIntegrityStatus
import com.vichua.where.core.model.PhotoAsset
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.MediaFilePromotion
import com.vichua.where.core.platform.StorageKeys
import com.vichua.where.feature.item.photo.ImportedItemPhoto

/**
 * 新增物品页面可选择的位置。
 *
 * @property locationId 位置节点 ID。
 * @property parentId 父节点 ID，家庭根节点下的房间为空以外的值。
 * @property displayPath 不包含家庭根节点的完整显示路径。
 * @property name 当前位置节点名称。
 * @property type 位置语义类型。
 * @property iconKey 受控图标键。
 */
data class ItemCreationLocation(
    val locationId: LocationNodeId,
    val parentId: LocationNodeId?,
    val displayPath: String,
    val name: String,
    val type: LocationType,
    val iconKey: String?,
)

/**
 * 创建物品所需的当前家庭上下文。
 *
 * @property householdId 当前未删除家庭 ID。
 * @property sourceDeviceId 当前有效设备 ID。
 * @property rootLocationId 家庭根位置，录入页新建房间时作为父节点。
 * @property availableLocations 可供物品选择的未删除非根位置。
 * @property categories 可供选择的物品分类。
 */
data class ItemCreationContext(
    val householdId: HouseholdId,
    val sourceDeviceId: DeviceId,
    val rootLocationId: LocationNodeId,
    val availableLocations: List<ItemCreationLocation>,
    val categories: List<ItemCategoryOption> = emptyList(),
)

/**
 * 手动创建物品的用户确认输入。
 *
 * @property name 物品名称。
 * @property locationId 用户选择的位置 ID。
 * @property locationDescription 可选位置补充说明。
 * @property note 可选备注。
 * @property aliases 用户输入的别名原文，可为空。
 * @property categoryId 可选分类。
 * @property quantity 大于 0 的数量，默认 1。
 * @property unit 可选数量单位。
 * @property photos 已导入的临时照片；可为空，第一张会成为封面。
 */
data class CreateManualItemRequest(
    val name: String,
    val locationId: LocationNodeId,
    val locationDescription: String? = null,
    val note: String? = null,
    val aliases: List<String> = emptyList(),
    val categoryId: CategoryId? = null,
    val quantity: Double = 1.0,
    val unit: String? = null,
    val photos: List<ImportedItemPhoto> = emptyList(),
)

/**
 * 建立全文索引所需的已确认文本。
 *
 * @property itemId 对应物品 ID。
 * @property name 物品名称。
 * @property aliasesText 当前别名文本，基础手动录入为空。
 * @property categoryText 当前分类文本，基础手动录入为空。
 * @property noteText 备注文本。
 * @property locationPathText 当前完整位置路径。
 */
data class ItemSearchContent(
    val itemId: ItemId,
    val name: String,
    val aliasesText: String,
    val categoryText: String,
    val noteText: String,
    val locationPathText: String,
)

/**
 * 手动创建物品时需要原子保存的领域聚合。
 *
 * @property item 新物品档案。
 * @property photos 与物品同时提交的正式照片记录。
 * @property initialLocationEvent 首次位置历史事件。
 * @property changeRecord 物品创建变更记录。
 * @property searchContent 全文索引内容。
 * @property aliases 与物品同时写入的别名。
 */
data class ManualItemCreation(
    val item: Item,
    val photos: List<PhotoAsset> = emptyList(),
    val initialLocationEvent: ItemLocationEvent,
    val changeRecord: ChangeRecord,
    val searchContent: ItemSearchContent,
    val aliases: List<ItemAlias> = emptyList(),
)

/**
 * 手动新增物品的数据仓储契约。
 */
interface ManualItemCreationRepository {
    /**
     * 加载当前家庭、设备和可选择位置。
     */
    suspend fun loadContext(): ItemCreationContext

    /**
     * 原子保存物品、位置历史、变更记录和搜索索引。
     */
    suspend fun create(creation: ManualItemCreation)
}

/**
 * 加载新增物品页面可选位置。
 */
class LoadItemCreationContextUseCase(
    private val repository: ManualItemCreationRepository,
) {
    /**
     * 返回当前物品创建上下文。
     */
    suspend operator fun invoke(): ItemCreationContext = repository.loadContext()
}

/**
 * 创建不依赖相机、语音或 AI 的基础物品记录，并可附带已导入的本地照片。
 */
class CreateManualItemUseCase(
    private val repository: ManualItemCreationRepository,
    private val mediaFileStore: ControlledMediaFileStore,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
    private val textNormalizer: TextNormalizer,
) {
    /**
     * 校验用户输入并原子保存物品。
     *
     * @param request 用户确认后的名称、位置和可选说明。
     * @return 新物品 ID。
     */
    suspend operator fun invoke(request: CreateManualItemRequest): ItemId {
        val context = repository.loadContext()
        val name = request.name.trim()
        val normalizedName = textNormalizer.normalize(name)
        val selectedLocation = context.availableLocations.singleOrNull { location ->
            location.locationId == request.locationId
        }
        require(name.isNotEmpty()) { "Item name must not be blank." }
        require(normalizedName.isNotEmpty()) { "Normalized item name must not be blank." }
        require(selectedLocation != null) {
            "Selected item location is unavailable."
        }

        val locationDescription = request.locationDescription
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val note = request.note
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val unit = request.unit
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val selectedCategory = request.categoryId?.let { categoryId ->
            context.categories.singleOrNull { option -> option.categoryId == categoryId }
        }
        require(request.categoryId == null || selectedCategory != null) {
            "Selected item category is unavailable."
        }
        require(request.quantity.isFinite() && request.quantity > 0.0) {
            "Item quantity must be finite and greater than zero."
        }
        val aliasNames = parseItemAliasInputs(request.aliases, textNormalizer)
        val now = UtcTimestamp(clock.now())
        val initialVersion = EntityVersion(INITIAL_ENTITY_VERSION)
        val itemId = ItemId(idGenerator.generate())
        val aliases = aliasNames.map { aliasName ->
            ItemAlias(
                id = ItemAliasId(idGenerator.generate()),
                itemId = itemId,
                alias = aliasName,
                normalizedAlias = textNormalizer.normalize(aliasName),
                createdAt = now,
            )
        }
        val item = Item(
            id = itemId,
            householdId = context.householdId,
            currentLocationId = selectedLocation.locationId,
            name = name,
            normalizedName = normalizedName,
            categoryId = selectedCategory?.categoryId,
            quantity = request.quantity,
            unit = unit,
            locationDescription = locationDescription,
            note = note,
            status = ItemStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
            version = initialVersion,
            sourceDeviceId = context.sourceDeviceId,
        )
        val locationEvent = ItemLocationEvent(
            id = ItemLocationEventId(idGenerator.generate()),
            householdId = context.householdId,
            itemId = itemId,
            fromLocationId = null,
            toLocationId = selectedLocation.locationId,
            fromPathSnapshot = null,
            toPathSnapshot = selectedLocation.displayPath,
            reason = ItemLocationReason.CREATED,
            occurredAt = now,
            sourceDeviceId = context.sourceDeviceId,
            version = initialVersion,
        )
        val changeRecord = ChangeRecord(
            id = ChangeRecordId(idGenerator.generate()),
            householdId = context.householdId,
            entityType = ChangeEntityType.ITEM,
            entityId = itemId.value,
            operation = ChangeOperation.CREATE,
            entityVersion = initialVersion,
            sourceDeviceId = context.sourceDeviceId,
            occurredAt = now,
        )
        val searchContent = ItemSearchContent(
            itemId = itemId,
            name = name,
            aliasesText = aliasNames.joinToString(" "),
            categoryText = selectedCategory?.name.orEmpty(),
            noteText = note.orEmpty(),
            locationPathText = selectedLocation.displayPath,
        )
        val photos = request.photos.mapIndexed { index, importedPhoto ->
            toPhotoAsset(
                importedPhoto = importedPhoto,
                item = item,
                sortOrder = index,
                now = now,
            )
        }

        repository.create(
            ManualItemCreation(
                item = item,
                photos = photos,
                initialLocationEvent = locationEvent,
                changeRecord = changeRecord,
                searchContent = searchContent,
                aliases = aliases,
            ),
        )
        // 先提交数据库再转正文件，避免事务失败后留下引用不存在文件的正式记录。
        promoteImportedPhotos(request.photos, photos)
        return itemId
    }

    /**
     * 把临时导入结果转换成带正式 storageKey 的照片记录。
     *
     * 第一张有效照片作为封面，后续照片保持导入顺序。
     */
    private fun toPhotoAsset(
        importedPhoto: ImportedItemPhoto,
        item: Item,
        sortOrder: Int,
        now: UtcTimestamp,
    ): PhotoAsset {
        val photoId = PhotoAssetId(idGenerator.generate())
        val extension = mimeTypeToExtension(importedPhoto.media.mimeType)
        return PhotoAsset(
            id = photoId,
            householdId = item.householdId,
            itemId = item.id,
            role = importedPhoto.role,
            storageKey = StorageKeys.itemOriginal(item.id.value, photoId.value, extension),
            thumbnailStorageKey = StorageKeys.itemThumbnail(item.id.value, photoId.value),
            mimeType = importedPhoto.media.mimeType,
            width = importedPhoto.media.width,
            height = importedPhoto.media.height,
            sizeBytes = importedPhoto.media.sizeBytes,
            contentHash = importedPhoto.media.contentHash,
            integrityStatus = MediaIntegrityStatus.AVAILABLE,
            lastIntegrityCheckedAt = now,
            sortOrder = SortOrder(sortOrder),
            isCover = sortOrder == 0,
            createdAt = now,
            updatedAt = now,
            version = item.version,
            sourceDeviceId = item.sourceDeviceId,
        )
    }

    /**
     * 数据库成功后把临时原图和缩略图移动到正式标识。
     *
     * 转正失败时仍保留照片记录，界面按缺失文件回退占位图，避免回滚已对用户可见的物品。
     */
    private suspend fun promoteImportedPhotos(
        importedPhotos: List<ImportedItemPhoto>,
        photos: List<PhotoAsset>,
    ) {
        if (importedPhotos.isEmpty()) {
            return
        }
        require(importedPhotos.size == photos.size) {
            "Imported photos and persisted photo records must align."
        }
        val promotions = importedPhotos.flatMapIndexed { index, importedPhoto ->
            val photo = photos[index]
            listOf(
                MediaFilePromotion(importedPhoto.media.tempStorageKey, photo.storageKey),
                MediaFilePromotion(
                    importedPhoto.media.thumbnailTempStorageKey,
                    photo.thumbnailStorageKey,
                ),
            )
        }
        try {
            mediaFileStore.promote(promotions)
        } catch (_: Exception) {
            // Intentionally keep the item after a failed file promotion.
        }
    }

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

    private companion object {
        const val INITIAL_ENTITY_VERSION = 1L
    }
}
