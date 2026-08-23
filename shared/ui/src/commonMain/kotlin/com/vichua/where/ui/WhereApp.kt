package com.vichua.where.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.DevicePlatform
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.MvpLimits
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.platform.DiagnosticEvent
import com.vichua.where.core.platform.DiagnosticLogGateway
import com.vichua.where.core.platform.HapticFeedbackGateway
import com.vichua.where.core.platform.HapticFeedbackKind
import com.vichua.where.core.platform.PhotoPickerGateway
import com.vichua.where.core.platform.ShareGateway
import com.vichua.where.core.platform.SharePayload
import com.vichua.where.core.platform.TextToSpeechGateway
import com.vichua.where.feature.item.creation.CreateManualItemUseCase
import com.vichua.where.feature.item.creation.ItemCreationContext
import com.vichua.where.feature.item.creation.LoadItemCreationContextUseCase
import com.vichua.where.feature.item.deletion.DeleteItemUseCase
import com.vichua.where.feature.item.deletion.ItemDeletionResult
import com.vichua.where.feature.item.deletion.RestoreDeletedItemUseCase
import com.vichua.where.feature.item.detail.ItemDetail
import com.vichua.where.feature.item.detail.LoadItemDetailUseCase
import com.vichua.where.feature.item.share.BuildItemLocationShareUseCase
import com.vichua.where.feature.item.share.BuildItemLocationSpeechUseCase
import com.vichua.where.feature.item.profile.UpdateItemProfileRequest
import com.vichua.where.feature.item.profile.UpdateItemProfileUseCase
import com.vichua.where.feature.item.draft.DiscardLatestItemDraftUseCase
import com.vichua.where.feature.item.draft.ItemDraftContent
import com.vichua.where.feature.item.draft.LoadLatestItemDraftUseCase
import com.vichua.where.feature.item.draft.SaveItemDraftUseCase
import com.vichua.where.feature.item.photo.AddItemPhotoUseCase
import com.vichua.where.feature.item.photo.DeleteItemPhotoUseCase
import com.vichua.where.feature.item.photo.ImportItemPhotoUseCase
import com.vichua.where.feature.item.photo.ImportedItemPhoto
import com.vichua.where.feature.item.photo.MoveItemPhotoUseCase
import com.vichua.where.feature.item.photo.ReorderItemPhotosUseCase
import com.vichua.where.feature.item.photo.PrepareAiPhotoRequestUseCase
import com.vichua.where.feature.item.photo.SetItemPhotoCoverUseCase
import com.vichua.where.feature.item.photo.UpdateItemPhotoRoleUseCase
import com.vichua.where.feature.location.initialization.HasActiveHouseholdUseCase
import com.vichua.where.feature.location.initialization.InitializeHouseholdRequest
import com.vichua.where.feature.location.initialization.InitializeHouseholdUseCase
import com.vichua.where.feature.location.management.CreateLocationPathUseCase
import com.vichua.where.feature.location.management.CreateLocationUseCase
import com.vichua.where.feature.location.management.DeleteEmptyLocationUseCase
import com.vichua.where.feature.location.management.LoadLocationTreeUseCase
import com.vichua.where.feature.location.management.LocationTreeSnapshot
import com.vichua.where.feature.location.management.RenameLocationUseCase
import com.vichua.where.feature.location.movement.LoadMoveItemContextUseCase
import com.vichua.where.feature.location.movement.MoveItemContext
import com.vichua.where.feature.location.movement.MoveItemUseCase
import com.vichua.where.core.common.VisibleDateTimeFormatter
import com.vichua.where.core.model.BackupVerificationResult
import com.vichua.where.core.model.LatestBackupStatus
import com.vichua.where.core.model.LocalAccessibilityPreferences
import com.vichua.where.core.model.LocalAppPreferences
import com.vichua.where.core.platform.AiAssistanceGateway
import com.vichua.where.core.platform.AiAssistanceOutcome
import com.vichua.where.core.platform.AiConnectionTestOutcome
import com.vichua.where.core.platform.PickedImage
import com.vichua.where.core.platform.SpeechRecognitionGateway
import com.vichua.where.core.platform.SpeechRecognitionOutcome
import com.vichua.where.core.model.ConflictResolution
import com.vichua.where.core.model.ExportDestination
import com.vichua.where.core.model.HouseholdDataSummary
import com.vichua.where.core.model.RestoreMode
import com.vichua.where.core.model.RestoreSession
import com.vichua.where.core.platform.ManagedBackupFile
import com.vichua.where.feature.backup.ApplyBackupRestoreUseCase
import com.vichua.where.feature.backup.ClearHouseholdDataUseCase
import com.vichua.where.feature.backup.CreateEncryptedBackupUseCase
import com.vichua.where.feature.backup.ExportHouseholdDataUseCase
import com.vichua.where.feature.backup.ListManagedBackupsUseCase
import com.vichua.where.feature.backup.LoadLatestBackupStatusUseCase
import com.vichua.where.feature.backup.PreviewBackupRestoreUseCase
import com.vichua.where.feature.backup.VerifyBackupPackageUseCase
import com.vichua.where.feature.search.home.HomeItemSummary
import com.vichua.where.feature.search.home.HomeSnapshot
import com.vichua.where.feature.search.home.LoadAllItemsUseCase
import com.vichua.where.feature.search.home.LoadHomeSnapshotUseCase
import com.vichua.where.feature.search.home.LoadItemsAtLocationUseCase
import com.vichua.where.feature.search.text.ItemTextSearchResult
import com.vichua.where.feature.search.text.SearchItemsUseCase
import com.vichua.where.feature.search.text.PrepareVoiceSearchQueryUseCase
import com.vichua.where.feature.settings.accessibility.LoadAccessibilityPreferencesUseCase
import com.vichua.where.feature.settings.accessibility.UpdateAccessibilityPreferencesUseCase
import com.vichua.where.core.model.AiProviderCredentials
import com.vichua.where.feature.settings.preferences.LoadAiProviderCredentialsUseCase
import com.vichua.where.feature.settings.preferences.LoadAppPreferencesUseCase
import com.vichua.where.feature.settings.preferences.UpdateAiProviderCredentialsUseCase
import com.vichua.where.feature.settings.preferences.UpdateAppPreferencesUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * 提供跨平台应用根界面，并根据本地家庭状态进入初始化页或首页。
 *
 * @param hasActiveHouseholdUseCase 查询本地是否已有家庭的用例。
 * @param initializeHouseholdUseCase 保存首个家庭的用例。
 * @param loadHomeSnapshotUseCase 加载首页本地摘要的用例。
 * @param loadAllItemsUseCase 加载全部物品列表的用例。
 * @param loadItemsAtLocationUseCase 加载某个位置及其下级物品的用例。
 * @param loadItemCreationContextUseCase 加载新增物品可选位置的用例。
 * @param loadLatestItemDraftUseCase 加载当前设备未过期草稿的用例。
 * @param saveItemDraftUseCase 保存新增物品未完成输入的用例。
 * @param discardLatestItemDraftUseCase 放弃当前草稿的用例。
 * @param importItemPhotoUseCase 把相册图片写入临时目录的用例。
 * @param photoPickerGateway 系统相册选择入口。
 * @param resolveMediaPath 把受控标识解析为本地绝对路径。
 * @param discardImportedPhotos 删除未转正的临时照片。
 * @param createManualItemUseCase 保存基础手动物品的用例。
 * @param searchItemsUseCase 执行本地文字搜索的用例。
 * @param loadItemDetailUseCase 加载物品详情的用例。
 * @param updateItemProfileUseCase 保存物品名称、位置说明和备注的用例。
 * @param addItemPhotoUseCase 向已有物品追加照片的用例。
 * @param setItemPhotoCoverUseCase 设置物品封面照片的用例。
 * @param updateItemPhotoRoleUseCase 修改照片用途的用例。
 * @param moveItemPhotoUseCase 调整照片画廊顺序的用例。
 * @param deleteItemPhotoUseCase 软删除物品照片的用例。
 * @param deleteItemUseCase 软删除物品及其本次级联记录的用例。
 * @param restoreDeletedItemUseCase 撤销当前会话内物品删除批次的用例。
 * @param buildItemLocationSpeechUseCase 组装详情朗读文本的用例。
 * @param buildItemLocationShareUseCase 组装位置分享内容的用例。
 * @param textToSpeechGateway 本地文字朗读入口。
 * @param hapticFeedbackGateway 本机触觉反馈入口。
 * @param shareGateway 系统分享面板入口。
 * @param loadMoveItemContextUseCase 加载更新位置上下文的用例。
 * @param moveItemUseCase 保存物品新位置的用例。
 * @param loadLocationTreeUseCase 加载位置管理树的用例。
 * @param createLocationUseCase 新增单个位置的用例。
 * @param createLocationPathUseCase 一次创建多层位置的用例。
 * @param renameLocationUseCase 重命名位置的用例。
 * @param deleteEmptyLocationUseCase 删除空位置的用例。
 * @param loadAccessibilityPreferencesUseCase 读取当前设备适老偏好的用例。
 * @param updateAccessibilityPreferencesUseCase 更新当前设备适老偏好的用例。
 * @param loadAppPreferencesUseCase 读取当前设备应用开关的用例。
 * @param updateAppPreferencesUseCase 更新当前设备应用开关的用例。
 * @param loadAiProviderCredentialsUseCase 读取本机 AI 接口凭证的用例。
 * @param updateAiProviderCredentialsUseCase 保存本机 AI 接口凭证的用例。
 * @param prepareVoiceSearchQueryUseCase 把语音查找转写收成本地关键词。
 * @param speechRecognitionGateway 可选语音识别入口。
 * @param prepareAiPhotoRequestUseCase 把用户选出的照片收成一次 AI 请求。
 * @param aiAssistanceGateway 可选 AI 辅助入口。
 * @param diagnosticLogGateway 不含敏感内容的本机诊断日志入口。
 * @param loadLatestBackupStatusUseCase 读取最近成功备份状态的用例。
 * @param listManagedBackupsUseCase 列出固定备份目录中的文件。
 * @param createEncryptedBackupUseCase 创建加密备份的用例。
 * @param exportHouseholdDataUseCase 导出完整家庭数据的用例。
 * @param verifyBackupPackageUseCase 只读验证备份的用例。
 * @param previewBackupRestoreUseCase 生成恢复预览的用例。
 * @param applyBackupRestoreUseCase 执行合并或替换恢复的用例。
 * @param clearHouseholdDataUseCase 清除家庭数据的用例。
 * @param visibleDateTimeFormatter 把备份时间格式化为本地可见文本。
 * @param suggestedDeviceName 当前平台提供的设备名称建议。
 * @param devicePlatform 当前运行平台。
 */
