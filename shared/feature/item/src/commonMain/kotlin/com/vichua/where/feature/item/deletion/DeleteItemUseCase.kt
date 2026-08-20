package com.vichua.where.feature.item.deletion

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemAlias
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.PhotoAsset
import com.vichua.where.core.model.UtcTimestamp

/**
 * 删除或撤销物品时需要的当前上下文。
 *
 * @property item 当前物品，撤销时已经带有删除批次时间。
 * @property aliases 该物品全部别名，包含更早软删除的记录。
 * @property photos 该物品全部照片，包含更早软删除的记录。
 * @property locationPath 当前位置路径，撤销后用于重建搜索索引。
 * @property aliasesText 当前未删除别名拼接文本。
 * @property categoryText 当前分类文本。
 * @property currentDeviceId 本次修改来源设备。
 */
data class ItemDeletionContext(
    val item: Item,
    val aliases: List<ItemAlias>,
    val photos: List<PhotoAsset>,
    val locationPath: String,
    val aliasesText: String,
    val categoryText: String,
    val currentDeviceId: DeviceId,
)

/**
 * 删除后需要原子保存的聚合。
 */
data class ItemDeletionWrite(
    val deletedItem: Item,
    val deletedAliases: List<ItemAlias>,
    val deletedPhotos: List<PhotoAsset>,
    val changeRecords: List<ChangeRecord>,
)

/**
 * 撤销后需要原子保存的聚合。
 */
data class ItemRestoreWrite(
    val restoredItem: Item,
    val restoredAliases: List<ItemAlias>,
    val restoredPhotos: List<PhotoAsset>,
    val changeRecords: List<ChangeRecord>,
    val aliasesText: String,
    val categoryText: String,
    val locationPathText: String,
    val expectedDeletedAt: UtcTimestamp,
    val expectedDeletedVersion: EntityVersion,
)

/**
 * 删除成功后留给当前会话撤销条的批次信息。
 *
 * 10 秒窗口由界面倒计时，不在用例里强制过期，因为规格写的是当前会话界面窗口。
 *
 * @property itemId 被删除的根物品。
 * @property itemName 删除时的物品名称，供撤销条展示。
 * @property deletedAt 本次级联删除共用的批次时间。
 * @property deletedItemVersion 删除完成后的物品版本，撤销前用来确认记录未被再次修改。
 */
data class ItemDeletionResult(
    val itemId: ItemId,
    val itemName: String,
    val deletedAt: UtcTimestamp,
    val deletedItemVersion: EntityVersion,
)

/**
 * 物品删除与短时撤销仓储契约。
 */
interface ItemDeletionRepository {
    /** 加载指定物品及其全部别名和照片。 */
    suspend fun load(itemId: ItemId): ItemDeletionContext

    /** 原子软删除物品、本次级联记录和搜索索引。 */
    suspend fun delete(write: ItemDeletionWrite)

    /** 原子撤销本次级联软删除并重建搜索索引。 */
    suspend fun restore(write: ItemRestoreWrite)
}

/**
 * 软删除物品，并把未删除别名和照片标成同一批次。
 *
 * 语音名称和常用物品入口表尚未建立，因此本步只处理已经存在的物品、别名和照片。
 * 位置历史保持原样，物品离开正常界面后历史不会再被查询。
 */
class DeleteItemUseCase(
    private val repository: ItemDeletionRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 校验物品仍未删除后写入级联软删除。
     *
     * 更早软删除的别名和照片保持原 `deletedAt`，避免撤销时把历史删除带回来。
     */
    suspend operator fun invoke(itemId: ItemId): ItemDeletionResult {
        val context = repository.load(itemId)
        require(context.item.deletedAt == null) { "Item is already deleted." }

        val now = UtcTimestamp(clock.now())
        val deletedItem = context.item.copy(
            updatedAt = now,
            version = context.item.version.next(),
            sourceDeviceId = context.currentDeviceId,
            deletedAt = now,
        )
        val deletedAliases = context.aliases.map { alias ->
            if (alias.deletedAt == null) {
                alias.copy(deletedAt = now)
            } else {
                alias
            }
        }
        val deletedPhotos = context.photos.map { photo ->
            if (photo.deletedAt == null) {
                photo.copy(
                    updatedAt = now,
                    version = photo.version.next(),
                    sourceDeviceId = context.currentDeviceId,
                    deletedAt = now,
                )
            } else {
                photo
            }
        }
        val changeRecords = buildList {
            add(
                itemChangeRecord(
                    item = deletedItem,
                    operation = ChangeOperation.DELETE,
                    occurredAt = now,
                    recordId = idGenerator.generate(),
                ),
            )
            deletedAliases
                .filter { alias -> alias.deletedAt == now }
                .forEach { alias ->
                    add(
                        aliasChangeRecord(
                            householdId = deletedItem.householdId,
                            alias = alias,
                            operation = ChangeOperation.DELETE,
                            sourceDeviceId = deletedItem.sourceDeviceId,
                            occurredAt = now,
                            recordId = idGenerator.generate(),
                        ),
                    )
                }
            deletedPhotos
                .filter { photo -> photo.deletedAt == now }
                .forEach { photo ->
                    add(
                        photoChangeRecord(
                            photo = photo,
                            operation = ChangeOperation.DELETE,
                            occurredAt = now,
                            recordId = idGenerator.generate(),
                        ),
                    )
                }
        }
        repository.delete(
            ItemDeletionWrite(
                deletedItem = deletedItem,
                deletedAliases = deletedAliases,
                deletedPhotos = deletedPhotos,
                changeRecords = changeRecords,
            ),
        )
        return ItemDeletionResult(
            itemId = deletedItem.id,
            itemName = deletedItem.name,
            deletedAt = now,
            deletedItemVersion = deletedItem.version,
        )
    }
}

