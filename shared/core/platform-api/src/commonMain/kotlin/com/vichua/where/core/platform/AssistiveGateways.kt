package com.vichua.where.core.platform

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