@Composable
fun WhereApp(
    hasActiveHouseholdUseCase: HasActiveHouseholdUseCase,
    initializeHouseholdUseCase: InitializeHouseholdUseCase,
    loadHomeSnapshotUseCase: LoadHomeSnapshotUseCase,
    loadAllItemsUseCase: LoadAllItemsUseCase,
    loadItemsAtLocationUseCase: LoadItemsAtLocationUseCase,
    loadItemCreationContextUseCase: LoadItemCreationContextUseCase,
    loadLatestItemDraftUseCase: LoadLatestItemDraftUseCase,
    saveItemDraftUseCase: SaveItemDraftUseCase,
    discardLatestItemDraftUseCase: DiscardLatestItemDraftUseCase,
    importItemPhotoUseCase: ImportItemPhotoUseCase,
    photoPickerGateway: PhotoPickerGateway,
    resolveMediaPath: (String) -> String?,
    discardImportedPhotos: suspend (Collection<String>) -> Unit,
    createManualItemUseCase: CreateManualItemUseCase,
    searchItemsUseCase: SearchItemsUseCase,
    loadItemDetailUseCase: LoadItemDetailUseCase,
    updateItemProfileUseCase: UpdateItemProfileUseCase,
    addItemPhotoUseCase: AddItemPhotoUseCase,
    setItemPhotoCoverUseCase: SetItemPhotoCoverUseCase,
    updateItemPhotoRoleUseCase: UpdateItemPhotoRoleUseCase,
    moveItemPhotoUseCase: MoveItemPhotoUseCase,
    reorderItemPhotosUseCase: ReorderItemPhotosUseCase,
    deleteItemPhotoUseCase: DeleteItemPhotoUseCase,
    deleteItemUseCase: DeleteItemUseCase,
    restoreDeletedItemUseCase: RestoreDeletedItemUseCase,
    buildItemLocationSpeechUseCase: BuildItemLocationSpeechUseCase,
    buildItemLocationShareUseCase: BuildItemLocationShareUseCase,
    textToSpeechGateway: TextToSpeechGateway,
    hapticFeedbackGateway: HapticFeedbackGateway,
    shareGateway: ShareGateway,
    loadMoveItemContextUseCase: LoadMoveItemContextUseCase,
    moveItemUseCase: MoveItemUseCase,
    loadLocationTreeUseCase: LoadLocationTreeUseCase,
    createLocationUseCase: CreateLocationUseCase,
    createLocationPathUseCase: CreateLocationPathUseCase,
    renameLocationUseCase: RenameLocationUseCase,
    deleteEmptyLocationUseCase: DeleteEmptyLocationUseCase,
    loadAccessibilityPreferencesUseCase: LoadAccessibilityPreferencesUseCase,
    updateAccessibilityPreferencesUseCase: UpdateAccessibilityPreferencesUseCase,
    loadAppPreferencesUseCase: LoadAppPreferencesUseCase,
    updateAppPreferencesUseCase: UpdateAppPreferencesUseCase,
    loadAiProviderCredentialsUseCase: LoadAiProviderCredentialsUseCase,
    updateAiProviderCredentialsUseCase: UpdateAiProviderCredentialsUseCase,
    prepareVoiceSearchQueryUseCase: PrepareVoiceSearchQueryUseCase,
    speechRecognitionGateway: SpeechRecognitionGateway,
    prepareAiPhotoRequestUseCase: PrepareAiPhotoRequestUseCase,
    aiAssistanceGateway: AiAssistanceGateway,
    diagnosticLogGateway: DiagnosticLogGateway,
    loadLatestBackupStatusUseCase: LoadLatestBackupStatusUseCase,
    listManagedBackupsUseCase: ListManagedBackupsUseCase,
    createEncryptedBackupUseCase: CreateEncryptedBackupUseCase,
    exportHouseholdDataUseCase: ExportHouseholdDataUseCase,
    verifyBackupPackageUseCase: VerifyBackupPackageUseCase,
    previewBackupRestoreUseCase: PreviewBackupRestoreUseCase,
    applyBackupRestoreUseCase: ApplyBackupRestoreUseCase,
    clearHouseholdDataUseCase: ClearHouseholdDataUseCase,
    visibleDateTimeFormatter: VisibleDateTimeFormatter,
    suggestedDeviceName: String,
    devicePlatform: DevicePlatform,
) {
    var destination by remember { mutableStateOf(AppDestination.LOADING) }
    val navigationBackStack = remember { mutableStateListOf<NavigationFrame>() }
    var startupAttempt by remember { mutableIntStateOf(0) }
    var initializationInProgress by remember { mutableStateOf(false) }
    var initializationError by remember { mutableStateOf<String?>(null) }
    var homeSnapshot by remember { mutableStateOf<HomeSnapshot?>(null) }
    var homeLoading by remember { mutableStateOf(false) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var homeLoadAttempt by remember { mutableIntStateOf(0) }
    var itemCreationContext by remember { mutableStateOf<ItemCreationContext?>(null) }
    var itemCreationLoading by remember { mutableStateOf(false) }
    var itemCreationError by remember { mutableStateOf<String?>(null) }
    var itemCreationAttempt by remember { mutableIntStateOf(0) }
    var itemCreationSubmitting by remember { mutableStateOf(false) }
    var itemDraft by remember { mutableStateOf<ItemDraftContent?>(null) }
    var pendingItemPhotos by remember { mutableStateOf<List<ImportedItemPhoto>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<ItemTextSearchResult>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchInProgress by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var selectedItemId by remember { mutableStateOf<ItemId?>(null) }

    /**
     * 首页、位置、设置之间替换当前根页，不把彼此压进返回栈。
     */
    fun switchRootTab(target: AppDestination) {
        require(target.isRootTab()) { "Only root tabs can replace each other." }
        if (destination == target) {
            return
        }
        navigationBackStack.clear()
        selectedItemId = null
        destination = target
    }

    /**
     * 进入可返回页面时压入当前页，系统 Back 和左上角返回共用这一层栈。
     */
    fun navigateTo(target: AppDestination, itemId: ItemId? = selectedItemId) {
        if (destination == target && selectedItemId == itemId) {
            return
        }
        if (destination.canEnterBackStack()) {
            navigationBackStack.add(NavigationFrame(destination, selectedItemId))
        }
        selectedItemId = itemId
        destination = target
    }

    /**
     * 完成录入、删除等收口操作后回到首页，并丢掉中间页，避免再回到已结束流程。
     */
    fun navigateHome() {
        navigationBackStack.clear()
        selectedItemId = null
        destination = AppDestination.HOME
    }

    /**
     * 清除家庭等需要离开当前栈的场景，直接落到目标页。
     */
    fun resetTo(target: AppDestination) {
        navigationBackStack.clear()
        selectedItemId = null
        destination = target
    }

    /**
     * 弹出上一层；栈空且不在首页时回到首页，首页则交给系统退出。
     */
    fun popNavigation(): Boolean {
        val previous = navigationBackStack.removeLastOrNull()
        if (previous != null) {
            selectedItemId = previous.selectedItemId
            destination = previous.destination
            return true
        }
        if (destination.canEnterBackStack() && destination != AppDestination.HOME) {
            selectedItemId = null
            destination = AppDestination.HOME
            return true
        }
        return false
    }

    var itemDetail by remember { mutableStateOf<ItemDetail?>(null) }
    var itemDetailLoading by remember { mutableStateOf(false) }
    var itemDetailError by remember { mutableStateOf<String?>(null) }
    var itemProfileEditorVisible by remember { mutableStateOf(false) }
    var itemProfileSubmitting by remember { mutableStateOf(false) }
    var itemProfileError by remember { mutableStateOf<String?>(null) }
    var itemPhotoSubmitting by remember { mutableStateOf(false) }
    var itemPhotoError by remember { mutableStateOf<String?>(null) }
    var itemDeletionSubmitting by remember { mutableStateOf(false) }
    var itemDeletionError by remember { mutableStateOf<String?>(null) }
    var pendingItemDeletionUndo by remember { mutableStateOf<ItemDeletionResult?>(null) }
    var itemDeletionUndoRemainingSeconds by remember { mutableIntStateOf(0) }
    var itemDeletionUndoGeneration by remember { mutableIntStateOf(0) }
    var itemDeletionUndoError by remember { mutableStateOf<String?>(null) }
    var itemSpeechSubmitting by remember { mutableStateOf(false) }
    var itemSpeechError by remember { mutableStateOf<String?>(null) }
    var itemSpeechCanRepeat by remember { mutableStateOf(false) }
    var itemShareSubmitting by remember { mutableStateOf(false) }
    var itemShareError by remember { mutableStateOf<String?>(null) }

    /**
     * 离开详情前停掉朗读并收起编辑态，避免返回后残留弹层。
     */
    fun leaveItemDetailAndPop() {
        textToSpeechGateway.stop()
        itemProfileEditorVisible = false
        itemProfileError = null
        itemSpeechError = null
        itemShareError = null
        popNavigation()
    }

    var moveItemContext by remember { mutableStateOf<MoveItemContext?>(null) }
    var moveItemLoading by remember { mutableStateOf(false) }
    var moveItemError by remember { mutableStateOf<String?>(null) }
    var moveVoiceQuery by remember { mutableStateOf<String?>(null) }
    var locationTree by remember { mutableStateOf<LocationTreeSnapshot?>(null) }
    var locationTreeLoading by remember { mutableStateOf(false) }
    var locationTreeSubmitting by remember { mutableStateOf(false) }
    var locationTreeError by remember { mutableStateOf<String?>(null) }
    var locationTreeAttempt by remember { mutableIntStateOf(0) }
    var accessibilityPreferences by remember {
        mutableStateOf<LocalAccessibilityPreferences?>(null)
    }
    var appPreferences by remember { mutableStateOf<LocalAppPreferences?>(null) }
    var aiProviderCredentials by remember { mutableStateOf<AiProviderCredentials?>(null) }
    var aiConnectionTesting by remember { mutableStateOf(false) }
    var aiConnectionTestMessage by remember { mutableStateOf<String?>(null) }
    var pendingImageHandler by remember { mutableStateOf<((PickedImage) -> Unit)?>(null) }
    var voiceListening by remember { mutableStateOf(false) }
    var voicePreparing by remember { mutableStateOf(false) }
    var pendingCreatedLocationId by remember { mutableStateOf<LocationNodeId?>(null) }
    var settingsLoading by remember { mutableStateOf(false) }
    var settingsSubmitting by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var settingsLoadAttempt by remember { mutableIntStateOf(0) }
    var latestBackupStatus by remember { mutableStateOf<LatestBackupStatus?>(null) }
    var backupSubmitting by remember { mutableStateOf(false) }
    var backupProgressText by remember { mutableStateOf<String?>(null) }
    var restoreCompletedText by remember { mutableStateOf<String?>(null) }
    var managedBackups by remember { mutableStateOf<List<ManagedBackupFile>>(emptyList()) }
    var managedBackupsLoading by remember { mutableStateOf(false) }
    var allItems by remember { mutableStateOf<List<HomeItemSummary>>(emptyList()) }
    var allItemsLoading by remember { mutableStateOf(false) }
    var allItemsError by remember { mutableStateOf<String?>(null) }
    var allItemsLoadAttempt by remember { mutableIntStateOf(0) }
    var selectedLocationId by remember { mutableStateOf<LocationNodeId?>(null) }
    var selectedLocationTitle by remember { mutableStateOf<String?>(null) }
    var locationItems by remember { mutableStateOf<List<HomeItemSummary>>(emptyList()) }
    var locationItemsLoading by remember { mutableStateOf(false) }
    var locationItemsError by remember { mutableStateOf<String?>(null) }
    var locationItemsLoadAttempt by remember { mutableIntStateOf(0) }
    var backupVerificationResult by remember { mutableStateOf<BackupVerificationResult?>(null) }
    var householdSummary by remember { mutableStateOf<HouseholdDataSummary?>(null) }
    var restoreSession by remember { mutableStateOf<RestoreSession?>(null) }
    var conflictResolutions by remember { mutableStateOf<Map<String, ConflictResolution>>(emptyMap()) }

    /**
     * 离开恢复页时丢掉未执行的会话，避免返回设置后仍占用预览状态。
     */
    fun leaveRestoreAndPop() {
        restoreSession = null
        conflictResolutions = emptyMap()
        popNavigation()
    }

    var searchSpeechError by remember { mutableStateOf<String?>(null) }
    var confirmationSpeechError by remember { mutableStateOf<String?>(null) }
    val elderFriendlyMode = accessibilityPreferences?.elderFriendly == true
    val coroutineScope = rememberCoroutineScope()
    /**
     * 备份重活前先刷新进度文案，让转圈有一帧可画。
     */
    suspend fun reportBackupProgress(text: String) {
        backupProgressText = text
        yield()
    }

    /**
     * 打开验证或恢复选择界面前刷新固定目录列表。
     */
    fun loadManagedBackups() {
        coroutineScope.launch {
            managedBackupsLoading = true
            try {
                managedBackups = listManagedBackupsUseCase()
            } catch (_: Exception) {
                managedBackups = emptyList()
                settingsError = "暂时无法读取已保存的备份。"
            } finally {
                managedBackupsLoading = false
            }
        }
    }

    /**
     * 从位置树或常用位置进入该处物品列表。
     */
    fun navigateToLocationItems(locationId: LocationNodeId, title: String) {
        if (destination == AppDestination.LOCATION_ITEMS && selectedLocationId == locationId) {
            return
        }
        if (destination.canEnterBackStack()) {
            navigationBackStack.add(NavigationFrame(destination, selectedItemId))
        }
        selectedLocationId = locationId
        selectedLocationTitle = title
        locationItems = emptyList()
        destination = AppDestination.LOCATION_ITEMS
    }
    /**
     * 开关打开且设备支持时给一次短反馈；不可用时忽略，避免打断当前操作。
     */
    val performHaptic: (HapticFeedbackKind) -> Unit = { kind ->
        if (accessibilityPreferences?.hapticFeedbackEnabled == true) {
            try {
                if (hapticFeedbackGateway.isAvailable()) {
                    hapticFeedbackGateway.perform(kind)
                }
            } catch (_: Exception) {
                // Haptic failure must not block the current action.
            }
        }
    }

    /**
     * 先让用户选拍照或相册，再把结果交给当前录入或照片管理流程。
     */
    fun requestImage(onPicked: (PickedImage) -> Unit) {
        pendingImageHandler = onPicked
    }

    /**
     * 先准备本机 Vosk 模型，再开始按住说话。
     *
     * 首次使用需要下载模型，这时先把准备状态交给界面，避免一直显示“正在听”。
     */
    suspend fun listenWithOfflineEngine(): SpeechRecognitionOutcome {
        val engineWasReady = speechRecognitionGateway.isEngineReady()
        if (!engineWasReady) {
            voicePreparing = true
        }
        try {
            if (!speechRecognitionGateway.ensureEngine()) {
                return SpeechRecognitionOutcome.Unavailable
            }
        } finally {
            if (!engineWasReady) {
                voicePreparing = false
            }
        }
        return speechRecognitionGateway.listen(allowNetwork = false)
    }

    suspend fun importPickedImage(
        pickedImage: PickedImage,
        role: PhotoRole,
        onImported: (ImportedItemPhoto) -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            onImported(
                importItemPhotoUseCase(
                    bytes = pickedImage.bytes,
                    sourceMimeType = pickedImage.mimeType,
                    role = role,
                ),
            )
        } catch (_: IllegalArgumentException) {
            onError("无法使用所选照片，请换一张后重试。")
        } catch (_: Exception) {
            onError("照片导入失败，请稍后重试。")
        }
    }

    /**
     * 给已有物品追加照片：先选拍照或相册，再导入并写入档案。
     */
    fun requestExistingItemPhoto(role: PhotoRole) {
        val itemId = selectedItemId
        if (itemId == null || itemPhotoSubmitting) {
            return
        }
        requestImage { pickedImage ->
            coroutineScope.launch {
                itemPhotoSubmitting = true
                itemPhotoError = null
                var importedPhoto: ImportedItemPhoto? = null
                try {
                    importedPhoto = importItemPhotoUseCase(
                        bytes = pickedImage.bytes,
                        sourceMimeType = pickedImage.mimeType,
                        role = role,
                    )
                    addItemPhotoUseCase(itemId, importedPhoto)
                    itemDetail = loadItemDetailUseCase(itemId)
                    homeLoadAttempt += 1
                } catch (_: IllegalArgumentException) {
                    importedPhoto?.let { photo ->
                        discardPendingPhotos(
                            photos = listOf(photo),
                            discardImportedPhotos = discardImportedPhotos,
                        )
                    }
                    itemPhotoError = "无法使用所选照片，请换一张后重试。"
                } catch (_: Exception) {
                    importedPhoto?.let { photo ->
                        discardPendingPhotos(
                            photos = listOf(photo),
                            discardImportedPhotos = discardImportedPhotos,
                        )
                    }
                    itemPhotoError = "照片保存失败，请稍后重试。"
                } finally {
                    itemPhotoSubmitting = false
                }
            }
        }
    }

    /**
     * 执行合并或替换后回到设置页，失败则留在恢复页展示错误。
     */
    fun applyRestore(mode: RestoreMode) {
        val session = restoreSession
        if (backupSubmitting || session == null) {
            return
        }
        performHaptic(
            if (mode == RestoreMode.REPLACE) {
                HapticFeedbackKind.WARNING
            } else {
                HapticFeedbackKind.CONFIRM
            },
        )
        coroutineScope.launch {
            backupSubmitting = true
            backupProgressText = if (mode == RestoreMode.REPLACE) {
                "正在替换家庭数据…"
            } else {
                "正在合并家庭数据…"
            }
            settingsError = null
            try {
                applyBackupRestoreUseCase(session, mode, conflictResolutions)
                restoreSession = null
                conflictResolutions = emptyMap()
                householdSummary = clearHouseholdDataUseCase.loadSummary()
                latestBackupStatus = loadLatestBackupStatusUseCase()
                homeLoadAttempt += 1
                backupProgressText = null
                restoreCompletedText = if (mode == RestoreMode.REPLACE) {
                    "已用备份替换当前家庭。"
                } else {
                    "已合并备份到当前家庭。"
                }
                popNavigation()
            } catch (_: IllegalArgumentException) {
                settingsError = "未处理的冲突不能合并，或备份来自另一个家庭。"
                backupProgressText = null
            } catch (_: Exception) {
                settingsError = "恢复失败，已保留恢复前的家庭数据。"
                backupProgressText = null
            } finally {
                backupSubmitting = false
            }
        }
    }

    /**
     * 开关打开时只写入固定事件码，避免把家庭数据打进日志。
     */
    val recordDiagnostic: (DiagnosticEvent) -> Unit = { event ->
        if (appPreferences?.diagnosticLoggingEnabled == true) {
            try {
                diagnosticLogGateway.record(event)
            } catch (_: Exception) {
                // Diagnostic logging must not block the current action.
            }
        }
    }
    val performSearch: (String) -> Unit = { query ->
        if (!searchInProgress) {
            searchQuery = query
            coroutineScope.launch {
                searchInProgress = true
                searchError = null
                try {
                    searchResults = searchItemsUseCase(query)
                    homeLoadAttempt += 1
                    performHaptic(HapticFeedbackKind.CONFIRM)
                } catch (_: IllegalArgumentException) {
                    searchError = "请输入要查找的物品。"
                } catch (_: Exception) {
                    searchError = "查找失败，请稍后重试。"
                } finally {
                    searchInProgress = false
                }
            }
        }
    }
    val runItemPhotoAction: (String, suspend (ItemId) -> Unit) -> Unit = { failureMessage, action ->
        val itemId = selectedItemId
        if (itemId != null && !itemPhotoSubmitting) {
            coroutineScope.launch {
                itemPhotoSubmitting = true
                itemPhotoError = null
                try {
                    action(itemId)
                    itemDetail = loadItemDetailUseCase(itemId)
                    homeLoadAttempt += 1
                } catch (_: IllegalArgumentException) {
                    itemPhotoError = "请检查当前照片后再试。"
                } catch (_: Exception) {
                    itemPhotoError = failureMessage
                } finally {
                    itemPhotoSubmitting = false
                }
            }
        }
    }

    LaunchedEffect(startupAttempt) {
        destination = AppDestination.LOADING
        destination = try {
            if (hasActiveHouseholdUseCase()) {
                AppDestination.HOME
            } else {
                AppDestination.INITIALIZATION
            }
        } catch (_: Exception) {
            AppDestination.STARTUP_ERROR
        }
    }

    LaunchedEffect(destination, itemCreationAttempt) {
        if (destination == AppDestination.ADD_ITEM) {
            itemCreationLoading = true
            itemCreationError = null
            try {
                itemCreationContext = loadItemCreationContextUseCase()
                itemDraft = loadLatestItemDraftUseCase()
            } catch (_: Exception) {
                itemCreationError = "暂时无法读取可用位置。"
            } finally {
                itemCreationLoading = false
            }
        }
    }

    LaunchedEffect(destination, selectedItemId) {
        val itemId = selectedItemId
        if ((destination == AppDestination.ITEM_DETAIL || destination == AppDestination.PHOTO_MANAGEMENT) && itemId != null) {
            val alreadyShowingItem = itemDetail?.itemId == itemId
            if (destination == AppDestination.ITEM_DETAIL) {
                itemProfileEditorVisible = false
                itemProfileError = null
                itemPhotoError = null
                itemSpeechError = null
                itemSpeechCanRepeat = false
                itemShareError = null
                textToSpeechGateway.stop()
            }
            // 详情已经在内存里时不再重拉，避免进出管理照片时照片闪一下。
            if (alreadyShowingItem) {
                return@LaunchedEffect
            }
            itemDetailLoading = true
            itemDetailError = null
            try {
                itemDetail = loadItemDetailUseCase(itemId)
            } catch (_: Exception) {
                itemDetailError = "暂时无法读取物品详情。"
            } finally {
                itemDetailLoading = false
            }
        }
    }

    LaunchedEffect(destination, selectedItemId) {
        val itemId = selectedItemId
        if (destination == AppDestination.MOVE_ITEM && itemId != null) {
            moveItemLoading = true
            moveItemError = null
            try {
                moveItemContext = loadMoveItemContextUseCase(itemId)
            } catch (_: Exception) {
                moveItemError = "暂时无法读取可选位置。"
            } finally {
                moveItemLoading = false
            }
        }
    }

    LaunchedEffect(destination, locationTreeAttempt) {
        if (destination == AppDestination.LOCATION) {
            // 已有树时后台刷新，避免底栏切回来先空白再闪出内容。
            if (locationTree == null) {
                locationTreeLoading = true
            }
            locationTreeError = null
            try {
                locationTree = loadLocationTreeUseCase()
            } catch (_: Exception) {
                locationTreeError = "暂时无法读取位置。"
            } finally {
                locationTreeLoading = false
            }
        }
    }

    LaunchedEffect(itemDeletionUndoGeneration) {
        if (itemDeletionUndoGeneration == 0 || pendingItemDeletionUndo == null) {
            return@LaunchedEffect
        }
        val startedGeneration = itemDeletionUndoGeneration
        var remainingSeconds = MvpLimits.DELETE_UNDO_WINDOW_SECONDS
        itemDeletionUndoRemainingSeconds = remainingSeconds
        while (remainingSeconds > 0) {
            delay(UNDO_COUNTDOWN_STEP_MILLISECONDS)
            if (itemDeletionUndoGeneration != startedGeneration || pendingItemDeletionUndo == null) {
                return@LaunchedEffect
            }
            remainingSeconds -= 1
            if (remainingSeconds == 0) {
                pendingItemDeletionUndo = null
                itemDeletionUndoError = null
            }
            itemDeletionUndoRemainingSeconds = remainingSeconds
        }
    }

    LaunchedEffect(destination, homeLoadAttempt) {
        if (destination == AppDestination.HOME) {
            // 已有首页数据时不要先进入加载态，否则每次返回都会整页闪白。
            if (homeSnapshot == null) {
                homeLoading = true
            }
            homeError = null
            try {
                homeSnapshot = loadHomeSnapshotUseCase()
            } catch (_: Exception) {
                homeError = "暂时无法读取首页数据。"
            } finally {
                homeLoading = false
            }
        }
    }

    LaunchedEffect(destination, allItemsLoadAttempt) {
        if (destination == AppDestination.ALL_ITEMS) {
            if (allItems.isEmpty()) {
                allItemsLoading = true
            }
            allItemsError = null
            try {
                allItems = loadAllItemsUseCase()
            } catch (_: Exception) {
                allItemsError = "暂时无法读取全部物品。"
            } finally {
                allItemsLoading = false
            }
        }
    }

    LaunchedEffect(destination, selectedLocationId, locationItemsLoadAttempt) {
        val locationId = selectedLocationId
        if (destination == AppDestination.LOCATION_ITEMS && locationId != null) {
            if (locationItems.isEmpty()) {
                locationItemsLoading = true
            }
            locationItemsError = null
            try {
                locationItems = loadItemsAtLocationUseCase(locationId)
            } catch (_: Exception) {
                locationItemsError = "暂时无法读取该位置的物品。"
            } finally {
                locationItemsLoading = false
            }
        }
    }

    LaunchedEffect(destination) {
        if (destination == AppDestination.RESTORE_BACKUP) {
            managedBackupsLoading = true
            try {
                managedBackups = listManagedBackupsUseCase()
            } catch (_: Exception) {
                managedBackups = emptyList()
                settingsError = "暂时无法读取已保存的备份。"
            } finally {
                managedBackupsLoading = false
            }
        }
    }

    LaunchedEffect(destination, settingsLoadAttempt) {
        val shouldLoadPreferences = destination == AppDestination.HOME ||
            destination == AppDestination.SETTINGS ||
            destination == AppDestination.SEARCH ||
            destination == AppDestination.ADD_ITEM ||
            destination == AppDestination.ITEM_DETAIL ||
            destination == AppDestination.PHOTO_MANAGEMENT ||
            destination == AppDestination.MOVE_ITEM ||
            destination == AppDestination.RESTORE_BACKUP ||
            destination == AppDestination.ALL_ITEMS ||
            destination == AppDestination.LOCATION_ITEMS
        if (shouldLoadPreferences && accessibilityPreferences == null) {
            settingsLoading = true
            settingsError = null
            try {
                accessibilityPreferences = loadAccessibilityPreferencesUseCase()
                appPreferences = loadAppPreferencesUseCase()
                aiProviderCredentials = loadAiProviderCredentialsUseCase()
            } catch (_: Exception) {
                settingsError = "暂时无法读取辅助设置。"
            } finally {
                settingsLoading = false
            }
        }
        if (destination == AppDestination.HOME || destination == AppDestination.SETTINGS) {
            try {
                latestBackupStatus = loadLatestBackupStatusUseCase()
                if (destination == AppDestination.SETTINGS) {
                    householdSummary = clearHouseholdDataUseCase.loadSummary()
                }
            } catch (_: Exception) {
                if (latestBackupStatus == null) {
                    latestBackupStatus = LatestBackupStatus(
                        lastVerifiedAt = null,
                        lastVerifiedSizeBytes = null,
                    )
                }
            }
        }
    }

    WhereTheme(
        highContrast = accessibilityPreferences?.highContrastEnabled == true,
        elderFriendly = elderFriendlyMode,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            NavigationBackHandler(
                enabled = destination.canEnterBackStack() &&
                    destination != AppDestination.HOME,
                onBack = {
                    when (destination) {
                        AppDestination.ITEM_DETAIL -> leaveItemDetailAndPop()
                        AppDestination.RESTORE_BACKUP -> leaveRestoreAndPop()
                        else -> popNavigation()
                    }
                },
            )
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val switchingPhotoManagement =
                        (initialState == AppDestination.ITEM_DETAIL &&
                            targetState == AppDestination.PHOTO_MANAGEMENT) ||
                            (initialState == AppDestination.PHOTO_MANAGEMENT &&
                                targetState == AppDestination.ITEM_DETAIL)
                    if (switchingPhotoManagement) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(animationSpec = tween(180)) togetherWith
                            fadeOut(animationSpec = tween(120))
                    }
                },
                label = "destination",
            ) { currentDestination ->
            when (currentDestination) {
                AppDestination.LOADING -> LoadingScreen()
                AppDestination.STARTUP_ERROR -> StartupErrorScreen(
                    onRetry = {
                        startupAttempt += 1
                    },
                )
                AppDestination.INITIALIZATION -> InitializationScreen(
                    suggestedDeviceName = suggestedDeviceName,
                    devicePlatform = devicePlatform,
                    isSubmitting = initializationInProgress,
                    errorMessage = initializationError,
                    onSubmit = { request, elderFriendlyEnabled ->
                        if (!initializationInProgress) {
                            coroutineScope.launch {
                                initializationInProgress = true
                                initializationError = null
                                try {
                                    initializeHouseholdUseCase(request)
                                    navigateHome()
                                    try {
                                        accessibilityPreferences = if (elderFriendlyEnabled) {
                                            updateAccessibilityPreferencesUseCase.setElderFriendly(true)
                                        } else {
                                            loadAccessibilityPreferencesUseCase()
                                        }
                                    } catch (_: Exception) {
                                        settingsLoadAttempt += 1
                                    }
                                } catch (_: IllegalArgumentException) {
                                    initializationError = "请检查家庭名称和房间设置。"
                                } catch (_: Exception) {
                                    initializationError = "保存失败，请稍后重试。"
                                } finally {
                                    initializationInProgress = false
                                }
                            }
                        }
                    },
                )
                AppDestination.HOME -> HomeScreen(
                    snapshot = homeSnapshot,
                    resolveMediaPath = resolveMediaPath,
                    loading = homeLoading,
                    errorMessage = homeError,
                    deletionUndo = pendingItemDeletionUndo?.let { undo ->
                        HomeDeletionUndo(
                            itemName = undo.itemName,
                            remainingSeconds = itemDeletionUndoRemainingSeconds,
                        )
                    },
                    deletionUndoSubmitting = itemDeletionSubmitting,
                    deletionUndoErrorMessage = itemDeletionUndoError,
                    confirmationSpeechErrorMessage = confirmationSpeechError,
                    backupReminderMessage = if (
                        appPreferences?.backupReminderEnabled == true &&
                        latestBackupStatus != null &&
                        latestBackupStatus?.lastVerifiedAt == null
                    ) {
                        "还没有成功备份，可到设置里创建加密备份。"
                    } else {
                        null
                    },
                    onBackupReminderClick = {
                        switchRootTab(AppDestination.SETTINGS)
                    },
                    onUndoDeletion = {
                        val undo = pendingItemDeletionUndo
                        if (undo != null && !itemDeletionSubmitting) {
                            coroutineScope.launch {
                                itemDeletionSubmitting = true
                                itemDeletionUndoError = null
                                try {
                                    restoreDeletedItemUseCase(undo)
                                    pendingItemDeletionUndo = null
                                    itemDeletionUndoRemainingSeconds = 0
                                    homeLoadAttempt += 1
                                } catch (_: IllegalArgumentException) {
                                    pendingItemDeletionUndo = null
                                    itemDeletionUndoError = "该物品已无法撤销。"
                                } catch (_: Exception) {
                                    itemDeletionUndoError = "撤销失败，请稍后重试。"
                                } finally {
                                    itemDeletionSubmitting = false
                                }
                            }
                        }
                    },
                    onRetry = {
                        homeLoadAttempt += 1
                    },
                    onTextSearch = { query ->
                        navigateTo(AppDestination.SEARCH)
                        performSearch(query)
                    },
                    onVoiceSearchRequested = {
                        if (!voiceListening) {
                            performHaptic(HapticFeedbackKind.CONFIRM)
                            coroutineScope.launch {
                                voiceListening = true
                                searchError = null
                                try {
                                    val preferences = appPreferences ?: loadAppPreferencesUseCase()
                                    appPreferences = preferences
                                    val outcome = listenWithOfflineEngine()
                                    when (outcome) {
                                        is SpeechRecognitionOutcome.Success -> {
                                            navigateTo(AppDestination.SEARCH)
                                            performSearch(
                                                prepareVoiceSearchQueryUseCase(outcome.text),
                                            )
                                        }
                                        SpeechRecognitionOutcome.Cancelled -> Unit
                                        else -> {
                                            searchResults = null
                                            searchError = voiceRecognitionMessage(
                                                outcome = outcome,
                                                cloudSpeechEnabled = preferences.canUseCloudSpeech,
                                            )
                                            navigateTo(AppDestination.SEARCH)
                                        }
                                    }
                                } catch (_: Exception) {
                                    searchResults = null
                                    searchError = "语音识别失败，请先使用键盘输入。"
                                    navigateTo(AppDestination.SEARCH)
                                } finally {
                                    voiceListening = false
                                }
                            }
                        }
                    },
                    onVoiceSearchReleased = {
                        speechRecognitionGateway.finishListening()
                    },
                    onItemClick = { item ->
                        navigateTo(AppDestination.ITEM_DETAIL, item.itemId)
                    },
                    onViewAllItems = {
                        navigateTo(AppDestination.ALL_ITEMS)
                    },
                    onRecentSearchClick = { query ->
                        navigateTo(AppDestination.SEARCH)
                        performSearch(query)
                    },
                    onFavoriteLocationClick = { favorite ->
                        navigateToLocationItems(favorite.locationNodeId, favorite.name)
                    },
                    onRecordItemClick = {
                        performHaptic(HapticFeedbackKind.CONFIRM)
                        confirmationSpeechError = null
                        navigateTo(AppDestination.ADD_ITEM)
                    },
                    onLocationClick = {
                        switchRootTab(AppDestination.LOCATION)
                    },
                    onSettingsClick = {
                        switchRootTab(AppDestination.SETTINGS)
                    },
                    elderFriendlyMode = elderFriendlyMode,
                    voiceListening = voiceListening,
                    voicePreparing = voicePreparing,
                )
                AppDestination.ALL_ITEMS -> AllItemsScreen(
                    items = allItems,
                    resolveMediaPath = resolveMediaPath,
                    loading = allItemsLoading,
                    errorMessage = allItemsError,
                    onBack = {
                        popNavigation()
                    },
                    onRetry = {
                        allItemsLoadAttempt += 1
                    },
                    onItemClick = { item ->
                        navigateTo(AppDestination.ITEM_DETAIL, item.itemId)
                    },
                )
                AppDestination.LOCATION_ITEMS -> AllItemsScreen(
                    title = selectedLocationTitle ?: "位置物品",
                    subtitle = "该位置及下级共 ${locationItems.size} 件，按最近更新排列。",
                    emptyText = "这个位置下还没有物品",
                    items = locationItems,
                    resolveMediaPath = resolveMediaPath,
                    loading = locationItemsLoading,
                    errorMessage = locationItemsError,
                    onBack = {
                        popNavigation()
                    },
                    onRetry = {
                        locationItemsLoadAttempt += 1
                    },
                    onItemClick = { item ->
                        navigateTo(AppDestination.ITEM_DETAIL, item.itemId)
                    },
                )
                AppDestination.SEARCH -> SearchScreen(
                    initialQuery = searchQuery,
                    results = searchResults,
                    searching = searchInProgress,
                    errorMessage = searchError,
                    onBack = {
                        popNavigation()
                    },
                    onSearch = performSearch,
                    resolveMediaPath = resolveMediaPath,
                    onResultClick = { result ->
                        navigateTo(AppDestination.ITEM_DETAIL, result.itemId)
                    },
                    elderFriendlyMode = elderFriendlyMode,
                    speechErrorMessage = searchSpeechError,
                    onReadLocation = { result ->
                        performHaptic(HapticFeedbackKind.CONFIRM)
                        coroutineScope.launch {
                            searchSpeechError = null
                            try {
                                if (!textToSpeechGateway.isAvailable()) {
                                    error("Text to speech is unavailable.")
                                }
                                textToSpeechGateway.speak(
                                    buildItemLocationSpeechUseCase.fromParts(
                                        name = result.name,
                                        locationPath = result.locationPath,
                                        locationDescription = null,
                                        updatedAt = result.updatedAt,
                                    ),
                                )
                            } catch (_: Exception) {
                                searchSpeechError = "当前设备无法朗读。"
                                recordDiagnostic(DiagnosticEvent.TTS_UNAVAILABLE)
                            }
                        }
                    },
                )
                AppDestination.ADD_ITEM -> AddItemScreen(
                    context = itemCreationContext,
                    draft = itemDraft,
                    photos = pendingItemPhotos,
                    resolveMediaPath = resolveMediaPath,
                    loading = itemCreationLoading,
                    submitting = itemCreationSubmitting,
                    errorMessage = itemCreationError,
                    creatingLocation = locationTreeSubmitting,
                    pendingCreatedLocationId = pendingCreatedLocationId,
                    onPendingCreatedLocationConsumed = {
                        pendingCreatedLocationId = null
                    },
                    onCreateLocation = { request ->
                        if (!locationTreeSubmitting && !itemCreationSubmitting) {
                            coroutineScope.launch {
                                locationTreeSubmitting = true
                                itemCreationError = null
                                try {
                                    pendingCreatedLocationId = createLocationPathUseCase(request)
                                    itemCreationContext = loadItemCreationContextUseCase()
                                    homeLoadAttempt += 1
                                    locationTreeAttempt += 1
                                } catch (_: IllegalArgumentException) {
                                    itemCreationError = "请检查位置名称和类型。"
                                } catch (_: Exception) {
                                    itemCreationError = "新建位置失败，请稍后重试。"
                                } finally {
                                    locationTreeSubmitting = false
                                }
                            }
                        }
                    },
                    onRetry = {
                        itemCreationAttempt += 1
                    },
                    onBack = {
                        popNavigation()
                    },
                    onPickPhoto = { role ->
                        if (!itemCreationSubmitting) {
                            requestImage { pickedImage ->
                                coroutineScope.launch {
                                    itemCreationError = null
                                    importPickedImage(
                                        pickedImage = pickedImage,
                                        role = role,
                                        onImported = { photo ->
                                            pendingItemPhotos = pendingItemPhotos + photo
                                        },
                                        onError = { message ->
                                            itemCreationError = message
                                        },
                                    )
                                }
                            }
                        }
                    },
                    onSaveDraft = { content ->
                        val creationContext = itemCreationContext
                        if (creationContext != null && !itemCreationSubmitting) {
                            coroutineScope.launch {
                                itemCreationSubmitting = true
                                itemCreationError = null
                                try {
                                    saveItemDraftUseCase(
                                        householdId = creationContext.householdId,
                                        deviceId = creationContext.sourceDeviceId,
                                        content = content,
                                    )
                                    discardPendingPhotos(
                                        photos = pendingItemPhotos,
                                        discardImportedPhotos = discardImportedPhotos,
                                    )
                                    pendingItemPhotos = emptyList()
                                    navigateHome()
                                } catch (_: IllegalArgumentException) {
                                    itemCreationError = "请先填写物品名称、位置或备注。"
                                } catch (_: Exception) {
                                    itemCreationError = "草稿保存失败，请稍后重试。"
                                } finally {
                                    itemCreationSubmitting = false
                                }
                            }
                        }
                    },
                    onDiscardDraft = {
                        if (!itemCreationSubmitting) {
                            coroutineScope.launch {
                                itemCreationSubmitting = true
                                itemCreationError = null
                                try {
                                    discardLatestItemDraftUseCase()
                                    discardPendingPhotos(
                                        photos = pendingItemPhotos,
                                        discardImportedPhotos = discardImportedPhotos,
                                    )
                                    pendingItemPhotos = emptyList()
                                    itemDraft = null
                                    navigateHome()
                                } catch (_: Exception) {
                                    itemCreationError = "放弃草稿失败，请稍后重试。"
                                } finally {
                                    itemCreationSubmitting = false
                                }
                            }
                        }
                    },
                    elderFriendlyMode = elderFriendlyMode,
                    voicePreparing = voicePreparing,
                    onAiRecognizeRequested = suspend {
                        val preferences = appPreferences ?: loadAppPreferencesUseCase()
                        appPreferences = preferences
                        if (!preferences.canUseAiAssistance) {
                            itemCreationError = "未开启 AI 辅助。可在设置中开启，或改用手填。"
                            null
                        } else {
                            val selectedPhotos = prepareAiPhotoRequestUseCase(pendingItemPhotos)
                            if (selectedPhotos.isEmpty()) {
                                itemCreationError = "请先选择要识别的照片。"
                                null
                            } else if (!aiAssistanceGateway.isAvailable()) {
                                itemCreationError = "当前无法使用 AI 辅助，请先手动填写。"
                                recordDiagnostic(DiagnosticEvent.AI_UNAVAILABLE)
                                null
                            } else {
                                val outcome = aiAssistanceGateway.analyzePhotos(selectedPhotos)
                                when (outcome) {
                                    is AiAssistanceOutcome.Success -> outcome.suggestions
                                    AiAssistanceOutcome.Cancelled -> null
                                    else -> {
                                        itemCreationError = aiAssistanceMessage(outcome)
                                        null
                                    }
                                }
                            }
                        }
                    },
                    onSpeakReleased = {
                        speechRecognitionGateway.finishListening()
                    },
                    onSpeakRequested = suspend {
                        val preferences = appPreferences ?: loadAppPreferencesUseCase()
                        appPreferences = preferences
                        val outcome = listenWithOfflineEngine()
                        when (outcome) {
                            is SpeechRecognitionOutcome.Success -> outcome.text
                            SpeechRecognitionOutcome.Cancelled -> null
                            else -> {
                                itemCreationError = voiceRecognitionMessage(
                                    outcome = outcome,
                                    cloudSpeechEnabled = preferences.canUseCloudSpeech,
                                    forItemName = true,
                                )
                                null
                            }
                        }
                    },
                    onSubmit = { request ->
                        if (!itemCreationSubmitting) {
                            coroutineScope.launch {
                                itemCreationSubmitting = true
                                itemCreationError = null
                                confirmationSpeechError = null
                                try {
                                    val createdItemId = createManualItemUseCase(
                                        request.copy(photos = pendingItemPhotos),
                                    )
                                    pendingItemPhotos = emptyList()
                                    itemDraft = null
                                    homeLoadAttempt += 1
                                    performHaptic(HapticFeedbackKind.CONFIRM)
                                    val preferences = accessibilityPreferences
                                        ?: loadAccessibilityPreferencesUseCase()
                                    accessibilityPreferences = preferences
                                    if (preferences.autoReadConfirmationEnabled) {
                                        try {
                                            if (!textToSpeechGateway.isAvailable()) {
                                                error("Text to speech is unavailable.")
                                            }
                                            val createdDetail = loadItemDetailUseCase(createdItemId)
                                            textToSpeechGateway.speak(
                                                buildItemLocationSpeechUseCase(createdDetail),
                                            )
                                        } catch (_: Exception) {
                                            confirmationSpeechError = "当前设备无法朗读。"
                                            recordDiagnostic(DiagnosticEvent.TTS_UNAVAILABLE)
                                        }
                                    }
                                    navigateHome()
                                } catch (_: IllegalArgumentException) {
                                    itemCreationError = "请检查物品名称和所在位置。"
                                } catch (_: Exception) {
                                    itemCreationError = "保存失败，请稍后重试。"
                                    recordDiagnostic(DiagnosticEvent.ITEM_SAVE_FAILED)
                                } finally {
                                    itemCreationSubmitting = false
                                }
                            }
                        }
                    },
                )
                AppDestination.LOCATION -> LocationManagementScreen(
                    snapshot = locationTree,
                    loading = locationTreeLoading,
                    submitting = locationTreeSubmitting,
                    errorMessage = locationTreeError,
                    onRetry = {
                        locationTreeAttempt += 1
                    },
                    onHomeClick = {
                        switchRootTab(AppDestination.HOME)
                    },
                    onSettingsClick = {
                        switchRootTab(AppDestination.SETTINGS)
                    },
                    onCreate = { request ->
                        if (!locationTreeSubmitting) {
                            coroutineScope.launch {
                                locationTreeSubmitting = true
                                locationTreeError = null
                                try {
                                    createLocationPathUseCase(request)
                                    locationTree = loadLocationTreeUseCase()
                                    homeLoadAttempt += 1
                                } catch (_: IllegalArgumentException) {
                                    locationTreeError = "请检查位置名称和类型。"
                                } catch (_: Exception) {
                                    locationTreeError = "保存位置失败，请稍后重试。"
                                } finally {
                                    locationTreeSubmitting = false
                                }
                            }
                        }
                    },
                    onRename = { node, name ->
                        if (!locationTreeSubmitting) {
                            coroutineScope.launch {
                                locationTreeSubmitting = true
                                locationTreeError = null
                                try {
                                    renameLocationUseCase(node.locationId, name)
                                    locationTree = loadLocationTreeUseCase()
                                    homeLoadAttempt += 1
                                } catch (_: IllegalArgumentException) {
                                    locationTreeError = "请检查位置名称。"
                                } catch (_: Exception) {
                                    locationTreeError = "重命名失败，请稍后重试。"
                                } finally {
                                    locationTreeSubmitting = false
                                }
                            }
                        }
                    },
                    onDelete = { node ->
                        if (!locationTreeSubmitting) {
                            performHaptic(HapticFeedbackKind.WARNING)
                            coroutineScope.launch {
                                locationTreeSubmitting = true
                                locationTreeError = null
                                try {
                                    deleteEmptyLocationUseCase(node.locationId)
                                    locationTree = loadLocationTreeUseCase()
                                    homeLoadAttempt += 1
                                } catch (_: IllegalArgumentException) {
                                    locationTreeError = "请先处理该位置中的物品或下级位置。"
                                } catch (_: Exception) {
                                    locationTreeError = "删除失败，请稍后重试。"
                                } finally {
                                    locationTreeSubmitting = false
                                }
                            }
                        }
                    },
                    onViewItems = { node ->
                        navigateToLocationItems(node.locationId, node.name)
                    },
                )
                AppDestination.SETTINGS -> SettingsScreen(
                    preferences = accessibilityPreferences,
                    appPreferences = appPreferences,
                    aiProviderCredentials = aiProviderCredentials,
                    loading = settingsLoading,
                    submitting = settingsSubmitting || backupSubmitting,
                    errorMessage = settingsError,
                    lastVerifiedBackupText = latestBackupStatus?.lastVerifiedAt?.let { timestamp ->
                        "最近成功备份：${visibleDateTimeFormatter.format(timestamp.epochMilliseconds)}"
                    },
                    backupProgressText = backupProgressText,
                    verificationResult = backupVerificationResult,
                    householdSummary = householdSummary,
                    onHomeClick = {
                        switchRootTab(AppDestination.HOME)
                    },
                    onLocationClick = {
                        switchRootTab(AppDestination.LOCATION)
                    },
                    onRetry = {
                        accessibilityPreferences = null
                        appPreferences = null
                        aiProviderCredentials = null
                        settingsLoadAttempt += 1
                    },
                    onSaveAiProviderCredentials = { vendor, apiKey, baseUrl, model ->
                        if (!settingsSubmitting) {
                            coroutineScope.launch {
                                settingsSubmitting = true
                                settingsError = null
                                aiConnectionTestMessage = null
                                try {
                                    aiProviderCredentials = updateAiProviderCredentialsUseCase(
                                        vendor = vendor,
                                        apiKey = apiKey,
                                        baseUrl = baseUrl,
                                        model = model,
                                    )
                                } catch (_: IllegalArgumentException) {
                                    settingsError = "请填写 https 接口地址和模型名称。"
                                } catch (_: Exception) {
                                    settingsError = "保存 AI 接口设置失败，请稍后重试。"
                                } finally {
                                    settingsSubmitting = false
                                }
                            }
                        }
                    },
                    onTestAiConnection = { vendor, apiKey, baseUrl, model ->
                        if (!aiConnectionTesting) {
                            coroutineScope.launch {
                                aiConnectionTesting = true
                                aiConnectionTestMessage = null
                                try {
                                    val credentials = AiProviderCredentials.normalized(
                                        vendor = vendor,
                                        apiKey = apiKey,
                                        baseUrl = baseUrl,
                                        model = model,
                                    )
                                    aiConnectionTestMessage = when (
                                        val outcome = aiAssistanceGateway.testConnection(credentials)
                                    ) {
                                        AiConnectionTestOutcome.Success -> "连接成功，可以保存。"
                                        AiConnectionTestOutcome.Incomplete -> "请先填写 Key、地址和模型。"
                                        is AiConnectionTestOutcome.Failed -> outcome.message
                                    }
                                } catch (_: IllegalArgumentException) {
                                    aiConnectionTestMessage = "请填写 https 接口地址和模型名称。"
                                } catch (_: Exception) {
                                    aiConnectionTestMessage = "测试失败，请稍后重试。"
                                } finally {
                                    aiConnectionTesting = false
                                }
                            }
                        }
                    },
                    aiConnectionTesting = aiConnectionTesting,
                    aiConnectionTestMessage = aiConnectionTestMessage,
                    onAiAssistanceChange = { enabled ->
                        if (!settingsSubmitting) {
                            coroutineScope.launch {
                                settingsSubmitting = true
                                settingsError = null
                                try {
                                    appPreferences = if (enabled) {
                                        updateAppPreferencesUseCase.enableAiAssistanceAfterDisclosure()
                                    } else {
                                        updateAppPreferencesUseCase.disableAiAssistance()
                                    }
                                } catch (_: Exception) {
                                    settingsError = "保存 AI 设置失败，请稍后重试。"
                                } finally {
                                    settingsSubmitting = false
                                }
                            }
                        }
                    },
                    onElderFriendlyChange = { enabled ->
                        if (!settingsSubmitting) {
                            coroutineScope.launch {
                                settingsSubmitting = true
                                settingsError = null
                                try {
                                    accessibilityPreferences =
                                        updateAccessibilityPreferencesUseCase.setElderFriendly(enabled)
                                } catch (_: Exception) {
                                    settingsError = "保存适老设置失败，请稍后重试。"
                                } finally {
                                    settingsSubmitting = false
                                }
                            }
                        }
                    },
                    onCloudSpeechChange = { enabled ->
                        if (!settingsSubmitting) {
                            coroutineScope.launch {
                                settingsSubmitting = true
                                settingsError = null
                                try {
                                    appPreferences = if (enabled) {
                                        updateAppPreferencesUseCase.enableCloudSpeechAfterDisclosure()
                                    } else {
                                        updateAppPreferencesUseCase.disableCloudSpeech()
                                    }
                                } catch (_: Exception) {
                                    settingsError = "保存语音设置失败，请稍后重试。"
                                } finally {
                                    settingsSubmitting = false
                                }
                            }
                        }
                    },
                    onHighContrastChange = { enabled ->
                        if (!settingsSubmitting) {
                            coroutineScope.launch {
                                settingsSubmitting = true
                                settingsError = null
                                try {
                                    accessibilityPreferences =
                                        updateAccessibilityPreferencesUseCase.setHighContrast(enabled)
                                } catch (_: Exception) {
                                    settingsError = "保存高对比度失败，请稍后重试。"
                                } finally {
                                    settingsSubmitting = false
                                }
                            }
                        }
                    },
                    onAutoReadConfirmationChange = { enabled ->
                        if (!settingsSubmitting) {
                            coroutineScope.launch {
                                settingsSubmitting = true
                                settingsError = null
                                try {
                                    accessibilityPreferences =
                                        updateAccessibilityPreferencesUseCase
                                            .setAutoReadConfirmation(enabled)
                                    if (!enabled) {
                                        confirmationSpeechError = null
                                    }
                                } catch (_: Exception) {
                                    settingsError = "保存自动朗读失败，请稍后重试。"
                                } finally {
                                    settingsSubmitting = false
                                }
                            }
                        }
                    },
                    onHapticFeedbackChange = { enabled ->
                        if (!settingsSubmitting) {
                            coroutineScope.launch {
                                settingsSubmitting = true
                                settingsError = null
                                try {
                                    accessibilityPreferences =
                                        updateAccessibilityPreferencesUseCase
                                            .setHapticFeedback(enabled)
                                    if (enabled) {
                                        performHaptic(HapticFeedbackKind.CONFIRM)
                                    }
                                } catch (_: Exception) {
                                    settingsError = "保存触觉反馈失败，请稍后重试。"
                                } finally {
                                    settingsSubmitting = false
                                }
                            }
                        }
                    },
                    onBackupReminderChange = { enabled ->
                        if (!settingsSubmitting) {
                            coroutineScope.launch {
                                settingsSubmitting = true
                                settingsError = null
                                try {
                                    appPreferences =
                                        updateAppPreferencesUseCase.setBackupReminder(enabled)
                                } catch (_: Exception) {
                                    settingsError = "保存备份提醒失败，请稍后重试。"
                                } finally {
                                    settingsSubmitting = false
                                }
                            }
                        }
                    },
                    onDiagnosticLoggingChange = { enabled ->
                        if (!settingsSubmitting) {
                            coroutineScope.launch {
                                settingsSubmitting = true
                                settingsError = null
                                try {
                                    appPreferences =
                                        updateAppPreferencesUseCase.setDiagnosticLogging(enabled)
                                    if (enabled) {
                                        recordDiagnostic(DiagnosticEvent.ENABLED)
                                    }
                                } catch (_: Exception) {
                                    settingsError = "保存诊断日志失败，请稍后重试。"
                                } finally {
                                    settingsSubmitting = false
                                }
                            }
                        }
                    },
                    managedBackups = managedBackups,
                    managedBackupsLoading = managedBackupsLoading,
                    onLoadManagedBackups = {
                        loadManagedBackups()
                    },
                    formatBackupTime = { epochMilliseconds ->
                        visibleDateTimeFormatter.format(epochMilliseconds)
                    },
                    onCreateBackup = { password, confirmation ->
                        if (!backupSubmitting) {
                            coroutineScope.launch {
                                backupSubmitting = true
                                reportBackupProgress("正在创建加密备份…")
                                settingsError = null
                                backupVerificationResult = null
                                try {
                                    val result = createEncryptedBackupUseCase(
                                        password = password,
                                        passwordConfirmation = confirmation,
                                        onProgress = { text ->
                                            reportBackupProgress(text)
                                        },
                                    )
                                    latestBackupStatus = loadLatestBackupStatusUseCase()
                                    backupVerificationResult = result
                                    managedBackups = listManagedBackupsUseCase()
                                    backupProgressText = null
                                } catch (_: IllegalArgumentException) {
                                    settingsError = "请检查密码，或确认两次输入一致。"
                                    backupProgressText = null
                                } catch (_: Exception) {
                                    settingsError = "创建备份失败，请稍后重试。"
                                    backupProgressText = null
                                    recordDiagnostic(DiagnosticEvent.BACKUP_CREATE_FAILED)
                                } finally {
                                    backupSubmitting = false
                                }
                            }
                        }
                    },
                    onExportHousehold = { password, confirmation, destination ->
                        if (!backupSubmitting) {
                            coroutineScope.launch {
                                backupSubmitting = true
                                reportBackupProgress(
                                    when (destination) {
                                        ExportDestination.SAVE_DOCUMENT -> "正在导出完整家庭数据…"
                                        ExportDestination.SHARE -> "正在准备分享导出包…"
                                    },
                                )
                                settingsError = null
                                backupVerificationResult = null
                                try {
                                    val result = exportHouseholdDataUseCase(
                                        password = password,
                                        passwordConfirmation = confirmation,
                                        destination = destination,
                                        onProgress = { text ->
                                            reportBackupProgress(text)
                                        },
                                    )
                                    if (result == null) {
                                        backupProgressText = null
                                        return@launch
                                    }
                                    latestBackupStatus = loadLatestBackupStatusUseCase()
                                    backupVerificationResult = result
                                    backupProgressText = null
                                } catch (_: IllegalArgumentException) {
                                    settingsError = "请检查密码，或确认两次输入一致。"
                                    backupProgressText = null
                                } catch (_: Exception) {
                                    settingsError = "导出家庭数据失败，请稍后重试。"
                                    backupProgressText = null
                                } finally {
                                    backupSubmitting = false
                                }
                            }
                        }
                    },
                    onVerifyBackup = { password, backupUri ->
                        if (!backupSubmitting) {
                            coroutineScope.launch {
                                backupSubmitting = true
                                reportBackupProgress("正在验证备份…")
                                settingsError = null
                                backupVerificationResult = null
                                try {
                                    val result = verifyBackupPackageUseCase(
                                        password = password,
                                        backupUri = backupUri,
                                        onProgress = { text ->
                                            reportBackupProgress(text)
                                        },
                                    )
                                    if (result == null) {
                                        backupProgressText = null
                                        return@launch
                                    }
                                    latestBackupStatus = loadLatestBackupStatusUseCase()
                                    backupVerificationResult = result
                                    backupProgressText = null
                                } catch (error: IllegalArgumentException) {
                                    println("Backup verify rejected: ${error.message}")
                                    settingsError = restorePreviewErrorMessage(error)
                                    backupProgressText = null
                                } catch (error: Exception) {
                                    println("Backup verify failed: ${error.message}")
                                    settingsError = restorePreviewErrorMessage(error).takeIf { text ->
                                        text != "密码错误、格式不兼容或备份已损坏。"
                                    } ?: "验证备份失败，请稍后重试。"
                                    backupProgressText = null
                                } finally {
                                    backupSubmitting = false
                                }
                            }
                        }
                    },
                    onDismissVerification = {
                        backupVerificationResult = null
                    },
                    onRestoreBackup = {
                        settingsError = null
                        restoreSession = null
                        conflictResolutions = emptyMap()
                        backupProgressText = null
                        navigateTo(AppDestination.RESTORE_BACKUP)
                    },
                    onClearHousehold = {
                        if (!backupSubmitting) {
                            performHaptic(HapticFeedbackKind.WARNING)
                            coroutineScope.launch {
                                backupSubmitting = true
                                backupProgressText = "正在清除家庭数据…"
                                settingsError = null
                                try {
                                    clearHouseholdDataUseCase()
                                    restoreSession = null
                                    conflictResolutions = emptyMap()
                                    homeSnapshot = null
                                    resetTo(AppDestination.INITIALIZATION)
                                    backupProgressText = null
                                } catch (_: Exception) {
                                    settingsError = "清除家庭数据失败，请稍后重试。"
                                    backupProgressText = null
                                } finally {
                                    backupSubmitting = false
                                }
                            }
                        }
                    },
                )
                AppDestination.RESTORE_BACKUP -> RestoreBackupScreen(
                    session = restoreSession,
                    formattedBackupCreatedAt = restoreSession?.preview?.manifest?.createdAt?.let { timestamp ->
                        visibleDateTimeFormatter.format(timestamp.epochMilliseconds)
                    },
                    lastVerifiedBackupText = latestBackupStatus?.lastVerifiedAt?.let { timestamp ->
                        "最近成功备份：${visibleDateTimeFormatter.format(timestamp.epochMilliseconds)}"
                    },
                    currentSummary = householdSummary,
                    resolutions = conflictResolutions,
                    submitting = backupSubmitting,
                    progressText = backupProgressText,
                    errorMessage = settingsError,
                    onBack = {
                        leaveRestoreAndPop()
                    },
                    managedBackups = managedBackups,
                    managedBackupsLoading = managedBackupsLoading,
                    formatBackupTime = { epochMilliseconds ->
                        visibleDateTimeFormatter.format(epochMilliseconds)
                    },
                    onPickAndPreview = { password, backupUri ->
                        if (!backupSubmitting) {
                            coroutineScope.launch {
                                backupSubmitting = true
                                reportBackupProgress("正在准备恢复预览…")
                                settingsError = null
                                restoreSession = null
                                conflictResolutions = emptyMap()
                                try {
                                    val session = previewBackupRestoreUseCase(
                                        password = password,
                                        backupUri = backupUri,
                                        onProgress = { text ->
                                            reportBackupProgress(text)
                                        },
                                    )
                                    if (session == null) {
                                        backupProgressText = null
                                        return@launch
                                    }
                                    restoreSession = session
                                    householdSummary = session.preview.currentSummary
                                    backupProgressText = null
                                } catch (error: IllegalArgumentException) {
                                    println("Restore preview rejected: ${error.message}")
                                    settingsError = restorePreviewErrorMessage(error)
                                    backupProgressText = null
                                } catch (error: Exception) {
                                    println("Restore preview failed: ${error.message}")
                                    settingsError = restorePreviewErrorMessage(error).takeIf { text ->
                                        text != "密码错误、格式不兼容或备份已损坏。"
                                    } ?: "恢复预览失败，请稍后重试。"
                                    backupProgressText = null
                                } finally {
                                    backupSubmitting = false
                                }
                            }
                        }
                    },
                    onResolve = { key, resolution ->
                        conflictResolutions = conflictResolutions + (key to resolution)
                    },
                    onMerge = {
                        applyRestore(RestoreMode.MERGE)
                    },
                    onReplace = {
                        applyRestore(RestoreMode.REPLACE)
                    },
                )
                AppDestination.ITEM_DETAIL -> ItemDetailScreen(
                    detail = itemDetail,
                    resolveMediaPath = resolveMediaPath,
                    loading = itemDetailLoading,
                    errorMessage = itemDetailError,
                    onBack = {
                        leaveItemDetailAndPop()
                    },
                    onReadLocation = {
                        val detail = itemDetail
                        if (detail != null && !itemSpeechSubmitting) {
                            performHaptic(HapticFeedbackKind.CONFIRM)
                            coroutineScope.launch {
                                itemSpeechSubmitting = true
                                itemSpeechError = null
                                try {
                                    if (!textToSpeechGateway.isAvailable()) {
                                        error("Text to speech is unavailable.")
                                    }
                                    textToSpeechGateway.speak(
                                        buildItemLocationSpeechUseCase(detail),
                                    )
                                    itemSpeechCanRepeat = true
                                } catch (_: Exception) {
                                    itemSpeechError = "当前设备无法朗读。"
                                    recordDiagnostic(DiagnosticEvent.TTS_UNAVAILABLE)
                                } finally {
                                    itemSpeechSubmitting = false
                                }
                            }
                        }
                    },
                    speechSubmitting = itemSpeechSubmitting,
                    speechErrorMessage = itemSpeechError,
                    canRepeatSpeech = itemSpeechCanRepeat,
                    formattedUpdatedAt = itemDetail?.let { detail ->
                        buildItemLocationShareUseCase.formatUpdatedAt(detail)
                    }.orEmpty(),
                    formatOccurredAt = { epochMilliseconds ->
                        visibleDateTimeFormatter.format(epochMilliseconds)
                    },
                    shareSubmitting = itemShareSubmitting,
                    shareErrorMessage = itemShareError,
                    onShareLocation = { includeUpdatedAt, selectedPhotoIds ->
                        val detail = itemDetail
                        if (detail != null && !itemShareSubmitting) {
                            coroutineScope.launch {
                                itemShareSubmitting = true
                                itemShareError = null
                                try {
                                    if (!shareGateway.isAvailable()) {
                                        error("System share is unavailable.")
                                    }
                                    val content = buildItemLocationShareUseCase(
                                        detail = detail,
                                        includeUpdatedAt = includeUpdatedAt,
                                        selectedPhotoIds = selectedPhotoIds,
                                    )
                                    shareGateway.share(
                                        SharePayload(
                                            text = content.text,
                                            imageAbsolutePaths = content.photoStorageKeys.mapNotNull(
                                                resolveMediaPath,
                                            ),
                                        ),
                                    )
                                } catch (_: Exception) {
                                    itemShareError = "分享失败，请稍后重试。"
                                } finally {
                                    itemShareSubmitting = false
                                }
                            }
                        }
                    },
                    onUpdateLocation = {
                        itemProfileEditorVisible = false
                        itemProfileError = null
                        navigateTo(AppDestination.MOVE_ITEM)
                    },
                    editorVisible = itemProfileEditorVisible,
                    editorSubmitting = itemProfileSubmitting,
                    editorErrorMessage = itemProfileError,
                    onEditProfile = {
                        itemProfileError = null
                        itemProfileEditorVisible = true
                    },
                    onDismissEditor = {
                        if (!itemProfileSubmitting) {
                            itemProfileEditorVisible = false
                            itemProfileError = null
                        }
                    },
                    onSaveProfile = { name, locationDescription, note ->
                        val itemId = selectedItemId
                        if (itemId != null && !itemProfileSubmitting) {
                            coroutineScope.launch {
                                itemProfileSubmitting = true
                                itemProfileError = null
                                try {
                                    updateItemProfileUseCase(
                                        UpdateItemProfileRequest(
                                            itemId = itemId,
                                            name = name,
                                            locationDescription = locationDescription,
                                            note = note,
                                        ),
                                    )
                                    itemDetail = loadItemDetailUseCase(itemId)
                                    homeLoadAttempt += 1
                                    itemProfileEditorVisible = false
                                } catch (_: IllegalArgumentException) {
                                    itemProfileError = "请检查物品名称，或确认至少修改了一项。"
                                } catch (_: Exception) {
                                    itemProfileError = "保存失败，请稍后重试。"
                                } finally {
                                    itemProfileSubmitting = false
                                }
                            }
                        }
                    },
                    photoSubmitting = itemPhotoSubmitting,
                    photoErrorMessage = itemPhotoError,
                    onOpenPhotoManagement = {
                        navigateTo(AppDestination.PHOTO_MANAGEMENT)
                    },
                    onAddPhoto = { role ->
                        requestExistingItemPhoto(role)
                    },
                    onSetPhotoCover = { photoId ->
                        runItemPhotoAction("设置封面失败，请稍后重试。") {
                            setItemPhotoCoverUseCase(it, photoId)
                        }
                    },
                    onSetPhotoRole = { photoId, role ->
                        runItemPhotoAction("修改用途失败，请稍后重试。") {
                            updateItemPhotoRoleUseCase(it, photoId, role)
                        }
                    },
                    onMovePhoto = { photoId, offset ->
                        runItemPhotoAction("调整顺序失败，请稍后重试。") {
                            moveItemPhotoUseCase(it, photoId, offset)
                        }
                    },
                    onDeletePhoto = { photoId ->
                        runItemPhotoAction("删除照片失败，请稍后重试。") {
                            deleteItemPhotoUseCase(it, photoId)
                        }
                    },
                    deletionSubmitting = itemDeletionSubmitting,
                    deletionErrorMessage = itemDeletionError,
                    elderFriendlyMode = elderFriendlyMode,
                    onDeleteItem = {
                        val itemId = selectedItemId
                        if (itemId != null && !itemDeletionSubmitting) {
                            performHaptic(HapticFeedbackKind.WARNING)
                            coroutineScope.launch {
                                itemDeletionSubmitting = true
                                itemDeletionError = null
                                try {
                                    val deletion = deleteItemUseCase(itemId)
                                    pendingItemDeletionUndo = deletion
                                    itemDeletionUndoRemainingSeconds =
                                        MvpLimits.DELETE_UNDO_WINDOW_SECONDS
                                    itemDeletionUndoGeneration += 1
                                    itemDeletionUndoError = null
                                    itemDetail = null
                                    selectedItemId = null
                                    homeLoadAttempt += 1
                                    navigateHome()
                                } catch (_: IllegalArgumentException) {
                                    itemDeletionError = "该物品当前无法删除。"
                                } catch (_: Exception) {
                                    itemDeletionError = "删除失败，请稍后重试。"
                                } finally {
                                    itemDeletionSubmitting = false
                                }
                            }
                        }
                    },
                )
                AppDestination.PHOTO_MANAGEMENT -> PhotoManagementScreen(
                    detail = itemDetail,
                    resolveMediaPath = resolveMediaPath,
                    submitting = itemPhotoSubmitting,
                    errorMessage = itemPhotoError,
                    onBack = {
                        popNavigation()
                    },
                    onAddPhoto = { role ->
                        requestExistingItemPhoto(role)
                    },
                    onSetPhotoCover = { photoId ->
                        runItemPhotoAction("设置封面失败，请稍后重试。") {
                            setItemPhotoCoverUseCase(it, photoId)
                        }
                    },
                    onSetPhotoRole = { photoId, role ->
                        runItemPhotoAction("修改用途失败，请稍后重试。") {
                            updateItemPhotoRoleUseCase(it, photoId, role)
                        }
                    },
                    onMovePhoto = { photoId, offset ->
                        runItemPhotoAction("调整顺序失败，请稍后重试。") {
                            moveItemPhotoUseCase(it, photoId, offset)
                        }
                    },
                    onReorderPhotos = { photoId, fromIndex, toIndex ->
                        runItemPhotoAction("调整顺序失败，请稍后重试。") {
                            reorderItemPhotosUseCase(it, photoId, fromIndex, toIndex)
                        }
                    },
                    onDeletePhoto = { photoId ->
                        runItemPhotoAction("删除照片失败，请稍后重试。") {
                            deleteItemPhotoUseCase(it, photoId)
                        }
                    },
                )
                AppDestination.MOVE_ITEM -> MoveItemScreen(
                    context = moveItemContext,
                    loading = moveItemLoading,
                    errorMessage = moveItemError,
                    voiceQuery = moveVoiceQuery,
                    voiceListening = voiceListening,
                    voicePreparing = voicePreparing,
                    creatingLocation = locationTreeSubmitting,
                    pendingCreatedLocationId = pendingCreatedLocationId,
                    onPendingCreatedLocationConsumed = {
                        pendingCreatedLocationId = null
                    },
                    onBack = {
                        popNavigation()
                    },
                    elderFriendlyMode = elderFriendlyMode,
                    onVoiceRequested = {
                        if (!voiceListening) {
                            performHaptic(HapticFeedbackKind.CONFIRM)
                            coroutineScope.launch {
                                voiceListening = true
                                moveItemError = null
                                try {
                                    val preferences = appPreferences ?: loadAppPreferencesUseCase()
                                    appPreferences = preferences
                                    val outcome = listenWithOfflineEngine()
                                    when (outcome) {
                                        is SpeechRecognitionOutcome.Success -> {
                                            moveVoiceQuery = prepareVoiceSearchQueryUseCase(outcome.text)
                                        }
                                        SpeechRecognitionOutcome.Cancelled -> Unit
                                        else -> {
                                            moveItemError = voiceRecognitionMessage(
                                                outcome = outcome,
                                                cloudSpeechEnabled = preferences.canUseCloudSpeech,
                                            )
                                        }
                                    }
                                } catch (_: Exception) {
                                    moveItemError = "语音识别失败，请先使用键盘输入。"
                                } finally {
                                    voiceListening = false
                                }
                            }
                        }
                    },
                    onVoiceReleased = {
                        speechRecognitionGateway.finishListening()
                    },
                    onVoiceQueryConsumed = {
                        moveVoiceQuery = null
                    },
                    onCreateLocation = { request ->
                        if (!locationTreeSubmitting) {
                            coroutineScope.launch {
                                locationTreeSubmitting = true
                                moveItemError = null
                                try {
                                    pendingCreatedLocationId = createLocationPathUseCase(request)
                                    val itemId = selectedItemId
                                    if (itemId != null) {
                                        moveItemContext = loadMoveItemContextUseCase(itemId)
                                    }
                                    homeLoadAttempt += 1
                                    locationTreeAttempt += 1
                                } catch (_: IllegalArgumentException) {
                                    moveItemError = "请检查位置名称和类型。"
                                } catch (_: Exception) {
                                    moveItemError = "新建位置失败，请稍后重试。"
                                } finally {
                                    locationTreeSubmitting = false
                                }
                            }
                        }
                    },
                    onSave = { locationId ->
                        val itemId = selectedItemId
                        if (itemId != null && !moveItemLoading) {
                            coroutineScope.launch {
                                moveItemLoading = true
                                moveItemError = null
                                try {
                                    moveItemUseCase(itemId, locationId)
                                    itemDetail = loadItemDetailUseCase(itemId)
                                    homeLoadAttempt += 1
                                    popNavigation()
                                } catch (_: Exception) {
                                    moveItemError = "更新位置失败，请重试。"
                                } finally {
                                    moveItemLoading = false
                                }
                            }
                        }
                    },
                )
            }
            }
            val imageHandler = pendingImageHandler
            if (imageHandler != null) {
                ImageSourceDialog(
                    onDismiss = {
                        pendingImageHandler = null
                    },
                    onCapture = {
                        val handler = pendingImageHandler
                        pendingImageHandler = null
                        if (handler != null) {
                            coroutineScope.launch {
                                val pickedImage = photoPickerGateway.captureImage() ?: return@launch
                                handler(pickedImage)
                            }
                        }
                    },
                    onPick = {
                        val handler = pendingImageHandler
                        pendingImageHandler = null
                        if (handler != null) {
                            coroutineScope.launch {
                                val pickedImage = photoPickerGateway.pickImage() ?: return@launch
                                handler(pickedImage)
                            }
                        }
                    },
                )
            }
            val completedRestoreText = restoreCompletedText
            if (completedRestoreText != null) {
                WhereDialog(
                    onDismissRequest = { restoreCompletedText = null },
                    title = "恢复完成",
                    confirmText = "好",
                    onConfirm = { restoreCompletedText = null },
                    dismissText = null,
                ) {
                    Text(
                        text = completedRestoreText,
                        color = WherePrimaryTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * 尚未接入业务用例的目标页占位状态，提供明确返回路径而不是无响应按钮。
 */
@Composable
private fun PendingFeatureScreen(
    title: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = WherePrimaryTextColor,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "页面业务正在接入。",
            color = WhereSecondaryTextColor,
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            modifier = Modifier.padding(top = 24.dp),
            onClick = onBack,
        ) {
            Text("返回首页")
        }
    }
}

/**
 * 显示数据库启动检查中的加载状态。
 */
@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = "正在读取家庭数据…",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * 显示启动读取失败状态，避免在数据库状态未知时误导用户重新初始化。
 */
@Composable
private fun StartupErrorScreen(
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "暂时无法读取本地家庭数据",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "现有数据不会被清除，请重试。",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            modifier = Modifier.padding(top = 24.dp),
            onClick = onRetry,
        ) {
            Text("重试")
        }
    }
}

