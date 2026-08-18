package com.vichua.where.feature.location.initialization

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.TextNormalizer
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.Device
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.DevicePlatform
import com.vichua.where.core.model.DomainValidators
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.Household
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp

/**
 * 初始化页面中单个基础房间的用户输入。
 *
 * @property name 用户可见房间名称。
 * @property iconKey 受控位置图标键，没有指定时为空。
 */
data class InitialRoomInput(
    val name: String,
    val iconKey: String? = null,
)

/**
 * 创建首个本地家庭所需的输入。
 *
 * @property householdName 家庭名称。
 * @property deviceName 当前设备的用户可见名称。
 * @property devicePlatform 当前设备平台。
 * @property rooms 至少一个基础房间。
 * @property rootIconKey 家庭根节点使用的受控图标键。
 */
data class InitializeHouseholdRequest(
    val householdName: String,
    val deviceName: String,
    val devicePlatform: DevicePlatform,
    val rooms: List<InitialRoomInput>,
    val rootIconKey: String,
)

/**
 * 初始化家庭时需要原子保存的完整聚合。
 *
 * @property household 新家庭记录。
 * @property device 当前设备记录。
 * @property rootLocation 家庭唯一根位置。
 * @property roomLocations 根位置下的基础房间。
 * @property changeRecords 与正式实体创建操作对应的变更记录。
 */
data class HouseholdInitialization(
    val household: Household,
    val device: Device,
    val rootLocation: LocationNode,
    val roomLocations: List<LocationNode>,
    val changeRecords: List<ChangeRecord>,
)

/**
 * 初始化家庭的数据仓储契约。
 *
 * 具体数据库实现必须保证家庭、设备、位置和变更记录全部成功或全部回滚。
 */
interface HouseholdInitializationRepository {
    /**
     * 判断本机是否已经存在未删除家庭。
     */
    suspend fun hasActiveHousehold(): Boolean

    /**
     * 原子保存首个家庭聚合。
     *
     * @param initialization 已完成领域校验的初始化聚合。
     */
    suspend fun initialize(initialization: HouseholdInitialization)
}

/**
 * 查询应用启动时是否已经存在可进入首页的家庭。
 */
class HasActiveHouseholdUseCase(
    private val repository: HouseholdInitializationRepository,
) {
    /**
     * 返回本地数据库是否存在未删除家庭。
     */
    suspend operator fun invoke(): Boolean = repository.hasActiveHousehold()
}

/**
 * 初始化首个本地家庭、当前设备和基础位置树。
 *
 * 用例只生成领域数据并执行跨记录校验，不直接依赖 Room、Android 或系统文件 API。
 */
