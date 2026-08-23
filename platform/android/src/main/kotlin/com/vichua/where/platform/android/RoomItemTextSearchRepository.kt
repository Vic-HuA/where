package com.vichua.where.platform.android

import com.vichua.where.core.database.query.ItemTextSearchStore
import com.vichua.where.core.model.DeviceId
import com.vichua.where.feature.search.text.ItemTextSearchExecution
import com.vichua.where.feature.search.text.ItemTextSearchRepository
import com.vichua.where.feature.search.text.ItemTextSearchResult

/**
 * 使用共享 Room FTS Store 实现 Android 本地文字搜索仓储。
 */
class RoomItemTextSearchRepository(
    private val store: ItemTextSearchStore,
) : ItemTextSearchRepository {
    /** 返回当前有效设备 ID。 */
    override suspend fun currentDeviceId(): DeviceId = store.currentDeviceId()

    /** 执行本地查询并转换为功能层结果。 */
    override suspend fun search(
        execution: ItemTextSearchExecution,
    ): List<ItemTextSearchResult> = store.search(
        ftsQuery = execution.ftsQuery,
        containsQuery = execution.containsQuery,
        history = execution.history,
    ).map { storedResult ->
        ItemTextSearchResult(
            itemId = storedResult.item.id,
            name = storedResult.item.name,
            locationPath = storedResult.locationPath,
            updatedAt = storedResult.item.updatedAt,
            thumbnailStorageKey = storedResult.thumbnailStorageKey,
        )
    }
}