/**
 * 撤销当前会话内尚未被再次修改的物品删除批次。
 */
class RestoreDeletedItemUseCase(
    private val repository: ItemDeletionRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 只恢复 `deletedAt` 与删除批次相同的记录。
     *
     * 物品删除后的版本必须仍与撤销条保存的版本一致，避免恢复已被再次改写的数据。
     */
    suspend operator fun invoke(request: ItemDeletionResult) {
        val context = repository.load(request.itemId)
        require(context.item.deletedAt == request.deletedAt) {
            "Item restore must target the current deletion batch."
        }
        require(context.item.version == request.deletedItemVersion) {
            "Item restore must target the unmodified deleted version."
        }

        val now = UtcTimestamp(clock.now())
        val restoredItem = context.item.copy(
            updatedAt = now,
            version = context.item.version.next(),
            sourceDeviceId = context.currentDeviceId,
            deletedAt = null,
        )
        val restoredAliases = context.aliases.map { alias ->
            if (alias.deletedAt == request.deletedAt) {
                alias.copy(deletedAt = null)
            } else {
                alias
            }
        }
        val restoredPhotos = context.photos.map { photo ->
            if (photo.deletedAt == request.deletedAt) {
                photo.copy(
                    updatedAt = now,
                    version = photo.version.next(),
                    sourceDeviceId = context.currentDeviceId,
                    deletedAt = null,
                )
            } else {
                photo
            }
        }
        val changeRecords = buildList {
            add(
                itemChangeRecord(
                    item = restoredItem,
                    operation = ChangeOperation.RESTORE,
                    occurredAt = now,
                    recordId = idGenerator.generate(),
                ),
            )
            restoredAliases
                .filter { alias ->
                    context.aliases.any { stored ->
                        stored.id == alias.id && stored.deletedAt == request.deletedAt
                    }
                }
                .forEach { alias ->
                    add(
                        aliasChangeRecord(
                            householdId = restoredItem.householdId,
                            alias = alias,
                            operation = ChangeOperation.RESTORE,
                            sourceDeviceId = restoredItem.sourceDeviceId,
                            occurredAt = now,
                            recordId = idGenerator.generate(),
                        ),
                    )
                }
            restoredPhotos
                .filter { photo ->
                    context.photos.any { stored ->
                        stored.id == photo.id && stored.deletedAt == request.deletedAt
                    }
                }
                .forEach { photo ->
                    add(
                        photoChangeRecord(
                            photo = photo,
                            operation = ChangeOperation.RESTORE,
                            occurredAt = now,
                            recordId = idGenerator.generate(),
                        ),
                    )
                }
        }
        repository.restore(
            ItemRestoreWrite(
                restoredItem = restoredItem,
                restoredAliases = restoredAliases,
                restoredPhotos = restoredPhotos,
                changeRecords = changeRecords,
                aliasesText = restoredAliases
                    .filter { alias -> alias.deletedAt == null }
                    .joinToString(" ") { alias -> alias.alias },
                categoryText = context.categoryText,
                locationPathText = context.locationPath,
                expectedDeletedAt = request.deletedAt,
                expectedDeletedVersion = request.deletedItemVersion,
            ),
        )
    }
}

/**
 * 构造物品删除或恢复的变更记录。
 */
private fun itemChangeRecord(
    item: Item,
    operation: ChangeOperation,
    occurredAt: UtcTimestamp,
    recordId: String,
): ChangeRecord = ChangeRecord(
    id = ChangeRecordId(recordId),
    householdId = item.householdId,
    entityType = ChangeEntityType.ITEM,
    entityId = item.id.value,
    operation = operation,
    entityVersion = item.version,
    sourceDeviceId = item.sourceDeviceId,
    occurredAt = occurredAt,
)

/**
 * 构造别名删除或恢复的变更记录。
 *
 * 别名实体没有独立版本，变更记录使用初始版本记录本次软删除或恢复，避免伪造别名版本字段。
 */
private fun aliasChangeRecord(
    householdId: HouseholdId,
    alias: ItemAlias,
    operation: ChangeOperation,
    sourceDeviceId: DeviceId,
    occurredAt: UtcTimestamp,
    recordId: String,
): ChangeRecord = ChangeRecord(
    id = ChangeRecordId(recordId),
    householdId = householdId,
    entityType = ChangeEntityType.ITEM_ALIAS,
    entityId = alias.id.value,
    operation = operation,
    entityVersion = EntityVersion(ALIAS_CHANGE_VERSION),
    sourceDeviceId = sourceDeviceId,
    occurredAt = occurredAt,
)

/**
 * 构造照片删除或恢复的变更记录。
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

private const val ALIAS_CHANGE_VERSION = 1L
