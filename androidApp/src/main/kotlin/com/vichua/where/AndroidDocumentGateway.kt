package com.vichua.where

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.vichua.where.core.platform.DocumentGateway
import com.vichua.where.core.platform.ManagedBackupFile
import com.vichua.where.core.platform.SelectedDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 日常备份写入应用专属目录；导出和导入旧文件才打开系统选择器。
 *
 * 必须在 Activity 进入 STARTED 之前注册启动器。读写大文件都放到 IO 线程，
 * 避免系统选完文件后主线程卡几秒、进度圈看起来像停住。
 */
class AndroidDocumentGateway(
    private val activity: ComponentActivity,
) : DocumentGateway {
    private val pendingCreate = AtomicReference<CreateRequest?>(null)
    private val pendingOpen = AtomicReference<CompletableDeferred<Uri?>?>(null)

    private val createLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument(DEFAULT_MIME_TYPE),
    ) { uri ->
        val request = pendingCreate.getAndSet(null) ?: return@registerForActivityResult
        request.deferred.complete(uri)
    }

    private val openLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val deferred = pendingOpen.getAndSet(null) ?: return@registerForActivityResult
        deferred.complete(uri)
    }

    /**
     * Android 始终可以打开系统文件选择器。
     */
    override fun isAvailable(): Boolean = true

    /**
     * 用相对应用目录的路径说明固定备份位置，避免引导用户去选 Downloads。
     */
    override fun managedBackupDirectoryLabel(): String = MANAGED_DIRECTORY_LABEL

    /**
     * 只列出固定目录里的 .wherebak，按修改时间从新到旧。
     */
    override suspend fun listManagedBackups(): List<ManagedBackupFile> =
        withContext(Dispatchers.IO) {
            val directory = backupsDirectory()
            directory.listFiles { file ->
                file.isFile && file.name.endsWith(BACKUP_FILE_EXTENSION, ignoreCase = true)
            }
                ?.sortedByDescending { file -> file.lastModified() }
                ?.map { file ->
                    ManagedBackupFile(
                        displayName = file.name,
                        opaqueDocumentUri = file.absolutePath,
                        sizeBytes = file.length(),
                        lastModifiedMillis = file.lastModified(),
                    )
                }
                ?: emptyList()
        }

    /**
     * 写入应用外部专属目录，不申请存储权限，电脑用 USB 也能看到该文件夹。
     */
    override suspend fun saveManagedBackup(bytes: ByteArray): SelectedDocument =
        withContext(Dispatchers.IO) {
            require(bytes.isNotEmpty()) { "Managed backup bytes must not be empty." }
            val file = newBackupFile(backupsDirectory())
            file.outputStream().use { output ->
                output.write(bytes)
                output.flush()
            }
            SelectedDocument(
                displayName = file.name,
                opaqueDocumentUri = file.absolutePath,
                bytes = bytes,
            )
        }

    /**
     * 只允许读取固定目录内的备份，防止传入任意路径。
     */
    override suspend fun readManagedBackup(opaqueDocumentUri: String): SelectedDocument =
        withContext(Dispatchers.IO) {
            val file = resolveManagedFile(opaqueDocumentUri)
            val bytes = file.readBytes()
            require(bytes.isNotEmpty()) { "Managed backup must not be empty." }
            SelectedDocument(
                displayName = file.name,
                opaqueDocumentUri = file.absolutePath,
                bytes = bytes,
            )
        }

    /**
     * 让用户选择保存位置后写入完整备份字节。
     */
    override suspend fun createDocument(
        suggestedFileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): SelectedDocument? {
        require(bytes.isNotEmpty()) { "Backup document bytes must not be empty." }
        val deferred = CompletableDeferred<Uri?>()
        val request = CreateRequest(bytes = bytes, deferred = deferred)
        check(pendingCreate.compareAndSet(null, request)) {
            "Document create picker is already active."
        }
        return try {
            createLauncher.launch(suggestedFileName)
            val uri = deferred.await() ?: return null
            withContext(Dispatchers.IO) {
                writeDocument(uri, request.bytes)
            }
        } catch (_: Exception) {
            pendingCreate.compareAndSet(request, null)
            null
        }
    }

    /**
     * 让用户选择已有备份并读取完整字节。
     */
    override suspend fun openDocument(): SelectedDocument? {
        val deferred = CompletableDeferred<Uri?>()
        check(pendingOpen.compareAndSet(null, deferred)) {
            "Document open picker is already active."
        }
        return try {
            openLauncher.launch(arrayOf(DEFAULT_MIME_TYPE, "*/*"))
            val uri = deferred.await() ?: return null
            withContext(Dispatchers.IO) {
                readDocument(uri)
            }
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

    /**
     * 应用外部 files/backups，卸载会一起删除，但无需存储权限。
     */
    private fun backupsDirectory(): File {
        val directory = activity.getExternalFilesDir(BACKUP_DIRECTORY)
            ?: File(activity.filesDir, BACKUP_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            error("Unable to create managed backup directory.")
        }
        return directory
    }

    /**
     * 用本地时间生成文件名；同一秒内多次备份时追加序号，避免覆盖。
     */
    private fun newBackupFile(directory: File): File {
        val stamp = SimpleDateFormat(BACKUP_FILE_STAMP_PATTERN, Locale.US).format(Date())
        var file = File(directory, "$BACKUP_FILE_PREFIX$stamp$BACKUP_FILE_EXTENSION")
        var index = 2
        while (file.exists()) {
            file = File(directory, "$BACKUP_FILE_PREFIX$stamp-$index$BACKUP_FILE_EXTENSION")
            index += 1
        }
        return file
    }

    /**
     * 规范化后再比对父目录，避免 ../ 逃出固定备份文件夹。
     */
    private fun resolveManagedFile(opaqueDocumentUri: String): File {
        val file = File(opaqueDocumentUri)
        val directory = backupsDirectory().canonicalFile
        val canonical = file.canonicalFile
        require(canonical.parentFile?.canonicalFile == directory) {
            "Backup file is outside the managed directory."
        }
        require(canonical.name.endsWith(BACKUP_FILE_EXTENSION, ignoreCase = true)) {
            "Backup file extension is not accepted."
        }
        require(canonical.isFile) { "Managed backup does not exist." }
        return canonical
    }

    private data class CreateRequest(
        val bytes: ByteArray,
        val deferred: CompletableDeferred<Uri?>,
    )

    private companion object {
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
        const val DEFAULT_DISPLAY_NAME = "where-backup.wherebak"
        const val DEFAULT_EXPORT_NAME = "where-export.wherebak"
        const val EXPORT_CACHE_DIRECTORY = "exports"
        const val BACKUP_DIRECTORY = "backups"
        const val BACKUP_FILE_PREFIX = "where-backup-"
        const val BACKUP_FILE_STAMP_PATTERN = "yyyyMMdd-HHmmss"
        const val BACKUP_FILE_EXTENSION = ".wherebak"
        const val MANAGED_DIRECTORY_LABEL = "Android/data/com.vichua.where/files/backups"
        const val FILE_PROVIDER_AUTHORITY = "com.vichua.where.fileprovider"
        const val SHARE_CLIP_LABEL = "where-exported-household"
        const val SHARE_CHOOSER_TITLE = "导出完整家庭数据"
    }
}
