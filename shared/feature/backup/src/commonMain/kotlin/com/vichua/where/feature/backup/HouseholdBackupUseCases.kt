package com.vichua.where.feature.backup

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.crypto.BackupCrypto
import com.vichua.where.core.crypto.BackupPackageCodec
import com.vichua.where.core.crypto.ContentHasher
import com.vichua.where.core.model.BackupEnvelope
import com.vichua.where.core.model.BackupFormat
import com.vichua.where.core.model.BackupManifest
import com.vichua.where.core.model.BackupMediaDescriptor
import com.vichua.where.core.model.BackupMediaKind
import com.vichua.where.core.model.BackupMediaPayload
import com.vichua.where.core.model.BackupVerificationResult
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdBackupSnapshot
import com.vichua.where.core.model.HouseholdDataSummary
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.ItemDraft
import com.vichua.where.core.model.LatestBackupStatus
import com.vichua.where.core.model.LocalBackupRecord
import com.vichua.where.core.model.LocalBackupRecordId
import com.vichua.where.core.model.LocalBackupStatus
import com.vichua.where.core.model.LocationPhotoAsset
import com.vichua.where.core.model.PhotoAsset
import com.vichua.where.core.model.UtcTimestamp
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.DocumentGateway
import com.vichua.where.core.platform.ManagedBackupFile
import com.vichua.where.core.platform.SelectedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 家庭备份快照和本机备份记录仓储契约。
 */
interface HouseholdBackupRepository {
    /**
     * 加载经过导出过滤的家庭快照。
     */
    suspend fun loadSnapshot(): HouseholdBackupSnapshot

    /**
     * 返回当前有效设备 ID。
     */
    suspend fun currentDeviceId(): DeviceId

    /**
     * 返回当前未删除家庭 ID。
     */
    suspend fun currentHouseholdId(): HouseholdId

    /**
     * 返回当前设备最近一条已验证备份。
     */
    suspend fun findLatestVerified(): LocalBackupRecord?

    /**
     * 保存一条本机备份记录。
     */
    suspend fun insertRecord(record: LocalBackupRecord)

    /**
     * 用目标快照覆盖家庭可导出实体。
     */
    suspend fun replaceSnapshot(snapshot: HouseholdBackupSnapshot)

    /**
     * 读取当前设备在当前家庭下的草稿。
     */
    suspend fun loadDrafts(): List<ItemDraft>

    /**
     * 写回校验后的草稿。
     */
    suspend fun saveDraft(draft: ItemDraft)

    /**
     * 读取清除或恢复确认所需的当前家庭摘要。
     */
    suspend fun loadSummary(): HouseholdDataSummary

    /**
     * 读取当前家庭照片元数据。
     */
    suspend fun loadPhotos(): List<PhotoAsset>

    /**
     * 读取当前家庭位置代表照元数据，供清除后删除受控文件。
     */
    suspend fun loadLocationPhotos(): List<LocationPhotoAsset>

    /**
     * 清除当前家庭可导出数据，保留本机设备数据。
     */
    suspend fun clearHousehold(clearedAtMillis: Long)
}

/**
 * 读取设置页需要展示的最近成功备份状态。
 */
class LoadLatestBackupStatusUseCase(
    private val repository: HouseholdBackupRepository,
) {
    /**
     * 没有已验证记录时返回空时间。
     */
    suspend operator fun invoke(): LatestBackupStatus {
        val latest = repository.findLatestVerified()
        return LatestBackupStatus(
            lastVerifiedAt = latest?.verifiedAt,
            lastVerifiedSizeBytes = latest?.sizeBytes,
        )
    }
}

/**
 * 列出固定备份目录中的文件，供验证和恢复在应用内选择。
 */
class ListManagedBackupsUseCase(
    private val documentGateway: DocumentGateway,
) {
    /**
     * 固定备份目录的用户可见说明。
     */
    fun directoryLabel(): String = documentGateway.managedBackupDirectoryLabel()

    /**
     * 按修改时间从新到旧返回本机备份。
     */
    suspend operator fun invoke(): List<ManagedBackupFile> = documentGateway.listManagedBackups()
}

