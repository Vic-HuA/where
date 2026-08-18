package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 物品照片用途。
 */
@Serializable
enum class PhotoRole {
    /** 展示房间、家具、区域或容器关系的环境照片。 */
    ENVIRONMENT,

    /** 展示具体物品外观的近照。 */
    ITEM,

    /** 用于识别品牌、型号、编号或包装文字的标签照片。 */
    LABEL,

    /** 展示其他角度、容器内部或易混淆特征的补充照片。 */
    SUPPLEMENTARY,
}

/**
 * 媒体文件完整性状态。
 */
@Serializable
enum class MediaIntegrityStatus {
    /** 文件存在，并且最近一次摘要和格式校验通过。 */
    AVAILABLE,

    /** 数据库记录存在，但对应文件缺失。 */
    MISSING,

    /** 文件存在，但摘要或格式校验失败。 */
    CORRUPTED,

    /** 文件尚未完成完整性校验。 */
    UNCHECKED,
}

/**
 * 关联到单个物品的照片资产。
 *
 * 数据库只保存受控文件标识和元数据，不保存原始图片二进制或任意外部路径。
 *
 * @property id 照片记录全局唯一标识。
 * @property householdId 照片所属家庭。
 * @property itemId 照片直接所属的唯一物品。
 * @property role 照片用途。
 * @property storageKey 应用私有目录中的受控原图文件标识。
 * @property thumbnailStorageKey 应用私有目录中的受控缩略图文件标识。
 * @property mimeType 图片 MIME 类型。
 * @property width 原图像素宽度。
 * @property height 原图像素高度。
 * @property sizeBytes 原图文件字节数。
 * @property contentHash 原图内容摘要。
 * @property integrityStatus 当前媒体完整性状态。
 * @property lastIntegrityCheckedAt 最近一次完整性检查时间，从未检查时为空。
 * @property sortOrder 物品画廊中的稳定顺序。
 * @property isCover 是否为该物品的封面照片。
 * @property capturedAt 原始拍摄时间，无法获得时为空。
 * @property createdAt 照片导入应用的时间。
 * @property updatedAt 照片元数据最近更新时间。
 * @property version 当前实体版本。
 * @property sourceDeviceId 导入或最近修改该照片的设备。
 * @property deletedAt 软删除时间，未删除时为空。
 */
@Serializable
data class PhotoAsset(
    val id: PhotoAssetId,
    val householdId: HouseholdId,
    val itemId: ItemId,
    val role: PhotoRole,
    val storageKey: String,
    val thumbnailStorageKey: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val contentHash: String,
    val integrityStatus: MediaIntegrityStatus = MediaIntegrityStatus.UNCHECKED,
    val lastIntegrityCheckedAt: UtcTimestamp? = null,
    val sortOrder: SortOrder,
    val isCover: Boolean,
    val capturedAt: UtcTimestamp? = null,
    val createdAt: UtcTimestamp,
    val updatedAt: UtcTimestamp,
    val version: EntityVersion,
    val sourceDeviceId: DeviceId,
    val deletedAt: UtcTimestamp? = null,
) {
    init {
        require(storageKey.isNotBlank()) { "Photo storage key must not be blank." }
        require(thumbnailStorageKey.isNotBlank()) {
            "Photo thumbnail storage key must not be blank."
        }
        require(mimeType.isNotBlank()) { "Photo MIME type must not be blank." }
        require(width > 0) { "Photo width must be greater than zero." }
        require(height > 0) { "Photo height must be greater than zero." }
        require(sizeBytes > 0L) { "Photo size must be greater than zero." }
        require(contentHash.isNotBlank()) { "Photo content hash must not be blank." }
        require(
            integrityStatus == MediaIntegrityStatus.UNCHECKED ||
                lastIntegrityCheckedAt != null,
        ) {
            "Checked photo integrity status requires a check timestamp."
        }
        require(lastIntegrityCheckedAt == null || lastIntegrityCheckedAt >= createdAt) {
            "Photo integrity check time must not precede import time."
        }
        require(updatedAt >= createdAt) { "Photo update time must not precede import time." }
        require(deletedAt == null || deletedAt >= createdAt) {
            "Photo deletion time must not precede import time."
        }
    }
}
