package com.vichua.where.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vichua.where.core.model.DevicePlatform
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.MvpLimits
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.PhotoRole
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
import com.vichua.where.feature.item.photo.SetItemPhotoCoverUseCase
import com.vichua.where.feature.item.photo.UpdateItemPhotoRoleUseCase
import com.vichua.where.feature.location.initialization.HasActiveHouseholdUseCase
import com.vichua.where.feature.location.initialization.InitializeHouseholdRequest
import com.vichua.where.feature.location.initialization.InitializeHouseholdUseCase
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
import com.vichua.where.core.model.ConflictResolution
import com.vichua.where.core.model.ExportDestination
import com.vichua.where.core.model.HouseholdDataSummary
import com.vichua.where.core.model.RestoreMode
import com.vichua.where.core.model.RestoreSession
import com.vichua.where.feature.backup.ApplyBackupRestoreUseCase
import com.vichua.where.feature.backup.ClearHouseholdDataUseCase
import com.vichua.where.feature.backup.CreateEncryptedBackupUseCase
import com.vichua.where.feature.backup.ExportHouseholdDataUseCase
import com.vichua.where.feature.backup.LoadLatestBackupStatusUseCase
import com.vichua.where.feature.backup.PreviewBackupRestoreUseCase
import com.vichua.where.feature.backup.VerifyBackupPackageUseCase
import com.vichua.where.feature.search.home.HomeSnapshot
import com.vichua.where.feature.search.home.LoadHomeSnapshotUseCase
import com.vichua.where.feature.search.text.ItemTextSearchResult
import com.vichua.where.feature.search.text.SearchItemsUseCase
import com.vichua.where.feature.settings.accessibility.LoadAccessibilityPreferencesUseCase
import com.vichua.where.feature.settings.accessibility.UpdateAccessibilityPreferencesUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 提供跨平台应用根界面，并根据本地家庭状态进入初始化页或首页。
 *
 * @param hasActiveHouseholdUseCase 查询本地是否已有家庭的用例。
 * @param initializeHouseholdUseCase 保存首个家庭的用例。
 * @param loadHomeSnapshotUseCase 加载首页本地摘要的用例。
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
 * @param shareGateway 系统分享面板入口。
 * @param loadMoveItemContextUseCase 加载更新位置上下文的用例。
 * @param moveItemUseCase 保存物品新位置的用例。
 * @param loadLocationTreeUseCase 加载位置管理树的用例。
 * @param createLocationUseCase 新增位置的用例。
 * @param renameLocationUseCase 重命名位置的用例。
 * @param deleteEmptyLocationUseCase 删除空位置的用例。
 * @param loadAccessibilityPreferencesUseCase 读取当前设备适老偏好的用例。
 * @param updateAccessibilityPreferencesUseCase 更新当前设备适老偏好的用例。
 * @param loadLatestBackupStatusUseCase 读取最近成功备份状态的用例。
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
    deleteItemPhotoUseCase: DeleteItemPhotoUseCase,
    deleteItemUseCase: DeleteItemUseCase,
    restoreDeletedItemUseCase: RestoreDeletedItemUseCase,
    buildItemLocationSpeechUseCase: BuildItemLocationSpeechUseCase,
    buildItemLocationShareUseCase: BuildItemLocationShareUseCase,
    textToSpeechGateway: TextToSpeechGateway,
    shareGateway: ShareGateway,
    loadMoveItemContextUseCase: LoadMoveItemContextUseCase,
    moveItemUseCase: MoveItemUseCase,
    loadLocationTreeUseCase: LoadLocationTreeUseCase,
    createLocationUseCase: CreateLocationUseCase,
    renameLocationUseCase: RenameLocationUseCase,
    deleteEmptyLocationUseCase: DeleteEmptyLocationUseCase,
    loadAccessibilityPreferencesUseCase: LoadAccessibilityPreferencesUseCase,
    updateAccessibilityPreferencesUseCase: UpdateAccessibilityPreferencesUseCase,
    loadLatestBackupStatusUseCase: LoadLatestBackupStatusUseCase,
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
    var moveItemContext by remember { mutableStateOf<MoveItemContext?>(null) }
    var moveItemLoading by remember { mutableStateOf(false) }
    var moveItemError by remember { mutableStateOf<String?>(null) }
    var locationTree by remember { mutableStateOf<LocationTreeSnapshot?>(null) }
    var locationTreeLoading by remember { mutableStateOf(false) }
    var locationTreeSubmitting by remember { mutableStateOf(false) }
    var locationTreeError by remember { mutableStateOf<String?>(null) }
    var locationTreeAttempt by remember { mutableIntStateOf(0) }
    var accessibilityPreferences by remember {
        mutableStateOf<LocalAccessibilityPreferences?>(null)
    }
    var settingsLoading by remember { mutableStateOf(false) }
    var settingsSubmitting by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var settingsLoadAttempt by remember { mutableIntStateOf(0) }
    var latestBackupStatus by remember { mutableStateOf<LatestBackupStatus?>(null) }
    var backupSubmitting by remember { mutableStateOf(false) }
    var backupProgressText by remember { mutableStateOf<String?>(null) }
    var backupVerificationResult by remember { mutableStateOf<BackupVerificationResult?>(null) }
    var householdSummary by remember { mutableStateOf<HouseholdDataSummary?>(null) }
    var restoreSession by remember { mutableStateOf<RestoreSession?>(null) }
    var conflictResolutions by remember { mutableStateOf<Map<String, ConflictResolution>>(emptyMap()) }
    var searchSpeechError by remember { mutableStateOf<String?>(null) }
    val elderFriendlyMode = accessibilityPreferences?.elderFriendly == true
    val coroutineScope = rememberCoroutineScope()
    val performSearch: (String) -> Unit = { query ->
        if (!searchInProgress) {
            searchQuery = query
            coroutineScope.launch {
                searchInProgress = true
                searchError = null
                try {
                    searchResults = searchItemsUseCase(query)
                    homeLoadAttempt += 1
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
        if (destination == AppDestination.ITEM_DETAIL && itemId != null) {
            itemDetailLoading = true
            itemDetailError = null
            itemProfileEditorVisible = false
            itemProfileError = null
            itemPhotoError = null
            itemSpeechError = null
            itemSpeechCanRepeat = false
            itemShareError = null
            textToSpeechGateway.stop()
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
            locationTreeLoading = true
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
            homeLoading = true
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

    LaunchedEffect(destination, settingsLoadAttempt) {
        val shouldLoadPreferences = destination == AppDestination.HOME ||
            destination == AppDestination.SETTINGS ||
            destination == AppDestination.SEARCH ||
            destination == AppDestination.ADD_ITEM ||
            destination == AppDestination.ITEM_DETAIL ||
            destination == AppDestination.MOVE_ITEM
        if (shouldLoadPreferences && accessibilityPreferences == null) {
            settingsLoading = true
            settingsError = null
            try {
                accessibilityPreferences = loadAccessibilityPreferencesUseCase()
            } catch (_: Exception) {
                settingsError = "暂时无法读取辅助设置。"
            } finally {
                settingsLoading = false
            }
        }
        if (destination == AppDestination.SETTINGS) {
            try {
                latestBackupStatus = loadLatestBackupStatusUseCase()
                householdSummary = clearHouseholdDataUseCase.loadSummary()
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
            when (destination) {
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
                                    destination = AppDestination.HOME
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
                        destination = AppDestination.SEARCH
                        performSearch(query)
                    },
                    onVoiceSearchRequested = {
                        searchResults = null
                        searchError = "语音识别尚未接入，请先使用键盘输入。"
                        destination = AppDestination.SEARCH
                    },
                    onItemClick = { item ->
                        selectedItemId = item.itemId
                        destination = AppDestination.ITEM_DETAIL
                    },
                    onRecordItemClick = {
                        destination = AppDestination.ADD_ITEM
                    },
                    onLocationClick = {
                        destination = AppDestination.LOCATION
                    },
                    onSettingsClick = {
                        destination = AppDestination.SETTINGS
                    },
                    elderFriendlyMode = elderFriendlyMode,
                )
                AppDestination.SEARCH -> SearchScreen(
                    initialQuery = searchQuery,
                    results = searchResults,
                    searching = searchInProgress,
                    errorMessage = searchError,
                    onBack = {
                        destination = AppDestination.HOME
                    },
                    onSearch = performSearch,
                    onResultClick = { result ->
                        selectedItemId = result.itemId
                        destination = AppDestination.ITEM_DETAIL
                    },
                    elderFriendlyMode = elderFriendlyMode,
                    speechErrorMessage = searchSpeechError,
                    onReadLocation = { result ->
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
                    onRetry = {
                        itemCreationAttempt += 1
                    },
                    onBack = {
                        destination = AppDestination.HOME
                    },
                    onPickPhoto = { role ->
                        if (!itemCreationSubmitting) {
                            coroutineScope.launch {
                                itemCreationError = null
                                try {
                                    val pickedImage = photoPickerGateway.pickImage() ?: return@launch
                                    pendingItemPhotos = pendingItemPhotos + importItemPhotoUseCase(
                                        bytes = pickedImage.bytes,
                                        sourceMimeType = pickedImage.mimeType,
                                        role = role,
                                    )
                                } catch (_: IllegalArgumentException) {
                                    itemCreationError = "无法使用所选照片，请换一张后重试。"
                                } catch (_: Exception) {
                                    itemCreationError = "照片导入失败，请稍后重试。"
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
                                    destination = AppDestination.HOME
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
                                    destination = AppDestination.HOME
                                } catch (_: Exception) {
                                    itemCreationError = "放弃草稿失败，请稍后重试。"
                                } finally {
                                    itemCreationSubmitting = false
                                }
                            }
                        }
                    },
                    elderFriendlyMode = elderFriendlyMode,
                    onSpeakRequested = {
                        itemCreationError = "语音识别尚未接入，请先手动填写名称。"
                    },
                    onSubmit = { request ->
                        if (!itemCreationSubmitting) {
                            coroutineScope.launch {
                                itemCreationSubmitting = true
                                itemCreationError = null
                                try {
                                    createManualItemUseCase(
                                        request.copy(photos = pendingItemPhotos),
                                    )
                                    pendingItemPhotos = emptyList()
                                    itemDraft = null
                                    homeLoadAttempt += 1
                                    destination = AppDestination.HOME
                                } catch (_: IllegalArgumentException) {
                                    itemCreationError = "请检查物品名称和所在位置。"
                                } catch (_: Exception) {
                                    itemCreationError = "保存失败，请稍后重试。"
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
                    onBack = {
                        destination = AppDestination.HOME
                    },
                    onCreate = { request ->
                        if (!locationTreeSubmitting) {
                            coroutineScope.launch {
                                locationTreeSubmitting = true
                                locationTreeError = null
                                try {
                                    createLocationUseCase(request)
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
                )
                AppDestination.SETTINGS -> SettingsScreen(
                    preferences = accessibilityPreferences,
                    loading = settingsLoading,
                    submitting = settingsSubmitting || backupSubmitting,
                    errorMessage = settingsError,
                    lastVerifiedBackupText = latestBackupStatus?.lastVerifiedAt?.let { timestamp ->
                        "最近成功备份：${visibleDateTimeFormatter.format(timestamp.epochMilliseconds)}"
                    },
                    backupProgressText = backupProgressText,
                    verificationResult = backupVerificationResult,
                    householdSummary = householdSummary,
                    restoreSession = restoreSession,
                    conflictResolutions = conflictResolutions,
                    onBack = {
                        destination = AppDestination.HOME
                    },
                    onRetry = {
                        accessibilityPreferences = null
                        settingsLoadAttempt += 1
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
                    onCreateBackup = { password, confirmation ->
                        if (!backupSubmitting) {
                            coroutineScope.launch {
                                backupSubmitting = true
                                backupProgressText = "正在创建加密备份…"
                                settingsError = null
                                backupVerificationResult = null
                                try {
                                    val result = createEncryptedBackupUseCase(password, confirmation)
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
                                    settingsError = "创建备份失败，请稍后重试。"
                                    backupProgressText = null
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
                                backupProgressText = when (destination) {
                                    ExportDestination.SAVE_DOCUMENT -> "正在导出完整家庭数据…"
                                    ExportDestination.SHARE -> "正在准备分享导出包…"
                                }
                                settingsError = null
                                backupVerificationResult = null
                                try {
                                    val result = exportHouseholdDataUseCase(
                                        password,
                                        confirmation,
                                        destination,
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
                    onVerifyBackup = { password ->
                        if (!backupSubmitting) {
                            coroutineScope.launch {
                                backupSubmitting = true
                                backupProgressText = "正在验证备份…"
                                settingsError = null
                                backupVerificationResult = null
                                try {
                                    val result = verifyBackupPackageUseCase(password)
                                    if (result == null) {
                                        backupProgressText = null
                                        return@launch
                                    }
                                    latestBackupStatus = loadLatestBackupStatusUseCase()
                                    backupVerificationResult = result
                                    backupProgressText = null
                                } catch (_: IllegalArgumentException) {
                                    settingsError = "密码错误、格式不兼容或备份已损坏。"
                                    backupProgressText = null
                                } catch (_: Exception) {
                                    settingsError = "验证备份失败，请稍后重试。"
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
                    onRestoreBackup = { password ->
                        if (!backupSubmitting) {
                            coroutineScope.launch {
                                backupSubmitting = true
                                backupProgressText = "正在准备恢复预览…"
                                settingsError = null
                                restoreSession = null
                                conflictResolutions = emptyMap()
                                try {
                                    val session = previewBackupRestoreUseCase(password)
                                    if (session == null) {
                                        backupProgressText = null
                                        return@launch
                                    }
                                    restoreSession = session
                                    householdSummary = session.preview.currentSummary
                                    backupProgressText = null
                                } catch (_: IllegalArgumentException) {
                                    settingsError = "密码错误、格式不兼容或备份已损坏。"
                                    backupProgressText = null
                                } catch (_: Exception) {
                                    settingsError = "恢复预览失败，请稍后重试。"
                                    backupProgressText = null
                                } finally {
                                    backupSubmitting = false
                                }
                            }
                        }
                    },
                    onDismissRestore = {
                        restoreSession = null
                        conflictResolutions = emptyMap()
                    },
                    onResolveConflict = { key, resolution ->
                        conflictResolutions = conflictResolutions + (key to resolution)
                    },
                    onApplyRestore = { mode ->
                        val session = restoreSession
                        if (!backupSubmitting && session != null) {
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
                                    backupProgressText = if (mode == RestoreMode.REPLACE) {
                                        "已用备份替换当前家庭。"
                                    } else {
                                        "已合并备份到当前家庭。"
                                    }
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
                    },
                    onClearHousehold = {
                        if (!backupSubmitting) {
                            coroutineScope.launch {
                                backupSubmitting = true
                                backupProgressText = "正在清除家庭数据…"
                                settingsError = null
                                try {
                                    clearHouseholdDataUseCase()
                                    restoreSession = null
                                    conflictResolutions = emptyMap()
                                    homeSnapshot = null
                                    destination = AppDestination.INITIALIZATION
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
                AppDestination.ITEM_DETAIL -> ItemDetailScreen(
                    detail = itemDetail,
                    resolveMediaPath = resolveMediaPath,
                    loading = itemDetailLoading,
                    errorMessage = itemDetailError,
                    onBack = {
                        textToSpeechGateway.stop()
                        itemProfileEditorVisible = false
                        itemProfileError = null
                        itemSpeechError = null
                        itemShareError = null
                        destination = AppDestination.HOME
                    },
                    onReadLocation = {
                        val detail = itemDetail
                        if (detail != null && !itemSpeechSubmitting) {
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
                        destination = AppDestination.MOVE_ITEM
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
                    onAddPhoto = { role ->
                        val itemId = selectedItemId
                        if (itemId != null && !itemPhotoSubmitting) {
                            coroutineScope.launch {
                                itemPhotoSubmitting = true
                                itemPhotoError = null
                                var importedPhoto: ImportedItemPhoto? = null
                                try {
                                    val pickedImage = photoPickerGateway.pickImage() ?: return@launch
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
                                    destination = AppDestination.HOME
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
                AppDestination.MOVE_ITEM -> MoveItemScreen(
                    context = moveItemContext,
                    loading = moveItemLoading,
                    errorMessage = moveItemError,
                    onBack = {
                        destination = AppDestination.ITEM_DETAIL
                    },
                    elderFriendlyMode = elderFriendlyMode,
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
                                    destination = AppDestination.ITEM_DETAIL
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
    SEARCH,
    ADD_ITEM,
    LOCATION,
    SETTINGS,
    ITEM_DETAIL,
    MOVE_ITEM,
}
