package com.vichua.where.feature.search.text

/**
 * 把语音查找转写收成可交给本地搜索的关键词。
 *
 * 只去掉常见问句尾巴，不调用 AI，也不改本地排序。
 */
class PrepareVoiceSearchQueryUseCase {
    /**
     * 返回去掉问句尾巴后的查询；去掉后为空时退回原文。
     */
    operator fun invoke(transcript: String): String {
        val trimmed = transcript.trim()
        require(trimmed.isNotEmpty()) { "Voice search transcript must not be blank." }
        var prepared = trimmed
        QUESTION_SUFFIXES.forEach { suffix ->
            if (prepared.endsWith(suffix)) {
                prepared = prepared.removeSuffix(suffix).trim()
            }
        }
        return prepared.ifBlank { trimmed }
    }

    private companion object {
        val QUESTION_SUFFIXES = listOf(
            "放哪里了",
            "放哪儿了",
            "放哪了",
            "在哪里",
            "在哪儿",
            "去哪了",
            "在哪",
        )
    }
}
