package com.vichua.where.feature.item.photo

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemStatus
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.MediaIntegrityStatus
import com.vichua.where.core.model.PhotoAsset
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.ImportedMediaFile
import com.vichua.where.core.platform.MediaFilePromotion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 验证物品照片追加、封面、用途、排序和软删除规则。
 */
class ManageItemPhotoUseCaseTest {
    /** 验证首张照片会成为封面并转正临时文件。 */
    @Test
    fun `首张追加照片必须成为封面`() = runTest {
        val repository = FakeItemPhotoRepository(existingPhotos = emptyList())
        val mediaFileStore = FakeControlledMediaFileStore()
        val useCase = AddItemPhotoUseCase(
            repository = repository,
            mediaFileStore = mediaFileStore,
            idGenerator = UniqueIdGenerator { "photo-new" },
            clock = EpochMillisecondsClock { TEST_TIME },
        )

        useCase(TEST_ITEM_ID, importedPhoto(PhotoRole.ITEM))

        val addition = assertNotNull(repository.savedAddition)
        assertTrue(addition.newPhoto.isCover)
        assertEquals(SortOrder(0), addition.newPhoto.sortOrder)
        assertEquals(ChangeOperation.CREATE, addition.changeRecord.operation)
        assertEquals(2, mediaFileStore.promoted.size)
    }

    /** 验证已有封面时新照片只能追加到末尾。 */
    @Test
    fun `已有照片时追加不得抢封面`() = runTest {
        val repository = FakeItemPhotoRepository(
            existingPhotos = listOf(storedPhoto(id = "photo-1", sortOrder = 0, isCover = true)),
        )
        val useCase = AddItemPhotoUseCase(
            repository = repository,
            mediaFileStore = FakeControlledMediaFileStore(),
            idGenerator = UniqueIdGenerator { "photo-new" },
            clock = EpochMillisecondsClock { TEST_TIME },
        )

        useCase(TEST_ITEM_ID, importedPhoto(PhotoRole.SUPPLEMENTARY))

        val addition = assertNotNull(repository.savedAddition)
        assertFalse(addition.newPhoto.isCover)
        assertEquals(SortOrder(1), addition.newPhoto.sortOrder)
        assertEquals(PhotoRole.SUPPLEMENTARY, addition.newPhoto.role)
    }

    /** 验证设封面会同时取消原封面。 */
    @Test
    fun `设封面必须同时取消原封面`() = runTest {
        val repository = FakeItemPhotoRepository(
            existingPhotos = listOf(
                storedPhoto(id = "photo-1", sortOrder = 0, isCover = true),
                storedPhoto(id = "photo-2", sortOrder = 1, isCover = false),
            ),
        )
        val useCase = SetItemPhotoCoverUseCase(
            repository = repository,
            idGenerator = sequentialIdGenerator(),
            clock = EpochMillisecondsClock { TEST_TIME },
        )

        useCase(TEST_ITEM_ID, PhotoAssetId("photo-2"))

        val update = assertNotNull(repository.savedUpdate)
        val coverIds = update.updatedPhotos.filter { photo -> photo.isCover && photo.deletedAt == null }
        assertEquals(listOf("photo-2"), coverIds.map { photo -> photo.id.value })
        assertEquals(2, update.changeRecords.size)
    }

    /** 验证改用途只影响目标照片。 */
    @Test
    fun `改用途必须只更新目标照片`() = runTest {
        val repository = FakeItemPhotoRepository(
            existingPhotos = listOf(storedPhoto(id = "photo-1", sortOrder = 0, isCover = true)),
        )
        val useCase = UpdateItemPhotoRoleUseCase(
            repository = repository,
            idGenerator = sequentialIdGenerator(),
            clock = EpochMillisecondsClock { TEST_TIME },
        )

        useCase(TEST_ITEM_ID, PhotoAssetId("photo-1"), PhotoRole.LABEL)

        val update = assertNotNull(repository.savedUpdate)
        assertEquals(PhotoRole.LABEL, update.updatedPhotos.single().role)
        assertTrue(update.updatedPhotos.single().isCover)
        assertEquals(ChangeOperation.UPDATE, update.changeRecords.single().operation)
    }

