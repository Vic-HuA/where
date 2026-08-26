package com.vichua.where.core.model

import kotlinx.serialization.Serializable

/**
 * 家庭记录的全局唯一标识。
 *
 * @property value 客户端生成且不包含用户隐私信息的标识文本。
 */
@Serializable
@JvmInline
value class HouseholdId(val value: String) {
    init {
        require(value.isNotBlank()) { "Household ID must not be blank." }
    }
}

/**
 * 设备记录的全局唯一标识。
 *
 * @property value 客户端生成且不包含设备名称等隐私信息的标识文本。
 */
@Serializable
@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Device ID must not be blank." }
    }
}

/**
 * 位置节点的全局唯一标识。
 *
 * @property value 客户端生成且不包含房间或容器名称的标识文本。
 */
@Serializable
@JvmInline
value class LocationNodeId(val value: String) {
    init {
        require(value.isNotBlank()) { "Location node ID must not be blank." }
    }
}

/**
 * 物品档案的全局唯一标识。
 *
 * @property value 客户端生成且不包含物品名称的标识文本。
 */
@Serializable
@JvmInline
value class ItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "Item ID must not be blank." }
    }
}

/**
 * 物品别名记录的全局唯一标识。
 *
 * @property value 客户端生成的标识文本。
 */
@Serializable
@JvmInline
value class ItemAliasId(val value: String) {
    init {
        require(value.isNotBlank()) { "Item alias ID must not be blank." }
    }
}

/**
 * 物品分类的全局唯一标识。
 *
 * @property value 客户端生成或系统预留的标识文本。
 */
@Serializable
@JvmInline
value class CategoryId(val value: String) {
    init {
        require(value.isNotBlank()) { "Category ID must not be blank." }
    }
}

/**
 * 物品照片记录的全局唯一标识。
 *
 * @property value 客户端生成且不包含文件路径的标识文本。
 */
@Serializable
@JvmInline
value class PhotoAssetId(val value: String) {
    init {
        require(value.isNotBlank()) { "Photo asset ID must not be blank." }
    }
}

/**
 * 位置照片记录的全局唯一标识。
 *
 * @property value 客户端生成且不包含文件路径的标识文本。
 */
@Serializable
@JvmInline
value class LocationPhotoAssetId(val value: String) {
    init {
        require(value.isNotBlank()) { "Location photo asset ID must not be blank." }
    }
}

/**
 * 语音名称记录的全局唯一标识。
 *
 * @property value 客户端生成且不包含文件路径的标识文本。
 */
@Serializable
@JvmInline
value class VoiceLabelAssetId(val value: String) {
    init {
        require(value.isNotBlank()) { "Voice label asset ID must not be blank." }
    }
}

/**
 * 位置历史事件的全局唯一标识。
 *
 * @property value 客户端生成的标识文本。
 */
@Serializable
@JvmInline
value class ItemLocationEventId(val value: String) {
    init {
        require(value.isNotBlank()) { "Item location event ID must not be blank." }
    }
}

/**
 * 未完成物品草稿的全局唯一标识。
 *
 * @property value 当前设备生成的标识文本。
 */
@Serializable
@JvmInline
value class ItemDraftId(val value: String) {
    init {
        require(value.isNotBlank()) { "Item draft ID must not be blank." }
    }
}

/**
 * 正式数据变更记录的全局唯一标识。
 *
 * @property value 客户端生成的标识文本。
 */
@Serializable
@JvmInline
value class ChangeRecordId(val value: String) {
    init {
        require(value.isNotBlank()) { "Change record ID must not be blank." }
    }
}

/**
 * 常用位置引用的全局唯一标识。
 *
 * @property value 客户端生成的标识文本。
 */
@Serializable
@JvmInline
value class FavoriteLocationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Favorite location ID must not be blank." }
    }
}

/**
 * 常用物品入口的全局唯一标识。
 *
 * @property value 客户端生成的标识文本。
 */
@Serializable
@JvmInline
value class PinnedItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "Pinned item ID must not be blank." }
    }
}

/**
 * 当前设备最近查找记录的唯一标识。
 *
 * @property value 当前设备生成的标识文本。
 */
@Serializable
@JvmInline
value class LocalSearchHistoryId(val value: String) {
    init {
        require(value.isNotBlank()) { "Local search history ID must not be blank." }
    }
}

/**
 * 当前设备本地备份记录的唯一标识。
 *
 * @property value 当前设备生成的标识文本。
 */
@Serializable
@JvmInline
value class LocalBackupRecordId(val value: String) {
    init {
        require(value.isNotBlank()) { "Local backup record ID must not be blank." }
    }
}

/**
 * 以 Unix Epoch 毫秒表示的 UTC 时间戳。
 *
 * 使用数值而不是格式化字符串保存时间，确保跨时区排序和迁移结果稳定。
 *
 * @property epochMilliseconds 从 Unix Epoch 开始计算的非负毫秒数。
 */
@Serializable
@JvmInline
value class UtcTimestamp(val epochMilliseconds: Long) : Comparable<UtcTimestamp> {
    init {
        require(epochMilliseconds >= 0L) { "UTC timestamp must not be negative." }
    }

    override fun compareTo(other: UtcTimestamp): Int =
        epochMilliseconds.compareTo(other.epochMilliseconds)
}

/**
 * 实体版本号，用于乐观并发校验和导入冲突判断。
 *
 * @property value 从 1 开始并在每次正式变更后递增的版本值。
 */
@Serializable
@JvmInline
value class EntityVersion(val value: Long) {
    init {
        require(value >= INITIAL_VALUE) { "Entity version must be at least $INITIAL_VALUE." }
    }

    /**
     * 返回下一版本，调用方仍需在同一事务中写入实体和变更记录。
     */
    fun next(): EntityVersion {
        require(value < Long.MAX_VALUE) { "Entity version has reached its maximum value." }
        return EntityVersion(value + 1L)
    }

    private companion object {
        const val INITIAL_VALUE = 1L
    }
}

/**
 * 同级实体的稳定展示顺序。
 *
 * @property value 从 0 开始的非负排序值。
 */
@Serializable
@JvmInline
value class SortOrder(val value: Int) : Comparable<SortOrder> {
    init {
        require(value >= 0) { "Sort order must not be negative." }
    }

    override fun compareTo(other: SortOrder): Int = value.compareTo(other.value)
}

/**
 * MVP 中跨模块共享的冻结限制值。
 *
 * 这些值来自已冻结的数据模型，修改时必须同步产品规格和迁移策略。
 */
object MvpLimits {
    /** 草稿从最近一次用户修改开始保留的天数。 */
    const val ITEM_DRAFT_TTL_DAYS = 7

    /** 删除物品后在当前应用会话提供的撤销秒数。 */
    const val DELETE_UNDO_WINDOW_SECONDS = 10

    /** 单物品达到该照片数量后开始提示存储占用，但不静默拒绝后续照片。 */
    const val ITEM_PHOTO_WARNING_THRESHOLD = 20

    /** 单次 AI 图片请求允许用户主动选择的最大照片数。 */
    const val AI_PHOTO_REQUEST_LIMIT = 6

    /** 单次云端语音识别允许的最长录音秒数。 */
    const val CLOUD_SPEECH_MAX_DURATION_SECONDS = 60
}
