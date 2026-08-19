package com.vichua.where.feature.item.detail

import com.vichua.where.core.model.ItemId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证物品详情用例的存在性处理。
 */
class LoadItemDetailUseCaseTest {
    /** 验证存在的详情原样返回。 */
    @Test
    fun `存在详情时返回本地数据`() = runTest {
        val detail = createDetail()
        val useCase = LoadItemDetailUseCase(
            repository = FakeItemDetailRepository(detail),
        )

        assertEquals(detail, useCase(detail.itemId))
    }

    /** 验证不存在或已删除物品会返回明确错误。 */
    @Test
    fun `不存在详情时必须失败`() = runTest {
        val useCase = LoadItemDetailUseCase(
            repository = FakeItemDetailRepository(null),
        )

        assertFailsWith<IllegalArgumentException> {
            useCase(ItemId("missing"))
        }
    }

    /** 创建最小有效详情测试对象。 */
    private fun createDetail(): ItemDetail = ItemDetail(
        itemId = ItemId("item"),
        name = "护照",
        aliases = emptyList(),
        categoryName = null,
        quantity = 1.0,
        unit = null,
        locationPath = "卧室",
        locationDescription = null,
        note = null,
        updatedAt = com.vichua.where.core.model.UtcTimestamp(1_000L),
        sourceDeviceName = "测试设备",
        photos = emptyList(),
        locationHistory = emptyList(),
    )

    /** 返回固定详情的内存仓储。 */
    private class FakeItemDetailRepository(
        private val detail: ItemDetail?,
    ) : ItemDetailRepository {
        override suspend fun find(itemId: ItemId): ItemDetail? = detail
    }
}