/**
 * 把识别失败收成可展示的中文原因，不包含转写原文。
 */
private fun voiceRecognitionMessage(
    outcome: SpeechRecognitionOutcome,
    cloudSpeechEnabled: Boolean,
    forItemName: Boolean = false,
): String = when (outcome) {
    is SpeechRecognitionOutcome.Success,
    SpeechRecognitionOutcome.Cancelled,
    -> if (forItemName) {
        "请先手动填写名称。"
    } else {
        "请先使用键盘输入。"
    }
    SpeechRecognitionOutcome.PermissionDenied ->
        "未授予麦克风权限，请先使用键盘输入。"
    SpeechRecognitionOutcome.Unavailable ->
        "离线语音还没准备好。首次使用需要下载中文模型，请连接网络后重试，或改用键盘。"
    SpeechRecognitionOutcome.NoMatch ->
        "没有听清，请再说一次或改用键盘。"
}

/**
 * 把 AI 识别失败收成可展示的中文原因，不包含照片路径或建议原文。
 */
private fun aiAssistanceMessage(outcome: AiAssistanceOutcome): String = when (outcome) {
    is AiAssistanceOutcome.Success,
    AiAssistanceOutcome.Cancelled,
    -> "请先手动填写。"
    AiAssistanceOutcome.Unavailable ->
        "当前无法使用 AI 辅助，请先手动填写。"
    AiAssistanceOutcome.NoSelectedContent ->
        "请先选择要识别的照片。"
    AiAssistanceOutcome.Failed ->
        "识别失败，请先手动填写。"
}

