package com.vichua.where.core.crypto

import com.vichua.where.core.model.BackupFormat

/**
 * 备份包使用的密钥派生参数。
 *
 * 盐值和迭代次数可以随包头公开，密码本身不得写入文件。
 */
data class BackupKeyDerivation(
    val algorithm: String,
    val iterations: Int,
    val salt: ByteArray,
) {
    init {
        require(algorithm == BackupFormat.KEY_DERIVATION_ALGORITHM) {
            "Unsupported backup key derivation algorithm."
        }
        require(iterations >= BackupFormat.MIN_KDF_ITERATIONS) {
            "Backup key derivation iterations are below the accepted minimum."
        }
        require(salt.size == BackupPackageCodec.SALT_SIZE) {
            "Backup key derivation salt size is invalid."
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupKeyDerivation) return false
        return algorithm == other.algorithm &&
            iterations == other.iterations &&
            salt.contentEquals(other.salt)
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + iterations
        result = 31 * result + salt.contentHashCode()
        return result
    }
}

/**
 * 认证加密后的备份密文。
 */
data class EncryptedBackupBlob(
    val formatVersion: Int,
    val derivation: BackupKeyDerivation,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
) {
    init {
        require(formatVersion >= BackupFormat.MIN_SUPPORTED_VERSION) {
            "Encrypted backup format version is not supported."
        }
        require(nonce.size == BackupPackageCodec.NONCE_SIZE) {
            "Encrypted backup nonce size is invalid."
        }
        require(ciphertext.isNotEmpty()) { "Encrypted backup ciphertext must not be empty." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBackupBlob) return false
        return formatVersion == other.formatVersion &&
            derivation == other.derivation &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = formatVersion
        result = 31 * result + derivation.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

/**
 * 平台认证加密入口。
 *
 * 共享层只描述格式和调用方式，具体算法由平台安全库实现。
 */
interface BackupCrypto {
    /**
     * 生成新的随机盐值和当前迭代次数。
     */
    fun createKeyDerivation(): BackupKeyDerivation

    /**
     * 用用户密码加密明文。
     */
    fun encrypt(
        password: String,
        derivation: BackupKeyDerivation,
        plaintext: ByteArray,
    ): EncryptedBackupBlob

    /**
     * 用用户密码解密；密码错误或密文损坏时抛出异常。
     */
    fun decrypt(
        password: String,
        blob: EncryptedBackupBlob,
    ): ByteArray
}

/**
 * 计算备份摘要，避免功能层直接依赖平台摘要 API。
 */
interface ContentHasher {
    /**
     * 返回小写十六进制 SHA-256。
     */
    fun sha256Hex(bytes: ByteArray): String
}

/**
 * 备份包二进制头编解码。
 *
 * 格式版本和派生参数放在明文头，以便拒绝不兼容版本后再索要密码。
 */
object BackupPackageCodec {
    /** 固定魔术字，便于识别本应用备份。 */
    const val MAGIC = "WHEREBAK"

    /** 盐值字节数。 */
    const val SALT_SIZE = 16

    /** AES-GCM nonce 字节数。 */
    const val NONCE_SIZE = 12

    /**
     * 把密文写成可保存的数据包。
     */
    fun encode(blob: EncryptedBackupBlob): ByteArray {
        val magic = MAGIC.encodeToByteArray()
        require(magic.size == MAGIC_SIZE) { "Backup magic size is invalid." }
        val encoded = ByteArray(
            MAGIC_SIZE + VERSION_SIZE + ITERATION_SIZE + blob.derivation.salt.size +
                blob.nonce.size + blob.ciphertext.size,
        )
        var offset = 0
        magic.copyInto(encoded, offset)
        offset += MAGIC_SIZE
        writeInt(encoded, offset, blob.formatVersion)
        offset += VERSION_SIZE
        writeInt(encoded, offset, blob.derivation.iterations)
        offset += ITERATION_SIZE
        blob.derivation.salt.copyInto(encoded, offset)
        offset += blob.derivation.salt.size
        blob.nonce.copyInto(encoded, offset)
        offset += blob.nonce.size
        blob.ciphertext.copyInto(encoded, offset)
        return encoded
    }

    /**
     * 解析数据包头和密文；格式无法识别时立即失败。
     */
    fun decode(bytes: ByteArray): EncryptedBackupBlob {
        val minimumSize = MAGIC_SIZE + VERSION_SIZE + ITERATION_SIZE + SALT_SIZE + NONCE_SIZE + 1
        require(bytes.size >= minimumSize) { "Backup package is truncated." }
        val magic = bytes.copyOfRange(0, MAGIC_SIZE).decodeToString()
        require(magic == MAGIC) { "Backup package magic is invalid." }
        var offset = MAGIC_SIZE
        val formatVersion = readInt(bytes, offset)
        offset += VERSION_SIZE
        require(formatVersion >= BackupFormat.MIN_SUPPORTED_VERSION) {
            "Backup package version is not supported."
        }
        val iterations = readInt(bytes, offset)
        offset += ITERATION_SIZE
        val salt = bytes.copyOfRange(offset, offset + SALT_SIZE)
        offset += SALT_SIZE
        val nonce = bytes.copyOfRange(offset, offset + NONCE_SIZE)
        offset += NONCE_SIZE
        val ciphertext = bytes.copyOfRange(offset, bytes.size)
        return EncryptedBackupBlob(
            formatVersion = formatVersion,
            derivation = BackupKeyDerivation(
                algorithm = BackupFormat.KEY_DERIVATION_ALGORITHM,
                iterations = iterations,
                salt = salt,
            ),
            nonce = nonce,
            ciphertext = ciphertext,
        )
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt(source: ByteArray, offset: Int): Int {
        return (source[offset].toInt() and 0xFF shl 24) or
            (source[offset + 1].toInt() and 0xFF shl 16) or
            (source[offset + 2].toInt() and 0xFF shl 8) or
            (source[offset + 3].toInt() and 0xFF)
    }

    private const val MAGIC_SIZE = 8
    private const val VERSION_SIZE = 4
    private const val ITERATION_SIZE = 4
}
