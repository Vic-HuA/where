package com.vichua.where.feature.backup

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.crypto.BackupCrypto
import com.vichua.where.core.crypto.BackupPackageCodec
import com.vichua.where.core.crypto.ContentHasher
import com.vichua.where.core.model.BackupEnvelope
import com.vichua.where.core.model.BackupMediaPayload
import com.vichua.where.core.model.ChangeEntityType
import com.vichua.where.core.model.ConflictResolution
import com.vichua.where.core.model.HouseholdBackupSnapshot
import com.vichua.where.core.model.HouseholdDataSummary
import com.vichua.where.core.model.ImportConflict
import com.vichua.where.core.model.ImportPreview
import com.vichua.where.core.model.Item
import com.vichua.where.core.model.ItemDraft
import com.vichua.where.core.model.ItemId
import com.vichua.where.core.model.RestoreMode
import com.vichua.where.core.model.RestoreSession
import com.vichua.where.core.platform.ControlledMediaFileStore
import com.vichua.where.core.platform.DocumentGateway
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 打开备份并在内存中生成恢复预览，不写入正式家庭库。
 */
class PreviewBackupRestoreUseCase(
    private val repository: HouseholdBackupRepository,
    private val documentGateway: DocumentGateway,
    private val backupCrypto: BackupCrypto,
    private val contentHasher: ContentHasher,
) {
    /**
     * 解密、校验并对比当前快照。用户取消选择文件时返回空。
     */
    suspend operator fun invoke(password: String): RestoreSession? {
        require(password.isNotEmpty()) { "Backup password must not be empty." }
        require(documentGateway.isAvailable()) { "Document picker is unavailable." }
        val opened = documentGateway.openDocument() ?: return null
        val envelope = decodeVerifiedEnvelope(
            packageBytes = opened.bytes,
            password = password,
            backupCrypto = backupCrypto,
            contentHasher = contentHasher,
        )
        val current = repository.loadSnapshot()
        return RestoreSession(
            preview = buildImportPreview(current, envelope, opened.bytes.size.toLong()),
            envelope = envelope,
        )
    }
}

/**
 * 在用户确认后执行合并或替换，失败时回滚到恢复前快照和原图。
 */
class ApplyBackupRestoreUseCase(
    private val repository: HouseholdBackupRepository,
    private val mediaFileStore: ControlledMediaFileStore,
    private val idGenerator: UniqueIdGenerator,
) {
    /**
     * 应用恢复会话。合并时每条冲突都必须已选择处理方式。
     */
    suspend operator fun invoke(
        session: RestoreSession,
        mode: RestoreMode,
        resolutions: Map<String, ConflictResolution>,
    ) {
        if (mode == RestoreMode.MERGE) {
            require(!session.preview.differentHousehold) {
                "Merge restore cannot apply a backup from another household."
            }
            session.preview.conflicts.forEach { conflict ->
                val resolution = resolutions[conflict.key]
                require(resolution != null) { "Every restore conflict must be resolved before merge." }
                require(resolution in conflict.allowedResolutions) {
                    "Restore conflict resolution is not allowed for this entity."
                }
            }
        }

        val current = repository.loadSnapshot()
        val safetyMedia = current.photos.mapNotNull { photo ->
            mediaFileStore.readBytes(photo.storageKey)?.let { bytes ->
                BackupMediaPayload(storageKey = photo.storageKey, bytes = bytes)
            }
        }
        val target = when (mode) {
            RestoreMode.REPLACE -> session.envelope.snapshot
            RestoreMode.MERGE -> mergeSnapshots(
                local = current,
                incoming = session.envelope.snapshot,
                resolutions = resolutions,
                idGenerator = idGenerator,
            )
        }
        val remappedMedia = collectRestoredMedia(session.envelope, target)
        try {
            writeMedia(mediaFileStore, remappedMedia)
            repository.replaceSnapshot(target)
            sanitizeDrafts(repository, target)
        } catch (error: Exception) {
            runCatching { repository.replaceSnapshot(current) }
            runCatching { writeMedia(mediaFileStore, safetyMedia.map { media ->
                val photo = current.photos.firstOrNull { item -> item.storageKey == media.storageKey }
                RestoredMedia(
                    storageKey = media.storageKey,
                    thumbnailStorageKey = photo?.thumbnailStorageKey ?: media.storageKey,
                    bytes = media.bytes,
                )
            }) }
            throw error
        }
    }
}

