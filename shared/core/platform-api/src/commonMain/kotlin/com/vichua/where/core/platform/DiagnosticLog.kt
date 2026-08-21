package com.vichua.where.core.platform

/**
 * 允许写入诊断日志的固定事件。
 *
 * 只使用英文事件码，禁止附带物品名称、位置、查询原文、媒体路径、原始语音或供应商完整响应。
 */
enum class DiagnosticEvent(
    val code: String,
) {
    /** 用户刚打开诊断日志开关。 */
    ENABLED("diagnostic.enabled"),

    /** 本地朗读引擎不可用。 */
    TTS_UNAVAILABLE("tts.unavailable"),

    /** 当前没有可用的 AI 供应商。 */
    AI_UNAVAILABLE("ai.unavailable"),

    /** 保存物品失败；不记录名称或位置。 */
    ITEM_SAVE_FAILED("item.save.failed"),

    /** 创建加密备份失败；不记录文件路径或密码。 */
    BACKUP_CREATE_FAILED("backup.create.failed"),
}

/**
 * 本机诊断日志入口。
 *
 * 开关关闭时调用方不得写入；实现也必须只输出事件码。
 */
interface DiagnosticLogGateway {
    /**
     * 写入一条不含敏感内容的诊断事件。
     */
    fun record(event: DiagnosticEvent)
}
