package com.vichua.where

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.vichua.where.core.platform.DocumentGateway
import com.vichua.where.core.platform.SelectedDocument
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred

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

    private data class CreateRequest(
        val bytes: ByteArray,
        val deferred: CompletableDeferred<SelectedDocument?>,
    )

    private companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
        const val DEFAULT_DISPLAY_NAME = "where-backup.wherebak"
    }
}
