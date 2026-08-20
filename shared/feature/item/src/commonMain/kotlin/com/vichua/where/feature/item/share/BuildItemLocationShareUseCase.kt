package com.vichua.where.feature.item.share

import com.vichua.where.core.common.VisibleDateTimeFormatter
import com.vichua.where.core.model.PhotoAssetId
import com.vichua.where.core.model.UtcTimestamp
import com.vichua.where.feature.item.detail.ItemDetail

/**
 * 分享预览和系统分享面板使用的位置内容。
 *
 * @property text 仅包含物品名称、位置和可选更新时间。
 * @property photoStorageKeys 用户选定且仍存在的照片受控标识。
 */
data class ItemLocationShareContent(
    val text: String,
    val photoStorageKeys: List<String>,
)

/**
 * 按用户勾选项组装分享文本和选定照片，避免把备注或家庭数据带出应用。
 */
class BuildItemLocationShareUseCase(
    private val dateTimeFormatter: VisibleDateTimeFormatter,
) {
    /**
     * 把物品最近更新时间格式化为分享预览中的本地时间。
     */
    fun formatUpdatedAt(detail: ItemDetail): String =
        dateTimeFormatter.format(detail.updatedAt.epochMilliseconds)

    /**
     * 生成分享内容。
     *
     * 未勾选照片时只返回文字；勾选但不存在的照片会被忽略，保证文字分享仍可用。
     */
    operator fun invoke(
        detail: ItemDetail,
        includeUpdatedAt: Boolean,
        selectedPhotoIds: Collection<PhotoAssetId>,
    ): ItemLocationShareContent {
        val text = buildString {
            append(detail.name)
            append('\n')
            append(detail.locationPath)
            val locationDescription = detail.locationDescription
            if (!locationDescription.isNullOrBlank()) {
                append('\n')
                append(locationDescription)
            }
            if (includeUpdatedAt) {
                append('\n')
                append(dateTimeFormatter.format(detail.updatedAt.epochMilliseconds))
            }
        }
        require(text.isNotBlank()) { "Share text must not be blank." }
        val selectedIds = selectedPhotoIds.toSet()
        val photoStorageKeys = detail.photos
            .filter { photo -> selectedIds.contains(photo.photoId) }
            .map { photo -> photo.storageKey }
        return ItemLocationShareContent(
            text = text,
            photoStorageKeys = photoStorageKeys,
        )
    }
}

/**
 * 组装详情页朗读文本：物品名称、逐级位置和更新时间。
 *
 * 不朗读备注，避免用户未主动要求时读出可能敏感的补充信息。
 */
class BuildItemLocationSpeechUseCase(
    private val dateTimeFormatter: VisibleDateTimeFormatter,
) {
    /**
     * 生成一段适合本地 TTS 的短句。
     */
    operator fun invoke(detail: ItemDetail): String = fromParts(
        name = detail.name,
        locationPath = detail.locationPath,
        locationDescription = detail.locationDescription,
        updatedAt = detail.updatedAt,
    )

    /**
     * 用名称、逐级位置和更新时间组装朗读文本，供详情和搜索结果共用。
     */
    fun fromParts(
        name: String,
        locationPath: String,
        locationDescription: String?,
        updatedAt: UtcTimestamp,
    ): String {
        val spokenTime = dateTimeFormatter.format(updatedAt.epochMilliseconds)
        return buildString {
            append(name)
            append('。')
            append(locationPath)
            append('。')
            if (!locationDescription.isNullOrBlank()) {
                append(locationDescription)
                append('。')
            }
            append("最近更新于")
            append(spokenTime)
            append('。')
        }
    }
}
