package com.vichua.where.feature.item.photo

import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.ImportedMediaFile

/**
 * 新增页已导入但尚未写入物品记录的照片。
 *
 * @property role 用户选择的照片用途。
 * @property media 已写入临时目录的文件元数据。
 */
data class ImportedItemPhoto(
    val role: PhotoRole,
    val media: ImportedMediaFile,
)

/**
 * 把用户选择的图片导入应用私有临时目录。
 *
 * 正式物品 ID 此时尚未确定，因此只写临时文件；保存物品成功后再转正。
 */
class ImportItemPhotoUseCase(
    private val mediaFileStore: ControlledMediaFileStore,
) {
    /**
     * 校验用途后写入临时原图和缩略图。
     *
     * @param bytes 系统相册返回的原始字节。
     * @param sourceMimeType 系统提供的 MIME，可能为空。
     * @param role 拍摄入口对应的照片用途。
     */
    suspend operator fun invoke(
        bytes: ByteArray,
        sourceMimeType: String?,
        role: PhotoRole,
    ): ImportedItemPhoto {
        require(bytes.isNotEmpty()) { "Imported photo bytes must not be empty." }
        return ImportedItemPhoto(
            role = role,
            media = mediaFileStore.importImage(bytes, sourceMimeType),
        )
    }
}
