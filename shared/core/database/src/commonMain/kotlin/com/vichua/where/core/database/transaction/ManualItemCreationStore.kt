package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType

/**
 * 数据库层提供的物品可选位置。
 *
 * @property locationId 位置节点 ID。
 * @property displayPath 当前完整位置路径。
 */
data class StoredItemCreationLocation(
    val locationId: LocationNodeId,
    val displayPath: String,
)

/**
 * 数据库层提供的物品创建上下文。
 *
 * @property householdId 当前家庭 ID。
 * @property sourceDeviceId 当前有效设备 ID。
 * @property availableLocations 未删除非根位置。
 */
data class StoredItemCreationContext(
    val householdId: HouseholdId,
    val sourceDeviceId: DeviceId,
    val availableLocations: List<StoredItemCreationLocation>,
)

/**
 * 聚合物品创建上下文，并复用物品原子写入 Store 完成基础手动录入。
 */
class ManualItemCreationStore(
    private val database: WhereDatabase,
) {
    private val itemWriteStore = ItemWriteStore(database)

    /**
     * 加载当前家庭、当前设备和可供物品选择的位置。
     */
    suspend fun loadContext(): StoredItemCreationContext {
        val household = requireNotNull(database.householdDao().findFirstActive()) {
            "Cannot create an item without an active household."
        }
        val device = requireNotNull(
            database.deviceDao().findFirstActiveByHousehold(household.id),
        ) {
            "Cannot create an item without an active source device."
        }
        val activeLocations = database.locationNodeDao()
            .findActiveTree(household.id)
            .map { entity -> entity.toDomain() }
        val locationsById = activeLocations.associateBy(LocationNode::id)
        val availableLocations = activeLocations
            .filter { location -> location.type != LocationType.HOME }
            .map { location ->
                StoredItemCreationLocation(
                    locationId = location.id,
                    displayPath = buildLocationPath(location, locationsById),
                )
            }
            .sortedBy(StoredItemCreationLocation::displayPath)

        return StoredItemCreationContext(
            householdId = HouseholdId(household.id),
            sourceDeviceId = DeviceId(device.id),
            availableLocations = availableLocations,
        )
    }

    /**
     * 原子保存基础手动物品及关联历史、变更记录和搜索索引。
     */
    suspend fun create(
        item: Item,
        locationEvent: ItemLocationEvent,
        changeRecord: ChangeRecord,
        searchDocument: ItemSearchDocument,
    ) {
        itemWriteStore.createItem(
            item = item,
            aliases = emptyList(),
            photos = emptyList(),
            initialLocationEvent = locationEvent,
            changeRecord = changeRecord,
            searchDocument = searchDocument,
        )
    }

    /**
     * 从当前位置沿父链生成不包含家庭根节点的显示路径。
     */
    private fun buildLocationPath(
        startLocation: LocationNode,
        locationsById: Map<LocationNodeId, LocationNode>,
    ): String {
        val names = mutableListOf<String>()
        val visitedIds = mutableSetOf<LocationNodeId>()
        var currentLocation: LocationNode? = startLocation

        while (currentLocation != null && visitedIds.add(currentLocation.id)) {
            if (currentLocation.type != LocationType.HOME) {
                names += currentLocation.name
            }
            currentLocation = currentLocation.parentId?.let(locationsById::get)
        }
        return names.asReversed().joinToString(PATH_SEPARATOR)
    }

    private companion object {
        const val PATH_SEPARATOR = " · "
    }
}
