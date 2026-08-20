package com.vichua.where

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.vichua.where.core.platform.DocumentGateway
import com.vichua.where.core.platform.SelectedDocument
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过系统文件选择器保存和打开加密备份，避免申请存储权限。
 *
 * 必须在 Activity 进入 STARTED 之前注册启动器。
 */
class AndroidDocumentGateway(
    private val activity: ComponentActivity,
) : DocumentGateway {
    private val pendingCreate = AtomicReference<CreateRequest?>(null)
    private val pendingOpen = AtomicReference<CompletableDeferred<SelectedDocument?>?>(null)

    private val createLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument(DEFAULT_MIME_TYPE),
    ) { uri ->
        val request = pendingCreate.getAndSet(null) ?: return@registerForActivityResult
        if (uri == null) {
            request.deferred.complete(null)
            return@registerForActivityResult
        }
        request.deferred.complete(writeDocument(uri, request.bytes))
    }

    private val openLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val deferred = pendingOpen.getAndSet(null) ?: return@registerForActivityResult
        if (uri == null) {
            deferred.complete(null)
            return@registerForActivityResult
        }
        deferred.complete(readDocument(uri))
    }

    /**
     * Android 始终可以打开系统文件选择器。
     */
    override fun isAvailable(): Boolean = true

    /**
     * 让用户选择保存位置后写入完整备份字节。
     */
    override suspend fun createDocument(
        suggestedFileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): SelectedDocument? {
        require(bytes.isNotEmpty()) { "Backup document bytes must not be empty." }
        val deferred = CompletableDeferred<SelectedDocument?>()
        val request = CreateRequest(bytes = bytes, deferred = deferred)
        check(pendingCreate.compareAndSet(null, request)) {
            "Document create picker is already active."
        }
        return try {
            createLauncher.launch(suggestedFileName)
            deferred.await()
        } catch (_: Exception) {
            pendingCreate.compareAndSet(request, null)
            null
        }
    }

    /**
     * 让用户选择已有备份并读取完整字节。
     */
    override suspend fun openDocument(): SelectedDocument? {
        val deferred = CompletableDeferred<SelectedDocument?>()
        check(pendingOpen.compareAndSet(null, deferred)) {
            "Document open picker is already active."
        }
        return try {
            openLauncher.launch(arrayOf(DEFAULT_MIME_TYPE, "*/*"))
            deferred.await()
        } catch (_: Exception) {
            pendingOpen.compareAndSet(deferred, null)
            null
        }
    }

    /**
     * 把加密导出包写到应用缓存后再打开系统分享，避免申请存储权限。
     */
    override suspend fun shareDocument(
        suggestedFileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): SelectedDocument? {
        require(bytes.isNotEmpty()) { "Export document bytes must not be empty." }
        val cacheFile = withContext(Dispatchers.IO) {
            writeExportCache(suggestedFileName, bytes)
        } ?: return null
        val uri = FileProvider.getUriForFile(activity, FILE_PROVIDER_AUTHORITY, cacheFile)
        val opened = withContext(Dispatchers.Main) {
            runCatching {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType.ifBlank { DEFAULT_MIME_TYPE }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(activity.contentResolver, SHARE_CLIP_LABEL, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(shareIntent, SHARE_CHOOSER_TITLE))
            }.isSuccess
        }
        if (!opened) {
            return null
        }
        return SelectedDocument(
            displayName = cacheFile.name,
            opaqueDocumentUri = uri.toString(),
            bytes = bytes,
        )
    }

    private fun writeDocument(
        uri: Uri,
        bytes: ByteArray,
    ): SelectedDocument? = runCatching {
        persistReadPermission(uri)
        activity.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: return@runCatching null
        SelectedDocument(
            displayName = displayNameOf(uri),
            opaqueDocumentUri = uri.toString(),
            bytes = bytes,
        )
    }.getOrNull()

    private fun readDocument(uri: Uri): SelectedDocument? = runCatching {
        persistReadPermission(uri)
        val bytes = activity.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return@runCatching null
        if (bytes.isEmpty()) {
            return@runCatching null
        }
        SelectedDocument(
            displayName = displayNameOf(uri),
            opaqueDocumentUri = uri.toString(),
            bytes = bytes,
        )
    }.getOrNull()

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun displayNameOf(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { DEFAULT_DISPLAY_NAME }
            ?: DEFAULT_DISPLAY_NAME

    /**
     * 覆盖写入缓存导出文件，避免分享过期副本。
     */
    private fun writeExportCache(
        suggestedFileName: String,
        bytes: ByteArray,
    ): File? = runCatching {
        val safeName = suggestedFileName.substringAfterLast('/').ifBlank { DEFAULT_EXPORT_NAME }
        require(!safeName.contains("..")) { "Export file name must not contain parent segments." }
        val directory = File(activity.cacheDir, EXPORT_CACHE_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            error("Unable to create export cache directory.")
        }
        val file = File(directory, safeName)
        file.outputStream().use { output ->
            output.write(bytes)
            output.flush()
        }
        file
    }.getOrNull()

    private data class CreateRequest(
        val bytes: ByteArray,
        val deferred: CompletableDeferred<SelectedDocument?>,
    )

    private companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
        const val DEFAULT_DISPLAY_NAME = "where-backup.wherebak"
        const val DEFAULT_EXPORT_NAME = "where-export.wherebak"
        const val EXPORT_CACHE_DIRECTORY = "exports"
        const val FILE_PROVIDER_AUTHORITY = "com.vichua.where.fileprovider"
        const val SHARE_CLIP_LABEL = "where-exported-household"
        const val SHARE_CHOOSER_TITLE = "导出完整家庭数据"
    }
}