/**
 * 创建加密备份并完成本地只读校验。
 *
 * 直接写入固定备份目录，不再打开系统选文件夹。
 * 密钥派生和加解密放到后台线程，避免把界面转圈堵死。
 */
class CreateEncryptedBackupUseCase(
    private val repository: HouseholdBackupRepository,
    private val mediaFileStore: ControlledMediaFileStore,
    private val documentGateway: DocumentGateway,
    private val backupCrypto: BackupCrypto,
    private val contentHasher: ContentHasher,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 使用用户密码创建备份。确认密码必须一致。
     */
    suspend operator fun invoke(
        password: String,
        passwordConfirmation: String,
        onProgress: suspend (String) -> Unit = {},
    ): BackupVerificationResult {
        require(password.length >= BackupFormat.MIN_PASSWORD_LENGTH) {
            "Backup password is shorter than the accepted minimum."
        }
        require(password == passwordConfirmation) { "Backup password confirmation does not match." }

        reportProgress(onProgress, "正在读取家庭数据…")
        val snapshot = repository.loadSnapshot()
        reportProgress(onProgress, "正在打包物品照片…")
        val packedMedia = withContext(Dispatchers.Default) {
            packOriginalPhotos(snapshot)
        }
        val createdAt = UtcTimestamp(clock.now())
        reportProgress(onProgress, "正在加密备份…")
        val packageBytes = withContext(Dispatchers.Default) {
            val snapshotBytes = BACKUP_JSON.encodeToString(snapshot).encodeToByteArray()
            val snapshotHash = contentHasher.sha256Hex(snapshotBytes)
            val manifest = BackupManifest(
                formatVersion = BackupFormat.CURRENT_VERSION,
                appVersion = BackupFormat.APP_VERSION,
                createdAt = createdAt,
                householdId = snapshot.household.id,
                sourceDeviceId = repository.currentDeviceId(),
                snapshotHash = snapshotHash,
                itemPhotoCount = snapshot.photos.size,
                locationPhotoCount = snapshot.locationPhotos.size,
                voiceLabelCount = 0,
                mediaTotalBytes = packedMedia.payloads.sumOf { media -> media.bytes.size.toLong() },
                mediaFiles = packedMedia.descriptors,
                encryptionAlgorithm = BackupFormat.ENCRYPTION_ALGORITHM,
                keyDerivationAlgorithm = BackupFormat.KEY_DERIVATION_ALGORITHM,
                keyDerivationIterations = BackupFormat.KDF_ITERATIONS,
            )
            val envelope = BackupEnvelope(
                manifest = manifest,
                snapshot = snapshot,
                mediaFiles = packedMedia.payloads,
            )
            val plaintext = BACKUP_JSON.encodeToString(envelope).encodeToByteArray()
            val blob = backupCrypto.encrypt(
                password = password,
                derivation = backupCrypto.createKeyDerivation(),
                plaintext = plaintext,
            )
            BackupPackageCodec.encode(blob)
        }
        reportProgress(onProgress, "正在写入备份文件…")
        val saved = documentGateway.saveManagedBackup(packageBytes)
        return try {
            reportProgress(onProgress, "正在校验备份…")
            val verified = withContext(Dispatchers.Default) {
                verifyPackageBytes(saved.bytes, password, saved.bytes.size.toLong())
            }
            repository.insertRecord(
                LocalBackupRecord(
                    id = LocalBackupRecordId(idGenerator.generate()),
                    deviceId = repository.currentDeviceId(),
                    householdId = repository.currentHouseholdId(),
                    packageHash = contentHasher.sha256Hex(saved.bytes),
                    sizeBytes = saved.bytes.size.toLong(),
                    status = LocalBackupStatus.VERIFIED,
                    opaqueDocumentUri = saved.opaqueDocumentUri,
                    createdAt = createdAt,
                    verifiedAt = UtcTimestamp(clock.now()),
                ),
            )
            verified
        } catch (error: Exception) {
            repository.insertRecord(
                LocalBackupRecord(
                    id = LocalBackupRecordId(idGenerator.generate()),
                    deviceId = repository.currentDeviceId(),
                    householdId = repository.currentHouseholdId(),
                    packageHash = null,
                    sizeBytes = saved.bytes.size.toLong(),
                    status = LocalBackupStatus.FAILED,
                    opaqueDocumentUri = saved.opaqueDocumentUri,
                    createdAt = createdAt,
                    verifiedAt = null,
                ),
            )
            throw error
        }
    }

    private suspend fun packOriginalPhotos(snapshot: HouseholdBackupSnapshot): PackedMedia {
        val descriptors = mutableListOf<BackupMediaDescriptor>()
        val payloads = mutableListOf<BackupMediaPayload>()
        snapshot.photos.forEach { photo ->
            val originalBytes = mediaFileStore.readBytes(photo.storageKey)
            val included = originalBytes != null
            descriptors += BackupMediaDescriptor(
                kind = BackupMediaKind.ITEM_PHOTO,
                storageKey = photo.storageKey,
                sizeBytes = photo.sizeBytes,
                contentHash = photo.contentHash,
                included = included,
            )
            if (originalBytes != null) {
                payloads += BackupMediaPayload(
                    storageKey = photo.storageKey,
                    bytes = originalBytes,
                )
            }
        }
        snapshot.locationPhotos.forEach { photo ->
            val originalBytes = mediaFileStore.readBytes(photo.storageKey)
            val included = originalBytes != null
            descriptors += BackupMediaDescriptor(
                kind = BackupMediaKind.LOCATION_PHOTO,
                storageKey = photo.storageKey,
                sizeBytes = photo.sizeBytes,
                contentHash = photo.contentHash,
                included = included,
            )
            if (originalBytes != null) {
                payloads += BackupMediaPayload(
                    storageKey = photo.storageKey,
                    bytes = originalBytes,
                )
            }
        }
        return PackedMedia(descriptors = descriptors, payloads = payloads)
    }

    private fun verifyPackageBytes(
        packageBytes: ByteArray,
        password: String,
        packageSizeBytes: Long,
    ): BackupVerificationResult {
        val blob = BackupPackageCodec.decode(packageBytes)
        val plaintext = backupCrypto.decrypt(password, blob)
        val envelope = BACKUP_JSON.decodeFromString<BackupEnvelope>(plaintext.decodeToString())
        val snapshotBytes = BACKUP_JSON.encodeToString(envelope.snapshot).encodeToByteArray()
        require(contentHasher.sha256Hex(snapshotBytes) == envelope.manifest.snapshotHash) {
            "Backup snapshot hash does not match the manifest."
        }
        envelope.mediaFiles.forEach { media ->
            val descriptor = envelope.manifest.mediaFiles.firstOrNull { item ->
                item.storageKey == media.storageKey && item.included
            }
            require(descriptor != null) { "Backup media is missing from the manifest." }
            require(contentHasher.sha256Hex(media.bytes) == descriptor.contentHash) {
                "Backup media hash does not match the manifest."
            }
        }
        return BackupVerificationResult(
            manifest = envelope.manifest,
            itemCount = envelope.snapshot.items.size,
            locationCount = envelope.snapshot.locations.size,
            itemPhotoCount = envelope.manifest.itemPhotoCount,
            includedMediaCount = envelope.mediaFiles.size,
            packageSizeBytes = packageSizeBytes,
        )
    }

    private data class PackedMedia(
        val descriptors: List<BackupMediaDescriptor>,
        val payloads: List<BackupMediaPayload>,
    )

    private companion object {
        val BACKUP_JSON = Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }
    }
}

