package com.vichua.where.feature.item.profile

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.TextNormalizer
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.CategoryId
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemAlias
import com.vichua.where.core.model.ItemAliasId
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
 * @property aliases 当前未删除别名。
 * @property categories 可供选择的分类。
 */
data class ItemProfileContext(
    val item: Item,
    val locationPath: String,
    val aliasesText: String,
    val categoryText: String,
    val currentDeviceId: DeviceId,
    val aliases: List<String> = emptyList(),
    val categories: List<ItemCategoryOption> = emptyList(),
)

/**
 * 录入和编辑共用的分类选项。
 */
data class ItemCategoryOption(
    val categoryId: CategoryId,
    val name: String,
)

/**
 * 把用户输入的别名拆成去重后的可见名称。
 *
 * 用逗号、顿号或换行分隔；标准化后相同的别名只保留第一次出现的原文。
 */
fun parseItemAliasInputs(
    rawAliases: Collection<String>,
    textNormalizer: TextNormalizer,
): List<String> {
    val seenNormalized = mutableSetOf<String>()
    return rawAliases
        .flatMap { raw -> raw.split(ALIAS_SEPARATOR_REGEX) }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filter { alias ->
            val normalized = textNormalizer.normalize(alias)
            normalized.isNotEmpty() && seenNormalized.add(normalized)
        }
}

private val ALIAS_SEPARATOR_REGEX = Regex("[,，;；\\n]+")

/**
 * 详情编辑对话框确认后的可见字段，物品 ID 由页面补上。
 *
 * @property name 新的物品名称。
 * @property locationDescription 可选位置补充说明。
 * @property note 可选备注。
 * @property aliases 用户确认后的别名原文。
 * @property categoryId 可选分类；为空表示不分类。
 * @property quantity 大于 0 的数量。
 * @property unit 可选数量单位。
 */
data class ItemProfileEdits(
    val name: String,
    val locationDescription: String? = null,
    val note: String? = null,
    val aliases: List<String> = emptyList(),
    val categoryId: CategoryId? = null,
    val quantity: Double = 1.0,
    val unit: String? = null,
)

data class UpdateItemProfileRequest(
    val itemId: ItemId,
    val name: String,
    val locationDescription: String? = null,
    val note: String? = null,
    val aliases: List<String> = emptyList(),
    val categoryId: CategoryId? = null,
    val quantity: Double = 1.0,
    val unit: String? = null,
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
    val aliases: List<ItemAlias> = emptyList(),
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
        val unit = request.unit
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        val selectedCategory = request.categoryId?.let { categoryId ->
            context.categories.singleOrNull { option -> option.categoryId == categoryId }
        }
        require(name.isNotEmpty()) { "Item name must not be blank." }
        require(normalizedName.isNotEmpty()) { "Normalized item name must not be blank." }
        require(request.categoryId == null || selectedCategory != null) {
            "Selected item category is unavailable."
        }
        require(request.quantity.isFinite() && request.quantity > 0.0) {
            "Item quantity must be finite and greater than zero."
        }
        val aliasNames = parseItemAliasInputs(request.aliases, textNormalizer)
        require(
            name != context.item.name ||
                locationDescription != context.item.locationDescription ||
                note != context.item.note ||
                aliasNames != context.aliases ||
                request.categoryId != context.item.categoryId ||
                request.quantity != context.item.quantity ||
                unit != context.item.unit,
        ) {
            "Item profile update must change at least one visible field."
        }

        val now = UtcTimestamp(clock.now())
        val aliases = aliasNames.map { aliasName ->
            ItemAlias(
                id = ItemAliasId(idGenerator.generate()),
                itemId = context.item.id,
                alias = aliasName,
                normalizedAlias = textNormalizer.normalize(aliasName),
                createdAt = now,
            )
        }
        val updatedItem = context.item.copy(
            name = name,
            normalizedName = normalizedName,
            categoryId = selectedCategory?.categoryId,
            quantity = request.quantity,
            unit = unit,
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
                aliasesText = aliasNames.joinToString(" "),
                categoryText = selectedCategory?.name.orEmpty(),
                locationPathText = context.locationPath,
                aliases = aliases,
            ),
        )
    }
}
