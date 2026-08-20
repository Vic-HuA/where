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
 * 当前只打包物品原图；位置照片和语音名称表尚未接入，计数固定为 0。
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
