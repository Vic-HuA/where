package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 本机备份记录状态。
 *
 * 只描述当前设备上的文件引用，不进入家庭数据包。
 */
@Serializable
enum class LocalBackupStatus {
    /** 已写出文件但尚未完成认证校验。 */
    CREATED,

    /** 已通过解密和摘要校验。 */
    VERIFIED,

    /** 创建或校验失败；摘要在失败前可能仍为空。 */
    FAILED,
}

/**
 * 备份包中的媒体类型。
 *
 * 当前打包物品原图和位置代表照；语音名称表尚未接入，计数固定为 0。
 */
@Serializable
enum class BackupMediaKind {
    ITEM_PHOTO,
    LOCATION_PHOTO,
    VOICE_LABEL,
}

/**
 * 备份清单中的单个媒体描述。
 *
 * @property kind 媒体类型。
 * @property storageKey 受控原图标识，不包含缩略图。
 * @property sizeBytes 文件字节数。
 * @property contentHash 内容摘要。
 * @property included 原图是否已写入加密包；缺失文件只保留元数据。
 */
@Serializable
data class BackupMediaDescriptor(
    val kind: BackupMediaKind,
    val storageKey: String,
    val sizeBytes: Long,
    val contentHash: String,
    val included: Boolean,
) {
    init {
        require(storageKey.isNotBlank()) { "Backup media storage key must not be blank." }
        require(sizeBytes >= 0L) { "Backup media size must not be negative." }
        require(contentHash.isNotBlank()) { "Backup media hash must not be blank." }
    }
}

/**
 * 加密备份与完整导出共用的数据包清单。
 *
 * @property formatVersion 数据包格式版本。
 * @property appVersion 生成该包的应用版本。
 * @property createdAt 创建时间。
 * @property householdId 家庭 ID。
 * @property sourceDeviceId 来源设备。
 * @property snapshotHash 过滤后家庭快照的摘要，不是运行中整库文件摘要。
 * @property itemPhotoCount 物品照片记录数，含软删除。
 * @property locationPhotoCount 位置照片记录数。
 * @property voiceLabelCount 语音名称记录数。
 * @property mediaTotalBytes 已打入包内的原图总大小。
 * @property mediaFiles 每个媒体文件的类型、标识、大小和摘要。
 * @property encryptionAlgorithm 认证加密算法标识。
 * @property keyDerivationAlgorithm 密钥派生算法标识。
 * @property keyDerivationIterations 密钥派生迭代次数。
 */
@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: UtcTimestamp,
    val householdId: HouseholdId,
    val sourceDeviceId: DeviceId,
    val snapshotHash: String,
    val itemPhotoCount: Int,
    val locationPhotoCount: Int,
    val voiceLabelCount: Int,
    val mediaTotalBytes: Long,
    val mediaFiles: List<BackupMediaDescriptor>,
    val encryptionAlgorithm: String,
    val keyDerivationAlgorithm: String,
    val keyDerivationIterations: Int,
) {
    init {
        require(formatVersion >= BackupFormat.MIN_SUPPORTED_VERSION) {
            "Backup format version is not supported."
        }
        require(appVersion.isNotBlank()) { "Backup app version must not be blank." }
        require(snapshotHash.isNotBlank()) { "Backup snapshot hash must not be blank." }
        require(itemPhotoCount >= 0) { "Item photo count must not be negative." }
        require(locationPhotoCount >= 0) { "Location photo count must not be negative." }
        require(voiceLabelCount >= 0) { "Voice label count must not be negative." }
        require(mediaTotalBytes >= 0L) { "Backup media total size must not be negative." }
        require(encryptionAlgorithm.isNotBlank()) { "Encryption algorithm must not be blank." }
        require(keyDerivationAlgorithm.isNotBlank()) {
            "Key derivation algorithm must not be blank."
        }
        require(keyDerivationIterations >= BackupFormat.MIN_KDF_ITERATIONS) {
            "Key derivation iterations are below the accepted minimum."
        }
    }
}

/**
 * 经过导出过滤的家庭数据快照。
 *
 * 只包含家庭实体、关联媒体元数据和变更记录，排除设备偏好、最近查找、本机备份记录和草稿。
 */
