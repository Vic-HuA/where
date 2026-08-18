package com.vichua.where.core.database.transaction

import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.mapper.toEntity
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.Device
import com.vichua.where.core.model.DomainValidators
import com.vichua.where.core.model.Household
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationType

/**
 * 原子保存首个家庭、当前设备、家庭根位置和基础房间。
 */
class HouseholdInitializationStore(
    database: WhereDatabase,
) {
    private val database = database
    private val transactionRunner = DatabaseTransactionRunner(database)

    /**
     * 判断数据库中是否已经存在未删除家庭。
     */
    suspend fun hasActiveHousehold(): Boolean =
        database.householdDao().countActive() > 0L

    /**
     * 在单个事务中保存完整初始化聚合。
     *
     * @param household 新家庭记录。
     * @param device 当前设备记录。
     * @param rootLocation 家庭唯一根位置。
     * @param roomLocations 根位置下至少一个基础房间。
     * @param changeRecords 家庭、设备和全部位置对应的创建变更记录。
     */
    suspend fun initialize(
        household: Household,
        device: Device,
        rootLocation: LocationNode,
        roomLocations: List<LocationNode>,
        changeRecords: List<ChangeRecord>,
    ) {
        validateInitialization(
            household = household,
            device = device,
            rootLocation = rootLocation,
            roomLocations = roomLocations,
            changeRecords = changeRecords,
        )

        transactionRunner.write {
            require(householdDao().countActive() == 0L) {
                "Cannot initialize more than one active household."
            }

            householdDao().insert(household.toEntity())
            deviceDao().insert(device.toEntity())
            locationNodeDao().insertAll(
                (listOf(rootLocation) + roomLocations).map(LocationNode::toEntity),
            )
            changeRecordDao().insertAll(changeRecords.map(ChangeRecord::toEntity))
        }
    }

    /**
     * 在进入事务前校验初始化聚合的家庭隔离、位置树和变更记录完整性。
     */
    private fun validateInitialization(
        household: Household,
        device: Device,
        rootLocation: LocationNode,
        roomLocations: List<LocationNode>,
        changeRecords: List<ChangeRecord>,
    ) {
        require(household.deletedAt == null) { "Initialized household must be active." }
        require(device.householdId == household.id) {
            "Initialized device must belong to the new household."
        }
        require(household.sourceDeviceId == device.id) {
            "Household source device must be the initialized device."
        }
        require(rootLocation.type == LocationType.HOME) {
            "Initialization root location type must be HOME."
        }
        require(rootLocation.householdId == household.id) {
            "Initialization root must belong to the new household."
        }
        require(roomLocations.isNotEmpty()) {
            "Initialization must contain at least one room."
        }
        require(roomLocations.all { room ->
            room.householdId == household.id &&
                room.type == LocationType.ROOM &&
                room.parentId == rootLocation.id
        }) {
            "Initial rooms must belong to the household and reference its root."
        }

        DomainValidators.validateLocationTree(
            householdId = household.id,
            locations = listOf(rootLocation) + roomLocations,
        )

        val expectedChangedEntities = buildSet {
            add(ChangeEntityType.HOUSEHOLD to household.id.value)
            add(ChangeEntityType.DEVICE to device.id.value)
            add(ChangeEntityType.LOCATION_NODE to rootLocation.id.value)
            roomLocations.forEach { room ->
                add(ChangeEntityType.LOCATION_NODE to room.id.value)
            }
        }
        val actualChangedEntities = changeRecords.map { record ->
            require(record.householdId == household.id) {
                "Initialization change record belongs to another household."
            }
            require(record.sourceDeviceId == device.id) {
                "Initialization change record belongs to another source device."
            }
            require(record.operation == ChangeOperation.CREATE) {
                "Initialization change records must use CREATE operation."
            }
            record.entityType to record.entityId
        }
        require(actualChangedEntities.size == actualChangedEntities.toSet().size) {
            "Initialization contains duplicate change records."
        }
        require(actualChangedEntities.toSet() == expectedChangedEntities) {
            "Initialization change records do not cover every created entity."
        }
    }
}