/**
 * 删除尚未转正的临时原图和缩略图，避免放弃录入后占用私有目录。
 */
private suspend fun discardPendingPhotos(
    photos: List<ImportedItemPhoto>,
    discardImportedPhotos: suspend (Collection<String>) -> Unit,
) {
    if (photos.isEmpty()) {
        return
    }
    val storageKeys = photos.flatMap { photo ->
        listOf(photo.media.tempStorageKey, photo.media.thumbnailTempStorageKey)
    }
    discardImportedPhotos(storageKeys)
}

private const val UNDO_COUNTDOWN_STEP_MILLISECONDS = 1_000L

/**
 * 应用当前顶层页面状态。
 */
private enum class AppDestination {
    LOADING,
    STARTUP_ERROR,
    INITIALIZATION,
    HOME,
    ALL_ITEMS,
    LOCATION_ITEMS,
    SEARCH,
    ADD_ITEM,
    LOCATION,
    SETTINGS,
    RESTORE_BACKUP,
    ITEM_DETAIL,
    PHOTO_MANAGEMENT,
    MOVE_ITEM,
}

/**
 * 用户可来回进出的页面才进入返回栈；启动和初始化不压栈。
 */
private fun AppDestination.isRootTab(): Boolean = this == AppDestination.HOME ||
    this == AppDestination.LOCATION ||
    this == AppDestination.SETTINGS