class InitializeHouseholdUseCase(
    private val repository: HouseholdInitializationRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
    private val textNormalizer: TextNormalizer,
) {
    /**
     * 创建并保存首个家庭。
     *
     * @param request 初始化页面确认后的用户输入。
     * @return 新建家庭的全局 ID。
     */
    suspend operator fun invoke(request: InitializeHouseholdRequest): HouseholdId {
        require(!repository.hasActiveHousehold()) {
            "An active household already exists on this device."
        }

        val householdName = request.householdName.trim()
        val deviceName = request.deviceName.trim()
        val rootIconKey = request.rootIconKey.trim()
        require(householdName.isNotEmpty()) { "Household name must not be blank." }
        require(deviceName.isNotEmpty()) { "Device name must not be blank." }
        require(rootIconKey.isNotEmpty()) { "Root icon key must not be blank." }
        require(request.rooms.isNotEmpty()) { "At least one initial room is required." }

        val normalizedRooms = request.rooms.mapIndexed { index, room ->
            normalizeRoomInput(room, index)
        }
        require(
            normalizedRooms.map(NormalizedRoomInput::normalizedName).distinct().size ==
                normalizedRooms.size,
        ) {
            "Initial room names must be unique after normalization."
        }

        val now = UtcTimestamp(clock.now())
        val initialVersion = EntityVersion(INITIAL_ENTITY_VERSION)
        val householdId = HouseholdId(idGenerator.generate())
        val deviceId = DeviceId(idGenerator.generate())
        val rootLocationId = LocationNodeId(idGenerator.generate())

        val household = Household(
            id = householdId,
            name = householdName,
            createdAt = now,
            updatedAt = now,
            version = initialVersion,
            sourceDeviceId = deviceId,
        )
        val device = Device(
            id = deviceId,
            householdId = householdId,
            displayName = deviceName,
            platform = request.devicePlatform,
            createdAt = now,
        )
        val rootLocation = LocationNode(
            id = rootLocationId,
            householdId = householdId,
            parentId = null,
            type = LocationType.HOME,
            name = householdName,
            normalizedName = textNormalizer.normalize(householdName),
            iconKey = rootIconKey,
            sortOrder = SortOrder(ROOT_SORT_ORDER),
            createdAt = now,
            updatedAt = now,
            version = initialVersion,
            sourceDeviceId = deviceId,
        )
        val roomLocations = normalizedRooms.mapIndexed { index, room ->
            LocationNode(
                id = LocationNodeId(idGenerator.generate()),
                householdId = householdId,
                parentId = rootLocationId,
                type = LocationType.ROOM,
                name = room.name,
                normalizedName = room.normalizedName,
                iconKey = room.iconKey,
                sortOrder = SortOrder(index),
                createdAt = now,
                updatedAt = now,
                version = initialVersion,
                sourceDeviceId = deviceId,
            )
        }

        DomainValidators.validateLocationTree(
            householdId = householdId,
            locations = listOf(rootLocation) + roomLocations,
        )

        val changeRecords = buildList {
            add(
                createChangeRecord(
                    householdId = householdId,
                    sourceDeviceId = deviceId,
                    entityType = ChangeEntityType.HOUSEHOLD,
                    entityId = householdId.value,
                    entityVersion = initialVersion,
                    occurredAt = now,
                ),
            )
            add(
                createChangeRecord(
                    householdId = householdId,
                    sourceDeviceId = deviceId,
                    entityType = ChangeEntityType.DEVICE,
                    entityId = deviceId.value,
                    entityVersion = initialVersion,
                    occurredAt = now,
                ),
            )
            add(
                createChangeRecord(
                    householdId = householdId,
                    sourceDeviceId = deviceId,
                    entityType = ChangeEntityType.LOCATION_NODE,
                    entityId = rootLocationId.value,
                    entityVersion = initialVersion,
                    occurredAt = now,
                ),
            )
            roomLocations.forEach { room ->
                add(
                    createChangeRecord(
                        householdId = householdId,
                        sourceDeviceId = deviceId,
                        entityType = ChangeEntityType.LOCATION_NODE,
                        entityId = room.id.value,
                        entityVersion = initialVersion,
                        occurredAt = now,
                    ),
                )
            }
        }

        repository.initialize(
            HouseholdInitialization(
                household = household,
                device = device,
                rootLocation = rootLocation,
                roomLocations = roomLocations,
                changeRecords = changeRecords,
            ),
        )
        return householdId
    }

    /**
     * 清理房间输入并生成稳定标准化名称。
     */
    private fun normalizeRoomInput(
        room: InitialRoomInput,
        index: Int,
    ): NormalizedRoomInput {
        val name = room.name.trim()
        val iconKey = room.iconKey?.trim()
        require(name.isNotEmpty()) { "Initial room at index $index must not be blank." }
        require(iconKey == null || iconKey.isNotEmpty()) {
            "Initial room icon key must be null or non-blank."
        }

        val normalizedName = textNormalizer.normalize(name)
        require(normalizedName.isNotEmpty()) {
            "Normalized initial room name at index $index must not be blank."
        }
        return NormalizedRoomInput(
            name = name,
            normalizedName = normalizedName,
            iconKey = iconKey,
        )
    }

    /**
     * 创建单条正式实体创建记录。
     */
    private fun createChangeRecord(
        householdId: HouseholdId,
        sourceDeviceId: DeviceId,
        entityType: ChangeEntityType,
        entityId: String,
        entityVersion: EntityVersion,
        occurredAt: UtcTimestamp,
    ): ChangeRecord = ChangeRecord(
        id = ChangeRecordId(idGenerator.generate()),
        householdId = householdId,
        entityType = entityType,
        entityId = entityId,
        operation = ChangeOperation.CREATE,
        entityVersion = entityVersion,
        sourceDeviceId = sourceDeviceId,
        occurredAt = occurredAt,
    )

    /**
     * 用例内部使用的已标准化房间输入。
     */
    private data class NormalizedRoomInput(
        val name: String,
        val normalizedName: String,
        val iconKey: String?,
    )

    private companion object {
        const val INITIAL_ENTITY_VERSION = 1L
        const val ROOT_SORT_ORDER = 0
    }
}
