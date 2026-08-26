package com.vichua.where.feature.location.management

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.TextNormalizer
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.DomainValidators
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.FavoriteLocation
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.ItemLocationEventId
import com.vichua.where.core.model.ItemLocationReason
import com.vichua.where.core.model.ItemStatus
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp

/**
 * 位置管理页展示的单个节点。
 *
 * @property locationId 位置节点 ID。
 * @property parentId 父节点 ID；家庭根节点为空。
 * @property type 位置语义类型。
 * @property name 用户可见名称。
 * @property iconKey 受控图标键，没有指定时为空。
 * @property displayPath 不包含家庭根节点的完整路径。
 * @property depth 相对家庭根节点的展示深度，根节点为 0。
 * @property childCount 未删除直接子节点数量。
 * @property itemCount 该节点及其后代上的未删除物品数量。
 * @property canRename 家庭根节点不允许普通重命名。
 * @property canDelete 家庭根和仍有子节点的位置不能删；叶子上有物品时可删，物品会标为待确认。
 * @property allowedChildTypes 当前节点允许新增的子类型。
 * @property coverThumbnailStorageKey 代表照缩略图标识，没有照片时为空。
 */
data class LocationTreeNode(
    val locationId: LocationNodeId,
    val parentId: LocationNodeId?,
    val type: LocationType,
    val name: String,
    val iconKey: String?,
    val displayPath: String,
    val depth: Int,
    val childCount: Int,
    val itemCount: Int,
    val canRename: Boolean,
    val canDelete: Boolean,
    val allowedChildTypes: List<LocationType>,
    val coverThumbnailStorageKey: String? = null,
    val hasVoiceLabel: Boolean = false,
    val voiceLabelStorageKey: String? = null,
)

/**
 * 位置管理页所需的家庭位置树快照。
 *
 * @property householdId 当前家庭。
 * @property sourceDeviceId 当前有效设备。
 * @property householdName 家庭名称。
 * @property rootLocationId 家庭根位置。
 * @property roomCount 未删除房间数量。
 * @property itemCount 未删除物品总数。
 * @property locationUnconfirmedCount 位置待确认物品数量。
 * @property locations 当前未删除位置领域模型，供写入前校验完整位置树。
 * @property favoriteLocations 仍有效的常用位置引用。
 * @property nodes 按父节点顺序展开的展示节点，包含家庭根节点。
 */
data class LocationTreeSnapshot(
    val householdId: HouseholdId,
    val sourceDeviceId: DeviceId,
    val householdName: String,
    val rootLocationId: LocationNodeId,
    val roomCount: Int,
    val itemCount: Long,
    val locationUnconfirmedCount: Long,
    val locations: List<LocationNode>,
    val favoriteLocations: List<FavoriteLocation>,
    val nodes: List<LocationTreeNode>,
)

/**
 * 在指定父位置下新增一个子位置。
 *
 * @property parentId 已存在的未删除父位置。
 * @property type 新位置类型，必须属于父节点允许的子类型。
 * @property name 用户可见名称。
 * @property iconKey 可选受控图标键。
 */
data class CreateLocationRequest(
    val parentId: LocationNodeId,
    val type: LocationType,
    val name: String,
    val iconKey: String? = null,
)

/**
 * 一次创建一条位置链里的单层。
 *
 * @property type 这一层的位置类型。
 * @property name 这一层的名称。
 */
data class CreateLocationPathSegment(
    val type: LocationType,
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "Location path segment name must not be blank." }
    }
}

/**
 * 一次创建一条位置链，例如房间、抽屉、格子。
 *
 * @property parentId 整条链的父位置，通常是家庭根或已有房间。
 * @property segments 按层级顺序填写的名称和类型，至少一层。
 */
data class CreateLocationPathRequest(
    val parentId: LocationNodeId,
    val segments: List<CreateLocationPathSegment>,
) {
    init {
        require(segments.isNotEmpty()) { "Location path must contain at least one segment." }
    }
}

