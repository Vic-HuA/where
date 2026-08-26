package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toDomain
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.database.model.ItemSearchDocument
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.DomainValidators
import com.vichua.where.core.model.FavoriteLocation
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.ItemLocationReason
import com.vichua.where.core.model.ItemStatus
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationPhotoAsset
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.VoiceLabelAsset

/**
 * 数据库层提供的位置树展示节点。
 *
 * @property location 未删除位置领域模型。
 * @property displayPath 从家庭根节点开始的完整路径。
 * @property depth 相对家庭根节点的展示深度。
 * @property childCount 未删除直接子节点数量。
 * @property itemCount 该节点及其后代上的未删除物品数量。
 * @property directItemCount 直接放在该节点上的未删除物品数量。
 * @property coverThumbnailStorageKey 代表照缩略图标识，没有照片时为空。
 */
data class StoredLocationTreeNode(
    val location: LocationNode,
    val displayPath: String,
    val depth: Int,
    val childCount: Int,
    val itemCount: Int,
    val directItemCount: Int,
    val coverThumbnailStorageKey: String? = null,
    val hasVoiceLabel: Boolean = false,
    val voiceLabelStorageKey: String? = null,
)

/**
 * 数据库层提供的位置管理快照。
 *
 * @property householdId 当前家庭。
 * @property sourceDeviceId 当前有效设备。
 * @property householdName 家庭名称。
 * @property rootLocationId 家庭根位置。
 * @property roomCount 未删除房间数量。
 * @property itemCount 未删除物品总数。
 * @property locationUnconfirmedCount 位置待确认物品数量。
 * @property locations 当前未删除位置。
 * @property favoriteLocations 仍有效的常用位置引用。
 * @property nodes 按父节点顺序展开的展示节点。
 */
data class StoredLocationTreeSnapshot(
    val householdId: HouseholdId,
    val sourceDeviceId: DeviceId,
    val householdName: String,
    val rootLocationId: LocationNodeId,
    val roomCount: Int,
    val itemCount: Long,
    val locationUnconfirmedCount: Long,
    val locations: List<LocationNode>,
    val favoriteLocations: List<FavoriteLocation>,
    val nodes: List<StoredLocationTreeNode>,
)

/**
 * 聚合位置树查询，并在同一事务中维护位置节点、常用位置引用和搜索路径。
 */
