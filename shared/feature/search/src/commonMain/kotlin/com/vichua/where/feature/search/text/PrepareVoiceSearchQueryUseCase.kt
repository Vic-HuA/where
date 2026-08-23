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
        // 小模型常在汉字之间插入空格，先去掉再剥问句，否则“我 要 找”对不上“我要找”。
        var prepared = trimmed.replace(" ", "")
        QUESTION_PREFIXES.forEach { prefix ->
            if (prepared.startsWith(prefix)) {
                prepared = prepared.removePrefix(prefix)
            }
        }
        QUESTION_SUFFIXES.forEach { suffix ->
            if (prepared.endsWith(suffix)) {
                prepared = prepared.removeSuffix(suffix)
            }
        }
        val leftoverParticles = listOf("一下", "一下下", "呢", "啊", "呀", "吧")
        leftoverParticles.forEach { particle ->
            if (prepared.endsWith(particle)) {
                prepared = prepared.removeSuffix(particle)
            }
        }
        return prepared.ifBlank { trimmed.replace(" ", "").ifBlank { trimmed } }
    }

    private companion object {
        val QUESTION_PREFIXES = listOf(
            "请帮我找一下",
            "帮我找一下",
            "我想找一下",
            "我要找一下",
            "帮我找",
            "我想找",
            "我要找",
            "找一下",
            "请找",
            "查找",
            "搜索",
            "找找",
            "找",
        )
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