/**
 * 清除当前家庭可导出数据，并保留本机设置与草稿文字。
 */
class ClearHouseholdDataUseCase(
    private val repository: HouseholdBackupRepository,
    private val mediaFileStore: ControlledMediaFileStore,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 读取清除确认所需的当前家庭摘要。
     */
    suspend fun loadSummary(): HouseholdDataSummary = repository.loadSummary()

    /**
     * 二次确认后删除家庭实体和物品原图。
     */
    suspend operator fun invoke() {
        val drafts = repository.loadDrafts()
        val photos = repository.loadPhotos()
        drafts.forEach { draft ->
            repository.saveDraft(clearDraftLocation(draft))
        }
        repository.clearHousehold(clock.now())
        val discardedKeys = photos.flatMap { photo ->
            listOf(photo.storageKey, photo.thumbnailStorageKey)
        }
        if (discardedKeys.isNotEmpty()) {
            mediaFileStore.discard(discardedKeys)
        }
    }
}

private fun decodeVerifiedEnvelope(
    packageBytes: ByteArray,
    password: String,
    backupCrypto: BackupCrypto,
    contentHasher: ContentHasher,
): BackupEnvelope {
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
    return envelope
}

private fun buildImportPreview(
    current: HouseholdBackupSnapshot,
    envelope: BackupEnvelope,
    packageSizeBytes: Long,
): ImportPreview {
    val incoming = envelope.snapshot
    val differentHousehold = current.household.id != incoming.household.id
    val conflicts = mutableListOf<ImportConflict>()
    var added = 0
    var updated = 0
    var deleted = 0

    fun <T> accumulate(
        localItems: List<T>,
        incomingItems: List<T>,
        idOf: (T) -> String,
        versionOf: (T) -> Long,
        equalTo: (T, T) -> Boolean,
        labelOf: (T) -> String,
        localTextOf: (T) -> String,
        incomingTextOf: (T) -> String,
        fieldsOf: (T, T) -> List<String>,
        entityType: ChangeEntityType,
        allowKeepBoth: Boolean,
    ) {
        val localById = localItems.associateBy(idOf)
        val incomingById = incomingItems.associateBy(idOf)
        incomingById.keys.minus(localById.keys).forEach { _ -> added += 1 }
        localById.keys.minus(incomingById.keys).forEach { _ -> deleted += 1 }
        incomingById.keys.intersect(localById.keys).forEach { id ->
            val local = requireNotNull(localById[id])
            val remote = requireNotNull(incomingById[id])
            if (equalTo(local, remote)) {
                return@forEach
            }
            if (versionOf(remote) > versionOf(local)) {
                updated += 1
                return@forEach
            }
            val resolutions = buildList {
                add(ConflictResolution.KEEP_LOCAL)
                add(ConflictResolution.USE_INCOMING)
                if (allowKeepBoth) {
                    add(ConflictResolution.KEEP_BOTH)
                }
            }
            conflicts += ImportConflict(
                entityType = entityType,
                entityId = id,
                entityLabel = labelOf(local),
                conflictFields = fieldsOf(local, remote),
                localValue = localTextOf(local),
                incomingValue = incomingTextOf(remote),
                localVersion = versionOf(local),
                incomingVersion = versionOf(remote),
                allowedResolutions = resolutions,
            )
        }
    }

    accumulate(
        localItems = listOf(current.household),
        incomingItems = listOf(incoming.household),
        idOf = { household -> household.id.value },
        versionOf = { household -> household.version.value },
        equalTo = { left, right -> left.name == right.name && left.deletedAt == right.deletedAt },
        labelOf = { household -> household.name },
        localTextOf = { household -> household.name },
        incomingTextOf = { household -> household.name },
        fieldsOf = { left, right -> differingFields("名称" to (left.name != right.name)) },
        entityType = ChangeEntityType.HOUSEHOLD,
        allowKeepBoth = false,
    )
    accumulate(
        localItems = current.locations,
        incomingItems = incoming.locations,
        idOf = { location -> location.id.value },
        versionOf = { location -> location.version.value },
        equalTo = { left, right ->
            left.name == right.name &&
                left.parentId == right.parentId &&
                left.deletedAt == right.deletedAt
        },
        labelOf = { location -> location.name },
        localTextOf = { location -> location.name },
        incomingTextOf = { location -> location.name },
        fieldsOf = { left, right ->
            differingFields(
                "名称" to (left.name != right.name),
                "父位置" to (left.parentId != right.parentId),
            )
        },
        entityType = ChangeEntityType.LOCATION_NODE,
        allowKeepBoth = false,
    )
    accumulate(
        localItems = current.items,
        incomingItems = incoming.items,
        idOf = { item -> item.id.value },
        versionOf = { item -> item.version.value },
        equalTo = { left, right ->
            left.name == right.name &&
                left.currentLocationId == right.currentLocationId &&
                left.note == right.note &&
                left.locationDescription == right.locationDescription &&
                left.deletedAt == right.deletedAt
        },
        labelOf = { item -> item.name },
        localTextOf = { item -> itemSummary(item) },
        incomingTextOf = { item -> itemSummary(item) },
        fieldsOf = { left, right ->
            differingFields(
                "名称" to (left.name != right.name),
                "位置" to (left.currentLocationId != right.currentLocationId),
                "备注" to (left.note != right.note),
            )
        },
        entityType = ChangeEntityType.ITEM,
        allowKeepBoth = true,
    )
    accumulate(
        localItems = current.photos,
        incomingItems = incoming.photos,
        idOf = { photo -> photo.id.value },
        versionOf = { photo -> photo.version.value },
        equalTo = { left, right ->
            left.contentHash == right.contentHash &&
                left.isCover == right.isCover &&
                left.deletedAt == right.deletedAt
        },
        labelOf = { photo -> "照片 ${photo.id.value.take(8)}" },
        localTextOf = { photo -> photo.contentHash.take(8) },
        incomingTextOf = { photo -> photo.contentHash.take(8) },
        fieldsOf = { left, right ->
            differingFields("照片内容" to (left.contentHash != right.contentHash))
        },
        entityType = ChangeEntityType.PHOTO_ASSET,
        allowKeepBoth = false,
    )
    accumulate(
        localItems = current.favoriteLocations,
        incomingItems = incoming.favoriteLocations,
        idOf = { favorite -> favorite.id.value },
        versionOf = { favorite -> favorite.version.value },
        equalTo = { left, right ->
            left.locationNodeId == right.locationNodeId && left.deletedAt == right.deletedAt
        },
        labelOf = { favorite -> "常用位置" },
        localTextOf = { favorite -> favorite.locationNodeId.value },
        incomingTextOf = { favorite -> favorite.locationNodeId.value },
        fieldsOf = { left, right ->
            differingFields("位置" to (left.locationNodeId != right.locationNodeId))
        },
        entityType = ChangeEntityType.FAVORITE_LOCATION,
        allowKeepBoth = false,
    )

    return ImportPreview(
        manifest = envelope.manifest,
        packageSizeBytes = packageSizeBytes,
        itemCount = incoming.items.size,
        locationCount = incoming.locations.size,
        itemPhotoCount = envelope.manifest.itemPhotoCount,
        locationPhotoCount = envelope.manifest.locationPhotoCount,
        voiceLabelCount = envelope.manifest.voiceLabelCount,
        addedCount = added,
        updatedCount = updated,
        deletedCount = deleted,
        conflictCount = conflicts.size,
        conflicts = conflicts,
        currentSummary = HouseholdDataSummary(
            itemCount = current.items.size,
            locationCount = current.locations.size,
            photoCount = current.photos.size,
        ),
        differentHousehold = differentHousehold,
    )
}

