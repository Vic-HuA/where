package com.vichua.where.feature.item.draft

import com.vichua.where.core.common.EpochMillisecondsClock
import com.vichua.where.core.common.UniqueIdGenerator
import com.vichua.where.core.model.DeviceId
import com.vichua.where.core.model.HouseholdId
import com.vichua.where.core.model.ItemDraft
import com.vichua.where.core.model.ItemDraftId
import com.vichua.where.core.model.LocationNodeId
import com.vichua.where.core.model.MvpLimits
import com.vichua.where.core.model.UtcTimestamp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 新增物品页可恢复的草稿内容。
 *
 * 草稿只保存当前设备上的未完成输入，不进入变更记录或家庭备份。
 */
data class ItemDraftContent(
    val name: String = "",
    val locationId: LocationNodeId? = null,
    val locationDescription: String = "",
    val note: String = "",
) {
    /** 判断用户是否已经填写过任何需要保留的字段。 */
    val hasUserInput: Boolean
        get() = name.isNotBlank() ||
            locationId != null ||
            locationDescription.isNotBlank() ||
            note.isNotBlank()
}

/**
 * 物品草稿的仓储契约。
 */
interface ItemDraftRepository {
    /** 清理过期草稿后，返回当前设备最近一份未过期草稿。 */
    suspend fun loadLatest(): ItemDraft?

    /** 保存当前设备草稿；同 ID 再次保存时覆盖内容并刷新过期时间。 */
    suspend fun save(draft: ItemDraft)

    /** 删除指定设备上的草稿，用于放弃修改或转为正式物品。 */
    suspend fun discard(draftId: ItemDraftId, deviceId: DeviceId)
}

/**
 * 读取当前设备最近一份仍有效的物品草稿。
 */
class LoadLatestItemDraftUseCase(
    private val repository: ItemDraftRepository,
) {
    /**
     * 返回可恢复内容；没有草稿或载荷无法解析时为空。
     *
     * 无法解析的草稿会直接删除，避免把损坏 JSON 反复恢复到录入页。
     */
    suspend operator fun invoke(): ItemDraftContent? {
        val draft = repository.loadLatest() ?: return null
        val content = decodeDraftPayload(draft.payload)
        if (content == null) {
            repository.discard(draft.id, draft.deviceId)
        }
        return content
    }
}

/**
 * 把新增物品页未完成输入保存为当前设备草稿。
 */
class SaveItemDraftUseCase(
    private val repository: ItemDraftRepository,
    private val idGenerator: UniqueIdGenerator,
    private val clock: EpochMillisecondsClock,
) {
    /**
     * 保存名称、位置和备注等未完成输入。
     *
     * 空白草稿不会写入，避免占用 7 天保留窗口。已有未过期草稿时复用同一 ID。
     */
    suspend operator fun invoke(
        householdId: HouseholdId,
        deviceId: DeviceId,
        content: ItemDraftContent,
    ) {
        require(content.hasUserInput) { "Item draft must contain user input." }

        val latestDraft = repository.loadLatest()
        val nowMillis = clock.now()
        val now = UtcTimestamp(nowMillis)
        val expiresAt = UtcTimestamp(
            nowMillis + DAYS_TO_MILLISECONDS * MvpLimits.ITEM_DRAFT_TTL_DAYS,
        )
        repository.save(
            ItemDraft(
                id = latestDraft?.id ?: ItemDraftId(idGenerator.generate()),
                householdId = householdId,
                deviceId = deviceId,
                payload = encodeDraftPayload(content),
                createdAt = latestDraft?.createdAt ?: now,
                updatedAt = now,
                expiresAt = expiresAt,
            ),
        )
    }
}

/**
 * 放弃当前设备最近一份未过期草稿。
 */
class DiscardLatestItemDraftUseCase(
    private val repository: ItemDraftRepository,
) {
    /** 没有草稿时保持静默成功，避免返回流程因空删除失败。 */
    suspend operator fun invoke() {
        val draft = repository.loadLatest() ?: return
        repository.discard(draft.id, draft.deviceId)
    }
}

/**
 * 将草稿内容编码为带格式版本的 JSON。
 *
 * 使用显式字段而不是领域模型直序列化，避免后续增加照片键时破坏旧草稿读取。
 */
internal fun encodeDraftPayload(content: ItemDraftContent): String = buildJsonObject {
    put(KEY_FORMAT_VERSION, CURRENT_FORMAT_VERSION)
    put(KEY_NAME, content.name)
    val locationId = content.locationId?.value
    if (locationId == null) {
        put(KEY_LOCATION_ID, JsonNull)
    } else {
        put(KEY_LOCATION_ID, locationId)
    }
    put(KEY_LOCATION_DESCRIPTION, content.locationDescription)
    put(KEY_NOTE, content.note)
}.toString()

/**
 * 解析草稿 JSON；格式版本不支持或字段损坏时返回空。
 */
internal fun decodeDraftPayload(payload: String): ItemDraftContent? {
    val root = runCatching {
        DRAFT_JSON.parseToJsonElement(payload) as? JsonObject
    }.getOrNull() ?: return null
    val formatVersion = root[KEY_FORMAT_VERSION]?.jsonPrimitive?.intOrNull
    if (formatVersion != CURRENT_FORMAT_VERSION) {
        return null
    }

    val name = root.stringValue(KEY_NAME) ?: return null
    val locationIdText = root.stringValue(KEY_LOCATION_ID)
    val locationId = locationIdText
        ?.takeIf(String::isNotBlank)
        ?.let { value ->
            runCatching { LocationNodeId(value) }.getOrNull()
        }
    return ItemDraftContent(
        name = name,
        locationId = locationId,
        locationDescription = root.stringValue(KEY_LOCATION_DESCRIPTION).orEmpty(),
        note = root.stringValue(KEY_NOTE).orEmpty(),
    )
}

private fun JsonObject.stringValue(key: String): String? {
    val element = this[key] ?: return ""
    val primitive = element as? JsonPrimitive ?: return null
    return primitive.contentOrNull.orEmpty()
}

private val DRAFT_JSON = Json {
    ignoreUnknownKeys = true
}

private const val CURRENT_FORMAT_VERSION = 1
private const val KEY_FORMAT_VERSION = "formatVersion"
private const val KEY_NAME = "name"
private const val KEY_LOCATION_ID = "locationId"
private const val KEY_LOCATION_DESCRIPTION = "locationDescription"
private const val KEY_NOTE = "note"
private const val DAYS_TO_MILLISECONDS = 24L * 60L * 60L * 1000L
