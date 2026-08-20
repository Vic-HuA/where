package com.vichua.where.core.database

import androidx.room.ConstructedBy
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.vichua.where.core.database.dao.AccessibilityPreferencesDao
import com.vichua.where.core.database.dao.CategoryDao
import com.vichua.where.core.database.dao.ChangeRecordDao
import com.vichua.where.core.database.dao.DeviceDao
import com.vichua.where.core.database.dao.HouseholdDao
import com.vichua.where.core.database.dao.HomeSupportDao
import com.vichua.where.core.database.dao.ItemAliasDao
import com.vichua.where.core.database.dao.ItemDao
import com.vichua.where.core.database.dao.ItemDraftDao
import com.vichua.where.core.database.dao.ItemLocationEventDao
import com.vichua.where.core.database.dao.ItemSearchDao
import com.vichua.where.core.database.dao.LocationNodeDao
import com.vichua.where.core.database.dao.PhotoAssetDao
import com.vichua.where.core.database.entity.CategoryEntity
import com.vichua.where.core.database.entity.ChangeRecordEntity
import com.vichua.where.core.database.entity.DeviceEntity
import com.vichua.where.core.database.entity.HouseholdEntity
import com.vichua.where.core.database.entity.FavoriteLocationEntity
import com.vichua.where.core.database.entity.ItemAliasEntity
import com.vichua.where.core.database.entity.ItemDraftEntity
import com.vichua.where.core.database.entity.ItemEntity
import com.vichua.where.core.database.entity.ItemLocationEventEntity
import com.vichua.where.core.database.entity.ItemSearchFtsEntity
import com.vichua.where.core.database.entity.LocationNodeEntity
import com.vichua.where.core.database.entity.LocalAccessibilityPreferencesEntity
import com.vichua.where.core.database.entity.LocalSearchHistoryEntity
import com.vichua.where.core.database.entity.PhotoAssetEntity

/**
 * 「在哪儿」本地家庭数据库。
 *
 * 数据库从版本 1 开始导出 Schema。后续版本必须提供显式迁移，不允许通过清空数据库升级。
 */
@Database(
    entities = [
        HouseholdEntity::class,
        DeviceEntity::class,
        LocationNodeEntity::class,
        CategoryEntity::class,
        ItemEntity::class,
        ItemAliasEntity::class,
        PhotoAssetEntity::class,
        ItemLocationEventEntity::class,
        ItemDraftEntity::class,
        ChangeRecordEntity::class,
        ItemSearchFtsEntity::class,
        FavoriteLocationEntity::class,
        LocalSearchHistoryEntity::class,
        LocalAccessibilityPreferencesEntity::class,
    ],
    version = WhereDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
@ConstructedBy(WhereDatabaseConstructor::class)
abstract class WhereDatabase : RoomDatabase() {
    /** 返回家庭根记录 DAO。 */
    abstract fun householdDao(): HouseholdDao

    /** 返回设备 DAO。 */
    abstract fun deviceDao(): DeviceDao

    /** 返回位置树 DAO。 */
    abstract fun locationNodeDao(): LocationNodeDao

    /** 返回物品分类 DAO。 */
    abstract fun categoryDao(): CategoryDao

    /** 返回物品档案 DAO。 */
    abstract fun itemDao(): ItemDao

    /** 返回物品别名 DAO。 */
    abstract fun itemAliasDao(): ItemAliasDao

    /** 返回照片元数据 DAO。 */
    abstract fun photoAssetDao(): PhotoAssetDao

    /** 返回位置历史 DAO。 */
    abstract fun itemLocationEventDao(): ItemLocationEventDao

    /** 返回设备本地草稿 DAO。 */
    abstract fun itemDraftDao(): ItemDraftDao

    /** 返回正式数据变更记录 DAO。 */
    abstract fun changeRecordDao(): ChangeRecordDao

    /** 返回可重建全文索引 DAO。 */
    abstract fun itemSearchDao(): ItemSearchDao

    /** 返回首页常用位置和本机最近查找 DAO。 */
    abstract fun homeSupportDao(): HomeSupportDao

    /** 返回当前设备适老与辅助偏好 DAO。 */
    abstract fun accessibilityPreferencesDao(): AccessibilityPreferencesDao

    companion object {
        /** 当前 Room Schema 版本。 */
        const val VERSION = 3

        /** 各平台使用的稳定数据库文件名。 */
        const val FILE_NAME = "where.db"
    }
}

/**
 * 由 Room KSP 为各平台生成的数据库构造器。
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object WhereDatabaseConstructor : RoomDatabaseConstructor<WhereDatabase> {
    override fun initialize(): WhereDatabase
}