    /** 验证相同用途不会进入仓储。 */
    @Test
    fun `未变化的用途必须被拒绝`() = runTest {
        val repository = FakeItemPhotoRepository(
            existingPhotos = listOf(storedPhoto(id = "photo-1", sortOrder = 0, isCover = true)),
        )
        val useCase = UpdateItemPhotoRoleUseCase(
            repository = repository,
            idGenerator = sequentialIdGenerator(),
            clock = EpochMillisecondsClock { TEST_TIME },
        )

        assertFailsWith<IllegalArgumentException> {
            useCase(TEST_ITEM_ID, PhotoAssetId("photo-1"), PhotoRole.ITEM)
        }
        assertNull(repository.savedUpdate)
    }

    /** 验证后移会交换相邻照片顺序。 */
    @Test
    fun `后移必须与下一张交换顺序`() = runTest {
        val repository = FakeItemPhotoRepository(
            existingPhotos = listOf(
                storedPhoto(id = "photo-1", sortOrder = 0, isCover = true),
                storedPhoto(id = "photo-2", sortOrder = 1, isCover = false),
            ),
        )
        val useCase = MoveItemPhotoUseCase(
            repository = repository,
            idGenerator = sequentialIdGenerator(),
            clock = EpochMillisecondsClock { TEST_TIME },
        )

        useCase(TEST_ITEM_ID, PhotoAssetId("photo-1"), offset = 1)

        val update = assertNotNull(repository.savedUpdate)
        val byId = update.updatedPhotos.associateBy { photo -> photo.id.value }
        assertEquals(SortOrder(1), byId.getValue("photo-1").sortOrder)
        assertEquals(SortOrder(0), byId.getValue("photo-2").sortOrder)
        assertTrue(byId.getValue("photo-1").isCover)
    }

    /** 验证删除封面会指定下一张为新封面。 */
    @Test
    fun `删除封面必须指定新封面`() = runTest {
        val repository = FakeItemPhotoRepository(
            existingPhotos = listOf(
                storedPhoto(id = "photo-1", sortOrder = 0, isCover = true),
                storedPhoto(id = "photo-2", sortOrder = 1, isCover = false),
            ),
        )
        val useCase = DeleteItemPhotoUseCase(
            repository = repository,
            idGenerator = sequentialIdGenerator(),
            clock = EpochMillisecondsClock { TEST_TIME },
        )

        useCase(TEST_ITEM_ID, PhotoAssetId("photo-1"))

        val update = assertNotNull(repository.savedUpdate)
        val deleted = update.updatedPhotos.single { photo -> photo.id.value == "photo-1" }
        val remaining = update.updatedPhotos.single { photo -> photo.id.value == "photo-2" }
        assertNotNull(deleted.deletedAt)
        assertFalse(deleted.isCover)
        assertTrue(remaining.isCover)
        assertEquals(
            setOf(ChangeOperation.DELETE, ChangeOperation.UPDATE),
            update.changeRecords.map { record -> record.operation }.toSet(),
        )
    }

    /** 验证删除最后一张照片后物品不再拥有封面。 */
    @Test
    fun `删除最后一张照片必须保留空画廊`() = runTest {
        val repository = FakeItemPhotoRepository(
            existingPhotos = listOf(storedPhoto(id = "photo-1", sortOrder = 0, isCover = true)),
        )
        val useCase = DeleteItemPhotoUseCase(
            repository = repository,
            idGenerator = sequentialIdGenerator(),
            clock = EpochMillisecondsClock { TEST_TIME },
        )

        useCase(TEST_ITEM_ID, PhotoAssetId("photo-1"))

        val update = assertNotNull(repository.savedUpdate)
        val deleted = update.updatedPhotos.single()
        assertNotNull(deleted.deletedAt)
        assertFalse(deleted.isCover)
        assertEquals(ChangeOperation.DELETE, update.changeRecords.single().operation)
    }