/**
 * 新增位置后需要原子保存的聚合。
 */
data class LocationCreation(
    val location: LocationNode,
    val changeRecord: ChangeRecord,
)

/**
 * 重命名位置后需要原子保存的聚合。
 */
data class LocationRename(
    val location: LocationNode,
    val changeRecord: ChangeRecord,
)

/**
 * 删除叶子位置后需要原子保存的聚合。
 *
 * @property location 已标记软删除的位置。
 * @property favoriteLocation 同步取消固定的常用位置，没有引用时为空。
 * @property changeRecords 位置删除、物品待确认及可选常用位置取消对应的变更记录。
 * @property displacedItems 原位置上的物品，已回退到家庭根并标为待确认。
 * @property locationEvents 物品因位置删除产生的历史事件。
 */
data class LocationDeletion(
    val location: LocationNode,
    val favoriteLocation: FavoriteLocation?,
    val changeRecords: List<ChangeRecord>,
    val displacedItems: List<Item> = emptyList(),
    val locationEvents: List<ItemLocationEvent> = emptyList(),
)

/**
 * 位置树查询与维护的仓储契约。
 */
interface LocationManagementRepository {
    /** 加载当前家庭的位置树和统计摘要。 */
    suspend fun loadTree(): LocationTreeSnapshot

    /** 原子保存新位置和对应变更记录。 */
    suspend fun create(creation: LocationCreation)

    /** 原子保存重命名后的位置、变更记录和受影响物品搜索路径。 */
    suspend fun rename(rename: LocationRename)

    /** 原子软删除叶子位置，并把其上物品标为位置待确认。 */
    suspend fun delete(deletion: LocationDeletion)

    /** 加载直接放在指定位置上的未删除物品。 */
    suspend fun findActiveItemsAt(locationId: LocationNodeId): List<Item>
}

/**
 * 加载位置管理页所需的位置树。
 */
class LoadLocationTreeUseCase(
    private val repository: LocationManagementRepository,
) {
    /** 返回当前家庭未删除位置和统计摘要。 */
    suspend operator fun invoke(): LocationTreeSnapshot = repository.loadTree()
}

/**
 * 在现有位置下新增房间、区域、家具、容器或具体位置。
 */
class CreateLocationUseCase(
    private val repository: LocationManagementRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
    private val textNormalizer: TextNormalizer,
) {
    /**
     * 创建并保存一个新的位置节点。
     *
     * 父节点必须存在；新类型必须属于推荐层级允许的子类型；同级同类型标准化名称必须唯一。
     */
    suspend operator fun invoke(request: CreateLocationRequest): LocationNodeId {
        val snapshot = repository.loadTree()
        val parent = requireNotNull(
            snapshot.locations.singleOrNull { location ->
                location.id == request.parentId && location.deletedAt == null
            },
        ) {
            "Parent location is unavailable."
        }
        val allowedChildTypes = allowedChildTypes(parent.type)
        require(request.type in allowedChildTypes) {
            "Location type is not allowed under the selected parent."
        }

        val name = request.name.trim()
        val iconKey = request.iconKey?.trim() ?: defaultIconKey(request.type)
        require(name.isNotEmpty()) { "Location name must not be blank." }
        require(iconKey.isNotEmpty()) { "Location icon key must not be blank." }

        val normalizedName = textNormalizer.normalize(name)
        require(normalizedName.isNotEmpty()) {
            "Normalized location name must not be blank."
        }
        require(
            snapshot.locations.none { sibling ->
                sibling.deletedAt == null &&
                    sibling.parentId == parent.id &&
                    sibling.type == request.type &&
                    sibling.normalizedName == normalizedName
            },
        ) {
            "Sibling location names must be unique after normalization."
        }

        val now = UtcTimestamp(clock.now())
        val nextSortOrder = snapshot.locations
            .filter { sibling -> sibling.deletedAt == null && sibling.parentId == parent.id }
            .maxOfOrNull { sibling -> sibling.sortOrder.value }
            ?.plus(1)
            ?: 0
        val location = LocationNode(
            id = LocationNodeId(idGenerator.generate()),
            householdId = snapshot.householdId,
            parentId = parent.id,
            type = request.type,
            name = name,
            normalizedName = normalizedName,
            iconKey = iconKey,
            sortOrder = SortOrder(nextSortOrder),
            createdAt = now,
            updatedAt = now,
            version = EntityVersion(INITIAL_ENTITY_VERSION),
            sourceDeviceId = snapshot.sourceDeviceId,
        )
        DomainValidators.validateLocationTree(
            householdId = snapshot.householdId,
            locations = snapshot.locations + location,
        )
        repository.create(
            LocationCreation(
                location = location,
                changeRecord = createLocationChangeRecord(
                    idGenerator = idGenerator,
                    householdId = snapshot.householdId,
                    sourceDeviceId = snapshot.sourceDeviceId,
                    entityId = location.id.value,
                    operation = ChangeOperation.CREATE,
                    entityVersion = location.version,
                    occurredAt = now,
                ),
            ),
        )
        return location.id
    }
}

