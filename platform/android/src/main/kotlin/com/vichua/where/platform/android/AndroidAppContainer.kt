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
import com.vichua.where.core.database.transaction.ItemDraftStore
import com.vichua.where.core.database.transaction.LocationManagementStore
import com.vichua.where.core.database.transaction.ManualItemCreationStore
import com.vichua.where.core.database.transaction.ItemMovementStore
import com.vichua.where.core.database.transaction.ItemProfileStore
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.feature.item.creation.CreateManualItemUseCase
import com.vichua.where.feature.item.creation.LoadItemCreationContextUseCase
import com.vichua.where.feature.item.photo.ImportItemPhotoUseCase
import com.vichua.where.feature.item.draft.DiscardLatestItemDraftUseCase
import com.vichua.where.feature.item.draft.LoadLatestItemDraftUseCase
import com.vichua.where.feature.item.draft.SaveItemDraftUseCase
import com.vichua.where.feature.item.detail.LoadItemDetailUseCase
import com.vichua.where.feature.item.profile.UpdateItemProfileUseCase
import com.vichua.where.feature.location.initialization.HasActiveHouseholdUseCase
import com.vichua.where.feature.location.initialization.InitializeHouseholdUseCase
import com.vichua.where.feature.location.management.CreateLocationUseCase
import com.vichua.where.feature.location.management.DeleteEmptyLocationUseCase
import com.vichua.where.feature.location.management.LoadLocationTreeUseCase
import com.vichua.where.feature.location.management.RenameLocationUseCase
import com.vichua.where.feature.location.movement.MoveItemUseCase
import com.vichua.where.feature.location.movement.LoadMoveItemContextUseCase
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
    private val itemMovementRepository =
        RoomItemMovementRepository(ItemMovementStore(database))
    private val itemProfileRepository =
        RoomItemProfileRepository(ItemProfileStore(database))
    private val locationManagementRepository =
        RoomLocationManagementRepository(LocationManagementStore(database))
    private val itemDraftRepository = RoomItemDraftRepository(
        store = ItemDraftStore(database),
        nowMillis = AndroidEpochMillisecondsClock::now,
    )
    private val mediaFileStore: ControlledMediaFileStore =
        AndroidControlledMediaFileStore(applicationContext)

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

    /** 加载当前设备未过期物品草稿的用例。 */
    val loadLatestItemDraftUseCase = LoadLatestItemDraftUseCase(itemDraftRepository)

    /** 保存新增物品未完成输入的用例。 */
    val saveItemDraftUseCase = SaveItemDraftUseCase(
        repository = itemDraftRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 放弃当前设备最近一份物品草稿的用例。 */
    val discardLatestItemDraftUseCase = DiscardLatestItemDraftUseCase(itemDraftRepository)

    /** 把相册图片导入私有临时目录的用例。 */
    val importItemPhotoUseCase = ImportItemPhotoUseCase(mediaFileStore)

    /** 解析受控照片路径，供首页和详情解码本地缩略图。 */
    val resolveMediaPath: (String) -> String? = mediaFileStore::resolveAbsolutePath

    /** 放弃新增页未转正的临时照片。 */
    val discardImportedPhotos: suspend (Collection<String>) -> Unit = mediaFileStore::discard

    /** 创建基础手动物品的用例。 */
    val createManualItemUseCase = CreateManualItemUseCase(
        repository = manualItemCreationRepository,
        mediaFileStore = mediaFileStore,
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

    /** 更新单个物品当前位置的用例。 */
    val moveItemUseCase = MoveItemUseCase(
        repository = itemMovementRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 更新物品名称、位置说明和备注的用例。 */
    val updateItemProfileUseCase = UpdateItemProfileUseCase(
        repository = itemProfileRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
        textNormalizer = DefaultTextNormalizer,
    )

    /** 加载更新位置页面上下文的用例。 */
    val loadMoveItemContextUseCase =
        LoadMoveItemContextUseCase(itemMovementRepository)

    /** 加载位置管理树和统计摘要的用例。 */
    val loadLocationTreeUseCase = LoadLocationTreeUseCase(locationManagementRepository)

    /** 在现有位置下新增子位置的用例。 */
    val createLocationUseCase = CreateLocationUseCase(
        repository = locationManagementRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
        textNormalizer = DefaultTextNormalizer,
    )

    /** 重命名非根位置的用例。 */
    val renameLocationUseCase = RenameLocationUseCase(
        repository = locationManagementRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
        textNormalizer = DefaultTextNormalizer,
    )

    /** 删除空位置的用例。 */
    val deleteEmptyLocationUseCase = DeleteEmptyLocationUseCase(
        repository = locationManagementRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 初始化页面使用的当前设备名称建议。 */
    val suggestedDeviceName: String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter(String::isNotBlank)
        .joinToString(separator = " ")
        .ifBlank { DEFAULT_DEVICE_NAME }

    private companion object {
        const val DEFAULT_DEVICE_NAME = "Android device"
    }
}