@Serializable
data class HouseholdBackupSnapshot(
    val household: Household,
    val devices: List<Device>,
    val locations: List<LocationNode>,
    val categories: List<Category>,
    val items: List<Item>,
    val aliases: List<ItemAlias>,
    val photos: List<PhotoAsset>,
    val locationPhotos: List<LocationPhotoAsset> = emptyList(),
    val voiceLabels: List<VoiceLabelAsset> = emptyList(),
    val locationEvents: List<ItemLocationEvent>,
    val changeRecords: List<ChangeRecord>,
    val favoriteLocations: List<FavoriteLocation>,
) {
    init {
        require(devices.all { device -> device.householdId == household.id }) {
            "Backup snapshot devices must belong to the household."
        }
        require(locations.all { location -> location.householdId == household.id }) {
            "Backup snapshot locations must belong to the household."
        }
        require(items.all { item -> item.householdId == household.id }) {
            "Backup snapshot items must belong to the household."
        }
        require(locationPhotos.all { photo -> photo.householdId == household.id }) {
            "Backup snapshot location photos must belong to the household."
        }
        require(voiceLabels.all { label -> label.householdId == household.id }) {
            "Backup snapshot voice labels must belong to the household."
        }
    }
}

/**
 * 写入加密包的原图内容。
 *
 * @property storageKey 受控原图标识。
 * @property bytes 原图完整字节。
 */
