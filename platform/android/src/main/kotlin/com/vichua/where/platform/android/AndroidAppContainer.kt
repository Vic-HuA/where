package com.vichua.where.platform.android

import android.content.Context
import android.os.Build
import com.vichua.where.core.common.DefaultTextNormalizer
import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.buildWhereDatabase
import com.vichua.where.core.database.createAndroidDatabaseBuilder
import com.vichua.where.core.database.query.HomeSnapshotStore
import com.vichua.where.core.database.query.ItemTextSearchStore
import com.vichua.where.core.database.query.ItemDetailStore
import com.vichua.where.core.database.transaction.HouseholdInitializationStore
import com.vichua.where.core.database.transaction.ManualItemCreationStore
import com.vichua.where.feature.item.creation.CreateManualItemUseCase
import com.vichua.where.feature.item.creation.LoadItemCreationContextUseCase
import com.vichua.where.feature.item.detail.LoadItemDetailUseCase
import com.vichua.where.feature.location.initialization.HasActiveHouseholdUseCase
import com.vichua.where.feature.location.initialization.InitializeHouseholdUseCase
import com.vichua.where.feature.search.home.LoadHomeSnapshotUseCase
import com.vichua.where.feature.search.text.SearchItemsUseCase

/**
 * Android 进程级依赖容器。
 *
 * 容器集中创建数据库和初始化家庭相关用例，避免 Activity 重建时重复打开数据库。
 *
 * @param context Android Application 上下文。
 */
class AndroidAppContainer(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val database: WhereDatabase = buildWhereDatabase(
        createAndroidDatabaseBuilder(applicationContext),
    )
    private val initializationStore = HouseholdInitializationStore(database)
    private val initializationRepository =
        RoomHouseholdInitializationRepository(initializationStore)
    private val homeSnapshotRepository =
        RoomHomeSnapshotRepository(HomeSnapshotStore(database))
    private val manualItemCreationRepository =
        RoomManualItemCreationRepository(ManualItemCreationStore(database))
    private val itemTextSearchRepository =
        RoomItemTextSearchRepository(ItemTextSearchStore(database))
    private val itemDetailRepository =
        RoomItemDetailRepository(ItemDetailStore(database))

    /** 查询启动时是否已有家庭的用例。 */
    val hasActiveHouseholdUseCase = HasActiveHouseholdUseCase(initializationRepository)

    /** 创建首个家庭、当前设备和基础位置树的用例。 */
    val initializeHouseholdUseCase = InitializeHouseholdUseCase(
        repository = initializationRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
        textNormalizer = DefaultTextNormalizer,
    )

    /** 加载首页最近记录、最近查找、常用位置和待确认数量的用例。 */
    val loadHomeSnapshotUseCase = LoadHomeSnapshotUseCase(homeSnapshotRepository)

    /** 加载新增物品页面可选位置的用例。 */
    val loadItemCreationContextUseCase =
        LoadItemCreationContextUseCase(manualItemCreationRepository)

    /** 创建基础手动物品的用例。 */
    val createManualItemUseCase = CreateManualItemUseCase(
        repository = manualItemCreationRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
        textNormalizer = DefaultTextNormalizer,
    )

    /** 执行本地文字搜索并保存最近查找的用例。 */
    val searchItemsUseCase = SearchItemsUseCase(
        repository = itemTextSearchRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
        textNormalizer = DefaultTextNormalizer,
    )

    /** 加载物品详情的用例。 */
    val loadItemDetailUseCase = LoadItemDetailUseCase(itemDetailRepository)

    /** 初始化页面使用的当前设备名称建议。 */
    val suggestedDeviceName: String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter(String::isNotBlank)
        .joinToString(separator = " ")
        .ifBlank { DEFAULT_DEVICE_NAME }

    private companion object {
        const val DEFAULT_DEVICE_NAME = "Android device"
    }
}