/**
 * 只读验证用户选择的加密备份，不写入正式家庭数据库。
 */
class VerifyBackupPackageUseCase(
    private val repository: HouseholdBackupRepository,
    private val documentGateway: DocumentGateway,
    private val backupCrypto: BackupCrypto,
    private val contentHasher: ContentHasher,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 打开备份并校验密码、格式和摘要。
     *
     * [backupUri] 指向固定目录中的文件；为空时才打开系统选择器导入旧备份。
     */
    suspend operator fun invoke(
        password: String,
        backupUri: String? = null,
        onProgress: suspend (String) -> Unit = {},
    ): BackupVerificationResult? {
        require(password.isNotEmpty()) { "Backup password must not be empty." }
        val opened = openBackupDocument(backupUri, onProgress) ?: return null
        reportProgress(onProgress, "正在解密并校验备份…")
        val result = withContext(Dispatchers.Default) {
            verifyOpenedBackup(
                opened = opened,
                password = password,
                backupCrypto = backupCrypto,
                contentHasher = contentHasher,
            )
        }
        val now = UtcTimestamp(clock.now())
        repository.insertRecord(
            LocalBackupRecord(
                id = LocalBackupRecordId(idGenerator.generate()),
                deviceId = repository.currentDeviceId(),
                householdId = repository.currentHouseholdId(),
                packageHash = contentHasher.sha256Hex(opened.bytes),
                sizeBytes = opened.bytes.size.toLong(),
                status = LocalBackupStatus.VERIFIED,
                opaqueDocumentUri = opened.opaqueDocumentUri,
                createdAt = now,
                verifiedAt = now,
            ),
        )
        return result
    }

    /**
     * 固定目录直接读文件；导入旧备份才打开系统选择器。
     */
    private suspend fun openBackupDocument(
        backupUri: String?,
        onProgress: suspend (String) -> Unit,
    ): SelectedDocument? {
        if (backupUri != null) {
            reportProgress(onProgress, "正在读取备份文件…")
            return documentGateway.readManagedBackup(backupUri)
        }
        require(documentGateway.isAvailable()) { "Document picker is unavailable." }
        reportProgress(onProgress, "请选择要验证的备份文件…")
        return documentGateway.openDocument()
    }
}

