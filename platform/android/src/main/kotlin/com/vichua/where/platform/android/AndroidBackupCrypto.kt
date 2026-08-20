package com.vichua.where.platform.android

import android.util.Log
import com.vichua.where.core.crypto.BackupCrypto
import com.vichua.where.core.crypto.BackupKeyDerivation
import com.vichua.where.core.crypto.BackupPackageCodec
import com.vichua.where.core.crypto.ContentHasher
import com.vichua.where.core.crypto.EncryptedBackupBlob
import com.vichua.where.core.model.BackupFormat
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 使用平台 AES-GCM 和 PBKDF2 实现备份认证加密，不自研算法。
 */
class AndroidBackupCrypto : BackupCrypto {
    private val secureRandom = SecureRandom()

    /**
     * 生成新的随机盐值。
     */
    override fun createKeyDerivation(): BackupKeyDerivation {
        val salt = ByteArray(BackupPackageCodec.SALT_SIZE)
        secureRandom.nextBytes(salt)
        return BackupKeyDerivation(
            algorithm = BackupFormat.KEY_DERIVATION_ALGORITHM,
            iterations = BackupFormat.KDF_ITERATIONS,
            salt = salt,
        )
    }

    /**
     * 用用户密码派生密钥并加密明文。
     */
    override fun encrypt(
        password: String,
        derivation: BackupKeyDerivation,
        plaintext: ByteArray,
    ): EncryptedBackupBlob {
        require(password.length >= BackupFormat.MIN_PASSWORD_LENGTH) {
            "Backup password is shorter than the accepted minimum."
        }
        require(plaintext.isNotEmpty()) { "Backup plaintext must not be empty." }
        return try {
            val nonce = ByteArray(BackupPackageCodec.NONCE_SIZE)
            secureRandom.nextBytes(nonce)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                deriveKey(password, derivation),
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            EncryptedBackupBlob(
                formatVersion = BackupFormat.CURRENT_VERSION,
                derivation = derivation,
                nonce = nonce,
                ciphertext = cipher.doFinal(plaintext),
            )
        } catch (error: Exception) {
            Log.e(LOG_TAG, "Backup encrypt failed", error)
            throw error
        }
    }

    /**
     * 解密备份密文；认证失败表示密码错误或文件损坏。
     */
    override fun decrypt(
        password: String,
        blob: EncryptedBackupBlob,
    ): ByteArray {
        require(password.isNotEmpty()) { "Backup password must not be empty." }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, blob.derivation),
                GCMParameterSpec(GCM_TAG_BITS, blob.nonce),
            )
            cipher.doFinal(blob.ciphertext)
        } catch (_: Exception) {
            Log.e(LOG_TAG, "Backup decrypt failed")
            throw IllegalArgumentException("Backup password is incorrect or the package is damaged.")
        }
    }

    private fun deriveKey(
        password: String,
        derivation: BackupKeyDerivation,
    ): SecretKeySpec {
        val keySpec = PBEKeySpec(
            password.toCharArray(),
            derivation.salt,
            derivation.iterations,
            KEY_BITS,
        )
        // 清单里写规范标识，JCA 必须使用 Android 可识别的工厂名。
        val factory = SecretKeyFactory.getInstance(JCA_KEY_DERIVATION_ALGORITHM)
        val keyBytes = factory.generateSecret(keySpec).encoded
        keySpec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val JCA_KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val GCM_TAG_BITS = 128
        const val KEY_BITS = 256
        const val LOG_TAG = "WhereBackup"
    }
}

/**
 * 使用平台 MessageDigest 计算备份摘要。
 */
object AndroidContentHasher : ContentHasher {
    /**
     * 返回小写十六进制 SHA-256。
     */
    override fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte ->
            val unsigned = byte.toInt() and 0xFF
            unsigned.toString(16).padStart(2, '0')
        }
    }
}
