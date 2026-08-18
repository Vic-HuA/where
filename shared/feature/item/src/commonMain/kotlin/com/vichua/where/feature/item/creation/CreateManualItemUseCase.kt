package com.vichua.where.feature.item.creation

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.TextNormalizer
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.ItemLocationEventId
import com.vichua.where.core.model.ItemLocationReason
import com.vichua.where.core.model.ItemStatus
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.UtcTimestamp

/**
 * 新增物品页面可选择的位置。
 *
 * @property locationId 位置节点 ID。
 * @property displayPath 不包含家庭根节点的完整显示路径。
 */
data class ItemCreationLocation(
    val locationId: LocationNodeId,
    val displayPath: String,
)

/**
 * 创建物品所需的当前家庭上下文。
 *
 * @property householdId 当前未删除家庭 ID。
 * @property sourceDeviceId 当前有效设备 ID。
 * @property availableLocations 可供物品选择的未删除非根位置。
 */
data class ItemCreationContext(
    val householdId: HouseholdId,
    val sourceDeviceId: DeviceId,
    val availableLocations: List<ItemCreationLocation>,
)

/**
 * 手动创建物品的用户确认输入。
 *
 * @property name 物品名称。
 * @property locationId 用户选择的位置 ID。
 * @property locationDescription 可选位置补充说明。
 * @property note 可选备注。
 */
data class CreateManualItemRequest(
    val name: String,
    val locationId: LocationNodeId,
    val locationDescription: String? = null,
    val note: String? = null,
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
 * @property initialLocationEvent 首次位置历史事件。
 * @property changeRecord 物品创建变更记录。
 * @property searchContent 全文索引内容。
 */
data class ManualItemCreation(
    val item: Item,
    val initialLocationEvent: ItemLocationEvent,
    val changeRecord: ChangeRecord,
    val searchContent: ItemSearchContent,
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
 * 创建不依赖相机、语音或 AI 的基础物品记录。
 */
class CreateManualItemUseCase(
    private val repository: ManualItemCreationRepository,
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
        val now = UtcTimestamp(clock.now())
        val initialVersion = EntityVersion(INITIAL_ENTITY_VERSION)
        val itemId = ItemId(idGenerator.generate())
        val item = Item(
            id = itemId,
            householdId = context.householdId,
            currentLocationId = selectedLocation.locationId,
            name = name,
            normalizedName = normalizedName,
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
            aliasesText = "",
            categoryText = "",
            noteText = note.orEmpty(),
            locationPathText = selectedLocation.displayPath,
        )

        repository.create(
            ManualItemCreation(
                item = item,
                initialLocationEvent = locationEvent,
                changeRecord = changeRecord,
                searchContent = searchContent,
            ),
        )
        return itemId
    }

    private companion object {
        const val INITIAL_ENTITY_VERSION = 1L
    }
}