/**
 * 先让界面刷新进度文案，再继续重活，避免转圈卡在同一帧。
 */
internal suspend fun reportProgress(
    onProgress: suspend (String) -> Unit,
    message: String,
) {
    onProgress(message)
    yield()
}

/**
 * 解密并核对清单摘要。放到后台线程调用，避免卡住界面。
 */
internal fun verifyOpenedBackup(
    opened: SelectedDocument,
    password: String,
    backupCrypto: BackupCrypto,
    contentHasher: ContentHasher,
): BackupVerificationResult {
    val blob = BackupPackageCodec.decode(opened.bytes)
    val plaintext = backupCrypto.decrypt(password, blob)
    val envelope = VERIFY_BACKUP_JSON.decodeFromString<BackupEnvelope>(plaintext.decodeToString())
    val snapshotBytes = VERIFY_BACKUP_JSON.encodeToString(envelope.snapshot).encodeToByteArray()
    require(contentHasher.sha256Hex(snapshotBytes) == envelope.manifest.snapshotHash) {
        "Backup snapshot hash does not match the manifest."
    }
    envelope.mediaFiles.forEach { media ->
        val descriptor = envelope.manifest.mediaFiles.firstOrNull { item ->
            item.storageKey == media.storageKey && item.included
        }
        require(descriptor != null) { "Backup media is missing from the manifest." }
        require(contentHasher.sha256Hex(media.bytes) == descriptor.contentHash) {
            "Backup media hash does not match the manifest."
        }
    }
    return BackupVerificationResult(
        manifest = envelope.manifest,
        itemCount = envelope.snapshot.items.size,
        locationCount = envelope.snapshot.locations.size,
        itemPhotoCount = envelope.manifest.itemPhotoCount,
        includedMediaCount = envelope.mediaFiles.size,
        packageSizeBytes = opened.bytes.size.toLong(),
    )
}

private val VERIFY_BACKUP_JSON = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
}
