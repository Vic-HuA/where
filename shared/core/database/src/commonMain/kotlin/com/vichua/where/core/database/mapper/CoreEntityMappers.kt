package com.vichua.where.core.database.mapper

import com.vichua.where.core.database.entity.CategoryEntity
import com.vichua.where.core.database.entity.ChangeRecordEntity
import com.vichua.where.core.database.entity.DeviceEntity
import com.vichua.where.core.database.entity.HouseholdEntity
import com.vichua.where.core.database.entity.ItemAliasEntity
import com.vichua.where.core.database.entity.ItemDraftEntity
import com.vichua.where.core.database.entity.ItemEntity
import com.vichua.where.core.database.entity.ItemLocationEventEntity
import com.vichua.where.core.database.entity.LocationNodeEntity
import com.vichua.where.core.database.entity.LocationPhotoAssetEntity
import com.vichua.where.core.database.entity.PhotoAssetEntity
import com.vichua.where.core.database.entity.VoiceLabelAssetEntity
import com.vichua.where.core.model.Category
import com.vichua.where.core.model.CategoryId
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ChangeOperation
import com.vichua.where.core.model.ChangeRecord
import com.vichua.where.core.model.ChangeRecordId
import com.vichua.where.core.model.Device
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.DevicePlatform
import com.vichua.where.core.model.EntityVersion
import com.vichua.where.core.model.Household
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemAlias
import com.vichua.where.core.model.ItemAliasId
import com.vichua.where.core.model.ItemDraft
import com.vichua.where.core.model.ItemDraftId
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.ItemLocationEvent
import com.vichua.where.core.model.ItemLocationEventId
import com.vichua.where.core.model.ItemLocationReason
import com.vichua.where.core.model.ItemStatus
import com.vichua.where.core.model.LocationNode
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.LocationPhotoAsset
import com.vichua.where.core.model.LocationPhotoAssetId
import com.vichua.where.core.model.VoiceLabelAsset
import com.vichua.where.core.model.VoiceLabelAssetId
import com.vichua.where.core.model.LocationType
import com.vichua.where.core.model.MediaIntegrityStatus
import com.vichua.where.core.model.PhotoAsset
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.model.SortOrder
import com.vichua.where.core.model.UtcTimestamp

