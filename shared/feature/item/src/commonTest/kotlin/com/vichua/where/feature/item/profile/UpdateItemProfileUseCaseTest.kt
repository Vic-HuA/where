package com.vichua.where.feature.item.profile

import com.vichua.where.core.common.DefaultTextNormalizer
import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemStatus
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.UtcTimestamp
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * 验证物品档案编辑的输入校验和版本递增。
 */
class UpdateItemProfileUseCaseTest {
    /** 验证改名后生成下一版本并保留原位置。 */
    @Test
    fun `合法改名会递增版本并保留位置`() = runTest {
        val repository = FakeItemProfileRepository()
        val useCase = createUseCase(repository)

        useCase(
            UpdateItemProfileRequest(
                itemId = TEST_ITEM_ID,
                name = "  新护照  ",
                locationDescription = "抽屉里",
                note = "备用",
            ),
        )

        val update = assertNotNull(repository.savedUpdate)
        assertEquals("新护照", update.updatedItem.name)
        assertEquals("抽屉里", update.updatedItem.locationDescription)
        assertEquals("备用", update.updatedItem.note)
        assertEquals(TEST_LOCATION_ID, update.updatedItem.currentLocationId)
        assertEquals(EntityVersion(2), update.updatedItem.version)
        assertEquals(EntityVersion(2), update.changeRecord.entityVersion)
    }

    /** 验证空白名称不会进入仓储。 */
    @Test
    fun `空白名称必须被拒绝`() = runTest {
        val repository = FakeItemProfileRepository()
        val useCase = createUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                UpdateItemProfileRequest(
                    itemId = TEST_ITEM_ID,
                    name = " ",
                ),
            )
        }
        assertEquals(null, repository.savedUpdate)
    }

    /** 验证没有任何可见字段变化时不能产生空变更。 */
    @Test
    fun `未改动字段必须被拒绝`() = runTest {
        val repository = FakeItemProfileRepository()
        val useCase = createUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                UpdateItemProfileRequest(
                    itemId = TEST_ITEM_ID,
                    name = "护照",
                ),
            )
        }
        assertEquals(null, repository.savedUpdate)
    }

    private fun createUseCase(
        repository: ItemProfileRepository,
    ): UpdateItemProfileUseCase {
        return UpdateItemProfileUseCase(
            repository = repository,
            idGenerator = UniqueIdGenerator { "change-1" },
            clock = EpochMillisecondsClock { TEST_TIME },
            textNormalizer = DefaultTextNormalizer,
        )
    }

    private class FakeItemProfileRepository : ItemProfileRepository {
        var savedUpdate: ItemProfileUpdate? = null
            private set

        override suspend fun load(itemId: ItemId): ItemProfileContext = ItemProfileContext(
            item = Item(
                id = TEST_ITEM_ID,
                householdId = HouseholdId("household"),
                currentLocationId = TEST_LOCATION_ID,
                name = "护照",
                normalizedName = "护照",
                createdAt = UtcTimestamp(1_000L),
                updatedAt = UtcTimestamp(1_000L),
                version = EntityVersion(1),
                sourceDeviceId = DeviceId("old-device"),
                status = ItemStatus.ACTIVE,
            ),
            locationPath = "书房",
            aliasesText = "",
            categoryText = "",
            currentDeviceId = DeviceId("device"),
        )

        override suspend fun update(update: ItemProfileUpdate) {
            savedUpdate = update
        }
    }

    private companion object {
        val TEST_ITEM_ID = ItemId("item")
        val TEST_LOCATION_ID = LocationNodeId("location")
        const val TEST_TIME = 20_000L
    }
}
