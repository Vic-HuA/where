package com.vichua.where.feature.search.text

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.TextNormalizer
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.LocalSearchHistory
import com.vichua.where.core.model.LocalSearchHistoryId
import com.vichua.where.core.model.UtcTimestamp

/**
 * 本地文字搜索结果摘要。
 *
 * @property itemId 物品 ID。
 * @property name 物品名称。
 * @property locationPath 当前完整位置路径。
 * @property updatedAt 最近更新时间。
 * @property thumbnailStorageKey 可选封面缩略图文件标识。
 */
data class ItemTextSearchResult(
    val itemId: ItemId,
    val name: String,
    val locationPath: String,
    val updatedAt: UtcTimestamp,
    val thumbnailStorageKey: String?,
)

/**
 * 单次本地搜索执行参数。
 *
 * @property ftsQuery 已安全转义的 SQLite FTS 查询。
 * @property history 当前设备需要保存的最近查找记录。
 */
data class ItemTextSearchExecution(
    val ftsQuery: String,
    val history: LocalSearchHistory,
)

/**
 * 本地物品文字搜索仓储契约。
 */
interface ItemTextSearchRepository {
    /**
     * 返回当前有效设备 ID，用于保存本机最近查找。
     */
    suspend fun currentDeviceId(): DeviceId

    /**
     * 执行本地 FTS 查询，并在同一设备保存去重后的最近查找。
     */
    suspend fun search(execution: ItemTextSearchExecution): List<ItemTextSearchResult>
}

/**
 * 把用户文字转换为安全 FTS 条件并执行本地搜索。
 */
class SearchItemsUseCase(
    private val repository: ItemTextSearchRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
    private val textNormalizer: TextNormalizer,
) {
    /**
     * 搜索本地已确认物品，不调用 AI，也不生成不存在的位置。
     *
     * @param query 用户输入的查询原文。
     */
    suspend operator fun invoke(query: String): List<ItemTextSearchResult> {
        val displayQuery = query.trim()
        val normalizedQuery = textNormalizer.normalize(displayQuery)
        require(displayQuery.isNotEmpty()) { "Search query must not be blank." }
        require(normalizedQuery.isNotEmpty()) { "Normalized search query must not be blank." }

        val history = LocalSearchHistory(
            id = LocalSearchHistoryId(idGenerator.generate()),
            deviceId = repository.currentDeviceId(),
            displayQuery = displayQuery,
            normalizedQuery = normalizedQuery,
            filtersPayload = EMPTY_FILTERS_PAYLOAD,
            filterHash = EMPTY_FILTERS_HASH,
            executedAt = UtcTimestamp(clock.now()),
        )
        return repository.search(
            ItemTextSearchExecution(
                ftsQuery = buildFtsQuery(normalizedQuery),
                history = history,
            ),
        )
    }

    /**
     * 把标准化文本拆为带前缀匹配的安全 FTS 词元。
     */
    private fun buildFtsQuery(normalizedQuery: String): String =
        normalizedQuery
            .split(' ')
            .filter(String::isNotBlank)
            .joinToString(separator = " AND ") { token ->
                val escapedToken = token.replace("\"", "\"\"")
                "\"$escapedToken\"*"
            }

    private companion object {
        const val EMPTY_FILTERS_PAYLOAD = "{}"
        const val EMPTY_FILTERS_HASH = "none"
    }
}
