package com.vichua.where.core.platform

/**
 * 一次成功导入后的临时媒体文件。
 *
 * 正式 `storageKey` 在物品 ID 确定后生成，数据库事务成功后再从临时标识转正。
 *
 * @property tempStorageKey 原图临时受控标识。
 * @property thumbnailTempStorageKey 缩略图临时受控标识。
 * @property mimeType 原图 MIME 类型。
 * @property width 原图像素宽度。
 * @property height 原图像素高度。
 * @property sizeBytes 原图字节数。
 * @property contentHash 原图内容摘要。
 */
data class ImportedMediaFile(
    val tempStorageKey: String,
    val thumbnailTempStorageKey: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val contentHash: String,
) {
    init {
        StorageKeys.validate(tempStorageKey)
        StorageKeys.validate(thumbnailTempStorageKey)
        require(mimeType.isNotBlank()) { "Imported media MIME type must not be blank." }
        require(width > 0) { "Imported media width must be greater than zero." }
        require(height > 0) { "Imported media height must be greater than zero." }
        require(sizeBytes > 0L) { "Imported media size must be greater than zero." }
        require(contentHash.isNotBlank()) { "Imported media hash must not be blank." }
    }
}

/**
 * 把临时文件移动到数据库已经引用的正式标识。
 *
 * @property tempStorageKey 导入阶段写入的临时标识。
 * @property finalStorageKey 照片记录中保存的正式标识。
 */
data class MediaFilePromotion(
    val tempStorageKey: String,
    val finalStorageKey: String,
) {
    init {
        StorageKeys.validate(tempStorageKey)
        StorageKeys.validate(finalStorageKey)
    }
}

/**
 * 系统相册返回的原始图片字节。
 *
 * @property bytes 图片完整字节，调用方负责在导入后丢弃。
 * @property mimeType 系统提供的 MIME，可能为空，由文件存储按内容识别。
 */
data class PickedImage(
    val bytes: ByteArray,
    val mimeType: String?,
) {
    init {
        require(bytes.isNotEmpty()) { "Picked image must not be empty." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedImage) return false
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        return 31 * bytes.contentHashCode() + (mimeType?.hashCode() ?: 0)
    }
}

/**
 * 打开系统照片选择器。取消选择时返回空，不能让录入主流程崩溃。
 */
interface PhotoPickerGateway {
    /**
     * 选择一张本地图片。
     *
     * @return 用户确认的图片；取消或当前设备不支持时为空。
     */
    suspend fun pickImage(): PickedImage?
}

/**
 * 在应用私有目录中写入、转正和解析受控媒体文件。
 *
 * 只接受通过 [StorageKeys] 校验的标识，避免把任意外部路径写入数据库。
 */
interface ControlledMediaFileStore {
    /**
     * 把用户选择的图片写入临时目录并生成缩略图。
     *
     * @param bytes 原始图片字节。
     * @param sourceMimeType 系统提供的 MIME，无法识别时按文件头判断。
     */
    suspend fun importImage(bytes: ByteArray, sourceMimeType: String?): ImportedMediaFile

    /**
     * 在数据库事务成功后，把临时文件移动到正式标识对应路径。
     */
    suspend fun promote(promotions: List<MediaFilePromotion>)

    /**
     * 删除仍未被正式记录引用的临时文件，例如放弃草稿或导入失败回滚。
     */
    suspend fun discard(storageKeys: Collection<String>)

    /**
     * 把受控标识解析为当前设备上的绝对路径，文件不存在时返回空。
     */
    fun resolveAbsolutePath(storageKey: String): String?

    /**
     * 读取受控原图字节，文件不存在时返回空。
     *
     * 只用于备份打包，不把绝对路径暴露给功能层。
     */
    suspend fun readBytes(storageKey: String): ByteArray?
}

/**
 * 生成并校验受控 `storageKey`，禁止路径穿越和绝对路径。
 */
object StorageKeys {
    private val ALLOWED_PATTERN = Regex("^[a-zA-Z0-9][a-zA-Z0-9._/-]*$")

    /**
     * 校验标识只能指向临时目录或物品照片目录。
     */
    fun validate(storageKey: String) {
        require(storageKey.isNotBlank()) { "Storage key must not be blank." }
        require(!storageKey.startsWith("/")) { "Storage key must not be an absolute path." }
        require(!storageKey.contains('\\')) { "Storage key must not contain backslashes." }
        require(!storageKey.contains("..")) { "Storage key must not contain parent segments." }
        require(!storageKey.contains("//")) { "Storage key must not contain empty segments." }
        require(ALLOWED_PATTERN.matches(storageKey)) { "Storage key contains unsupported characters." }
        require(storageKey.startsWith("tmp/") || storageKey.startsWith("items/")) {
            "Storage key must stay inside tmp or items directories."
        }
    }

    /**
     * 生成物品原图正式标识。
     */
    fun itemOriginal(itemId: String, photoId: String, extension: String): String {
        require(itemId.isNotBlank()) { "Item ID for storage key must not be blank." }
        require(photoId.isNotBlank()) { "Photo ID for storage key must not be blank." }
        val safeExtension = normalizeExtension(extension)
        return "items/$itemId/$photoId.$safeExtension".also(::validate)
    }

    /**
     * 生成物品缩略图正式标识。缩略图统一使用 JPEG，降低解码成本。
     */
    fun itemThumbnail(itemId: String, photoId: String): String {
        require(itemId.isNotBlank()) { "Item ID for storage key must not be blank." }
        require(photoId.isNotBlank()) { "Photo ID for storage key must not be blank." }
        return "items/$itemId/thumb-$photoId.jpg".also(::validate)
    }

    /**
     * 生成导入阶段原图临时标识。
     */
    fun tempOriginal(importId: String, extension: String): String {
        require(importId.isNotBlank()) { "Import ID for storage key must not be blank." }
        val safeExtension = normalizeExtension(extension)
        return "tmp/$importId.$safeExtension".also(::validate)
    }

    /**
     * 生成导入阶段缩略图临时标识。
     */
    fun tempThumbnail(importId: String): String {
        require(importId.isNotBlank()) { "Import ID for storage key must not be blank." }
        return "tmp/thumb-$importId.jpg".also(::validate)
    }

    /**
     * 只允许受控扩展名，避免把可执行后缀写进私有目录。
     */
    fun normalizeExtension(extension: String): String {
        return when (extension.lowercase().removePrefix(".")) {
            "jpg", "jpeg" -> "jpg"
            "png" -> "png"
            "webp" -> "webp"
            else -> error("Unsupported photo file extension.")
        }
    }
}
