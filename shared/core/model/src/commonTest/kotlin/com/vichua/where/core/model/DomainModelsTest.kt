package com.vichua.where.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证领域值对象、单实体约束和跨记录不变量。
 */
class DomainModelsTest {
    /** 验证空白标识不会进入领域层。 */
    @Test
    fun `空白标识必须被拒绝`() {
        assertFailsWith<IllegalArgumentException> {
            HouseholdId(" ")
        }
    }

    /** 验证 UTC 时间戳不能使用负值。 */
    @Test
    fun `UTC 时间戳不能早于 Unix Epoch`() {
        assertFailsWith<IllegalArgumentException> {
            UtcTimestamp(-1L)
        }
    }

    /** 验证实体版本按顺序递增。 */
    @Test
    fun `实体版本可以安全递增`() {
        assertEquals(EntityVersion(2L), EntityVersion(1L).next())
    }

    /** 验证家庭根节点不能拥有父节点。 */
    @Test
    fun `家庭根节点不能设置父位置`() {
        assertFailsWith<IllegalArgumentException> {
            createLocation(
                id = LocationNodeId("home"),
                parentId = LocationNodeId("another-home"),
                type = LocationType.HOME,
                name = "我的家",
            )
        }
    }

    /** 验证非家庭根节点必须关联父位置。 */
    @Test
    fun `普通位置必须设置父位置`() {
        assertFailsWith<IllegalArgumentException> {
            createLocation(
                id = LocationNodeId("room"),
                parentId = null,
                type = LocationType.ROOM,
                name = "书房",
            )
        }
    }

    /** 验证位置树能够接受可变深度且不存在重复的正常结构。 */
    @Test
    fun `可变深度位置树通过校验`() {
        val home = createLocation(
            id = LocationNodeId("home"),
            parentId = null,
            type = LocationType.HOME,
            name = "我的家",
        )
        val room = createLocation(
            id = LocationNodeId("room"),
            parentId = home.id,
            type = LocationType.ROOM,
            name = "书房",
        )
        val container = createLocation(
            id = LocationNodeId("container"),
            parentId = room.id,
            type = LocationType.CONTAINER,
            name = "蓝色盒子",
        )

        DomainValidators.validateLocationTree(
            householdId = TEST_HOUSEHOLD_ID,
            locations = listOf(home, room, container),
        )
    }

    /** 验证位置父链形成环路时会被拒绝。 */
    @Test
    fun `位置树环路必须被拒绝`() {
        val home = createLocation(
            id = LocationNodeId("home"),
            parentId = null,
            type = LocationType.HOME,
            name = "我的家",
        )
        val firstContainer = createLocation(
            id = LocationNodeId("first"),
            parentId = LocationNodeId("second"),
            type = LocationType.CONTAINER,
            name = "盒子一",
        )
        val secondContainer = createLocation(
            id = LocationNodeId("second"),
            parentId = firstContainer.id,
            type = LocationType.CONTAINER,
            name = "盒子二",
        )

        assertFailsWith<IllegalArgumentException> {
            DomainValidators.validateLocationTree(
                householdId = TEST_HOUSEHOLD_ID,
                locations = listOf(home, firstContainer, secondContainer),
            )
        }
    }

    /** 验证同一父位置下同类型、同标准化名称的节点不能重复。 */
    @Test
    fun `同级重复位置必须被拒绝`() {
        val home = createLocation(
            id = LocationNodeId("home"),
            parentId = null,
            type = LocationType.HOME,
            name = "我的家",
        )
        val firstRoom = createLocation(
            id = LocationNodeId("room-1"),
            parentId = home.id,
            type = LocationType.ROOM,
            name = "书房",
        )
        val secondRoom = createLocation(
            id = LocationNodeId("room-2"),
            parentId = home.id,
            type = LocationType.ROOM,
            name = "书房",
        )

        assertFailsWith<IllegalArgumentException> {
            DomainValidators.validateLocationTree(
                householdId = TEST_HOUSEHOLD_ID,
                locations = listOf(home, firstRoom, secondRoom),
            )
        }
    }

    /** 验证无效数量不会写入物品档案。 */
    @Test
    fun `物品数量必须大于零`() {
        assertFailsWith<IllegalArgumentException> {
            createItem(quantity = 0.0)
        }
    }

