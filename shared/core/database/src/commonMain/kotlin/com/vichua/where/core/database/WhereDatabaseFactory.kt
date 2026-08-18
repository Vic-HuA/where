package com.vichua.where.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * 使用跨平台一致的 SQLite 驱动完成数据库实例配置。
 *
 * 平台层只负责提供位于应用私有目录中的 Builder；驱动、协程上下文和迁移策略在共享层统一。
 *
 * @param builder 平台根据私有文件路径创建的 Room Builder。
 * @return 已启用 Bundled SQLite 的数据库实例。
 */
fun buildWhereDatabase(
    builder: RoomDatabase.Builder<WhereDatabase>,
): WhereDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .build()
