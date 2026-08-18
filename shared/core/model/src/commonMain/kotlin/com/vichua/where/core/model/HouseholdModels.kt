package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 家庭数据的根实体。
 *
 * @property id 家庭全局唯一标识。
 * @property name 用户可见且不能为空的家庭名称。
 * @property createdAt 家庭创建时间。
 * @property updatedAt 家庭最近一次正式变更时间。
 * @property version 当前实体版本。
 * @property sourceDeviceId 最近修改该家庭的设备。
 * @property deletedAt 软删除时间，未删除时为空。
 */
@Serializable
data class Household(
    val id: HouseholdId,
    val name: String,
    val createdAt: UtcTimestamp,
    val updatedAt: UtcTimestamp,
    val version: EntityVersion,
    val sourceDeviceId: DeviceId,
    val deletedAt: UtcTimestamp? = null,
) {
    init {
        require(name.isNotBlank()) { "Household name must not be blank." }
        require(updatedAt >= createdAt) { "Household update time must not precede creation time." }
        require(deletedAt == null || deletedAt >= createdAt) {
            "Household deletion time must not precede creation time."
        }
    }
}

/**
 * 设备所属的平台类型。
 */
@Serializable
enum class DevicePlatform {
    /** Android 设备。 */
    ANDROID,

    /** iOS 设备。 */
    IOS,
}

/**
 * 参与家庭数据变更的设备记录。
 *
 * MVP 只创建当前设备记录，但提前保留配对、公钥和撤销字段，避免后续共享阶段破坏性迁移。
 *
 * @property id 设备全局唯一标识。
 * @property householdId 设备所属家庭。
 * @property displayName 用户可见且不能为空的设备名称。
 * @property platform 设备平台类型。
 * @property publicKey 后续设备配对使用的公钥文本，MVP 可以为空。
 * @property pairedAt 完成家庭配对的时间，MVP 当前设备可以为空。
 * @property lastSeenAt 设备最近活动时间，从未记录时为空。
 * @property revokedAt 设备被撤销的时间，仍有效时为空。
 * @property createdAt 设备记录创建时间。
 */
@Serializable
data class Device(
    val id: DeviceId,
    val householdId: HouseholdId,
    val displayName: String,
    val platform: DevicePlatform,
    val publicKey: String? = null,
    val pairedAt: UtcTimestamp? = null,
    val lastSeenAt: UtcTimestamp? = null,
    val revokedAt: UtcTimestamp? = null,
    val createdAt: UtcTimestamp,
) {
    init {
        require(displayName.isNotBlank()) { "Device display name must not be blank." }
        require(publicKey == null || publicKey.isNotBlank()) {
            "Device public key must be null or non-blank."
        }
        require(pairedAt == null || pairedAt >= createdAt) {
            "Device pairing time must not precede creation time."
        }
        require(lastSeenAt == null || lastSeenAt >= createdAt) {
            "Device last-seen time must not precede creation time."
        }
        require(revokedAt == null || revokedAt >= createdAt) {
            "Device revocation time must not precede creation time."
        }
    }
}
