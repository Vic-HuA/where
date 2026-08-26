package com.vichua.where.platform.android

import com.vichua.where.core.database.transaction.VoiceLabelStore
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.VoiceLabelAsset
import com.vichua.where.feature.location.voice.VoiceLabelContext
import com.vichua.where.feature.location.voice.VoiceLabelRepository

/**
 * 使用共享 Room Store 实现 Android 语音名称仓储。
 */
class RoomVoiceLabelRepository(
    private val store: VoiceLabelStore,
) : VoiceLabelRepository {
    override suspend fun loadForLocation(locationId: LocationNodeId): VoiceLabelContext {
        val context = store.loadForLocation(locationId)
        return VoiceLabelContext(
            householdId = context.householdId,
            locationNodeId = context.locationNodeId,
            itemId = context.itemId,
            labels = context.labels,
            currentDeviceId = context.currentDeviceId,
        )
    }

    override suspend fun loadForItem(itemId: ItemId): VoiceLabelContext {
        val context = store.loadForItem(itemId)
        return VoiceLabelContext(
            householdId = context.householdId,
            locationNodeId = context.locationNodeId,
            itemId = context.itemId,
            labels = context.labels,
            currentDeviceId = context.currentDeviceId,
        )
    }

    override suspend fun replace(
        newLabel: VoiceLabelAsset,
        retiredLabels: List<VoiceLabelAsset>,
        changeRecords: List<ChangeRecord>,
    ) {
        store.replace(
            newLabel = newLabel,
            retiredLabels = retiredLabels,
            changeRecords = changeRecords,
        )
    }

    override suspend fun delete(
        updatedLabel: VoiceLabelAsset,
        changeRecord: ChangeRecord,
    ) {
        store.delete(
            updatedLabel = updatedLabel,
            changeRecord = changeRecord,
        )
    }
}