class LocationManagementStore(
    database: WhereDatabase,
) {
    private val database = database
    private val transactionRunner = DatabaseTransactionRunner(database)

    /**
     * 加载当前家庭的位置树、物品统计和常用位置引用。
     */
    suspend fun loadTree(): StoredLocationTreeSnapshot {
        val household = requireNotNull(database.householdDao().findFirstActive()) {
            "Cannot manage locations without an active household."
        }
        val device = requireNotNull(
            database.deviceDao().findFirstActiveByHousehold(household.id),
        ) {
            "Cannot manage locations without an active source device."
        }
        val locations = database.locationNodeDao()
            .findActiveTree(household.id)
            .map { entity -> entity.toDomain() }
        DomainValidators.validateLocationTree(
            householdId = HouseholdId(household.id),
            locations = locations,
        )
        val locationsById = locations.associateBy(LocationNode::id)
        val childrenByParent = locations
            .filterNot(LocationNode::isHouseholdRoot)
            .groupBy { location -> location.parentId }
        val items = database.itemDao().findActiveByHousehold(household.id)
        val directItemCountByLocation = items
            .groupingBy { item -> LocationNodeId(item.currentLocationId) }
            .eachCount()
        val descendantIdsByLocation = locations.associate { location ->
            location.id to descendantIds(location.id, childrenByParent)
        }
        val root = requireNotNull(
            locations.singleOrNull(LocationNode::isHouseholdRoot),
        ) {
            "Active household must contain exactly one home root."
        }
        val locationIds = locations.map { location -> location.id.value }
        val coverThumbnailByLocationId = if (locationIds.isEmpty()) {
            emptyMap()
        } else {
            database.locationPhotoAssetDao()
                .findActiveCovers(locationIds)
                .associate { photo -> photo.locationNodeId to photo.thumbnailStorageKey }
        }
        val voiceLabelByLocationId = if (locationIds.isEmpty()) {
            emptyMap()
        } else {
            database.voiceLabelAssetDao()
                .findActiveByLocations(locationIds)
                .mapNotNull { label ->
                    val locationId = label.locationNodeId ?: return@mapNotNull null
                    locationId to label.storageKey
                }
                .toMap()
        }
        val nodes = mutableListOf<StoredLocationTreeNode>()
        appendNodes(
            current = root,
            depth = 0,
            locationsById = locationsById,
            childrenByParent = childrenByParent,
            descendantIdsByLocation = descendantIdsByLocation,
            directItemCountByLocation = directItemCountByLocation,
            coverThumbnailByLocationId = coverThumbnailByLocationId,
            voiceLabelByLocationId = voiceLabelByLocationId,
            nodes = nodes,
        )

        return StoredLocationTreeSnapshot(
            householdId = HouseholdId(household.id),
            sourceDeviceId = DeviceId(device.id),
            householdName = household.name,
            rootLocationId = root.id,
            roomCount = locations.count { location -> location.type == LocationType.ROOM },
            itemCount = items.size.toLong(),
            locationUnconfirmedCount = database.itemDao()
                .countLocationUnconfirmed(household.id),
            locations = locations,
            favoriteLocations = database.homeSupportDao()
                .findAllActiveFavoriteLocations(household.id)
                .map { entity -> entity.toDomain() },
            nodes = nodes,
        )
    }

    /**
     * 固定一个仍有效的常用位置引用。
     */
    suspend fun pinFavorite(
        favorite: FavoriteLocation,
        changeRecord: ChangeRecord,
    ) {
        require(favorite.deletedAt == null) { "Pinned favorite location must be active." }
        require(changeRecord.entityType == ChangeEntityType.FAVORITE_LOCATION) {
            "Favorite location change record entity type must be FAVORITE_LOCATION."
        }
        transactionRunner.write {
            val location = locationNodeDao().findById(favorite.locationNodeId.value)?.toDomain()
            require(location != null && location.deletedAt == null) {
                "Favorite location target does not exist."
            }
            require(!location.isHouseholdRoot) { "Household root cannot be a favorite location." }
            require(
                homeSupportDao().findActiveFavoriteByLocation(favorite.locationNodeId.value) == null,
            ) {
                "Location is already a favorite."
            }
            homeSupportDao().insertFavoriteLocations(listOf(favorite.toEntity()))
            changeRecordDao().insert(changeRecord.toEntity())
        }
    }

    /**
     * 取消固定常用位置。
     */
    suspend fun unpinFavorite(
        favorite: FavoriteLocation,
        changeRecord: ChangeRecord,
    ) {
        require(favorite.deletedAt != null) { "Unpinned favorite location must be soft-deleted." }
        require(changeRecord.entityType == ChangeEntityType.FAVORITE_LOCATION) {
            "Favorite location change record entity type must be FAVORITE_LOCATION."
        }
        transactionRunner.write {
            require(homeSupportDao().updateFavoriteLocation(favorite.toEntity()) == 1) {
                "Favorite location update must affect exactly one row."
            }
            changeRecordDao().insert(changeRecord.toEntity())
        }
    }

    /**
     * 原子保存新位置和对应变更记录。
     */
    suspend fun create(
        location: LocationNode,
        changeRecord: ChangeRecord,
    ) {
        require(location.deletedAt == null) { "Created location must be active." }
        require(changeRecord.entityType == ChangeEntityType.LOCATION_NODE) {
            "Created location change record entity type must be LOCATION_NODE."
        }
        require(changeRecord.operation == ChangeOperation.CREATE) {
            "Created location change record operation must be CREATE."
        }
        require(changeRecord.entityId == location.id.value) {
            "Created location change record must reference the new location."
        }

        transactionRunner.write {
            val existingLocations = locationNodeDao()
                .findActiveTree(location.householdId.value)
                .map { entity -> entity.toDomain() }
            DomainValidators.validateLocationTree(
                householdId = location.householdId,
                locations = existingLocations + location,
            )
            locationNodeDao().insert(location.toEntity())
            changeRecordDao().insert(changeRecord.toEntity())
        }
    }

    /**
     * 原子保存重命名后的位置，并重建受影响物品的搜索路径。
     *
     * 路径缓存不是事实来源；名称变化后必须在同一事务中按最新位置树重写 FTS。
     */
    suspend fun rename(
        location: LocationNode,
        changeRecord: ChangeRecord,
    ) {
        require(location.deletedAt == null) { "Renamed location must remain active." }
        require(!location.isHouseholdRoot) {
            "Household root cannot be renamed by a regular location operation."
        }
        require(changeRecord.entityType == ChangeEntityType.LOCATION_NODE) {
            "Renamed location change record entity type must be LOCATION_NODE."
        }
        require(changeRecord.operation == ChangeOperation.UPDATE) {
            "Renamed location change record operation must be UPDATE."
        }
        require(changeRecord.entityId == location.id.value) {
            "Renamed location change record must reference the updated location."
        }
        require(changeRecord.entityVersion == location.version) {
            "Renamed location change record version must match the location."
        }

        transactionRunner.write {
            val storedEntity = locationNodeDao().findById(location.id.value)
            require(storedEntity != null && storedEntity.deletedAt == null) {
                "Renamed location does not exist."
            }
            val storedLocation = storedEntity.toDomain()
            require(location.version == storedLocation.version.next()) {
                "Renamed location version must increment the stored version by one."
            }

            val existingLocations = locationNodeDao()
                .findActiveTree(location.householdId.value)
                .map { entity -> entity.toDomain() }
            val updatedLocations = existingLocations.map { current ->
                if (current.id == location.id) location else current
            }
            DomainValidators.validateLocationTree(
                householdId = location.householdId,
                locations = updatedLocations,
            )
            require(locationNodeDao().update(location.toEntity()) == 1) {
                "Renamed location update must affect exactly one row."
            }
            changeRecordDao().insert(changeRecord.toEntity())
            rebuildAffectedSearchDocuments(
                renamedLocationId = location.id,
                locations = updatedLocations,
            )
        }
    }

    /**
     * 加载直接放在指定位置上的未删除物品。
     */
    suspend fun findActiveItemsAt(locationId: LocationNodeId): List<Item> =
        database.itemDao()
            .findActiveAtLocation(locationId.value)
            .map { entity -> entity.toDomain() }

    /**
     * 加载指定位置当前未删除代表照。
     */
    suspend fun findActivePhotosAt(locationId: LocationNodeId): List<LocationPhotoAsset> =
        database.locationPhotoAssetDao()
            .findActiveByLocation(locationId.value)
            .map { entity -> entity.toDomain() }

    /**
     * 加载指定位置当前未删除语音名称。
     */
    suspend fun findActiveVoiceLabelsAt(locationId: LocationNodeId): List<VoiceLabelAsset> =
        database.voiceLabelAssetDao()
            .findActiveByLocations(listOf(locationId.value))
            .map { entity -> entity.toDomain() }

    /**
     * 原子软删除叶子位置；其上物品回退到家庭根并标为待确认。
     */
    suspend fun delete(
        location: LocationNode,
        favoriteLocation: FavoriteLocation?,
        changeRecords: List<ChangeRecord>,
        displacedItems: List<Item> = emptyList(),
        locationEvents: List<ItemLocationEvent> = emptyList(),
        retiredPhotos: List<LocationPhotoAsset> = emptyList(),
        retiredVoiceLabels: List<VoiceLabelAsset> = emptyList(),
    ) {
        require(location.deletedAt != null) { "Deleted location must be soft-deleted." }
        require(!location.isHouseholdRoot) { "Household root cannot be deleted." }
        require(changeRecords.any { record ->
            record.entityType == ChangeEntityType.LOCATION_NODE &&
                record.entityId == location.id.value &&
                record.operation == ChangeOperation.DELETE
        }) {
            "Deleted location must include a LOCATION_NODE DELETE change record."
        }
        if (favoriteLocation != null) {
            require(favoriteLocation.locationNodeId == location.id) {
                "Deleted favorite must reference the deleted location."
            }
            require(favoriteLocation.deletedAt != null) {
                "Deleted favorite must be soft-deleted."
            }
        }
        require(displacedItems.size == locationEvents.size) {
            "Each displaced item must have one location-deleted event."
        }

        transactionRunner.write {
            val storedEntity = locationNodeDao().findById(location.id.value)
            require(storedEntity != null && storedEntity.deletedAt == null) {
                "Deleted location does not exist."
            }
            val storedLocation = storedEntity.toDomain()
            require(location.version == storedLocation.version.next()) {
                "Deleted location version must increment the stored version by one."
            }
            require(locationNodeDao().countActiveChildren(location.id.value) == 0L) {
                "Location with active children cannot be deleted."
            }

            val remainingLocations = locationNodeDao()
                .findActiveTree(location.householdId.value)
                .map { entity -> entity.toDomain() }
                .map { current ->
                    if (current.id == location.id) location else current
                }
            DomainValidators.validateLocationTree(
                householdId = location.householdId,
                locations = remainingLocations,
            )
            val activeLocations = remainingLocations.filter { current ->
                current.deletedAt == null
            }
            val root = requireNotNull(
                activeLocations.singleOrNull(LocationNode::isHouseholdRoot),
            ) {
                "Active household must contain exactly one home root."
            }
            val storedItems = itemDao()
                .findActiveAtLocation(location.id.value)
                .map { entity -> entity.toDomain() }
            val storedItemsById = storedItems.associateBy(Item::id)
            require(displacedItems.map(Item::id).toSet() == storedItemsById.keys) {
                "Displaced items must match the items stored at the deleted location."
            }
            displacedItems.forEach { updatedItem ->
                val storedItem = requireNotNull(storedItemsById[updatedItem.id]) {
                    "Displaced item does not exist."
                }
                require(updatedItem.deletedAt == null) {
                    "Displaced item must remain active."
                }
                require(updatedItem.status == ItemStatus.LOCATION_UNCONFIRMED) {
                    "Displaced item must be marked location-unconfirmed."
                }
                require(updatedItem.currentLocationId == root.id) {
                    "Displaced item must fall back to the household root."
                }
                require(updatedItem.version == storedItem.version.next()) {
                    "Displaced item version must increment the stored version by one."
                }
                DomainValidators.validateItemLocation(updatedItem, activeLocations)
            }
            locationEvents.forEach { event ->
                val updatedItem = requireNotNull(
                    displacedItems.singleOrNull { item -> item.id == event.itemId },
                ) {
                    "Location-deleted event must belong to a displaced item."
                }
                require(event.reason == ItemLocationReason.LOCATION_DELETED) {
                    "Displaced item event reason must be LOCATION_DELETED."
                }
                require(event.fromLocationId == location.id) {
                    "Location-deleted event source must be the deleted location."
                }
                require(event.toLocationId == updatedItem.currentLocationId) {
                    "Location-deleted event target must match the displaced item."
                }
            }

            require(locationNodeDao().update(location.toEntity()) == 1) {
                "Deleted location update must affect exactly one row."
            }
            if (favoriteLocation != null) {
                require(
                    homeSupportDao().updateFavoriteLocation(favoriteLocation.toEntity()) == 1,
                ) {
                    "Deleted favorite update must affect exactly one row."
                }
            }
            displacedItems.forEach { updatedItem ->
                require(itemDao().update(updatedItem.toEntity()) == 1) {
                    "Displaced item update must affect exactly one row."
                }
            }
            locationEvents.forEach { event ->
                itemLocationEventDao().insert(event.toEntity())
            }
            retiredPhotos.forEach { photo ->
                require(photo.locationNodeId == location.id) {
                    "Retired location photo must belong to the deleted location."
                }
                require(photo.deletedAt != null) {
                    "Retired location photo must be soft-deleted."
                }
                require(locationPhotoAssetDao().update(photo.toEntity()) == 1) {
                    "Retired location photo update must affect exactly one row."
                }
            }
            retiredVoiceLabels.forEach { label ->
                require(label.locationNodeId == location.id) {
                    "Retired voice label must belong to the deleted location."
                }
                require(label.deletedAt != null) {
                    "Retired voice label must be soft-deleted."
                }
                require(voiceLabelAssetDao().update(label.toEntity()) == 1) {
                    "Retired voice label update must affect exactly one row."
                }
            }
            rebuildDisplacedSearchDocuments(displacedItems = displacedItems)
            changeRecordDao().insertAll(changeRecords.map(ChangeRecord::toEntity))
        }
    }

    /**
     * 按父节点顺序递归生成展示节点。
     */
    private fun appendNodes(
        current: LocationNode,
        depth: Int,
        locationsById: Map<LocationNodeId, LocationNode>,
        childrenByParent: Map<LocationNodeId?, List<LocationNode>>,
        descendantIdsByLocation: Map<LocationNodeId, Set<LocationNodeId>>,
        directItemCountByLocation: Map<LocationNodeId, Int>,
        coverThumbnailByLocationId: Map<String, String>,
        voiceLabelByLocationId: Map<String, String>,
        nodes: MutableList<StoredLocationTreeNode>,
    ) {
        val children = childrenByParent[current.id].orEmpty()
            .sortedWith(compareBy(LocationNode::sortOrder, { location -> location.id.value }))
        val descendantIds = descendantIdsByLocation[current.id].orEmpty()
        val itemCount = descendantIds.sumOf { locationId ->
            directItemCountByLocation[locationId] ?: 0
        }
        val directItemCount = directItemCountByLocation[current.id] ?: 0
        nodes += StoredLocationTreeNode(
            location = current,
            displayPath = buildLocationPath(current.id, locationsById),
            depth = depth,
            childCount = children.size,
            itemCount = itemCount,
            directItemCount = directItemCount,
            coverThumbnailStorageKey = coverThumbnailByLocationId[current.id.value],
            hasVoiceLabel = voiceLabelByLocationId.containsKey(current.id.value),
            voiceLabelStorageKey = voiceLabelByLocationId[current.id.value],
        )
        children.forEach { child ->
            appendNodes(
                current = child,
                depth = depth + 1,
                locationsById = locationsById,
                childrenByParent = childrenByParent,
                descendantIdsByLocation = descendantIdsByLocation,
                directItemCountByLocation = directItemCountByLocation,
                coverThumbnailByLocationId = coverThumbnailByLocationId,
                voiceLabelByLocationId = voiceLabelByLocationId,
                nodes = nodes,
            )
        }
    }

    /**
     * 收集指定节点及其全部未删除后代，用于统计物品数量。
     */
    private fun descendantIds(
        rootId: LocationNodeId,
        childrenByParent: Map<LocationNodeId?, List<LocationNode>>,
    ): Set<LocationNodeId> {
        val collectedIds = mutableSetOf(rootId)
        val pendingIds = ArrayDeque(listOf(rootId))
        while (pendingIds.isNotEmpty()) {
            val currentId = pendingIds.removeFirst()
            childrenByParent[currentId].orEmpty().forEach { child ->
                if (collectedIds.add(child.id)) {
                    pendingIds.addLast(child.id)
                }
            }
        }
        return collectedIds
    }

    /**
     * 位置删除后按待确认路径重写被回退物品的搜索文档。
     */
    private suspend fun WhereDatabase.rebuildDisplacedSearchDocuments(
        displacedItems: List<Item>,
    ) {
        displacedItems.forEach { item ->
            val aliasesText = itemAliasDao()
                .findActiveByItem(item.id.value)
                .joinToString(" ") { alias -> alias.alias }
            val categoryText = item.categoryId
                ?.let { categoryId -> categoryDao().findActiveById(categoryId.value)?.name }
                .orEmpty()
            itemSearchDao().deleteByItemId(item.id.value)
            itemSearchDao().insert(
                ItemSearchDocument(
                    itemId = item.id,
                    name = item.name,
                    aliasesText = aliasesText,
                    categoryText = categoryText,
                    noteText = item.note.orEmpty(),
                    locationPathText = UNKNOWN_LOCATION_TEXT,
                ).toEntity(),
            )
        }
    }

    /**
     * 重建名称变化后仍位于该节点或后代上的物品搜索路径。
     */
    private suspend fun WhereDatabase.rebuildAffectedSearchDocuments(
        renamedLocationId: LocationNodeId,
        locations: List<LocationNode>,
    ) {
        val locationsById = locations.associateBy(LocationNode::id)
        val childrenByParent = locations
            .filterNot(LocationNode::isHouseholdRoot)
            .groupBy { location -> location.parentId }
        val affectedLocationIds = descendantIds(renamedLocationId, childrenByParent)
        val affectedItems = itemDao()
            .findActiveByHousehold(locations.first().householdId.value)
            .map { entity -> entity.toDomain() }
            .filter { item -> item.currentLocationId in affectedLocationIds }

        affectedItems.forEach { item ->
            val aliasesText = itemAliasDao()
                .findActiveByItem(item.id.value)
                .joinToString(" ") { alias -> alias.alias }
            val categoryText = item.categoryId
                ?.let { categoryId -> categoryDao().findActiveById(categoryId.value)?.name }
                .orEmpty()
            val locationPathText = buildLocationPath(item.currentLocationId, locationsById)
            itemSearchDao().deleteByItemId(item.id.value)
            itemSearchDao().insert(
                ItemSearchDocument(
                    itemId = item.id,
                    name = item.name,
                    aliasesText = aliasesText,
                    categoryText = categoryText,
                    noteText = item.note.orEmpty(),
                    locationPathText = locationPathText,
                ).toEntity(),
            )
        }
    }

    /**
     * 从指定节点沿父链生成路径；家庭根节点使用家庭名称，避免页面出现空路径。
     */
    private fun buildLocationPath(
        locationId: LocationNodeId,
        locationsById: Map<LocationNodeId, LocationNode>,
    ): String {
        val pathNames = mutableListOf<String>()
        val visitedLocationIds = mutableSetOf<LocationNodeId>()
        var currentLocation = locationsById[locationId]

        while (currentLocation != null && visitedLocationIds.add(currentLocation.id)) {
            pathNames += currentLocation.name
            currentLocation = currentLocation.parentId?.let(locationsById::get)
        }
        return pathNames.asReversed().joinToString(PATH_SEPARATOR)
            .ifBlank { UNKNOWN_LOCATION_TEXT }
    }

    private companion object {
        const val PATH_SEPARATOR = " · "
        const val UNKNOWN_LOCATION_TEXT = "位置待确认"
    }
}