private fun mergeSnapshots(
    local: HouseholdBackupSnapshot,
    incoming: HouseholdBackupSnapshot,
    resolutions: Map<String, ConflictResolution>,
    idGenerator: UniqueIdGenerator,
): HouseholdBackupSnapshot {
    val items = mergeItems(local.items, incoming.items, resolutions, idGenerator)
    val itemIds = items.map(Item::id).toSet()
    val aliases = mergeById(
        local = local.aliases,
        incoming = incoming.aliases,
        idOf = { alias -> alias.id.value },
        entityType = ChangeEntityType.ITEM_ALIAS,
        resolutions = resolutions,
        versionOf = { 1L },
        copyIncoming = { alias -> alias },
    )
    val photos = mergeById(
        local = local.photos,
        incoming = incoming.photos,
        idOf = { photo -> photo.id.value },
        entityType = ChangeEntityType.PHOTO_ASSET,
        resolutions = resolutions,
        versionOf = { photo -> photo.version.value },
        copyIncoming = { photo -> photo },
    )
    val locations = mergeById(
        local = local.locations,
        incoming = incoming.locations,
        idOf = { location -> location.id.value },
        entityType = ChangeEntityType.LOCATION_NODE,
        resolutions = resolutions,
        versionOf = { location -> location.version.value },
        copyIncoming = { location -> location },
    )
    val household = when (resolutions["${ChangeEntityType.HOUSEHOLD.name}:${local.household.id.value}"]) {
        ConflictResolution.USE_INCOMING -> incoming.household
        else -> if (incoming.household.version.value > local.household.version.value) {
            incoming.household
        } else {
            local.household
        }
    }
    val favorites = mergeById(
        local = local.favoriteLocations,
        incoming = incoming.favoriteLocations,
        idOf = { favorite -> favorite.id.value },
        entityType = ChangeEntityType.FAVORITE_LOCATION,
        resolutions = resolutions,
        versionOf = { favorite -> favorite.version.value },
        copyIncoming = { favorite -> favorite },
    )
    val categories = incoming.categories.associateBy { category -> category.id } +
        local.categories.associateBy { category -> category.id }
    val events = (local.locationEvents + incoming.locationEvents)
        .associateBy { event -> event.id }
        .values
        .toList()
    val changeRecords = (local.changeRecords + incoming.changeRecords)
        .associateBy { record -> record.id }
        .values
        .toList()
    return HouseholdBackupSnapshot(
        household = household,
        devices = local.devices,
        locations = locations,
        categories = categories.values.toList(),
        items = items,
        aliases = aliases.filter { alias -> alias.itemId in itemIds },
        photos = photos,
        locationEvents = events,
        changeRecords = changeRecords,
        favoriteLocations = favorites,
    )
}

