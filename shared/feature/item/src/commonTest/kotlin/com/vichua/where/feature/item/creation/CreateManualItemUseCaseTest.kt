package com.vichua.where.feature.item.creation

import com.vichua.where.core.common.DefaultTextNormalizer
import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.ImportedMediaFile
import com.vichua.where.core.platform.MediaFilePromotion
import com.vichua.where.feature.item.photo.ImportedItemPhoto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertEquals(emptyList(), creation.photos)
    }

    /** 验证导入照片会写入正式记录，并在数据库成功后转正临时文件。 */
    @Test
    fun `导入照片会生成封面并转正文件`() = runTest {
        val repository = FakeManualItemCreationRepository()
        val mediaFileStore = FakeControlledMediaFileStore()
        val useCase = createUseCase(repository, mediaFileStore)
        val importedPhoto = ImportedItemPhoto(
            role = PhotoRole.ITEM,
            media = ImportedMediaFile(
                tempStorageKey = "tmp/import-1.jpg",
                thumbnailTempStorageKey = "tmp/thumb-import-1.jpg",
                mimeType = "image/jpeg",
                width = 800,
                height = 600,
                sizeBytes = 1024L,
                contentHash = "hash-1",
            ),
        )

        useCase(
            CreateManualItemRequest(
                name = "钥匙",
                locationId = TEST_LOCATION_ID,
                photos = listOf(importedPhoto),
            ),
        )

        val creation = assertNotNull(repository.savedCreation)
        assertEquals(1, creation.photos.size)
        assertTrue(creation.photos.single().isCover)
        assertEquals(PhotoRole.ITEM, creation.photos.single().role)
        assertEquals(2, mediaFileStore.promoted.size)
        assertEquals("tmp/import-1.jpg", mediaFileStore.promoted[0].tempStorageKey)
        assertEquals(
            creation.photos.single().storageKey,
            mediaFileStore.promoted[0].finalStorageKey,
        )
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
        mediaFileStore: ControlledMediaFileStore = FakeControlledMediaFileStore(),
    ): CreateManualItemUseCase {
        var nextId = 0
        return CreateManualItemUseCase(
            repository = repository,
            mediaFileStore = mediaFileStore,
            idGenerator = UniqueIdGenerator {
                nextId += 1
                "id-$nextId"
            },
            clock = EpochMillisecondsClock { TEST_TIME },
            textNormalizer = DefaultTextNormalizer,
        )
    }

    /**
     * 记录文件转正请求的内存文件存储。
     */
    private class FakeControlledMediaFileStore : ControlledMediaFileStore {
        val promoted = mutableListOf<MediaFilePromotion>()

        override suspend fun importImage(
            bytes: ByteArray,
            sourceMimeType: String?,
        ): ImportedMediaFile = error("Import is not used by create-item tests.")

        override suspend fun promote(promotions: List<MediaFilePromotion>) {
            promoted += promotions
        }

        override suspend fun discard(storageKeys: Collection<String>) = Unit

        override fun resolveAbsolutePath(storageKey: String): String? = null

        override suspend fun readBytes(storageKey: String): ByteArray? = null

        override suspend fun writeRestoredPhoto(
            storageKey: String,
            thumbnailStorageKey: String,
            bytes: ByteArray,
        ) = Unit
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
            rootLocationId = LocationNodeId("home"),
            availableLocations = listOf(
                ItemCreationLocation(
                    locationId = TEST_LOCATION_ID,
                    parentId = null,
                    displayPath = TEST_LOCATION_PATH,
                    name = "第二层",
                    type = LocationType.SLOT,
                    iconKey = "location.slot",
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
