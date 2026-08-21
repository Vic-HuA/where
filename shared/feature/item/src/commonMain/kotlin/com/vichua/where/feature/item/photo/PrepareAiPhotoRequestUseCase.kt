package com.vichua.where.feature.item.photo

import com.vichua.where.core.model.MvpLimits
import com.vichua.where.core.platform.AiPhotoInput

/**
 * 把用户为当前任务主动选出的照片收成一次 AI 请求。
 *
 * 只取前 [MvpLimits.AI_PHOTO_REQUEST_LIMIT] 张，超出部分留在本地，不自动加入请求。
 */
class PrepareAiPhotoRequestUseCase {
    /**
     * 返回本次允许交给 AI 的照片；没有选中照片时为空列表。
     */
    operator fun invoke(photos: List<ImportedItemPhoto>): List<AiPhotoInput> {
        return photos
            .take(MvpLimits.AI_PHOTO_REQUEST_LIMIT)
            .map { photo ->
                AiPhotoInput(
                    role = photo.role,
                    sizeBytes = photo.media.sizeBytes,
                    storageKey = photo.media.tempStorageKey,
                    thumbnailStorageKey = photo.media.thumbnailTempStorageKey,
                    mimeType = photo.media.mimeType,
                )
            }
    }
}
