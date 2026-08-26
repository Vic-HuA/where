package com.vichua.where.platform.android

import com.vichua.where.core.database.query.ItemDetailStore
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemStatus
import com.vichua.where.feature.item.detail.ItemDetail
import com.vichua.where.feature.item.detail.ItemDetailLocationHistory
import com.vichua.where.feature.item.detail.ItemDetailPhoto
import com.vichua.where.feature.item.detail.ItemDetailRepository

/**
 * 使用共享 Room Store 实现 Android 物品详情仓储。
 */
class RoomItemDetailRepository(
    private val store: ItemDetailStore,
) : ItemDetailRepository {
    /** 查询并转换指定物品详情。 */
    override suspend fun find(itemId: ItemId): ItemDetail? {
        val detail = store.find(itemId) ?: return null
        return ItemDetail(
            itemId = detail.item.id,
            name = detail.item.name,
            aliases = detail.aliases.map { alias -> alias.alias },
            categoryId = detail.item.categoryId,
            categoryName = detail.categoryName,
            quantity = detail.item.quantity,
            unit = detail.item.unit,
            locationPath = detail.locationPath,
            locationDescription = detail.item.locationDescription,
            note = detail.item.note,
            updatedAt = detail.item.updatedAt,
            sourceDeviceName = detail.sourceDeviceName,
            photos = detail.photos.map { photo ->
                ItemDetailPhoto(
                    photoId = photo.id,
                    role = photo.role,
                    storageKey = photo.storageKey,
                    thumbnailStorageKey = photo.thumbnailStorageKey,
                    isCover = photo.isCover,
                    sortOrder = photo.sortOrder.value,
                )
            },
            locationHistory = detail.locationHistory.map { event ->
                ItemDetailLocationHistory(
                    fromPath = event.fromPathSnapshot,
                    toPath = event.toPathSnapshot,
                    reason = event.reason,
                    occurredAt = event.occurredAt,
                )
            },
            locationUnconfirmed = detail.item.status == ItemStatus.LOCATION_UNCONFIRMED,
            voiceLabelStorageKey = detail.voiceLabelStorageKey,
            isPinned = detail.isPinned,
        )
    }
}
