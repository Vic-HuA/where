package com.vichua.where.feature.item.profile

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.TextNormalizer
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.UtcTimestamp

/**
 * 编辑物品档案时需要的当前上下文。
 *
 * @property item 当前未删除物品。
 * @property locationPath 当前完整位置路径，编辑档案时不改位置。
 * @property aliasesText 未删除别名，用于重建搜索索引。
 * @property categoryText 当前分类文本。
 * @property currentDeviceId 本次修改来源设备。
 */
data class ItemProfileContext(
    val item: Item,
    val locationPath: String,
    val aliasesText: String,
    val categoryText: String,
    val currentDeviceId: DeviceId,
)

/**
 * 用户确认后的档案编辑输入。
 *
 * @property itemId 要编辑的物品。
 * @property name 新的物品名称。
 * @property locationDescription 可选位置补充说明。
 * @property note 可选备注。
 */
data class UpdateItemProfileRequest(
    val itemId: ItemId,
    val name: String,
    val locationDescription: String? = null,
    val note: String? = null,
)

/**
 * 档案编辑需要原子保存的聚合。
 */
data class ItemProfileUpdate(
    val updatedItem: Item,
    val changeRecord: ChangeRecord,
    val aliasesText: String,
    val categoryText: String,
    val locationPathText: String,
)

/**
 * 物品档案编辑仓储契约。
 */
interface ItemProfileRepository {
    /** 加载指定物品和当前设备。 */
    suspend fun load(itemId: ItemId): ItemProfileContext

    /** 原子保存档案、变更记录和搜索索引。 */
    suspend fun update(update: ItemProfileUpdate)
}

/**
 * 更新物品名称、位置说明和备注，不改当前位置。
 */
class UpdateItemProfileUseCase(
    private val repository: ItemProfileRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
    private val textNormalizer: TextNormalizer,
) {
    /**
     * 校验输入后写入新版本档案。
     *
     * 名称、位置说明和备注都没有变化时拒绝保存，避免产生空变更记录。
     */
    suspend operator fun invoke(request: UpdateItemProfileRequest) {
        val context = repository.load(request.itemId)
        val name = request.name.trim()
        val normalizedName = textNormalizer.normalize(name)
        val locationDescription = request.locationDescription
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val note = request.note
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        require(name.isNotEmpty()) { "Item name must not be blank." }
        require(normalizedName.isNotEmpty()) { "Normalized item name must not be blank." }
        require(
            name != context.item.name ||
                locationDescription != context.item.locationDescription ||
                note != context.item.note,
        ) {
            "Item profile update must change at least one visible field."
        }

        val now = UtcTimestamp(clock.now())
        val updatedItem = context.item.copy(
            name = name,
            normalizedName = normalizedName,
            locationDescription = locationDescription,
            note = note,
            updatedAt = now,
            version = context.item.version.next(),
            sourceDeviceId = context.currentDeviceId,
        )
        repository.update(
            ItemProfileUpdate(
                updatedItem = updatedItem,
                changeRecord = ChangeRecord(
                    id = ChangeRecordId(idGenerator.generate()),
                    householdId = updatedItem.householdId,
                    entityType = ChangeEntityType.ITEM,
                    entityId = updatedItem.id.value,
                    operation = ChangeOperation.UPDATE,
                    entityVersion = updatedItem.version,
                    sourceDeviceId = updatedItem.sourceDeviceId,
                    occurredAt = now,
                ),
                aliasesText = context.aliasesText,
                categoryText = context.categoryText,
                locationPathText = context.locationPath,
            ),
        )
    }
}
