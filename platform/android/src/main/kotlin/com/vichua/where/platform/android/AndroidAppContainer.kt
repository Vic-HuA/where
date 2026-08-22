package com.vichua.where.platform.android

import android.content.Context
import android.os.Build
import com.vichua.where.core.common.DefaultTextNormalizer
import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.buildWhereDatabase
import com.vichua.where.core.database.createAndroidDatabaseBuilder
import com.vichua.where.core.database.query.AccessibilityPreferencesStore
import com.vichua.where.core.database.query.HomeSnapshotStore
import com.vichua.where.core.database.query.HouseholdBackupSnapshotStore
import com.vichua.where.core.database.query.ItemTextSearchStore
import com.vichua.where.core.database.query.ItemDetailStore
import com.vichua.where.core.database.query.LocalBackupRecordStore
import com.vichua.where.core.database.transaction.HouseholdClearStore
import com.vichua.where.core.database.transaction.HouseholdInitializationStore
import com.vichua.where.core.database.transaction.HouseholdRestoreStore
import com.vichua.where.core.database.transaction.ItemDeletionStore
import com.vichua.where.core.database.transaction.ItemDraftStore
import com.vichua.where.core.database.transaction.LocationManagementStore
import com.vichua.where.core.database.transaction.ManualItemCreationStore
import com.vichua.where.core.database.transaction.ItemMovementStore
import com.vichua.where.core.database.transaction.ItemPhotoStore
import com.vichua.where.core.database.transaction.ItemProfileStore
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.feature.item.creation.CreateManualItemUseCase
import com.vichua.where.feature.item.creation.LoadItemCreationContextUseCase
import com.vichua.where.feature.item.deletion.DeleteItemUseCase
import com.vichua.where.feature.item.deletion.RestoreDeletedItemUseCase
import com.vichua.where.feature.item.share.BuildItemLocationShareUseCase
import com.vichua.where.feature.item.share.BuildItemLocationSpeechUseCase
import com.vichua.where.feature.item.photo.AddItemPhotoUseCase
import com.vichua.where.feature.item.photo.DeleteItemPhotoUseCase
import com.vichua.where.feature.item.photo.ImportItemPhotoUseCase
import com.vichua.where.feature.item.photo.MoveItemPhotoUseCase
import com.vichua.where.feature.item.photo.ReorderItemPhotosUseCase
import com.vichua.where.feature.location.management.CreateLocationPathUseCase
import com.vichua.where.feature.item.photo.PrepareAiPhotoRequestUseCase
import com.vichua.where.feature.item.photo.SetItemPhotoCoverUseCase
import com.vichua.where.feature.item.photo.UpdateItemPhotoRoleUseCase
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
import com.vichua.where.core.platform.DocumentGateway
import com.vichua.where.feature.backup.ApplyBackupRestoreUseCase
import com.vichua.where.feature.backup.ClearHouseholdDataUseCase
import com.vichua.where.feature.backup.CreateEncryptedBackupUseCase
import com.vichua.where.feature.backup.ExportHouseholdDataUseCase
import com.vichua.where.feature.backup.LoadLatestBackupStatusUseCase
import com.vichua.where.feature.backup.PreviewBackupRestoreUseCase
import com.vichua.where.feature.backup.VerifyBackupPackageUseCase
import com.vichua.where.feature.search.text.PrepareVoiceSearchQueryUseCase
import com.vichua.where.feature.settings.accessibility.LoadAccessibilityPreferencesUseCase
import com.vichua.where.feature.settings.accessibility.UpdateAccessibilityPreferencesUseCase
import com.vichua.where.feature.settings.preferences.LoadAiProviderCredentialsUseCase
import com.vichua.where.feature.settings.preferences.LoadAppPreferencesUseCase
import com.vichua.where.feature.settings.preferences.UpdateAiProviderCredentialsUseCase
import com.vichua.where.feature.settings.preferences.UpdateAppPreferencesUseCase

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
    private val itemPhotoRepository =
        RoomItemPhotoRepository(ItemPhotoStore(database))
    private val itemDeletionRepository =
        RoomItemDeletionRepository(ItemDeletionStore(database))
    private val locationManagementRepository =
        RoomLocationManagementRepository(LocationManagementStore(database))
    private val accessibilityPreferencesRepository =
        RoomAccessibilityPreferencesRepository(
            store = AccessibilityPreferencesStore(database),
            clock = AndroidEpochMillisecondsClock,
        )
    private val appPreferencesRepository = AndroidAppPreferencesRepository(
        context = applicationContext,
        database = database,
        clock = AndroidEpochMillisecondsClock,
    )
    /** 本机 AI 接口凭证，只给适配器和设置用例使用。 */
    val aiProviderCredentialsStore = AndroidAiProviderCredentialsStore(applicationContext)
    private val householdBackupRepository = RoomHouseholdBackupRepository(
        snapshotStore = HouseholdBackupSnapshotStore(database),
        recordStore = LocalBackupRecordStore(database),
        restoreStore = HouseholdRestoreStore(database),
        clearStore = HouseholdClearStore(database),
    )
    private val backupCrypto = AndroidBackupCrypto()
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

    /** 按受控标识读取原图或缩略图字节，只交给 AI 适配器。 */
    val readPhotoBytes: suspend (String) -> ByteArray? = mediaFileStore::readBytes

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

    /** 向已有物品追加一张相册照片的用例。 */
    val addItemPhotoUseCase = AddItemPhotoUseCase(
        repository = itemPhotoRepository,
        mediaFileStore = mediaFileStore,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 把指定照片设为封面的用例。 */
    val setItemPhotoCoverUseCase = SetItemPhotoCoverUseCase(
        repository = itemPhotoRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 修改单张照片用途的用例。 */
    val updateItemPhotoRoleUseCase = UpdateItemPhotoRoleUseCase(
        repository = itemPhotoRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 调整照片画廊顺序的用例。 */
    val moveItemPhotoUseCase = MoveItemPhotoUseCase(
        repository = itemPhotoRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 拖动后一次性重排照片顺序的用例。 */
    val reorderItemPhotosUseCase = ReorderItemPhotosUseCase(
        repository = itemPhotoRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 软删除物品照片的用例。 */
    val deleteItemPhotoUseCase = DeleteItemPhotoUseCase(
        repository = itemPhotoRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 软删除物品及其本次级联记录的用例。 */
    val deleteItemUseCase = DeleteItemUseCase(
        repository = itemDeletionRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 撤销当前会话内物品删除批次的用例。 */
    val restoreDeletedItemUseCase = RestoreDeletedItemUseCase(
        repository = itemDeletionRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /** 组装详情页朗读文本的用例。 */
    val buildItemLocationSpeechUseCase = BuildItemLocationSpeechUseCase(
        dateTimeFormatter = AndroidVisibleDateTimeFormatter,
    )

    /** 组装位置分享预览和选定照片的用例。 */
    val buildItemLocationShareUseCase = BuildItemLocationShareUseCase(
        dateTimeFormatter = AndroidVisibleDateTimeFormatter,
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

    /** 一次创建多层位置的用例。 */
    val createLocationPathUseCase = CreateLocationPathUseCase(
        createLocationUseCase = createLocationUseCase,
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

    /** 读取当前设备适老与辅助偏好的用例。 */
    val loadAccessibilityPreferencesUseCase =
        LoadAccessibilityPreferencesUseCase(accessibilityPreferencesRepository)

    /** 更新当前设备适老与辅助偏好的用例。 */
    val updateAccessibilityPreferencesUseCase = UpdateAccessibilityPreferencesUseCase(
        repository = accessibilityPreferencesRepository,
        clock = AndroidEpochMillisecondsClock,
    )

    /** 读取当前设备应用开关的用例。 */
    val loadAppPreferencesUseCase = LoadAppPreferencesUseCase(appPreferencesRepository)

    /** 更新当前设备应用开关的用例。 */
    val updateAppPreferencesUseCase = UpdateAppPreferencesUseCase(
        repository = appPreferencesRepository,
        clock = AndroidEpochMillisecondsClock,
    )

    /** 读取本机 AI 接口凭证的用例。 */
    val loadAiProviderCredentialsUseCase =
        LoadAiProviderCredentialsUseCase(aiProviderCredentialsStore)

    /** 保存本机 AI 接口凭证的用例。 */
    val updateAiProviderCredentialsUseCase =
        UpdateAiProviderCredentialsUseCase(aiProviderCredentialsStore)

    /** 把语音查找转写收成本地关键词。 */
    val prepareVoiceSearchQueryUseCase = PrepareVoiceSearchQueryUseCase()

    /** 把用户为当前任务选出的照片收成一次 AI 请求。 */
    val prepareAiPhotoRequestUseCase = PrepareAiPhotoRequestUseCase()

    /** 读取最近一次已验证备份状态的用例。 */
    val loadLatestBackupStatusUseCase =
        LoadLatestBackupStatusUseCase(householdBackupRepository)

    /**
     * 创建加密备份用例。文档选择器绑定 Activity，因此在界面层注入。
     */
    fun createEncryptedBackupUseCase(
        documentGateway: DocumentGateway,
    ): CreateEncryptedBackupUseCase = CreateEncryptedBackupUseCase(
        repository = householdBackupRepository,
        mediaFileStore = mediaFileStore,
        documentGateway = documentGateway,
        backupCrypto = backupCrypto,
        contentHasher = AndroidContentHasher,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /**
     * 导出完整家庭数据用例。文档选择器绑定 Activity，因此在界面层注入。
     */
    fun exportHouseholdDataUseCase(
        documentGateway: DocumentGateway,
    ): ExportHouseholdDataUseCase = ExportHouseholdDataUseCase(
        repository = householdBackupRepository,
        mediaFileStore = mediaFileStore,
        documentGateway = documentGateway,
        backupCrypto = backupCrypto,
        contentHasher = AndroidContentHasher,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /**
     * 只读验证备份用例。文档选择器绑定 Activity，因此在界面层注入。
     */
    fun verifyBackupPackageUseCase(
        documentGateway: DocumentGateway,
    ): VerifyBackupPackageUseCase = VerifyBackupPackageUseCase(
        repository = householdBackupRepository,
        documentGateway = documentGateway,
        backupCrypto = backupCrypto,
        contentHasher = AndroidContentHasher,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
    )

    /**
     * 恢复预览用例。文档选择器绑定 Activity，因此在界面层注入。
     */
    fun previewBackupRestoreUseCase(
        documentGateway: DocumentGateway,
    ): PreviewBackupRestoreUseCase = PreviewBackupRestoreUseCase(
        repository = householdBackupRepository,
        documentGateway = documentGateway,
        backupCrypto = backupCrypto,
        contentHasher = AndroidContentHasher,
    )

    /**
     * 确认后执行合并或替换恢复。
     */
    val applyBackupRestoreUseCase = ApplyBackupRestoreUseCase(
        repository = householdBackupRepository,
        mediaFileStore = mediaFileStore,
        idGenerator = AndroidUniqueIdGenerator(),
    )

    /**
     * 二次确认后清除家庭数据。
     */
    val clearHouseholdDataUseCase = ClearHouseholdDataUseCase(
        repository = householdBackupRepository,
        mediaFileStore = mediaFileStore,
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