@Serializable
data class BackupMediaPayload(
    val storageKey: String,
    val bytes: ByteArray,
) {
    init {
        require(storageKey.isNotBlank()) { "Backup media payload key must not be blank." }
        require(bytes.isNotEmpty()) { "Backup media payload must not be empty." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupMediaPayload) return false
        return storageKey == other.storageKey && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * storageKey.hashCode() + bytes.contentHashCode()
}

/**
 * 解密后的完整备份内容，只用于校验或后续恢复预览。
 */
@Serializable
data class BackupEnvelope(
    val manifest: BackupManifest,
    val snapshot: HouseholdBackupSnapshot,
    val mediaFiles: List<BackupMediaPayload>,
) {
    init {
        require(manifest.householdId == snapshot.household.id) {
            "Backup envelope household must match the snapshot."
        }
    }
}

/**
 * 当前设备上的备份文件引用，不属于备份包内部数据。
 */
@Serializable
data class LocalBackupRecord(
    val id: LocalBackupRecordId,
    val deviceId: DeviceId,
    val householdId: HouseholdId,
    val packageHash: String?,
    val sizeBytes: Long?,
    val status: LocalBackupStatus,
    val opaqueDocumentUri: String?,
    val createdAt: UtcTimestamp,
    val verifiedAt: UtcTimestamp?,
) {
    init {
        require(packageHash == null || packageHash.isNotBlank()) {
            "Backup package hash must be null or non-blank."
        }
        require(sizeBytes == null || sizeBytes >= 0L) {
            "Backup package size must be null or non-negative."
        }
        require(opaqueDocumentUri == null || opaqueDocumentUri.isNotBlank()) {
            "Backup document URI must be null or non-blank."
        }
        if (status == LocalBackupStatus.VERIFIED) {
            require(!packageHash.isNullOrBlank()) { "Verified backup must include a package hash." }
            require(sizeBytes != null) { "Verified backup must include a package size." }
            require(verifiedAt != null) { "Verified backup must include a verification time." }
        }
        require(verifiedAt == null || verifiedAt >= createdAt) {
            "Backup verification time must not precede creation time."
        }
    }
}

/**
 * 只读验证成功后展示给用户的摘要，不写入正式家庭库。
 */
data class BackupVerificationResult(
    val manifest: BackupManifest,
    val itemCount: Int,
    val locationCount: Int,
    val itemPhotoCount: Int,
    val includedMediaCount: Int,
    val packageSizeBytes: Long,
)

/**
 * 设置页展示的最近成功备份状态。
 */
data class LatestBackupStatus(
    val lastVerifiedAt: UtcTimestamp?,
    val lastVerifiedSizeBytes: Long?,
)

/**
 * 恢复或清除前展示的当前家庭数据摘要。
 */
data class HouseholdDataSummary(
    val itemCount: Int,
    val locationCount: Int,
    val photoCount: Int,
) {
    init {
        require(itemCount >= 0) { "Household summary item count must not be negative." }
        require(locationCount >= 0) { "Household summary location count must not be negative." }
        require(photoCount >= 0) { "Household summary photo count must not be negative." }
    }
}

/**
 * 恢复时对单条冲突的处理方式。
 */
@Serializable
enum class ConflictResolution {
    /** 保留当前设备上的值。 */
    KEEP_LOCAL,

    /** 采用备份中的值。 */
    USE_INCOMING,

    /** 仅对允许复制的实体生成新 ID 并同时保留双方。 */
    KEEP_BOTH,
}

/**
 * 用户确认后的恢复方式。
 */
@Serializable
enum class RestoreMode {
    /** 保留本机独有数据，只导入新增和可自动更新的内容。 */
    MERGE,

    /** 用备份覆盖当前家庭数据。 */
    REPLACE,
}

/**
 * 完整导出时由用户选择的数据包去向。
 *
 * 导出与加密备份共用同一格式，只区分保存位置和系统分享。
 */
@Serializable
enum class ExportDestination {
    /** 通过系统文件选择器保存到用户指定位置。 */
    SAVE_DOCUMENT,

    /** 通过系统分享面板交给用户选择的目标应用。 */
    SHARE,
}

/**
 * 恢复预览中的一条冲突。
 *
 * 未选择处理方式时不能执行合并。
 */
data class ImportConflict(
    val entityType: ChangeEntityType,
    val entityId: String,
    val entityLabel: String,
    val conflictFields: List<String>,
    val localValue: String,
    val incomingValue: String,
    val localVersion: Long,
    val incomingVersion: Long,
    val allowedResolutions: List<ConflictResolution>,
) {
    init {
        require(entityId.isNotBlank()) { "Import conflict entity ID must not be blank." }
        require(entityLabel.isNotBlank()) { "Import conflict label must not be blank." }
        require(conflictFields.isNotEmpty()) { "Import conflict must name at least one field." }
        require(localValue.isNotBlank()) { "Import conflict local value must not be blank." }
        require(incomingValue.isNotBlank()) { "Import conflict incoming value must not be blank." }
        require(localVersion >= 1L) { "Import conflict local version must be at least 1." }
        require(incomingVersion >= 1L) { "Import conflict incoming version must be at least 1." }
        require(allowedResolutions.isNotEmpty()) {
            "Import conflict must provide at least one resolution."
        }
    }

    /**
     * 设置页和用作用例共用的冲突键，避免界面层自己拼接枚举名。
     */
    val key: String
        get() = "${entityType.name}:$entityId"
}

/**
 * 隔离在内存中生成的恢复预览，不写入正式家庭库。
 */
data class ImportPreview(
    val manifest: BackupManifest,
    val packageSizeBytes: Long,
    val itemCount: Int,
    val locationCount: Int,
    val itemPhotoCount: Int,
    val locationPhotoCount: Int,
    val voiceLabelCount: Int,
    val addedCount: Int,
    val updatedCount: Int,
    val deletedCount: Int,
    val conflictCount: Int,
    val conflicts: List<ImportConflict>,
    val currentSummary: HouseholdDataSummary,
    val differentHousehold: Boolean,
) {
    init {
        require(itemCount >= 0) { "Import preview item count must not be negative." }
        require(locationCount >= 0) { "Import preview location count must not be negative." }
        require(itemPhotoCount >= 0) { "Import preview photo count must not be negative." }
        require(locationPhotoCount >= 0) {
            "Import preview location photo count must not be negative."
        }
        require(voiceLabelCount >= 0) { "Import preview voice label count must not be negative." }
        require(addedCount >= 0) { "Import preview added count must not be negative." }
        require(updatedCount >= 0) { "Import preview updated count must not be negative." }
        require(deletedCount >= 0) { "Import preview deleted count must not be negative." }
        require(conflictCount == conflicts.size) {
            "Import preview conflict count must match the conflict list."
        }
        require(packageSizeBytes > 0L) { "Import preview package size must be greater than zero." }
    }
}

/**
 * 解密并校验通过后的恢复会话，只保存在内存中直到用户确认或取消。
 */
data class RestoreSession(
    val preview: ImportPreview,
    val envelope: BackupEnvelope,
)

/**
 * 备份数据包冻结常量。
 */
object BackupFormat {
    /** 当前写出的数据包格式版本。 */
    const val CURRENT_VERSION = 1

    /** 本版本可以打开的最低格式版本。 */
    const val MIN_SUPPORTED_VERSION = 1

    /** 生成清单时写入的应用版本。 */
    const val APP_VERSION = "1.0.0-mvp"

    /** 认证加密算法标识。 */
    const val ENCRYPTION_ALGORITHM = "AES-256-GCM"

    /** 密钥派生算法标识。 */
    const val KEY_DERIVATION_ALGORITHM = "PBKDF2-HMAC-SHA256"

    /** 可接受的最低派生迭代次数。 */
    const val MIN_KDF_ITERATIONS = 210000

    /** 创建备份时使用的派生迭代次数。 */
    const val KDF_ITERATIONS = 210000

    /** 用户密码最短长度，避免空口令被写进认证加密包。 */
    const val MIN_PASSWORD_LENGTH = 8
}