    private fun sequentialIdGenerator(): UniqueIdGenerator {
        var nextId = 0
        return UniqueIdGenerator {
            nextId += 1
            "change-$nextId"
        }
    }

    private class FakeItemPhotoRepository(
        existingPhotos: List<PhotoAsset>,
    ) : ItemPhotoRepository {
        private val photos = existingPhotos
        var savedAddition: ItemPhotoAddition? = null
            private set
        var savedUpdate: ItemPhotoCollectionUpdate? = null
            private set

        override suspend fun load(itemId: ItemId): ItemPhotoContext = ItemPhotoContext(
            item = Item(
                id = TEST_ITEM_ID,
                householdId = HouseholdId("household"),
                currentLocationId = LocationNodeId("location"),
                name = "Journal",
                normalizedName = "journal",
                createdAt = UtcTimestamp(1_000L),
                updatedAt = UtcTimestamp(1_000L),
                version = EntityVersion(1),
                sourceDeviceId = DeviceId("old-device"),
                status = ItemStatus.ACTIVE,
            ),
            photos = photos,
            currentDeviceId = DeviceId("device"),
        )

        override suspend fun add(addition: ItemPhotoAddition) {
            savedAddition = addition
        }

        override suspend fun update(update: ItemPhotoCollectionUpdate) {
            savedUpdate = update
        }
    }

    private class FakeControlledMediaFileStore : ControlledMediaFileStore {
        val promoted = mutableListOf<MediaFilePromotion>()

        override suspend fun importImage(
            bytes: ByteArray,
            sourceMimeType: String?,
        ): ImportedMediaFile = error("Import is not used by manage-photo tests.")

        override suspend fun promote(promotions: List<MediaFilePromotion>) {
            promoted += promotions
        }

        override suspend fun discard(storageKeys: Collection<String>) = Unit

        override fun resolveAbsolutePath(storageKey: String): String? = null

        override suspend fun readBytes(storageKey: String): ByteArray? = null
    }

    private companion object {
        val TEST_ITEM_ID = ItemId("item")
        const val TEST_TIME = 20_000L

        fun importedPhoto(role: PhotoRole): ImportedItemPhoto = ImportedItemPhoto(
            role = role,
            media = ImportedMediaFile(
                tempStorageKey = "tmp/import-1.jpg",
                thumbnailTempStorageKey = "tmp/thumb-import-1.jpg",
                mimeType = "image/jpeg",
                width = 100,
                height = 80,
                sizeBytes = 1_024L,
                contentHash = "hash-1",
            ),
        )

        fun storedPhoto(
            id: String,
            sortOrder: Int,
            isCover: Boolean,
        ): PhotoAsset = PhotoAsset(
            id = PhotoAssetId(id),
            householdId = HouseholdId("household"),
            itemId = TEST_ITEM_ID,
            role = PhotoRole.ITEM,
            storageKey = "items/item/$id.jpg",
            thumbnailStorageKey = "items/item/thumb-$id.jpg",
            mimeType = "image/jpeg",
            width = 100,
            height = 80,
            sizeBytes = 1_024L,
            contentHash = "hash-$id",
            integrityStatus = MediaIntegrityStatus.AVAILABLE,
            lastIntegrityCheckedAt = UtcTimestamp(1_000L),
            sortOrder = SortOrder(sortOrder),
            isCover = isCover,
            createdAt = UtcTimestamp(1_000L),
            updatedAt = UtcTimestamp(1_000L),
            version = EntityVersion(1),
            sourceDeviceId = DeviceId("old-device"),
        )
    }
}
