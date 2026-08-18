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
import com.vichua.where.feature.location.initialization.HasActiveHouseholdUseCase
import com.vichua.where.feature.location.initialization.InitializeHouseholdRequest
import com.vichua.where.feature.location.initialization.InitializeHouseholdUseCase
import kotlinx.coroutines.launch

/**
 * 提供跨平台应用根界面，并根据本地家庭状态进入初始化页或首页。
 *
 * @param hasActiveHouseholdUseCase 查询本地是否已有家庭的用例。
 * @param initializeHouseholdUseCase 保存首个家庭的用例。
 * @param suggestedDeviceName 当前平台提供的设备名称建议。
 * @param devicePlatform 当前运行平台。
 */
@Composable
fun WhereApp(
    hasActiveHouseholdUseCase: HasActiveHouseholdUseCase,
    initializeHouseholdUseCase: InitializeHouseholdUseCase,
    suggestedDeviceName: String,
    devicePlatform: DevicePlatform,
) {
    var destination by remember { mutableStateOf(AppDestination.LOADING) }
    var startupAttempt by remember { mutableIntStateOf(0) }
    var initializationInProgress by remember { mutableStateOf(false) }
    var initializationError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

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
                AppDestination.HOME -> HomeScreen()
            }
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
}