/**
 * 按填写顺序补齐一条位置链：已有同名同类型节点直接沿用，只新建缺失的层。
 *
 * 这样「书房」已经存在时，再填书房、课桌不会因为重名失败，只会在书房下补上课桌。
 */
class CreateLocationPathUseCase(
    private val repository: LocationManagementRepository,
    private val createLocationUseCase: CreateLocationUseCase,
    private val textNormalizer: TextNormalizer,
) {
    /**
     * 返回最后一层的位置 ID，方便录入后直接选中。
     */
    suspend operator fun invoke(request: CreateLocationPathRequest): LocationNodeId {
        var parentId = request.parentId
        var lastResolvedId = request.parentId
        request.segments.forEach { segment ->
            val snapshot = repository.loadTree()
            val normalizedName = textNormalizer.normalize(segment.name.trim())
            require(normalizedName.isNotEmpty()) {
                "Normalized location name must not be blank."
            }
            val existingLocation = snapshot.locations.singleOrNull { sibling ->
                sibling.deletedAt == null &&
                    sibling.parentId == parentId &&
                    sibling.type == segment.type &&
                    sibling.normalizedName == normalizedName
            }
            lastResolvedId = existingLocation?.id ?: createLocationUseCase(
                CreateLocationRequest(
                    parentId = parentId,
                    type = segment.type,
                    name = segment.name,
                ),
            )
            parentId = lastResolvedId
        }
        return lastResolvedId
    }
}

/**
 * 重命名现有非根位置，并保持同级同类型名称唯一。
 */
class RenameLocationUseCase(
    private val repository: LocationManagementRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
    private val textNormalizer: TextNormalizer,
) {
    /**
     * 更新指定位置的可见名称。
     *
     * 家庭根节点不能通过普通位置操作重命名；名称变化后由仓储重建受影响物品的搜索路径。
     */
    suspend operator fun invoke(
        locationId: LocationNodeId,
        name: String,
    ) {
        val snapshot = repository.loadTree()
        val currentLocation = requireNotNull(
            snapshot.locations.singleOrNull { location ->
                location.id == locationId && location.deletedAt == null
            },
        ) {
            "Location is unavailable."
        }
        require(!currentLocation.isHouseholdRoot) {
            "Household root cannot be renamed by a regular location operation."
        }

        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Location name must not be blank." }
        val normalizedName = textNormalizer.normalize(trimmedName)
        require(normalizedName.isNotEmpty()) {
            "Normalized location name must not be blank."
        }
        require(
            snapshot.locations.none { sibling ->
                sibling.id != currentLocation.id &&
                    sibling.deletedAt == null &&
                    sibling.parentId == currentLocation.parentId &&
                    sibling.type == currentLocation.type &&
                    sibling.normalizedName == normalizedName
            },
        ) {
            "Sibling location names must be unique after normalization."
        }

        val now = UtcTimestamp(clock.now())
        val updatedLocation = currentLocation.copy(
            name = trimmedName,
            normalizedName = normalizedName,
            updatedAt = now,
            version = currentLocation.version.next(),
            sourceDeviceId = snapshot.sourceDeviceId,
        )
        DomainValidators.validateLocationTree(
            householdId = snapshot.householdId,
            locations = snapshot.locations.map { location ->
                if (location.id == updatedLocation.id) updatedLocation else location
            },
        )
        repository.rename(
            LocationRename(
                location = updatedLocation,
                changeRecord = createLocationChangeRecord(
                    idGenerator = idGenerator,
                    householdId = snapshot.householdId,
                    sourceDeviceId = snapshot.sourceDeviceId,
                    entityId = updatedLocation.id.value,
                    operation = ChangeOperation.UPDATE,
                    entityVersion = updatedLocation.version,
                    occurredAt = now,
                ),
            ),
        )
    }
}

