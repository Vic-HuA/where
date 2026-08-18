package com.vichua.where.core.database

import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 创建指向 iOS 应用私有目录的 Room Builder。
 *
 * 路径必须由 iOS 平台层通过系统容器 API 生成，共享代码不会推测或拼接公共目录。
 *
 * @param databasePath iOS 应用私有容器中的完整数据库路径。
 * @return 尚未构建的数据库 Builder，由共享工厂统一配置驱动。
 */
fun createIosDatabaseBuilder(
    databasePath: String,
): RoomDatabase.Builder<WhereDatabase> {
    require(databasePath.isNotBlank()) { "iOS database path must not be blank." }
    return Room.databaseBuilder<WhereDatabase>(
        name = databasePath,
    )
}
