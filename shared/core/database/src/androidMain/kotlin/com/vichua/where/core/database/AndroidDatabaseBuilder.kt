package com.vichua.where.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 创建指向 Android 应用私有数据库目录的 Room Builder。
 *
 * 使用 `applicationContext` 避免数据库持有 Activity，并且不接受外部文件路径，防止家庭数据写入公共目录。
 *
 * @param context Android 应用或组件上下文。
 * @return 尚未构建的数据库 Builder，由共享工厂统一配置驱动。
 */
fun createAndroidDatabaseBuilder(
    context: Context,
): RoomDatabase.Builder<WhereDatabase> {
    val applicationContext = context.applicationContext
    val databaseFile = applicationContext.getDatabasePath(WhereDatabase.FILE_NAME)
    return Room.databaseBuilder<WhereDatabase>(
        context = applicationContext,
        name = databaseFile.absolutePath,
    )
}