private fun AppDestination.canEnterBackStack(): Boolean = when (this) {
    AppDestination.HOME,
    AppDestination.ALL_ITEMS,
    AppDestination.LOCATION_ITEMS,
    AppDestination.SEARCH,
    AppDestination.ADD_ITEM,
    AppDestination.LOCATION,
    AppDestination.SETTINGS,
    AppDestination.RESTORE_BACKUP,
    AppDestination.ITEM_DETAIL,
    AppDestination.PHOTO_MANAGEMENT,
    AppDestination.MOVE_ITEM,
    -> true
    AppDestination.LOADING,
    AppDestination.STARTUP_ERROR,
    AppDestination.INITIALIZATION,
    -> false
}

/**
 * 返回栈中的一帧，用来恢复上一页和当时选中的物品。
 */
private data class NavigationFrame(
    val destination: AppDestination,
    val selectedItemId: ItemId?,
)

/**
 * 把备份校验失败收成用户能区分的原因，避免截断文件和密码错误共用一句。
 */
private fun restorePreviewErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        "truncated" in message || "magic" in message -> "这个文件不是有效的加密备份。"
        "Unable to read" in message || "unreadable" in message -> "没能读取选中的文件，请再选一次。"
        "hash" in message -> "备份内容校验失败，文件可能不完整。"
        "outside" in message || "does not exist" in message || "extension" in message ->
            "无法读取这个备份文件。"
        "incorrect" in message || "password" in message.lowercase() -> "密码不正确，或备份已损坏。"
        else -> "密码错误、格式不兼容或备份已损坏。"
    }
}
