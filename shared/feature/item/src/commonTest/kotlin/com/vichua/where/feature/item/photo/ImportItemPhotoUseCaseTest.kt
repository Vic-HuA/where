package com.vichua.where.feature.item.photo

import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.ImportedMediaFile
import com.vichua.where.core.platform.MediaFilePromotion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证照片导入用例只包装文件存储结果，并把用途一起带回新增页。
 */
class ImportItemPhotoUseCaseTest {
    /** 验证合法字节会保留用途和临时文件标识。 */
    @Test
    fun `导入成功时保留用途和临时文件`() = runTest {
        val store = FakeControlledMediaFileStore()
        val useCase = ImportItemPhotoUseCase(store)

        val imported = useCase(
            bytes = byteArrayOf(1, 2, 3),
            sourceMimeType = "image/jpeg",
            role = PhotoRole.ENVIRONMENT,
        )

        assertEquals(PhotoRole.ENVIRONMENT, imported.role)
        assertEquals("tmp/import.jpg", imported.media.tempStorageKey)
        assertEquals(1, store.importedCount)
    }

    /** 验证空字节不会进入文件存储。 */
    @Test
    fun `空字节必须被拒绝`() = runTest {
        val store = FakeControlledMediaFileStore()
        val useCase = ImportItemPhotoUseCase(store)

        assertFailsWith<IllegalArgumentException> {
            useCase(
                bytes = byteArrayOf(),
                sourceMimeType = "image/jpeg",
                role = PhotoRole.ITEM,
            )
        }
        assertEquals(0, store.importedCount)
    }

    /**
     * 记录导入次数的内存文件存储。
     */
    private class FakeControlledMediaFileStore : ControlledMediaFileStore {
        var importedCount: Int = 0
            private set

        override suspend fun importImage(
            bytes: ByteArray,
            sourceMimeType: String?,
        ): ImportedMediaFile {
            importedCount += 1
            return ImportedMediaFile(
                tempStorageKey = "tmp/import.jpg",
                thumbnailTempStorageKey = "tmp/thumb-import.jpg",
                mimeType = "image/jpeg",
                width = 10,
                height = 10,
                sizeBytes = bytes.size.toLong(),
                contentHash = "hash",
            )
        }

        override suspend fun promote(promotions: List<MediaFilePromotion>) = Unit

        override suspend fun discard(storageKeys: Collection<String>) = Unit

        override fun resolveAbsolutePath(storageKey: String): String? = null
    }
}
