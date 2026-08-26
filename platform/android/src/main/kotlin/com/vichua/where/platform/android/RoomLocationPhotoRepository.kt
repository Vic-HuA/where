package com.vichua.where.platform.android

import com.vichua.where.core.database.transaction.LocationPhotoStore
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationPhotoAsset
import com.vichua.where.feature.location.photo.LocationPhotoContext
import com.vichua.where.feature.location.photo.LocationPhotoRepository

/**
 * 使用共享 Room Store 实现 Android 位置照片仓储。
 */
class RoomLocationPhotoRepository(
    private val store: LocationPhotoStore,
) : LocationPhotoRepository {
    /** 加载位置和全部照片。 */
    override suspend fun load(locationId: LocationNodeId): LocationPhotoContext {
        val context = store.load(locationId)
        return LocationPhotoContext(
            location = context.location,
            photos = context.photos,
            currentDeviceId = context.currentDeviceId,
        )
    }

    /** 写入新代表照并停用旧照。 */
    override suspend fun replaceCover(
        newPhoto: LocationPhotoAsset,
        retiredPhotos: List<LocationPhotoAsset>,
        changeRecords: List<ChangeRecord>,
    ) {
        store.replaceCover(
            newPhoto = newPhoto,
            retiredPhotos = retiredPhotos,
            changeRecords = changeRecords,
        )
    }

    /** 软删除当前代表照。 */
    override suspend fun deleteCover(
        updatedPhoto: LocationPhotoAsset,
        changeRecord: ChangeRecord,
    ) {
        store.deleteCover(
            updatedPhoto = updatedPhoto,
            changeRecord = changeRecord,
        )
    }
}