/**
 * 删除没有子节点的叶子位置；其上物品回退到家庭根并标为位置待确认。
 */
class DeleteEmptyLocationUseCase(
    private val repository: LocationManagementRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 软删除指定叶子位置。
     *
     * 仍有子节点的位置必须先处理下级；家庭根节点不能删除。
     * 叶子上的物品保留档案，当前位置改为家庭根，状态改为待确认。
     */
    suspend operator fun invoke(locationId: LocationNodeId) {
        val snapshot = repository.loadTree()
        val currentNode = requireNotNull(
            snapshot.nodes.singleOrNull { node -> node.locationId == locationId },
        ) {
            "Location is unavailable."
        }
        require(currentNode.canDelete) {
            "Only a non-root location without children can be deleted."
        }
        val currentLocation = requireNotNull(
            snapshot.locations.singleOrNull { location ->
                location.id == locationId && location.deletedAt == null
            },
        ) {
            "Location is unavailable."
        }

        val now = UtcTimestamp(clock.now())
        val deletedLocation = currentLocation.copy(
            updatedAt = now,
            version = currentLocation.version.next(),
            sourceDeviceId = snapshot.sourceDeviceId,
            deletedAt = now,
        )
        val deletedFavorite = snapshot.favoriteLocations
            .singleOrNull { favorite ->
                favorite.locationNodeId == deletedLocation.id && favorite.deletedAt == null
            }
            ?.let { favorite ->
                favorite.copy(
                    updatedAt = now,
                    version = favorite.version.next(),
                    sourceDeviceId = snapshot.sourceDeviceId,
                    deletedAt = now,
                )
            }
        val previousPath = currentNode.displayPath.trim().ifBlank { currentLocation.name }
        val itemsAtLocation = repository.findActiveItemsAt(locationId)
        val displacedItems = itemsAtLocation.map { item ->
            item.copy(
                currentLocationId = snapshot.rootLocationId,
                locationDescription = item.locationDescription ?: previousPath,
                status = ItemStatus.LOCATION_UNCONFIRMED,
                updatedAt = now,
                version = item.version.next(),
                sourceDeviceId = snapshot.sourceDeviceId,
            )
        }
        val locationEvents = itemsAtLocation.zip(displacedItems).map { (originalItem, displacedItem) ->
            ItemLocationEvent(
                id = ItemLocationEventId(idGenerator.generate()),
                householdId = displacedItem.householdId,
                itemId = displacedItem.id,
                fromLocationId = originalItem.currentLocationId,
                toLocationId = snapshot.rootLocationId,
                fromPathSnapshot = previousPath,
                toPathSnapshot = UNCONFIRMED_LOCATION_PATH,
                reason = ItemLocationReason.LOCATION_DELETED,
                occurredAt = now,
                sourceDeviceId = snapshot.sourceDeviceId,
                version = displacedItem.version,
            )
        }
        DomainValidators.validateLocationTree(
            householdId = snapshot.householdId,
            locations = snapshot.locations.map { location ->
                if (location.id == deletedLocation.id) deletedLocation else location
            },
        )
        repository.delete(
            LocationDeletion(
                location = deletedLocation,
                favoriteLocation = deletedFavorite,
                changeRecords = buildList {
                    add(
                        createLocationChangeRecord(
                            idGenerator = idGenerator,
                            householdId = snapshot.householdId,
                            sourceDeviceId = snapshot.sourceDeviceId,
                            entityId = deletedLocation.id.value,
                            operation = ChangeOperation.DELETE,
                            entityVersion = deletedLocation.version,
                            occurredAt = now,
                        ),
                    )
                    if (deletedFavorite != null) {
                        add(
                            ChangeRecord(
                                id = ChangeRecordId(idGenerator.generate()),
                                householdId = snapshot.householdId,
                                entityType = ChangeEntityType.FAVORITE_LOCATION,
                                entityId = deletedFavorite.id.value,
                                operation = ChangeOperation.DELETE,
                                entityVersion = deletedFavorite.version,
                                sourceDeviceId = snapshot.sourceDeviceId,
                                occurredAt = now,
                            ),
                        )
                    }
                    displacedItems.forEach { item ->
                        add(
                            ChangeRecord(
                                id = ChangeRecordId(idGenerator.generate()),
                                householdId = snapshot.householdId,
                                entityType = ChangeEntityType.ITEM,
                                entityId = item.id.value,
                                operation = ChangeOperation.UPDATE,
                                entityVersion = item.version,
                                sourceDeviceId = snapshot.sourceDeviceId,
                                occurredAt = now,
                            ),
                        )
                    }
                },
                displacedItems = displacedItems,
                locationEvents = locationEvents,
            ),
        )
    }
}

