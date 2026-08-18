package com.vichua.where.core.common

/**
 * 生成不包含用户隐私信息的全局唯一 ID。
 *
 * 平台实现可以使用系统安全随机 UUID；业务层只依赖该接口，便于测试和后续替换实现。
 */
fun interface UniqueIdGenerator {
    /**
     * 生成新的唯一标识文本。
     *
     * @return 非空白且不包含物品、位置或设备名称的标识。
     */
    fun generate(): String
}

/**
 * 提供当前 UTC Epoch 毫秒值。
 *
 * 使用接口隔离系统时钟，保证过期、排序和历史逻辑能够通过固定时间进行测试。
 */
fun interface EpochMillisecondsClock {
    /**
     * 返回当前 UTC 时间。
     *
     * @return 从 Unix Epoch 开始计算的非负毫秒数。
     */
    fun now(): Long
}

/**
 * 把用户输入转换为可稳定去重和搜索的标准化文本。
 */
fun interface TextNormalizer {
    /**
     * 标准化输入文本。
     *
     * @param value 用户输入原文。
     * @return 去除首尾空白、折叠连续空白并转换为小写后的文本。
     */
    fun normalize(value: String): String
}

/**
 * 不依赖平台区域设置的默认文本标准化实现。
 */
object DefaultTextNormalizer : TextNormalizer {
    /**
     * 逐字符折叠空白，避免通过正则表达式为每次录入创建额外中间对象。
     */
    override fun normalize(value: String): String {
        val trimmedLowercaseValue = value.trim().lowercase()
        if (trimmedLowercaseValue.isEmpty()) {
            return ""
        }

        return buildString(trimmedLowercaseValue.length) {
            var hasPendingSpace = false
            trimmedLowercaseValue.forEach { character ->
                if (character.isWhitespace()) {
                    hasPendingSpace = isNotEmpty()
                } else {
                    if (hasPendingSpace) {
                        append(' ')
                        hasPendingSpace = false
                    }
                    append(character)
                }
            }
        }
    }
}