/** 将家庭领域模型转换为 Room 实体。 */
internal fun Household.toEntity(): HouseholdEntity = HouseholdEntity(
    id = id.value,
    name = name,
    createdAt = createdAt.epochMilliseconds,
    updatedAt = updatedAt.epochMilliseconds,
    version = version.value,
    sourceDeviceId = sourceDeviceId.value,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将家庭 Room 实体还原为领域模型并重新执行领域约束。 */
internal fun HouseholdEntity.toDomain(): Household = Household(
    id = HouseholdId(id),
    name = name,
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    version = EntityVersion(version),
    sourceDeviceId = DeviceId(sourceDeviceId),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将设备领域模型转换为 Room 实体。 */
internal fun Device.toEntity(): DeviceEntity = DeviceEntity(
    id = id.value,
    householdId = householdId.value,
    displayName = displayName,
    platform = platform.name,
    publicKey = publicKey,
    pairedAt = pairedAt?.epochMilliseconds,
    lastSeenAt = lastSeenAt?.epochMilliseconds,
    revokedAt = revokedAt?.epochMilliseconds,
    createdAt = createdAt.epochMilliseconds,
)

/** 将设备 Room 实体还原为领域模型。 */
internal fun DeviceEntity.toDomain(): Device = Device(
    id = DeviceId(id),
    householdId = HouseholdId(householdId),
    displayName = displayName,
    platform = enumValueOf<DevicePlatform>(platform),
    publicKey = publicKey,
    pairedAt = pairedAt?.let(::UtcTimestamp),
    lastSeenAt = lastSeenAt?.let(::UtcTimestamp),
    revokedAt = revokedAt?.let(::UtcTimestamp),
    createdAt = UtcTimestamp(createdAt),
)

/** 将位置领域模型转换为 Room 实体。 */
internal fun LocationNode.toEntity(): LocationNodeEntity = LocationNodeEntity(
    id = id.value,
    householdId = householdId.value,
    parentId = parentId?.value,
    type = type.name,
    name = name,
    normalizedName = normalizedName,
    description = description,
    iconKey = iconKey,
    sortOrder = sortOrder.value,
    createdAt = createdAt.epochMilliseconds,
    updatedAt = updatedAt.epochMilliseconds,
    version = version.value,
    sourceDeviceId = sourceDeviceId.value,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将位置 Room 实体还原为领域模型。 */
internal fun LocationNodeEntity.toDomain(): LocationNode = LocationNode(
    id = LocationNodeId(id),
    householdId = HouseholdId(householdId),
    parentId = parentId?.let(::LocationNodeId),
    type = enumValueOf<LocationType>(type),
    name = name,
    normalizedName = normalizedName,
    description = description,
    iconKey = iconKey,
    sortOrder = SortOrder(sortOrder),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    version = EntityVersion(version),
    sourceDeviceId = DeviceId(sourceDeviceId),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将分类领域模型转换为 Room 实体。 */
internal fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id.value,
    householdId = householdId.value,
    name = name,
    normalizedName = normalizedName,
    iconKey = iconKey,
    sortOrder = sortOrder.value,
    isSystem = isSystem,
    createdAt = createdAt.epochMilliseconds,
    updatedAt = updatedAt.epochMilliseconds,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将分类 Room 实体还原为领域模型。 */
internal fun CategoryEntity.toDomain(): Category = Category(
    id = CategoryId(id),
    householdId = HouseholdId(householdId),
    name = name,
    normalizedName = normalizedName,
    iconKey = iconKey,
    sortOrder = SortOrder(sortOrder),
    isSystem = isSystem,
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将物品领域模型转换为 Room 实体。 */
internal fun Item.toEntity(): ItemEntity = ItemEntity(
    id = id.value,
    householdId = householdId.value,
    currentLocationId = currentLocationId.value,
    name = name,
    normalizedName = normalizedName,
    categoryId = categoryId?.value,
    quantity = quantity,
    unit = unit,
    locationDescription = locationDescription,
    note = note,
    status = status.name,
    createdAt = createdAt.epochMilliseconds,
    updatedAt = updatedAt.epochMilliseconds,
    version = version.value,
    sourceDeviceId = sourceDeviceId.value,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将物品 Room 实体还原为领域模型。 */
internal fun ItemEntity.toDomain(): Item = Item(
    id = ItemId(id),
    householdId = HouseholdId(householdId),
    currentLocationId = LocationNodeId(currentLocationId),
    name = name,
    normalizedName = normalizedName,
    categoryId = categoryId?.let(::CategoryId),
    quantity = quantity,
    unit = unit,
    locationDescription = locationDescription,
    note = note,
    status = enumValueOf<ItemStatus>(status),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    version = EntityVersion(version),
    sourceDeviceId = DeviceId(sourceDeviceId),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将物品别名领域模型转换为 Room 实体。 */
internal fun ItemAlias.toEntity(): ItemAliasEntity = ItemAliasEntity(
    id = id.value,
    itemId = itemId.value,
    alias = alias,
    normalizedAlias = normalizedAlias,
    createdAt = createdAt.epochMilliseconds,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将物品别名 Room 实体还原为领域模型。 */
internal fun ItemAliasEntity.toDomain(): ItemAlias = ItemAlias(
    id = ItemAliasId(id),
    itemId = ItemId(itemId),
    alias = alias,
    normalizedAlias = normalizedAlias,
    createdAt = UtcTimestamp(createdAt),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将照片领域模型转换为 Room 实体。 */
internal fun PhotoAsset.toEntity(): PhotoAssetEntity = PhotoAssetEntity(
    id = id.value,
    householdId = householdId.value,
    itemId = itemId.value,
    role = role.name,
    storageKey = storageKey,
    thumbnailStorageKey = thumbnailStorageKey,
    mimeType = mimeType,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    contentHash = contentHash,
    integrityStatus = integrityStatus.name,
    lastIntegrityCheckedAt = lastIntegrityCheckedAt?.epochMilliseconds,
    sortOrder = sortOrder.value,
    isCover = isCover,
    capturedAt = capturedAt?.epochMilliseconds,
    createdAt = createdAt.epochMilliseconds,
    updatedAt = updatedAt.epochMilliseconds,
    version = version.value,
    sourceDeviceId = sourceDeviceId.value,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将照片 Room 实体还原为领域模型。 */
internal fun PhotoAssetEntity.toDomain(): PhotoAsset = PhotoAsset(
    id = PhotoAssetId(id),
    householdId = HouseholdId(householdId),
    itemId = ItemId(itemId),
    role = enumValueOf<PhotoRole>(role),
    storageKey = storageKey,
    thumbnailStorageKey = thumbnailStorageKey,
    mimeType = mimeType,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    contentHash = contentHash,
    integrityStatus = enumValueOf<MediaIntegrityStatus>(integrityStatus),
    lastIntegrityCheckedAt = lastIntegrityCheckedAt?.let(::UtcTimestamp),
    sortOrder = SortOrder(sortOrder),
    isCover = isCover,
    capturedAt = capturedAt?.let(::UtcTimestamp),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    version = EntityVersion(version),
    sourceDeviceId = DeviceId(sourceDeviceId),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将位置照片领域模型转换为 Room 实体。 */
internal fun LocationPhotoAsset.toEntity(): LocationPhotoAssetEntity = LocationPhotoAssetEntity(
    id = id.value,
    householdId = householdId.value,
    locationNodeId = locationNodeId.value,
    storageKey = storageKey,
    thumbnailStorageKey = thumbnailStorageKey,
    mimeType = mimeType,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    contentHash = contentHash,
    integrityStatus = integrityStatus.name,
    lastIntegrityCheckedAt = lastIntegrityCheckedAt?.epochMilliseconds,
    sortOrder = sortOrder.value,
    isCover = isCover,
    createdAt = createdAt.epochMilliseconds,
    updatedAt = updatedAt.epochMilliseconds,
    version = version.value,
    sourceDeviceId = sourceDeviceId.value,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将位置照片 Room 实体还原为领域模型。 */
internal fun LocationPhotoAssetEntity.toDomain(): LocationPhotoAsset = LocationPhotoAsset(
    id = LocationPhotoAssetId(id),
    householdId = HouseholdId(householdId),
    locationNodeId = LocationNodeId(locationNodeId),
    storageKey = storageKey,
    thumbnailStorageKey = thumbnailStorageKey,
    mimeType = mimeType,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    contentHash = contentHash,
    integrityStatus = enumValueOf<MediaIntegrityStatus>(integrityStatus),
    lastIntegrityCheckedAt = lastIntegrityCheckedAt?.let(::UtcTimestamp),
    sortOrder = SortOrder(sortOrder),
    isCover = isCover,
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    version = EntityVersion(version),
    sourceDeviceId = DeviceId(sourceDeviceId),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将语音名称领域模型转换为 Room 实体。 */
internal fun VoiceLabelAsset.toEntity(): VoiceLabelAssetEntity = VoiceLabelAssetEntity(
    id = id.value,
    householdId = householdId.value,
    locationNodeId = locationNodeId?.value,
    itemId = itemId?.value,
    storageKey = storageKey,
    mimeType = mimeType,
    durationMillis = durationMillis,
    sizeBytes = sizeBytes,
    contentHash = contentHash,
    integrityStatus = integrityStatus.name,
    lastIntegrityCheckedAt = lastIntegrityCheckedAt?.epochMilliseconds,
    createdAt = createdAt.epochMilliseconds,
    updatedAt = updatedAt.epochMilliseconds,
    version = version.value,
    sourceDeviceId = sourceDeviceId.value,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将语音名称 Room 实体还原为领域模型。 */
internal fun VoiceLabelAssetEntity.toDomain(): VoiceLabelAsset = VoiceLabelAsset(
    id = VoiceLabelAssetId(id),
    householdId = HouseholdId(householdId),
    locationNodeId = locationNodeId?.let(::LocationNodeId),
    itemId = itemId?.let(::ItemId),
    storageKey = storageKey,
    mimeType = mimeType,
    durationMillis = durationMillis,
    sizeBytes = sizeBytes,
    contentHash = contentHash,
    integrityStatus = enumValueOf<MediaIntegrityStatus>(integrityStatus),
    lastIntegrityCheckedAt = lastIntegrityCheckedAt?.let(::UtcTimestamp),
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    version = EntityVersion(version),
    sourceDeviceId = DeviceId(sourceDeviceId),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将位置历史领域模型转换为 Room 实体。 */
internal fun ItemLocationEvent.toEntity(): ItemLocationEventEntity = ItemLocationEventEntity(
    id = id.value,
    householdId = householdId.value,
    itemId = itemId.value,
    fromLocationId = fromLocationId?.value,
    toLocationId = toLocationId.value,
    fromPathSnapshot = fromPathSnapshot,
    toPathSnapshot = toPathSnapshot,
    reason = reason.name,
    note = note,
    occurredAt = occurredAt.epochMilliseconds,
    sourceDeviceId = sourceDeviceId.value,
    version = version.value,
    deletedAt = deletedAt?.epochMilliseconds,
)

/** 将位置历史 Room 实体还原为领域模型。 */
internal fun ItemLocationEventEntity.toDomain(): ItemLocationEvent = ItemLocationEvent(
    id = ItemLocationEventId(id),
    householdId = HouseholdId(householdId),
    itemId = ItemId(itemId),
    fromLocationId = fromLocationId?.let(::LocationNodeId),
    toLocationId = LocationNodeId(toLocationId),
    fromPathSnapshot = fromPathSnapshot,
    toPathSnapshot = toPathSnapshot,
    reason = enumValueOf<ItemLocationReason>(reason),
    note = note,
    occurredAt = UtcTimestamp(occurredAt),
    sourceDeviceId = DeviceId(sourceDeviceId),
    version = EntityVersion(version),
    deletedAt = deletedAt?.let(::UtcTimestamp),
)

/** 将草稿领域模型转换为 Room 实体。 */
internal fun ItemDraft.toEntity(): ItemDraftEntity = ItemDraftEntity(
    id = id.value,
    householdId = householdId.value,
    deviceId = deviceId.value,
    payload = payload,
    createdAt = createdAt.epochMilliseconds,
    updatedAt = updatedAt.epochMilliseconds,
    expiresAt = expiresAt.epochMilliseconds,
)

/** 将草稿 Room 实体还原为领域模型。 */
internal fun ItemDraftEntity.toDomain(): ItemDraft = ItemDraft(
    id = ItemDraftId(id),
    householdId = HouseholdId(householdId),
    deviceId = DeviceId(deviceId),
    payload = payload,
    createdAt = UtcTimestamp(createdAt),
    updatedAt = UtcTimestamp(updatedAt),
    expiresAt = UtcTimestamp(expiresAt),
)

/** 将变更记录领域模型转换为 Room 实体。 */
internal fun ChangeRecord.toEntity(): ChangeRecordEntity = ChangeRecordEntity(
    id = id.value,
    householdId = householdId.value,
    entityType = entityType.name,
    entityId = entityId,
    operation = operation.name,
    entityVersion = entityVersion.value,
    sourceDeviceId = sourceDeviceId.value,
    occurredAt = occurredAt.epochMilliseconds,
    payloadHash = payloadHash,
)

/** 将变更记录 Room 实体还原为领域模型。 */
internal fun ChangeRecordEntity.toDomain(): ChangeRecord = ChangeRecord(
    id = ChangeRecordId(id),
    householdId = HouseholdId(householdId),
    entityType = enumValueOf<ChangeEntityType>(entityType),
    entityId = entityId,
    operation = enumValueOf<ChangeOperation>(operation),
    entityVersion = EntityVersion(entityVersion),
    sourceDeviceId = DeviceId(sourceDeviceId),
    occurredAt = UtcTimestamp(occurredAt),
    payloadHash = payloadHash,
)