    /** 验证位置待确认物品只能关联家庭根节点。 */
    @Test
    fun `位置待确认物品必须关联家庭根节点`() {
        val home = createLocation(
            id = LocationNodeId("home"),
            parentId = null,
            type = LocationType.HOME,
            name = "我的家",
        )
        val item = createItem(
            currentLocationId = home.id,
            status = ItemStatus.LOCATION_UNCONFIRMED,
        )

        DomainValidators.validateItemLocation(item, listOf(home))
    }

    /** 验证正常物品不能把家庭根节点作为普通位置。 */
    @Test
    fun `正常物品不能直接关联家庭根节点`() {
        val home = createLocation(
            id = LocationNodeId("home"),
            parentId = null,
            type = LocationType.HOME,
            name = "我的家",
        )
        val item = createItem(
            currentLocationId = home.id,
            status = ItemStatus.ACTIVE,
        )

        assertFailsWith<IllegalArgumentException> {
            DomainValidators.validateItemLocation(item, listOf(home))
        }
    }

    /** 验证有照片的物品只能保留一个未删除封面。 */
    @Test
    fun `多个活动封面必须被拒绝`() {
        val item = createItem()
        val photos = listOf(
            createPhoto(id = PhotoAssetId("photo-1"), isCover = true, sortOrder = 0),
            createPhoto(id = PhotoAssetId("photo-2"), isCover = true, sortOrder = 1),
        )

        assertFailsWith<IllegalArgumentException> {
            DomainValidators.validatePhotoCollection(item, photos)
        }
    }

    /**
     * 创建满足基础字段约束的位置测试对象。
     */
    private fun createLocation(
        id: LocationNodeId,
        parentId: LocationNodeId?,
        type: LocationType,
        name: String,
    ): LocationNode = LocationNode(
        id = id,
        householdId = TEST_HOUSEHOLD_ID,
        parentId = parentId,
        type = type,
        name = name,
        normalizedName = name.lowercase(),
        sortOrder = SortOrder(0),
        createdAt = TEST_TIME,
        updatedAt = TEST_TIME,
        version = EntityVersion(1L),
        sourceDeviceId = TEST_DEVICE_ID,
    )

    /**
     * 创建满足基础字段约束的物品测试对象。
     */
    private fun createItem(
        quantity: Double = 1.0,
        currentLocationId: LocationNodeId = LocationNodeId("room"),
        status: ItemStatus = ItemStatus.ACTIVE,
    ): Item = Item(
        id = TEST_ITEM_ID,
        householdId = TEST_HOUSEHOLD_ID,
        currentLocationId = currentLocationId,
        name = "护照",
        normalizedName = "护照",
        quantity = quantity,
        status = status,
        createdAt = TEST_TIME,
        updatedAt = TEST_TIME,
        version = EntityVersion(1L),
        sourceDeviceId = TEST_DEVICE_ID,
    )

    /**
     * 创建满足基础字段约束的照片测试对象。
     */
    private fun createPhoto(
        id: PhotoAssetId,
        isCover: Boolean,
        sortOrder: Int,
    ): PhotoAsset = PhotoAsset(
        id = id,
        householdId = TEST_HOUSEHOLD_ID,
        itemId = TEST_ITEM_ID,
        role = PhotoRole.ITEM,
        storageKey = "items/${TEST_ITEM_ID.value}/${id.value}.jpg",
        thumbnailStorageKey = "items/${TEST_ITEM_ID.value}/thumb-${id.value}.jpg",
        mimeType = "image/jpeg",
        width = 100,
        height = 100,
        sizeBytes = 1_024L,
        contentHash = "hash-${id.value}",
        sortOrder = SortOrder(sortOrder),
        isCover = isCover,
        createdAt = TEST_TIME,
        updatedAt = TEST_TIME,
        version = EntityVersion(1L),
        sourceDeviceId = TEST_DEVICE_ID,
    )

    private companion object {
        val TEST_HOUSEHOLD_ID = HouseholdId("household")
        val TEST_DEVICE_ID = DeviceId("device")
        val TEST_ITEM_ID = ItemId("item")
        val TEST_TIME = UtcTimestamp(1_000L)
    }
}
