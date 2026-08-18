package com.vichua.where.platform.android

import com.vichua.where.core.database.transaction.HouseholdInitializationStore
import com.vichua.where.feature.location.initialization.HouseholdInitialization
import com.vichua.where.feature.location.initialization.HouseholdInitializationRepository

/**
 * 使用共享 Room Store 实现 Android 初始化家庭仓储契约。
 *
 * 适配层只负责连接功能契约与数据库实现，不复制初始化业务规则。
 */
class RoomHouseholdInitializationRepository(
    private val store: HouseholdInitializationStore,
) : HouseholdInitializationRepository {
    /**
     * 查询本地数据库是否已有未删除家庭。
     */
    override suspend fun hasActiveHousehold(): Boolean =
        store.hasActiveHousehold()

    /**
     * 把已经校验的功能聚合交给共享数据库事务保存。
     */
    override suspend fun initialize(initialization: HouseholdInitialization) {
        store.initialize(
            household = initialization.household,
            device = initialization.device,
            rootLocation = initialization.rootLocation,
            roomLocations = initialization.roomLocations,
            favoriteLocations = initialization.favoriteLocations,
            changeRecords = initialization.changeRecords,
        )
    }
}
