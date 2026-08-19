package com.vichua.where.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 验证受控 storageKey 拒绝路径穿越，并生成稳定的物品照片标识。
 */
class StorageKeysTest {
    /** 合法物品路径必须保留目录分层，供文件转正后按 ID 查找。 */
    @Test
    fun `合法物品照片标识可以通过校验`() {
        val original = StorageKeys.itemOriginal("item-1", "photo-1", "jpeg")
        val thumbnail = StorageKeys.itemThumbnail("item-1", "photo-1")

        assertEquals("items/item-1/photo-1.jpg", original)
        assertEquals("items/item-1/thumb-photo-1.jpg", thumbnail)
        StorageKeys.validate(original)
        StorageKeys.validate(thumbnail)
    }

    /** 绝对路径、父目录和反斜杠都会绕过私有目录边界，必须拒绝。 */
    @Test
    fun `危险路径必须被拒绝`() {
        assertFailsWith<IllegalArgumentException> {
            StorageKeys.validate("/items/photo.jpg")
        }
        assertFailsWith<IllegalArgumentException> {
            StorageKeys.validate("items/../photo.jpg")
        }
        assertFailsWith<IllegalArgumentException> {
            StorageKeys.validate("items\\photo.jpg")
        }
        assertFailsWith<IllegalArgumentException> {
            StorageKeys.validate("voice/name.m4a")
        }
    }
}