private fun mergeItems(
    local: List<Item>,
    incoming: List<Item>,
    resolutions: Map<String, ConflictResolution>,
    idGenerator: UniqueIdGenerator,
): List<Item> {
    val localById = local.associateBy { item -> item.id.value }
    val incomingById = incoming.associateBy { item -> item.id.value }
    val result = linkedMapOf<String, Item>()
    localById.forEach { (id, item) ->
        result[id] = item
    }
    incomingById.forEach { (id, item) ->
        val localItem = localById[id]
        if (localItem == null) {
            result[id] = item
            return@forEach
        }
        if (item.version.value > localItem.version.value &&
            resolutions["${ChangeEntityType.ITEM.name}:$id"] == null
        ) {
            result[id] = item
            return@forEach
        }
        when (resolutions["${ChangeEntityType.ITEM.name}:$id"]) {
            ConflictResolution.USE_INCOMING -> result[id] = item
            ConflictResolution.KEEP_BOTH -> {
                val copied = item.copy(id = ItemId(idGenerator.generate()))
                result[copied.id.value] = copied
            }
            ConflictResolution.KEEP_LOCAL, null -> Unit
        }
    }
    return result.values.toList()
}

private fun <T> mergeById(
    local: List<T>,
    incoming: List<T>,
    idOf: (T) -> String,
    entityType: ChangeEntityType,
    resolutions: Map<String, ConflictResolution>,
    versionOf: (T) -> Long,
    copyIncoming: (T) -> T,
): List<T> {
    val localById = local.associateBy(idOf)
    val incomingById = incoming.associateBy(idOf)
    val result = linkedMapOf<String, T>()
    localById.forEach { (id, value) ->
        result[id] = value
    }
    incomingById.forEach { (id, value) ->
        val localValue = localById[id]
        if (localValue == null) {
            result[id] = copyIncoming(value)
            return@forEach
        }
        when (resolutions["${entityType.name}:$id"]) {
            ConflictResolution.USE_INCOMING -> result[id] = copyIncoming(value)
            ConflictResolution.KEEP_LOCAL, ConflictResolution.KEEP_BOTH, null -> {
                if (versionOf(value) > versionOf(localValue) &&
                    resolutions["${entityType.name}:$id"] == null
                ) {
                    result[id] = copyIncoming(value)
                }
            }
        }
    }
    return result.values.toList()
}

