package com.vichua.where.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 当前设备备份文件引用的 Room 持久化结构。
 *
 * 只保存在本机，避免把文档 URI 和摘要写进家庭数据包。
 */
@Entity(
    tableName = "local_backup_records",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["device_id", "created_at"]),
        Index(value = ["household_id", "status", "created_at"]),
    ],
)
data class LocalBackupRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "household_id")
    val householdId: String,
    @ColumnInfo(name = "package_hash")
    val packageHash: String?,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long?,
    val status: String,
    @ColumnInfo(name = "opaque_document_uri")
    val opaqueDocumentUri: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "verified_at")
    val verifiedAt: Long?,
)
