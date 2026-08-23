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
import java.io.FileOutputStream
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
 * 选文件的等待对象放在进程级，避免恢复后内存紧张导致 Activity 重建时
 * 丢掉系统选择器的结果。从系统目录导入成功后立刻拷进固定备份目录，
 * 下次就可以在应用内列表里打开，不必再依赖系统授权。
 */
class AndroidDocumentGateway(
    private val activity: ComponentActivity,
) : DocumentGateway {
    private val createLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument(DEFAULT_MIME_TYPE),
    ) { uri ->
        val request = pendingCreate.getAndSet(null)
        if (request != null) {
            request.deferred.complete(uri)
        } else if (uri != null) {
            leftoverCreateUri.set(uri)
        }
    }

    private val openLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val deferred = pendingOpen.getAndSet(null)
        if (deferred != null) {
            deferred.complete(uri)
        } else if (uri != null) {
            leftoverOpenUri.set(uri)
        }
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
                file.isFile &&
                    file.name.endsWith(BACKUP_FILE_EXTENSION, ignoreCase = true) &&
                    isReadableBackupPackage(file)
            }
                ?.sortedByDescending { file -> file.lastModified() }
                ?.map { file ->
                    ManagedBackupFile(
                        displayName = file.name,
                        opaqueDocumentUri = file.canonicalPath,
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
            writeBytesToFile(file, bytes)
            SelectedDocument(
                displayName = file.name,
                opaqueDocumentUri = file.canonicalPath,
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
            require(looksLikeBackupPackage(bytes)) { "Managed backup magic is invalid." }
            SelectedDocument(
                displayName = file.name,
                opaqueDocumentUri = file.canonicalPath,
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
        leftoverCreateUri.getAndSet(null)?.let { uri ->
            return withContext(Dispatchers.IO) {
                writeDocument(uri, bytes)
            }
        }
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
                    ?: error("Unable to write the selected backup document.")
            }
        } catch (error: Exception) {
            pendingCreate.compareAndSet(request, null)
            throw error
        }
    }

    /**
     * 让用户选择已有备份并读取完整字节；有效包会拷进固定目录。
     */
    override suspend fun openDocument(): SelectedDocument? {
        leftoverOpenUri.getAndSet(null)?.let { uri ->
            return readImportedDocument(uri)
        }
        val deferred = CompletableDeferred<Uri?>()
        check(pendingOpen.compareAndSet(null, deferred)) {
            "Document open picker is already active."
        }
        return try {
            openLauncher.launch(arrayOf(DEFAULT_MIME_TYPE, "*/*"))
            val uri = deferred.await() ?: return null
            readImportedDocument(uri)
        } catch (error: Exception) {
            pendingOpen.compareAndSet(deferred, null)
            throw error
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

    /**
     * 先读字节再尝试持久化授权，避免部分机型第二次 takePersistable 后反而打不开流。
     */
    private suspend fun readImportedDocument(uri: Uri): SelectedDocument {
        val opened = withContext(Dispatchers.IO) {
            readDocument(uri) ?: error("Unable to read the selected backup document.")
        }
        return withContext(Dispatchers.IO) {
            copyImportedBackupIfNeeded(opened)
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

    private fun readDocument(uri: Uri): SelectedDocument? {
        val bytes = runCatching {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            }
        }.onFailure { error ->
            println("Backup document stream failed: ${error.message}")
        }.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            println("Backup document stream was empty or unreadable.")
            return null
        }
        persistReadPermission(uri)
        return SelectedDocument(
            displayName = displayNameOf(uri),
            opaqueDocumentUri = uri.toString(),
            bytes = bytes,
        )
    }

    /**
     * 系统目录里的有效备份拷进固定目录，避免第二次恢复还要向系统要授权。
     */
    private fun copyImportedBackupIfNeeded(opened: SelectedDocument): SelectedDocument {
        if (!looksLikeBackupPackage(opened.bytes)) {
            return opened
        }
        return runCatching {
            val directory = backupsDirectory()
            val file = uniqueImportedFile(directory, opened.displayName, opened.bytes)
            if (!file.exists() || file.length() != opened.bytes.size.toLong()) {
                writeBytesToFile(file, opened.bytes)
            }
            SelectedDocument(
                displayName = file.name,
                opaqueDocumentUri = file.canonicalPath,
                bytes = opened.bytes,
            )
        }.getOrElse { error ->
            println("Imported backup copy skipped: ${error.message}")
            opened
        }
    }

    private fun persistReadPermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val alreadyPersisted = activity.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }
        if (alreadyPersisted) {
            return
        }
        runCatching {
            activity.contentResolver.takePersistableUriPermission(uri, flags)
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
        writeBytesToFile(file, bytes)
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
     * 同内容复用已有文件；否则按原名或序号写入，避免重复导入堆很多份。
     */
    private fun uniqueImportedFile(
        directory: File,
        displayName: String,
        bytes: ByteArray,
    ): File {
        val rawName = displayName.substringAfterLast('/').ifBlank { DEFAULT_DISPLAY_NAME }
        require(!rawName.contains("..")) { "Imported backup name must not contain parent segments." }
        val baseName = if (rawName.endsWith(BACKUP_FILE_EXTENSION, ignoreCase = true)) {
            rawName
        } else {
            rawName + BACKUP_FILE_EXTENSION
        }
        directory.listFiles()?.firstOrNull { file ->
            file.isFile &&
                file.length() == bytes.size.toLong() &&
                file.readBytes().contentEquals(bytes)
        }?.let { existing ->
            return existing
        }
        var file = File(directory, baseName)
        var index = 2
        while (file.exists()) {
            val stem = baseName.removeSuffix(BACKUP_FILE_EXTENSION)
            file = File(directory, "$stem-$index$BACKUP_FILE_EXTENSION")
            index += 1
        }
        return file
    }

    private fun writeBytesToFile(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { output ->
            output.write(bytes)
            output.flush()
            output.fd.sync()
        }
        val written = file.readBytes()
        require(written.contentEquals(bytes)) { "Managed backup write did not match source bytes." }
    }

    /**
     * 先看魔术字再进列表，避免把测试残文件或空文件当成可恢复备份。
     */
    private fun isReadableBackupPackage(file: File): Boolean {
        if (file.length() < MIN_BACKUP_PACKAGE_BYTES) {
            return false
        }
        return runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(BACKUP_MAGIC.size)
                input.read(magic) == BACKUP_MAGIC.size && magic.contentEquals(BACKUP_MAGIC)
            }
        }.getOrDefault(false)
    }

    private fun looksLikeBackupPackage(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_BACKUP_PACKAGE_BYTES) {
            return false
        }
        return bytes.copyOfRange(0, BACKUP_MAGIC.size).contentEquals(BACKUP_MAGIC)
    }

    /**
     * 规范化后再比对父目录；路径写法不一致时退回按文件名打开。
     */
    private fun resolveManagedFile(opaqueDocumentUri: String): File {
        val directory = backupsDirectory().canonicalFile
        val requested = File(opaqueDocumentUri)
        val canonical = runCatching { requested.canonicalFile }.getOrNull()
        if (canonical != null &&
            canonical.isFile &&
            canonical.name.endsWith(BACKUP_FILE_EXTENSION, ignoreCase = true)
        ) {
            val parent = canonical.parentFile?.canonicalFile
            if (parent == directory) {
                return canonical
            }
        }
        val byName = File(directory, requested.name)
        require(byName.isFile && byName.name.endsWith(BACKUP_FILE_EXTENSION, ignoreCase = true)) {
            "Managed backup does not exist."
        }
        return byName.canonicalFile
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
        val BACKUP_MAGIC = "WHEREBAK".encodeToByteArray()
        const val MIN_BACKUP_PACKAGE_BYTES = 45L
        const val MANAGED_DIRECTORY_LABEL = "Android/data/com.vichua.where/files/backups"
        const val FILE_PROVIDER_AUTHORITY = "com.vichua.where.fileprovider"
        const val SHARE_CLIP_LABEL = "where-exported-household"
        const val SHARE_CHOOSER_TITLE = "导出完整家庭数据"

        val pendingCreate = AtomicReference<CreateRequest?>(null)
        val pendingOpen = AtomicReference<CompletableDeferred<Uri?>?>(null)
        val leftoverOpenUri = AtomicReference<Uri?>(null)
        val leftoverCreateUri = AtomicReference<Uri?>(null)
    }
}
