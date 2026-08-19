package com.vichua.where.feature.item.draft

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.ItemDraft
import com.vichua.where.core.model.ItemDraftId
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.MvpLimits
import com.vichua.where.core.model.UtcTimestamp
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 验证物品草稿的编码、7 天过期窗口和空内容拒绝规则。
 */
class ManageItemDraftUseCaseTest {
    /** 验证已填写内容会生成可恢复载荷，并复用同一草稿 ID。 */
    @Test
    fun `合法输入保存后可以完整恢复`() = runTest {
        val repository = FakeItemDraftRepository()
        val saveUseCase = saveUseCase(repository)
        val loadUseCase = LoadLatestItemDraftUseCase(repository)

        saveUseCase(
            householdId = HOUSEHOLD_ID,
            deviceId = DEVICE_ID,
            content = ItemDraftContent(
                name = "  护照  ",
                locationId = LOCATION_ID,
                locationDescription = "蓝色盒子",
                note = "出国使用",
            ),
        )

        val saved = assertNotNull(repository.savedDraft)
        assertEquals("id-1", saved.id.value)
        assertEquals(TEST_TIME + SEVEN_DAYS, saved.expiresAt.epochMilliseconds)
        val restored = assertNotNull(loadUseCase())
        assertEquals("  护照  ", restored.name)
        assertEquals(LOCATION_ID, restored.locationId)
        assertEquals("蓝色盒子", restored.locationDescription)
        assertEquals("出国使用", restored.note)

        saveUseCase(
            householdId = HOUSEHOLD_ID,
            deviceId = DEVICE_ID,
            content = ItemDraftContent(name = "通行证"),
        )
        assertEquals(saved.id, repository.savedDraft?.id)
        assertEquals("通行证", loadUseCase()?.name)
    }

    /** 验证空白草稿不会进入仓储。 */
    @Test
    fun `空白草稿必须被拒绝`() = runTest {
        val repository = FakeItemDraftRepository()
        val saveUseCase = saveUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            saveUseCase(
                householdId = HOUSEHOLD_ID,
                deviceId = DEVICE_ID,
                content = ItemDraftContent(),
            )
        }
        assertNull(repository.savedDraft)
    }

    /** 验证损坏载荷会被删除，而不是恢复到录入页。 */
    @Test
    fun `损坏草稿必须被删除`() = runTest {
        val repository = FakeItemDraftRepository(
            existingDraft = ItemDraft(
                id = ItemDraftId("broken"),
                householdId = HOUSEHOLD_ID,
                deviceId = DEVICE_ID,
                payload = "{invalid",
                createdAt = UtcTimestamp(TEST_TIME),
                updatedAt = UtcTimestamp(TEST_TIME),
                expiresAt = UtcTimestamp(TEST_TIME + SEVEN_DAYS),
            ),
        )
        val loadUseCase = LoadLatestItemDraftUseCase(repository)

        assertNull(loadUseCase())
        assertTrue(repository.discardedIds.contains(ItemDraftId("broken")))
    }

    /** 验证放弃草稿会删除当前设备最近一份记录。 */
    @Test
    fun `放弃草稿会删除最近记录`() = runTest {
        val repository = FakeItemDraftRepository()
        saveUseCase(repository)(
            householdId = HOUSEHOLD_ID,
            deviceId = DEVICE_ID,
            content = ItemDraftContent(name = "护照"),
        )

        DiscardLatestItemDraftUseCase(repository)()

        assertEquals(listOf(ItemDraftId("id-1")), repository.discardedIds)
        assertNull(repository.loadLatest())
    }

    /**
     * 使用固定时间和可预测 ID 创建保存用例。
     */
    private fun saveUseCase(
        repository: ItemDraftRepository,
    ): SaveItemDraftUseCase {
        var nextId = 0
        return SaveItemDraftUseCase(
            repository = repository,
            idGenerator = UniqueIdGenerator {
                nextId += 1
                "id-$nextId"
            },
            clock = EpochMillisecondsClock { TEST_TIME },
        )
    }

    /**
     * 记录草稿读写的内存仓储。
     */
    private class FakeItemDraftRepository(
        existingDraft: ItemDraft? = null,
    ) : ItemDraftRepository {
        var savedDraft: ItemDraft? = existingDraft
            private set
        val discardedIds = mutableListOf<ItemDraftId>()

        override suspend fun loadLatest(): ItemDraft? = savedDraft

        override suspend fun save(draft: ItemDraft) {
            savedDraft = draft
        }

        override suspend fun discard(draftId: ItemDraftId, deviceId: DeviceId) {
            discardedIds += draftId
            if (savedDraft?.id == draftId && savedDraft?.deviceId == deviceId) {
                savedDraft = null
            }
        }
    }

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("household")
        val DEVICE_ID = DeviceId("device")
        val LOCATION_ID = LocationNodeId("location")
        const val TEST_TIME = 40_000L
        val SEVEN_DAYS = 24L * 60L * 60L * 1000L * MvpLimits.ITEM_DRAFT_TTL_DAYS
    }
}
