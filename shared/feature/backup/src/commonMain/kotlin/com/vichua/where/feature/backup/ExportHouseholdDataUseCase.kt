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
import com.vichua.where.core.model.ExportDestination
import com.vichua.where.core.model.HouseholdBackupSnapshot
import com.vichua.where.core.model.LocalBackupRecord
import com.vichua.where.core.model.LocalBackupRecordId
import com.vichua.where.core.model.LocalBackupStatus
import com.vichua.where.core.model.UtcTimestamp
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.DocumentGateway
import com.vichua.where.core.platform.SelectedDocument
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 导出完整家庭数据。
 *
 * 数据包格式、清单和过滤边界与加密备份相同，只是由用户选择保存位置或系统分享。
 */
class ExportHouseholdDataUseCase(
    private val repository: HouseholdBackupRepository,
    private val mediaFileStore: ControlledMediaFileStore,
    private val documentGateway: DocumentGateway,
    private val backupCrypto: BackupCrypto,
    private val contentHasher: ContentHasher,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 使用用户密码导出家庭数据。确认密码必须一致。
     *
     * 用户取消保存或无法打开分享面板时返回空，不写入失败记录；写出后校验失败才记为失败。
     */
    suspend operator fun invoke(
        password: String,
        passwordConfirmation: String,
        destination: ExportDestination,
    ): BackupVerificationResult? {
        require(password.length >= BackupFormat.MIN_PASSWORD_LENGTH) {
            "Backup password is shorter than the accepted minimum."
        }
        require(password == passwordConfirmation) { "Backup password confirmation does not match." }
        require(documentGateway.isAvailable()) { "Document picker is unavailable." }

        val snapshot = repository.loadSnapshot()
        val packedMedia = packOriginalPhotos(snapshot)
        val snapshotBytes = BACKUP_JSON.encodeToString(snapshot).encodeToByteArray()
        val snapshotHash = contentHasher.sha256Hex(snapshotBytes)
        val createdAt = UtcTimestamp(clock.now())
        val manifest = BackupManifest(
            formatVersion = BackupFormat.CURRENT_VERSION,
            appVersion = BackupFormat.APP_VERSION,
            createdAt = createdAt,
            householdId = snapshot.household.id,
            sourceDeviceId = repository.currentDeviceId(),
            snapshotHash = snapshotHash,
            itemPhotoCount = snapshot.photos.size,
            locationPhotoCount = 0,
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
        val packageBytes = BackupPackageCodec.encode(blob)
        val saved = writeDestination(destination, packageBytes) ?: return null
        return try {
            val verified = verifyPackageBytes(saved.bytes, password, saved.bytes.size.toLong())
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

    /**
     * 保存走系统文档选择器，分享只附带已加密字节。
     */
    private suspend fun writeDestination(
        destination: ExportDestination,
        packageBytes: ByteArray,
    ): SelectedDocument? = when (destination) {
        ExportDestination.SAVE_DOCUMENT -> documentGateway.createDocument(
            suggestedFileName = SUGGESTED_FILE_NAME,
            mimeType = PACKAGE_MIME_TYPE,
            bytes = packageBytes,
        )
        ExportDestination.SHARE -> documentGateway.shareDocument(
            suggestedFileName = SUGGESTED_FILE_NAME,
            mimeType = PACKAGE_MIME_TYPE,
            bytes = packageBytes,
        )
    }

    /**
     * 只打包物品原图，位置照片和语音名称保持计数为 0。
     */
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
        const val SUGGESTED_FILE_NAME = "where-export.wherebak"
        const val PACKAGE_MIME_TYPE = "application/octet-stream"
        val BACKUP_JSON = Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }
    }
}
