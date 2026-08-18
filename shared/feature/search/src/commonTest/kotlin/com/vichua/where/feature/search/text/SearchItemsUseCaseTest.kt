package com.vichua.where.feature.search.text

import com.vichua.where.core.common.DefaultTextNormalizer
import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.DeviceId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * 验证本地文字搜索的标准化、FTS 转义和历史记录生成。
 */
class SearchItemsUseCaseTest {
    /** 验证多词查询转换为安全的前缀匹配表达式。 */
    @Test
    fun `多词查询生成安全 FTS 条件`() = runTest {
        val repository = FakeItemTextSearchRepository()
        val useCase = createUseCase(repository)

        useCase("  Travel   Passport  ")

        val execution = assertNotNull(repository.execution)
        assertEquals("\"travel\"* AND \"passport\"*", execution.ftsQuery)
        assertEquals("Travel   Passport", execution.history.displayQuery)
        assertEquals("travel passport", execution.history.normalizedQuery)
        assertEquals(TEST_DEVICE_ID, execution.history.deviceId)
    }

    /** 验证双引号不会破坏 FTS 查询结构。 */
    @Test
    fun `查询引号必须被转义`() = runTest {
        val repository = FakeItemTextSearchRepository()
        val useCase = createUseCase(repository)

        useCase("证件\"夹")

        assertEquals("\"证件\"\"夹\"*", repository.execution?.ftsQuery)
    }

    /** 验证空白查询不会写入最近查找。 */
    @Test
    fun `空白查询必须被拒绝`() = runTest {
        val repository = FakeItemTextSearchRepository()
        val useCase = createUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(" ")
        }
        assertEquals(null, repository.execution)
    }

    /** 使用固定依赖创建待测用例。 */
    private fun createUseCase(
        repository: ItemTextSearchRepository,
    ): SearchItemsUseCase = SearchItemsUseCase(
        repository = repository,
        idGenerator = UniqueIdGenerator { "history" },
        clock = EpochMillisecondsClock { 30_000L },
        textNormalizer = DefaultTextNormalizer,
    )

    /** 记录搜索执行参数的内存仓储。 */
    private class FakeItemTextSearchRepository : ItemTextSearchRepository {
        var execution: ItemTextSearchExecution? = null
            private set

        override suspend fun currentDeviceId(): DeviceId = TEST_DEVICE_ID

        override suspend fun search(
            execution: ItemTextSearchExecution,
        ): List<ItemTextSearchResult> {
            this.execution = execution
            return emptyList()
        }
    }

    private companion object {
        val TEST_DEVICE_ID = DeviceId("device")
    }
}
