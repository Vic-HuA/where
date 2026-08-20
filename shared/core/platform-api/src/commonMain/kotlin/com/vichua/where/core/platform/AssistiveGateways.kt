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
