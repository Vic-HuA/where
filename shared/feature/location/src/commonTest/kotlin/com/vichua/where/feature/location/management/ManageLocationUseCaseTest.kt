package com.vichua.where.feature.location.management

import com.vichua.where.core.common.DefaultTextNormalizer
import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 验证位置管理用例的层级约束、同级去重和空位置删除规则。
 */
class ManageLocationUseCaseTest {
    /** 验证在家庭根下新增房间时生成合法位置和创建变更记录。 */
    @Test
    fun `合法房间输入生成完整新增聚合`() = runTest {
        val repository = FakeLocationManagementRepository()
        val useCase = createLocationUseCase(repository)

        val locationId = useCase(
            CreateLocationRequest(
                parentId = HOME_ID,
                type = LocationType.ROOM,
                name = "  阳台  ",
            ),
        )

        val creation = assertNotNull(repository.savedCreation)
        assertEquals(locationId, creation.location.id)
        assertEquals("阳台", creation.location.name)
        assertEquals("阳台", creation.location.normalizedName)
        assertEquals(HOME_ID, creation.location.parentId)
        assertEquals(LocationType.ROOM, creation.location.type)
        assertEquals(ChangeOperation.CREATE, creation.changeRecord.operation)
        assertEquals(creation.location.version, creation.changeRecord.entityVersion)
    }