private fun collectRestoredMedia(
    envelope: BackupEnvelope,
    target: HouseholdBackupSnapshot,
): List<RestoredMedia> {
    val payloadByKey = envelope.mediaFiles.associateBy { media -> media.storageKey }
    return target.photos.mapNotNull { photo ->
        val payload = payloadByKey[photo.storageKey] ?: return@mapNotNull null
        RestoredMedia(
            storageKey = photo.storageKey,
            thumbnailStorageKey = photo.thumbnailStorageKey,
            bytes = payload.bytes,
        )
    }
}

private suspend fun writeMedia(
    mediaFileStore: ControlledMediaFileStore,
    media: List<RestoredMedia>,
) {
    media.forEach { item ->
        mediaFileStore.writeRestoredPhoto(
            storageKey = item.storageKey,
            thumbnailStorageKey = item.thumbnailStorageKey,
            bytes = item.bytes,
        )
    }
}

private suspend fun sanitizeDrafts(
    repository: HouseholdBackupRepository,
    snapshot: HouseholdBackupSnapshot,
) {
    val validLocationIds = snapshot.locations
        .filter { location -> location.deletedAt == null }
        .map { location -> location.id.value }
        .toSet()
    repository.loadDrafts().forEach { draft ->
        repository.saveDraft(clearDraftLocationIfInvalid(draft, validLocationIds))
    }
}

private fun clearDraftLocation(draft: ItemDraft): ItemDraft =
    clearDraftLocationIfInvalid(draft, emptySet())

private fun clearDraftLocationIfInvalid(
    draft: ItemDraft,
    validLocationIds: Set<String>,
): ItemDraft {
    val root = runCatching {
        BACKUP_JSON.parseToJsonElement(draft.payload) as? JsonObject
    }.getOrNull() ?: return draft
    val locationId = root["locationId"]?.jsonPrimitive?.contentOrNull
    if (locationId.isNullOrBlank() || locationId in validLocationIds) {
        return draft
    }
    val rewritten = buildJsonObject {
        root.forEach { (key, value) ->
            if (key == "locationId") {
                put(key, JsonNull)
            } else {
                put(key, value)
            }
        }
    }.toString()
    return draft.copy(payload = rewritten)
}

private fun differingFields(vararg fields: Pair<String, Boolean>): List<String> {
    val names = fields.filter { field -> field.second }.map { field -> field.first }
    return names.ifEmpty { listOf("内容") }
}

private fun itemSummary(item: Item): String = buildString {
    append(item.name)
    if (item.note != null) {
        append(" · ")
        append(item.note)
    }
}

private data class RestoredMedia(
    val storageKey: String,
    val thumbnailStorageKey: String,
    val bytes: ByteArray,
)

private val BACKUP_JSON = Json {
    encodeDefaults = true
    ignoreUnknownKeys = false
}
