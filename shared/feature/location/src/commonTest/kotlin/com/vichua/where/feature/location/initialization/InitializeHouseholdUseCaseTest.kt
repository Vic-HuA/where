package com.vichua.where.feature.location.initialization

import com.vichua.where.core.common.DefaultTextNormalizer
import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.DevicePlatform
import com.vichua.where.core.model.LocationType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 验证初始化家庭用例的输入约束、模型生成和仓储调用。
 */
class InitializeHouseholdUseCaseTest {
    /** 验证合法输入会生成同一家庭下的设备、根位置、房间和完整变更记录。 */
    @Test
    fun `合法输入生成完整初始化聚合`() = runTest {
        val repository = FakeHouseholdInitializationRepository()
        val useCase = createUseCase(repository)

        val householdId = useCase(
            InitializeHouseholdRequest(
                householdName = "  我的家  ",
                deviceName = "  客厅手机  ",
                devicePlatform = DevicePlatform.ANDROID,
                rooms = listOf(
                    InitialRoomInput(name = "书房", iconKey = "room.study"),
                    InitialRoomInput(name = "  储物   间  ", iconKey = "room.storage"),
                ),
                rootIconKey = "location.home",
            ),
        )

        val initialization = assertNotNull(repository.savedInitialization)
        assertEquals(householdId, initialization.household.id)
        assertEquals("我的家", initialization.household.name)
        assertEquals("客厅手机", initialization.device.displayName)
        assertEquals(LocationType.HOME, initialization.rootLocation.type)
        assertEquals(2, initialization.roomLocations.size)
        assertEquals("储物 间", initialization.roomLocations[1].normalizedName)
        assertTrue(initialization.roomLocations.all { room ->
            room.parentId == initialization.rootLocation.id &&
                room.householdId == householdId
        })
        assertEquals(5, initialization.changeRecords.size)
        assertEquals(
            setOf(
                ChangeEntityType.HOUSEHOLD,
                ChangeEntityType.DEVICE,
                ChangeEntityType.LOCATION_NODE,
            ),
            initialization.changeRecords.map { it.entityType }.toSet(),
        )
        assertTrue(initialization.changeRecords.all { record ->
            record.occurredAt.epochMilliseconds == FIXED_TIME
        })
    }

    /** 验证标准化后重名的房间不会进入仓储。 */
    @Test
    fun `标准化后重复房间必须被拒绝`() = runTest {
        val repository = FakeHouseholdInitializationRepository()
        val useCase = createUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                InitializeHouseholdRequest(
                    householdName = "我的家",
                    deviceName = "当前设备",
                    devicePlatform = DevicePlatform.ANDROID,
                    rooms = listOf(
                        InitialRoomInput(name = "Storage Room"),
                        InitialRoomInput(name = " storage   room "),
                    ),
                    rootIconKey = "location.home",
                ),
            )
        }
        assertEquals(null, repository.savedInitialization)
    }

    /** 验证已有家庭时不会创建第二个家庭。 */
    @Test
    fun `已有家庭时初始化必须停止`() = runTest {
        val repository = FakeHouseholdInitializationRepository(hasActiveHousehold = true)
        val useCase = createUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                InitializeHouseholdRequest(
                    householdName = "另一个家",
                    deviceName = "当前设备",
                    devicePlatform = DevicePlatform.ANDROID,
                    rooms = listOf(InitialRoomInput(name = "客厅")),
                    rootIconKey = "location.home",
                ),
            )
        }
        assertFalse(repository.initializeCalled)
    }

    /**
     * 使用固定时钟和可预测 ID 创建待测用例。
     */
    private fun createUseCase(
        repository: HouseholdInitializationRepository,
    ): InitializeHouseholdUseCase {
        var nextId = 0
        val idGenerator = UniqueIdGenerator {
            nextId += 1
            "id-$nextId"
        }
        val clock = EpochMillisecondsClock { FIXED_TIME }
        return InitializeHouseholdUseCase(
            repository = repository,
            idGenerator = idGenerator,
            clock = clock,
            textNormalizer = DefaultTextNormalizer,
        )
    }

    /**
     * 记录用例写入内容的内存仓储。
     */
    private class FakeHouseholdInitializationRepository(
        private val hasActiveHousehold: Boolean = false,
    ) : HouseholdInitializationRepository {
        var savedInitialization: HouseholdInitialization? = null
            private set

        var initializeCalled: Boolean = false
            private set

        override suspend fun hasActiveHousehold(): Boolean = hasActiveHousehold

        override suspend fun initialize(initialization: HouseholdInitialization) {
            initializeCalled = true
            savedInitialization = initialization
        }
    }

    private companion object {
        const val FIXED_TIME = 10_000L
    }
}
