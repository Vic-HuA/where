package com.vichua.where.platform.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.ImportedMediaFile
import com.vichua.where.core.platform.MediaFilePromotion
import com.vichua.where.core.platform.StorageKeys
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把物品照片限制在应用私有 `media` 目录，并用规范 storageKey 访问。
 *
 * 先写临时文件再转正，是为了让数据库事务失败时不会留下正式照片路径。
 */
class AndroidControlledMediaFileStore(
    context: Context,
) : ControlledMediaFileStore {
    private val mediaRoot = File(context.applicationContext.filesDir, MEDIA_DIRECTORY_NAME)

    /**
     * 解码图片、计算摘要并写入临时原图和缩略图。
     */
    override suspend fun importImage(
        bytes: ByteArray,
        sourceMimeType: String?,
    ): ImportedMediaFile = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "Imported image bytes must not be empty." }
        require(bytes.size <= MAX_IMPORT_BYTES) { "Imported image exceeds the size limit." }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imported image cannot be decoded." }

        val mimeType = resolveMimeType(bytes, sourceMimeType)
        val extension = StorageKeys.normalizeExtension(
            when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            },
        )
        val importId = UUID.randomUUID().toString()
        val originalKey = StorageKeys.tempOriginal(importId, extension)
        val thumbnailKey = StorageKeys.tempThumbnail(importId)
        writeAtomically(resolveExistingOrCreate(originalKey), bytes)
        writeAtomically(resolveExistingOrCreate(thumbnailKey), createThumbnailBytes(bytes))

        ImportedMediaFile(
            tempStorageKey = originalKey,
            thumbnailTempStorageKey = thumbnailKey,
            mimeType = mimeType,
            width = bounds.outWidth,
            height = bounds.outHeight,
            sizeBytes = bytes.size.toLong(),
            contentHash = sha256Hex(bytes),
        )
    }

    /**
     * 把已经通过校验的临时文件移动到正式标识。
     */
    override suspend fun promote(promotions: List<MediaFilePromotion>) = withContext(Dispatchers.IO) {
        promotions.forEach { promotion ->
            val source = requireExistingFile(promotion.tempStorageKey)
            val destination = resolveExistingOrCreate(promotion.finalStorageKey)
            if (destination.exists() && !destination.delete()) {
                error("Unable to replace an existing media file.")
            }
            if (!source.renameTo(destination)) {
                source.copyTo(destination, overwrite = true)
                source.delete()
            }
        }
    }

    /**
     * 删除指定受控文件；文件不存在时忽略，避免放弃草稿因残留清理失败。
     */
    override suspend fun discard(storageKeys: Collection<String>) = withContext(Dispatchers.IO) {
        storageKeys.forEach { storageKey ->
            val file = resolveValidatedFile(storageKey)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    /**
     * 仅在文件真实存在时返回绝对路径，供界面解码缩略图。
     */
    override fun resolveAbsolutePath(storageKey: String): String? {
        val file = resolveValidatedFile(storageKey)
        return file.takeIf(File::isFile)?.absolutePath
    }

    /**
     * 读取已通过校验的受控文件，缺失时返回空以便备份标记异常。
     */
    override suspend fun readBytes(storageKey: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = resolveValidatedFile(storageKey)
        if (!file.isFile) {
            return@withContext null
        }
        file.readBytes()
    }

    /**
     * 把恢复包中的原图写到正式标识，并按现有规则重建缩略图。
     */
    override suspend fun writeRestoredPhoto(
        storageKey: String,
        thumbnailStorageKey: String,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "Restored image bytes must not be empty." }
        StorageKeys.validate(storageKey)
        StorageKeys.validate(thumbnailStorageKey)
        writeAtomically(resolveExistingOrCreate(storageKey), bytes)
        writeAtomically(resolveExistingOrCreate(thumbnailStorageKey), createThumbnailBytes(bytes))
    }

    /**
     * 按文件头识别真实类型，避免只信任系统相册给出的 MIME。
     */
    private fun resolveMimeType(bytes: ByteArray, sourceMimeType: String?): String {
        val sniffed = when {
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> "image/jpeg"
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> "image/png"
            bytes.size >= 12 &&
                bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> null
        }
        val normalizedSource = sourceMimeType?.lowercase()
        return sniffed ?: when (normalizedSource) {
            "image/jpeg", "image/jpg" -> "image/jpeg"
            "image/png" -> "image/png"
            "image/webp" -> "image/webp"
            else -> error("Unsupported photo MIME type.")
        }
    }

    /**
     * 生成边长不超过阈值的 JPEG 缩略图，降低首页和详情解码成本。
     */
    private fun createThumbnailBytes(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, THUMBNAIL_MAX_EDGE)
        }
        val decoded = requireNotNull(
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions),
        ) { "Imported image thumbnail cannot be decoded." }
        val thumbnail = scaleToMaxEdge(decoded, THUMBNAIL_MAX_EDGE)
        return try {
            ByteArrayOutputStream().use { output ->
                if (!thumbnail.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, output)) {
                    error("Unable to compress photo thumbnail.")
                }
                output.toByteArray()
            }
        } finally {
            if (thumbnail !== decoded) {
                thumbnail.recycle()
            }
            decoded.recycle()
        }
    }

    /**
     * 按最长边缩放，保持宽高比以免封面变形。
     */
    private fun scaleToMaxEdge(source: Bitmap, maxEdge: Int): Bitmap {
        val longestEdge = maxOf(source.width, source.height)
        if (longestEdge <= maxEdge) {
            return source
        }
        val scale = maxEdge.toFloat() / longestEdge.toFloat()
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    /**
     * 先按目标边长估算采样率，避免把整张原图解码进内存。
     */
    private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= maxEdge && currentHeight / 2 >= maxEdge) {
            currentWidth /= 2
            currentHeight /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * 先写同目录临时文件再替换，避免半截文件被界面解码。
     */
    private fun writeAtomically(file: File, bytes: ByteArray) {
        val parent = requireNotNull(file.parentFile) { "Media file must have a parent directory." }
        if (!parent.exists() && !parent.mkdirs()) {
            error("Unable to create media directory.")
        }
        val staging = File(parent, "${file.name}.part")
        staging.outputStream().use { output ->
            output.write(bytes)
            output.flush()
        }
        if (file.exists() && !file.delete()) {
            staging.delete()
            error("Unable to replace an existing media file.")
        }
        if (!staging.renameTo(file)) {
            staging.copyTo(file, overwrite = true)
            staging.delete()
        }
    }

    /**
     * 转正前确认临时文件已经落盘。
     */
    private fun requireExistingFile(storageKey: String): File {
        val file = resolveValidatedFile(storageKey)
        require(file.isFile) { "Promoted media file does not exist." }
        return file
    }

    /**
     * 为正式路径创建父目录，但在写入前不要求文件已存在。
     */
    private fun resolveExistingOrCreate(storageKey: String): File {
        val file = resolveValidatedFile(storageKey)
        val parent = requireNotNull(file.parentFile) { "Media file must have a parent directory." }
        if (!parent.exists() && !parent.mkdirs()) {
            error("Unable to create media directory.")
        }
        return file
    }

    /**
     * 校验 storageKey 后解析路径，并用 canonical path 防止目录穿越。
     */
    private fun resolveValidatedFile(storageKey: String): File {
        StorageKeys.validate(storageKey)
        if (!mediaRoot.exists() && !mediaRoot.mkdirs()) {
            error("Unable to create media root.")
        }
        val target = File(mediaRoot, storageKey)
        val rootPath = mediaRoot.canonicalPath
        val targetPath = target.canonicalPath
        require(targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)) {
            "Resolved media path escaped the private media directory."
        }
        return target
    }

    /**
     * 计算原图摘要，供后续完整性校验复用。
     */
    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte)
            }
    }

    private companion object {
        const val MEDIA_DIRECTORY_NAME = "media"
        const val MAX_IMPORT_BYTES = 15 * 1024 * 1024
        const val THUMBNAIL_MAX_EDGE = 256
        const val THUMBNAIL_JPEG_QUALITY = 80
    }
}
