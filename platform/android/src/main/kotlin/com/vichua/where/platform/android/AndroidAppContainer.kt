package com.vichua.where.platform.android

import android.content.Context
import android.os.Build
import com.vichua.where.core.common.DefaultTextNormalizer
import com.vichua.where.core.database.WhereDatabase
import com.vichua.where.core.database.buildWhereDatabase
import com.vichua.where.core.database.createAndroidDatabaseBuilder
import com.vichua.where.core.database.transaction.HouseholdInitializationStore
import com.vichua.where.feature.location.initialization.HasActiveHouseholdUseCase
import com.vichua.where.feature.location.initialization.InitializeHouseholdUseCase

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

    /** 查询启动时是否已有家庭的用例。 */
    val hasActiveHouseholdUseCase = HasActiveHouseholdUseCase(initializationRepository)

    /** 创建首个家庭、当前设备和基础位置树的用例。 */
    val initializeHouseholdUseCase = InitializeHouseholdUseCase(
        repository = initializationRepository,
        idGenerator = AndroidUniqueIdGenerator(),
        clock = AndroidEpochMillisecondsClock,
        textNormalizer = DefaultTextNormalizer,
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
