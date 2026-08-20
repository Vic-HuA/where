package com.vichua.where

import android.util.Log
import com.vichua.where.core.platform.AiAssistanceGateway
import com.vichua.where.core.platform.AiAssistanceOutcome
import com.vichua.where.core.platform.AiPhotoInput

/**
 * Android 上的 AI 辅助适配器。
 *
 * 当前没有配置云端供应商，因此不读取原图、不发起上传，只返回可降级结果。
 */
class AndroidAiAssistanceGateway : AiAssistanceGateway {
    /**
     * 未配置供应商时始终不可用。
     */
    override fun isAvailable(): Boolean = false

    /**
     * 没有选中内容时直接返回；有选中内容也不上传，避免后台把照片送出去。
     */
    override suspend fun analyzePhotos(photos: List<AiPhotoInput>): AiAssistanceOutcome {
        if (photos.isEmpty()) {
            return AiAssistanceOutcome.NoSelectedContent
        }
        Log.i(TAG, "AI assistance provider is not configured; skip upload.")
        return AiAssistanceOutcome.Unavailable
    }

    private companion object {
        const val TAG = "WhereAiAssistance"
    }
}
