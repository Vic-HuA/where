package com.vichua.where.feature.item.detail

import com.vichua.where.core.model.CategoryId
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemLocationReason
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.model.UtcTimestamp

/**
 * 详情页单张照片摘要。
 *
 * @property photoId 照片记录 ID。
 * @property role 照片用途。
 * @property storageKey 受控原图文件标识。
 * @property thumbnailStorageKey 受控缩略图文件标识。
 * @property isCover 是否为封面。
 * @property sortOrder 画廊顺序。
 */
data class ItemDetailPhoto(
    val photoId: PhotoAssetId,
    val role: PhotoRole,
    val storageKey: String,
    val thumbnailStorageKey: String,
    val isCover: Boolean,
    val sortOrder: Int,
)

/**
 * 详情页单条位置历史摘要。
 *
 * @property fromPath 原位置路径，首次记录时为空。
 * @property toPath 新位置路径。
 * @property reason 位置变化原因。
 * @property occurredAt 发生时间。
 */
data class ItemDetailLocationHistory(
    val fromPath: String?,
    val toPath: String,
    val reason: ItemLocationReason,
    val occurredAt: UtcTimestamp,
)

/**
 * 物品详情页所需的完整只读数据。
 *
 * @property itemId 物品 ID。
 * @property name 物品名称。
 * @property aliases 未删除别名。
 * @property categoryName 可选分类名称。
 * @property quantity 数量。
 * @property unit 可选单位。
 * @property locationPath 当前完整位置路径。
 * @property locationDescription 可选位置补充说明。
 * @property note 可选备注。
 * @property updatedAt 最近更新时间。
 * @property sourceDeviceName 来源设备名称。
 * @property photos 按画廊顺序排列的未删除照片。
 * @property locationHistory 按时间倒序排列的位置历史。
 * @property locationUnconfirmed 原位置失效后是否仍待用户重新确认。
 */
data class ItemDetail(
    val itemId: ItemId,
    val name: String,
    val aliases: List<String>,
    val categoryId: CategoryId? = null,
    val categoryName: String?,
    val quantity: Double,
    val unit: String?,
    val locationPath: String,
    val locationDescription: String?,
    val note: String?,
    val updatedAt: UtcTimestamp,
    val sourceDeviceName: String,
    val photos: List<ItemDetailPhoto>,
    val locationHistory: List<ItemDetailLocationHistory>,
    val locationUnconfirmed: Boolean = false,
    val voiceLabelStorageKey: String? = null,
    val isPinned: Boolean = false,
)

/**
 * 物品详情本地仓储契约。
 */
interface ItemDetailRepository {
    /**
     * 查询指定未删除物品的详情，不存在时返回空。
     */
    suspend fun find(itemId: ItemId): ItemDetail?
}

/**
 * 加载指定物品的本地详情。
 */
class LoadItemDetailUseCase(
    private val repository: ItemDetailRepository,
) {
    /**
     * 返回物品详情；物品不存在或已删除时抛出明确错误。
     */
    suspend operator fun invoke(itemId: ItemId): ItemDetail =
        requireNotNull(repository.find(itemId)) {
            "Requested item detail does not exist."
        }
}
