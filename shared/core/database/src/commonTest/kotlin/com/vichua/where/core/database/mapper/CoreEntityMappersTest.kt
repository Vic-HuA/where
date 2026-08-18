package com.vichua.where.core.database.mapper

import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemStatus
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.PhotoAsset
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证领域模型与 Room 实体之间的双向映射不会丢失冻结字段。
 */
class CoreEntityMappersTest {
    /** 验证位置节点完整往返映射。 */
    @Test
    fun `位置节点往返映射保持全部字段`() {
        val location = LocationNode(
            id = LocationNodeId("location"),
            householdId = TEST_HOUSEHOLD_ID,
            parentId = LocationNodeId("home"),
            type = LocationType.CONTAINER,
            name = "蓝色盒子",
            normalizedName = "蓝色盒子",
            description = "书柜第二层",
            iconKey = "container.box",
            sortOrder = SortOrder(3),
            createdAt = TEST_TIME,
            updatedAt = UtcTimestamp(2_000L),
            version = EntityVersion(2L),
            sourceDeviceId = TEST_DEVICE_ID,
        )

        assertEquals(location, location.toEntity().toDomain())
    }

    /** 验证物品档案完整往返映射。 */
    @Test
    fun `物品往返映射保持全部字段`() {
        val item = Item(
            id = TEST_ITEM_ID,
            householdId = TEST_HOUSEHOLD_ID,
            currentLocationId = LocationNodeId("location"),
            name = "护照",
            normalizedName = "护照",
            quantity = 1.0,
            unit = "本",
            locationDescription = "蓝色盒子内",
            note = "出国使用",
            status = ItemStatus.ACTIVE,
            createdAt = TEST_TIME,
            updatedAt = UtcTimestamp(2_000L),
            version = EntityVersion(2L),
            sourceDeviceId = TEST_DEVICE_ID,
        )

        assertEquals(item, item.toEntity().toDomain())
    }

    /** 验证照片元数据完整往返映射。 */
    @Test
    fun `照片往返映射保持全部字段`() {
        val photo = PhotoAsset(
            id = PhotoAssetId("photo"),
            householdId = TEST_HOUSEHOLD_ID,
            itemId = TEST_ITEM_ID,
            role = PhotoRole.ITEM,
            storageKey = "items/item/photo.jpg",
            thumbnailStorageKey = "items/item/photo-thumb.jpg",
            mimeType = "image/jpeg",
            width = 1_920,
            height = 1_080,
            sizeBytes = 2_048L,
            contentHash = "sha256",
            sortOrder = SortOrder(0),
            isCover = true,
            capturedAt = UtcTimestamp(500L),
            createdAt = TEST_TIME,
            updatedAt = TEST_TIME,
            version = EntityVersion(1L),
            sourceDeviceId = TEST_DEVICE_ID,
        )

        assertEquals(photo, photo.toEntity().toDomain())
    }

    private companion object {
        val TEST_HOUSEHOLD_ID = HouseholdId("household")
        val TEST_DEVICE_ID = DeviceId("device")
        val TEST_ITEM_ID = ItemId("item")
        val TEST_TIME = UtcTimestamp(1_000L)
    }
}