    /** 验证空白名称不会进入仓储。 */
    @Test
    fun `空白位置名称必须被拒绝`() = runTest {
        val repository = FakeLocationManagementRepository()
        val useCase = createLocationUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                CreateLocationRequest(
                    parentId = HOME_ID,
                    type = LocationType.ROOM,
                    name = " ",
                ),
            )
        }
        assertNull(repository.savedCreation)
    }

    /** 验证家庭根下不能直接新增容器，必须先有房间。 */
    @Test
    fun `家庭根下不允许新增容器`() = runTest {
        val repository = FakeLocationManagementRepository()
        val useCase = createLocationUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                CreateLocationRequest(
                    parentId = HOME_ID,
                    type = LocationType.CONTAINER,
                    name = "收纳盒",
                ),
            )
        }
        assertNull(repository.savedCreation)
    }

    /** 验证同级同类型标准化重名不会进入仓储。 */
    @Test
    fun `同级同类型重名必须被拒绝`() = runTest {
        val repository = FakeLocationManagementRepository()
        val useCase = createLocationUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                CreateLocationRequest(
                    parentId = HOME_ID,
                    type = LocationType.ROOM,
                    name = "  书房  ",
                ),
            )
        }
        assertNull(repository.savedCreation)
    }

    /** 验证重命名会递增版本并写入更新变更记录。 */
    @Test
    fun `合法重命名更新位置名称`() = runTest {
        val repository = FakeLocationManagementRepository()
        val useCase = renameLocationUseCase(repository)

        useCase(STUDY_ID, "  工作室  ")

        val rename = assertNotNull(repository.savedRename)
        assertEquals("工作室", rename.location.name)
        assertEquals("工作室", rename.location.normalizedName)
        assertEquals(EntityVersion(2L), rename.location.version)
        assertEquals(ChangeOperation.UPDATE, rename.changeRecord.operation)
    }

    /** 验证家庭根节点不能通过普通位置操作重命名。 */
    @Test
    fun `家庭根节点不能重命名`() = runTest {
        val repository = FakeLocationManagementRepository()
        val useCase = renameLocationUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(HOME_ID, "新的家")
        }
        assertNull(repository.savedRename)
    }

    /** 验证空位置可以软删除。 */
    @Test
    fun `空位置可以删除`() = runTest {
        val repository = FakeLocationManagementRepository()
        val useCase = deleteLocationUseCase(repository)

        useCase(EMPTY_ROOM_ID)

        val deletion = assertNotNull(repository.savedDeletion)
        assertEquals(EMPTY_ROOM_ID, deletion.location.id)
        assertNotNull(deletion.location.deletedAt)
        assertEquals(ChangeOperation.DELETE, deletion.changeRecords.single().operation)
        assertTrue(deletion.favoriteLocation == null)
    }

    /** 验证仍包含物品或子节点的位置不能直接删除。 */
    @Test
    fun `非空位置不能直接删除`() = runTest {
        val repository = FakeLocationManagementRepository()
        val useCase = deleteLocationUseCase(repository)

        assertFailsWith<IllegalArgumentException> {
            useCase(STUDY_ID)
        }
        assertNull(repository.savedDeletion)
    }

    /**
     * 使用固定时间和可预测 ID 创建新增用例。
     */
    private fun createLocationUseCase(
        repository: LocationManagementRepository,
    ): CreateLocationUseCase {
        var nextId = 0
        return CreateLocationUseCase(
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
     * 使用固定时间和可预测 ID 创建重命名用例。
     */
    private fun renameLocationUseCase(
        repository: LocationManagementRepository,
    ): RenameLocationUseCase {
        var nextId = 0
        return RenameLocationUseCase(
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
     * 使用固定时间和可预测 ID 创建删除用例。
     */
    private fun deleteLocationUseCase(
        repository: LocationManagementRepository,
    ): DeleteEmptyLocationUseCase {
        var nextId = 0
        return DeleteEmptyLocationUseCase(
            repository = repository,
            idGenerator = UniqueIdGenerator {
                nextId += 1
                "id-$nextId"
            },
            clock = EpochMillisecondsClock { TEST_TIME },
        )
    }

    /**
     * 记录位置写入聚合的内存仓储。
     */
    private class FakeLocationManagementRepository : LocationManagementRepository {
        var savedCreation: LocationCreation? = null
            private set
        var savedRename: LocationRename? = null
            private set
        var savedDeletion: LocationDeletion? = null
            private set

        override suspend fun loadTree(): LocationTreeSnapshot = SAMPLE_SNAPSHOT

        override suspend fun create(creation: LocationCreation) {
            savedCreation = creation
        }

        override suspend fun rename(rename: LocationRename) {
            savedRename = rename
        }

        override suspend fun delete(deletion: LocationDeletion) {
            savedDeletion = deletion
        }
    }

    private companion object {
        val HOUSEHOLD_ID = HouseholdId("household")
        val DEVICE_ID = DeviceId("device")
        val HOME_ID = LocationNodeId("home")
        val STUDY_ID = LocationNodeId("study")
        val EMPTY_ROOM_ID = LocationNodeId("empty-room")
        const val TEST_TIME = 30_000L

        val HOME_LOCATION = location(
            id = HOME_ID,
            parentId = null,
            type = LocationType.HOME,
            name = "我的家",
        )
        val STUDY_LOCATION = location(
            id = STUDY_ID,
            parentId = HOME_ID,
            type = LocationType.ROOM,
            name = "书房",
            sortOrder = 0,
        )
        val EMPTY_ROOM_LOCATION = location(
            id = EMPTY_ROOM_ID,
            parentId = HOME_ID,
            type = LocationType.ROOM,
            name = "客房",
            sortOrder = 1,
        )
        val SAMPLE_SNAPSHOT = LocationTreeSnapshot(
            householdId = HOUSEHOLD_ID,
            sourceDeviceId = DEVICE_ID,
            householdName = "我的家",
            rootLocationId = HOME_ID,
            roomCount = 2,
            itemCount = 1L,
            locationUnconfirmedCount = 0L,
            locations = listOf(HOME_LOCATION, STUDY_LOCATION, EMPTY_ROOM_LOCATION),
            favoriteLocations = emptyList(),
            nodes = listOf(
                node(
                    location = HOME_LOCATION,
                    depth = 0,
                    childCount = 2,
                    itemCount = 1,
                    canRename = false,
                    canDelete = false,
                ),
                node(
                    location = STUDY_LOCATION,
                    depth = 1,
                    childCount = 0,
                    itemCount = 1,
                    canRename = true,
                    canDelete = false,
                ),
                node(
                    location = EMPTY_ROOM_LOCATION,
                    depth = 1,
                    childCount = 0,
                    itemCount = 0,
                    canRename = true,
                    canDelete = true,
                ),
            ),
        )

        fun location(
            id: LocationNodeId,
            parentId: LocationNodeId?,
            type: LocationType,
            name: String,
            sortOrder: Int = 0,
        ): LocationNode = LocationNode(
            id = id,
            householdId = HOUSEHOLD_ID,
            parentId = parentId,
            type = type,
            name = name,
            normalizedName = DefaultTextNormalizer.normalize(name),
            iconKey = defaultIconKey(type),
            sortOrder = SortOrder(sortOrder),
            createdAt = UtcTimestamp(TEST_TIME),
            updatedAt = UtcTimestamp(TEST_TIME),
            version = EntityVersion(1L),
            sourceDeviceId = DEVICE_ID,
        )

        fun node(
            location: LocationNode,
            depth: Int,
            childCount: Int,
            itemCount: Int,
            canRename: Boolean,
            canDelete: Boolean,
        ): LocationTreeNode = LocationTreeNode(
            locationId = location.id,
            parentId = location.parentId,
            type = location.type,
            name = location.name,
            iconKey = location.iconKey,
            displayPath = location.name,
            depth = depth,
            childCount = childCount,
            itemCount = itemCount,
            canRename = canRename,
            canDelete = canDelete,
            allowedChildTypes = allowedChildTypes(location.type),
        )
    }
}
