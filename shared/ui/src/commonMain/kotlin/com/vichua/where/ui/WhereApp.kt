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
import com.vichua.where.feature.item.creation.CreateManualItemUseCase
import com.vichua.where.feature.item.creation.ItemCreationContext
import com.vichua.where.feature.item.creation.LoadItemCreationContextUseCase
import com.vichua.where.feature.item.detail.ItemDetail
import com.vichua.where.feature.item.detail.LoadItemDetailUseCase
import com.vichua.where.feature.location.initialization.HasActiveHouseholdUseCase
import com.vichua.where.feature.location.initialization.InitializeHouseholdRequest
import com.vichua.where.feature.location.initialization.InitializeHouseholdUseCase
import com.vichua.where.feature.location.movement.LoadMoveItemContextUseCase
import com.vichua.where.feature.location.movement.MoveItemContext
import com.vichua.where.feature.location.movement.MoveItemUseCase
import com.vichua.where.feature.search.home.HomeSnapshot
import com.vichua.where.feature.search.home.LoadHomeSnapshotUseCase
import com.vichua.where.feature.search.text.ItemTextSearchResult
import com.vichua.where.feature.search.text.SearchItemsUseCase
import kotlinx.coroutines.launch

/**
 * 提供跨平台应用根界面，并根据本地家庭状态进入初始化页或首页。
 *
 * @param hasActiveHouseholdUseCase 查询本地是否已有家庭的用例。
 * @param initializeHouseholdUseCase 保存首个家庭的用例。
 * @param loadHomeSnapshotUseCase 加载首页本地摘要的用例。
 * @param loadItemCreationContextUseCase 加载新增物品可选位置的用例。
 * @param createManualItemUseCase 保存基础手动物品的用例。
 * @param searchItemsUseCase 执行本地文字搜索的用例。
 * @param loadItemDetailUseCase 加载物品详情的用例。
 * @param loadMoveItemContextUseCase 加载更新位置上下文的用例。
 * @param moveItemUseCase 保存物品新位置的用例。
 * @param suggestedDeviceName 当前平台提供的设备名称建议。
 * @param devicePlatform 当前运行平台。
 */
@Composable
fun WhereApp(
    hasActiveHouseholdUseCase: HasActiveHouseholdUseCase,
    initializeHouseholdUseCase: InitializeHouseholdUseCase,
    loadHomeSnapshotUseCase: LoadHomeSnapshotUseCase,
    loadItemCreationContextUseCase: LoadItemCreationContextUseCase,
    createManualItemUseCase: CreateManualItemUseCase,
    searchItemsUseCase: SearchItemsUseCase,
    loadItemDetailUseCase: LoadItemDetailUseCase,
    loadMoveItemContextUseCase: LoadMoveItemContextUseCase,
    moveItemUseCase: MoveItemUseCase,
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
    var searchResults by remember { mutableStateOf<List<ItemTextSearchResult>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchInProgress by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var selectedItemId by remember { mutableStateOf<ItemId?>(null) }
    var itemDetail by remember { mutableStateOf<ItemDetail?>(null) }
    var itemDetailLoading by remember { mutableStateOf(false) }
    var itemDetailError by remember { mutableStateOf<String?>(null) }
    var moveItemContext by remember { mutableStateOf<MoveItemContext?>(null) }
    var moveItemLoading by remember { mutableStateOf(false) }
    var moveItemError by remember { mutableStateOf<String?>(null) }
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

    WhereTheme {
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
                    onSubmit = { request ->
                        if (!initializationInProgress) {
                            coroutineScope.launch {
                                initializationInProgress = true
                                initializationError = null
                                try {
                                    initializeHouseholdUseCase(request)
                                    destination = AppDestination.HOME
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
                    loading = homeLoading,
                    errorMessage = homeError,
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
                )
                AppDestination.ADD_ITEM -> AddItemScreen(
                    context = itemCreationContext,
                    loading = itemCreationLoading,
                    submitting = itemCreationSubmitting,
                    errorMessage = itemCreationError,
                    onRetry = {
                        itemCreationAttempt += 1
                    },
                    onBack = {
                        destination = AppDestination.HOME
                    },
                    onSubmit = { request ->
                        if (!itemCreationSubmitting) {
                            coroutineScope.launch {
                                itemCreationSubmitting = true
                                itemCreationError = null
                                try {
                                    createManualItemUseCase(request)
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
                AppDestination.LOCATION -> PendingFeatureScreen(
                    title = "位置管理",
                    onBack = {
                        destination = AppDestination.HOME
                    },
                )
                AppDestination.SETTINGS -> PendingFeatureScreen(
                    title = "设置与数据",
                    onBack = {
                        destination = AppDestination.HOME
                    },
                )
                AppDestination.ITEM_DETAIL -> ItemDetailScreen(
                    detail = itemDetail,
                    loading = itemDetailLoading,
                    errorMessage = itemDetailError,
                    onBack = {
                        destination = AppDestination.HOME
                    },
                    onReadLocation = {},
                    onUpdateLocation = {
                        destination = AppDestination.MOVE_ITEM
                    },
                )
                AppDestination.MOVE_ITEM -> MoveItemScreen(
                    context = moveItemContext,
                    loading = moveItemLoading,
                    errorMessage = moveItemError,
                    onBack = {
                        destination = AppDestination.ITEM_DETAIL
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