/**
 * 返回指定父类型允许新增的子位置类型。
 *
 * 规则遵循“房间 → 区域或家具 → 容器 → 具体位置”的推荐顺序，同时允许跳过中间层级。
 */
fun allowedChildTypes(parentType: LocationType): List<LocationType> = when (parentType) {
    LocationType.HOME -> listOf(LocationType.ROOM)
    LocationType.ROOM -> listOf(LocationType.AREA, LocationType.FURNITURE, LocationType.CONTAINER)
    LocationType.AREA -> listOf(LocationType.FURNITURE, LocationType.CONTAINER, LocationType.SLOT)
    LocationType.FURNITURE -> listOf(LocationType.CONTAINER, LocationType.SLOT)
    LocationType.CONTAINER -> listOf(LocationType.CONTAINER, LocationType.SLOT)
    LocationType.SLOT -> listOf(LocationType.SLOT)
}

/**
 * 在用户未指定图标时，按位置类型回退到受控默认图标。
 */
fun defaultIconKey(type: LocationType): String = when (type) {
    LocationType.HOME -> "location.home"
    LocationType.ROOM -> "room.storage"
    LocationType.AREA -> "location.area"
    LocationType.FURNITURE -> "location.furniture"
    LocationType.CONTAINER -> "location.container"
    LocationType.SLOT -> "location.slot"
}

/**
 * 创建位置节点对应的正式变更记录。
 */
private fun createLocationChangeRecord(
    idGenerator: UniqueIdGenerator,
    householdId: HouseholdId,
    sourceDeviceId: DeviceId,
    entityId: String,
    operation: ChangeOperation,
    entityVersion: EntityVersion,
    occurredAt: UtcTimestamp,
): ChangeRecord = ChangeRecord(
    id = ChangeRecordId(idGenerator.generate()),
    householdId = householdId,
    entityType = ChangeEntityType.LOCATION_NODE,
    entityId = entityId,
    operation = operation,
    entityVersion = entityVersion,
    sourceDeviceId = sourceDeviceId,
    occurredAt = occurredAt,
)

private const val INITIAL_ENTITY_VERSION = 1L
private const val UNCONFIRMED_LOCATION_PATH = "位置待确认"
