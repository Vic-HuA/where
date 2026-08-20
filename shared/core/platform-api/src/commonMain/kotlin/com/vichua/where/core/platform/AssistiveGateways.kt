package com.vichua.where.core.platform

import com.vichua.where.core.model.PhotoRole

/**
 * 本地文字朗读入口。
 *
 * 引擎不可用时必须返回可降级结果，不能让详情页主流程崩溃。
 */
interface TextToSpeechGateway {
    /**
     * 当前设备是否具备可用的本地朗读引擎。
     */
    fun isAvailable(): Boolean

    /**
     * 朗读一段用户主动触发的文本，完成后返回。
     *
     * 新的朗读会打断正在播放的内容；取消协程时停止发音。
     */
    suspend fun speak(text: String)

    /**
     * 立即停止当前朗读。
     */
    fun stop()
}

/**
 * 一次系统分享需要的文本和可选本地照片。
 *
 * @property text 物品名称、位置和可选更新时间，不包含家庭数据包。
 * @property imageAbsolutePaths 已解析的私有照片绝对路径；空列表表示只分享文字。
 */
data class SharePayload(
    val text: String,
    val imageAbsolutePaths: List<String> = emptyList(),
) {
    init {
        require(text.isNotBlank()) { "Share text must not be blank." }
        require(imageAbsolutePaths.all { path -> path.isNotBlank() }) {
            "Share image path must not be blank."
        }
    }
}

/**
 * 系统分享面板入口。
 *
 * 只分享调用方准备好的文本和选定照片，不得附带数据库或其他家庭文件。
 */
interface ShareGateway {
    /**
     * 当前设备是否可以打开系统分享面板。
     */
    fun isAvailable(): Boolean

    /**
     * 打开系统分享面板。用户取消分享时正常返回。
     */
    suspend fun share(payload: SharePayload)
}

/**
 * 用户通过系统文件选择器保存或打开的文档。
 *
 * @property displayName 用户可见文件名。
 * @property opaqueDocumentUri 平台文档 URI，只作为受控引用。
 * @property bytes 完整文件字节。
 */
data class SelectedDocument(
    val displayName: String,
    val opaqueDocumentUri: String,
    val bytes: ByteArray,
) {
    init {
        require(displayName.isNotBlank()) { "Selected document name must not be blank." }
        require(opaqueDocumentUri.isNotBlank()) { "Selected document URI must not be blank." }
        require(bytes.isNotEmpty()) { "Selected document must not be empty." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectedDocument) return false
        return displayName == other.displayName &&
            opaqueDocumentUri == other.opaqueDocumentUri &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + opaqueDocumentUri.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/**
 * 系统文件选择器入口，用于加密备份的保存和只读打开。
 *
 * 取消选择时返回空，不能让设置页主流程崩溃。
 */
interface DocumentGateway {
    /**
     * 当前设备是否可以打开系统文件选择器。
     */
    fun isAvailable(): Boolean

    /**
     * 让用户选择保存位置并写入完整备份字节。
     *
     * @return 写出后的文档引用；用户取消时为空。
     */
    suspend fun createDocument(
        suggestedFileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): SelectedDocument?

    /**
     * 让用户选择已有备份文件并读取完整字节。
     *
     * @return 选中的文档；用户取消时为空。
     */
    suspend fun openDocument(): SelectedDocument?

    /**
     * 通过系统分享面板送出完整导出数据包。
     *
     * 只分享调用方已经加密好的字节，不得附带运行中数据库或其他家庭文件。
     *
     * @return 已写出的缓存文档引用；无法打开分享面板时为空。
     */
    suspend fun shareDocument(
        suggestedFileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): SelectedDocument?
}

/**
 * 一次语音识别的结果。
 *
 * 原始录音不得写入文件或诊断日志；界面只使用转写后的文字。
 */
sealed class SpeechRecognitionOutcome {
    /** 识别到可编辑文字。 */
    data class Success(val text: String) : SpeechRecognitionOutcome() {
        init {
            require(text.isNotBlank()) { "Speech recognition text must not be blank." }
        }
    }

    /** 用户取消或松开后没有得到结果。 */
    data object Cancelled : SpeechRecognitionOutcome()

    /** 当前设备没有可用识别器。 */
    data object Unavailable : SpeechRecognitionOutcome()

    /** 用户拒绝麦克风权限。 */
    data object PermissionDenied : SpeechRecognitionOutcome()

    /** 听清了环境声但没有匹配到文字。 */
    data object NoMatch : SpeechRecognitionOutcome()
}

/**
 * 可选语音识别入口。
 *
 * 只有用户主动触发后才开始听；云端路径关闭时只使用设备离线识别。
 */
interface SpeechRecognitionGateway {
    /**
     * 当前是否具备可用识别器。
     *
     * @param allowNetwork 是否允许把这次主动录音交给系统联网识别。
     */
    fun isAvailable(allowNetwork: Boolean): Boolean

    /**
     * 开始听用户主动说的一句话，结束后返回转写结果。
     *
     * @param allowNetwork 是否允许联网识别；关闭时只走离线识别。
     */
    suspend fun listen(allowNetwork: Boolean): SpeechRecognitionOutcome

    /**
     * 立即停止当前识别，不保存录音。
     */
    fun cancel()
}

/**
 * 用户为当前任务主动选出、准备交给 AI 的一张照片。
 *
 * @property role 用户选择的照片用途。
 * @property sizeBytes 原图大小，只用于数量和上限判断，不写入日志。
 */
data class AiPhotoInput(
    val role: PhotoRole,
    val sizeBytes: Long,
) {
    init {
        require(sizeBytes > 0L) { "AI photo size must be greater than zero." }
    }
}

/**
 * AI 给出的可编辑建议，确认前不得写入物品。
 *
 * @property itemName 建议的物品名称。
 * @property locationDescription 建议的位置说明。
 */
data class AiFieldSuggestions(
    val itemName: String?,
    val locationDescription: String?,
) {
    init {
        require(!itemName.isNullOrBlank() || !locationDescription.isNullOrBlank()) {
            "AI suggestions must include at least one editable field."
        }
    }
}

/**
 * 一次 AI 辅助请求的结果。
 *
 * 不得把照片路径、物品名称或供应商完整响应写入诊断日志。
 */
sealed class AiAssistanceOutcome {
    /** 返回可编辑建议，仍须用户确认。 */
    data class Success(val suggestions: AiFieldSuggestions) : AiAssistanceOutcome()

    /** 用户取消本次识别。 */
    data object Cancelled : AiAssistanceOutcome()

    /** 当前没有可用的 AI 供应商。 */
    data object Unavailable : AiAssistanceOutcome()

    /** 这次请求没有用户主动选出的照片或文字。 */
    data object NoSelectedContent : AiAssistanceOutcome()

    /** 识别失败，可继续手填。 */
    data object Failed : AiAssistanceOutcome()
}

/**
 * 可选 AI 辅助入口。
 *
 * 只有用户主动选择识别后才处理这次选中的照片或文字；开关关闭或未配置供应商时必须降级。
 */
interface AiAssistanceGateway {
    /**
     * 当前是否具备可发起请求的供应商。
     */
    fun isAvailable(): Boolean

    /**
     * 分析用户为当前任务主动选出的照片。
     *
     * 调用方必须先截到最多 6 张。未配置供应商时不得读取或上传原图。
     */
    suspend fun analyzePhotos(photos: List<AiPhotoInput>): AiAssistanceOutcome
}
