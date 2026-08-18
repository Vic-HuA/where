package com.vichua.where.feature.item.creation

import com.vichua.where.core.common.DefaultTextNormalizer
import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.LocationNodeId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * 验证基础手动物品录入用例的输入校验和聚合生成。
 */
class CreateManualItemUseCaseTest {
    /** 验证名称和位置有效时生成完整物品创建聚合。 */
    @Test
    fun `合法输入生成完整物品聚合`() = runTest {
        val repository = FakeManualItemCreationRepository()
        val useCase = createUseCase(repository)

        val itemId = useCase(
            CreateManualItemRequest(
                name = "  护照  ",
                locationId = TEST_LOCATION_ID,
                locationDescription = "  蓝色盒子内  ",
                note = "  出国使用  ",
            ),
        )

        val creation = assertNotNull(repository.savedCreation)
        assertEquals(itemId, creation.item.id)
        assertEquals("护照", creation.item.name)
        assertEquals("蓝色盒子内", creation.item.locationDescription)
        assertEquals("出国使用", creation.item.note)
        assertEquals(TEST_LOCATION_ID, creation.item.currentLocationId)
        assertEquals(TEST_LOCATION_PATH, creation.initialLocationEvent.toPathSnapshot)
        assertEquals(creation.item.version, creation.changeRecord.entityVersion)
        assertEquals("护照", creation.searchContent.name)
        assertEquals(TEST_LOCATION_PATH, creation.searchContent.locationPathText)
    }

    /** 验证空白名称不会进入仓储。 */
    @Test
    fun `空白物品名称必须被拒绝`() = runTest {
        val repository = FakeManualItemCreationRepository()
        val useCase = createUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                CreateManualItemRequest(
                    name = " ",
                    locationId = TEST_LOCATION_ID,
                ),
            )
        }
        assertEquals(null, repository.savedCreation)
    }

    /** 验证已经失效或不属于当前家庭的位置不能保存。 */
    @Test
    fun `不可用位置必须被拒绝`() = runTest {
        val repository = FakeManualItemCreationRepository()
        val useCase = createUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                CreateManualItemRequest(
                    name = "护照",
                    locationId = LocationNodeId("missing"),
                ),
            )
        }
        assertEquals(null, repository.savedCreation)
    }

    /**
     * 使用固定时间和可预测 ID 创建待测用例。
     */
    private fun createUseCase(
        repository: ManualItemCreationRepository,
    ): CreateManualItemUseCase {
        var nextId = 0
        return CreateManualItemUseCase(
            repository = repository,
            idGenerator = UniqueIdGenerator {
                nextId += 1
                "id-$nextId"
            },
            clock = EpochMillisecondsClock { TEST_TIME },
            textNormalizer = DefaultTextNormalizer,
        )
    }

    /**
     * 记录物品创建聚合的内存仓储。
     */
    private class FakeManualItemCreationRepository : ManualItemCreationRepository {
        var savedCreation: ManualItemCreation? = null
            private set

        override suspend fun loadContext(): ItemCreationContext = ItemCreationContext(
            householdId = HouseholdId("household"),
            sourceDeviceId = DeviceId("device"),
            availableLocations = listOf(
                ItemCreationLocation(
                    locationId = TEST_LOCATION_ID,
                    displayPath = TEST_LOCATION_PATH,
                ),
            ),
        )

        override suspend fun create(creation: ManualItemCreation) {
            savedCreation = creation
        }
    }

    private companion object {
        val TEST_LOCATION_ID = LocationNodeId("location")
        const val TEST_LOCATION_PATH = "书房 · 书柜 · 第二层"
        const val TEST_TIME = 20_000L
    }
}
