package com.vichua.where

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 首次使用时下载并解压 Vosk 中文小模型，之后只读本地目录。
 *
 * 模型只存在本机应用目录，不进入家庭备份。
 */
class AndroidVoskModelStore(
    context: Context,
) {
    private val modelsRoot = File(context.applicationContext.filesDir, MODELS_DIRECTORY)
    private val prepareMutex = Mutex()

    /**
     * 模型目录已存在且含必要文件时才视为可用。
     */
    fun isReady(): Boolean = isModelReady(modelDirectory())

    /**
     * 返回已解压模型目录；缺失时先下载。失败返回空，让语音入口降级到手填。
     */
    suspend fun ensureReady(): File? = prepareMutex.withLock {
        val modelDirectory = modelDirectory()
        if (isModelReady(modelDirectory)) {
            return modelDirectory
        }
        withContext(Dispatchers.IO) {
            runCatching {
                modelsRoot.mkdirs()
                val archiveFile = File(modelsRoot, ARCHIVE_NAME)
                downloadArchive(archiveFile)
                unzipArchive(archiveFile, modelsRoot)
                archiveFile.delete()
                require(isModelReady(modelDirectory)) { "Unpacked Vosk model is incomplete." }
                Log.i(TAG, "Vosk Chinese model is ready.")
                modelDirectory
            }.onFailure { error ->
                Log.w(TAG, "Could not prepare Vosk model: ${error.javaClass.simpleName}")
                if (modelDirectory.exists()) {
                    modelDirectory.deleteRecursively()
                }
            }.getOrNull()
        }
    }

    private fun modelDirectory(): File = File(modelsRoot, MODEL_DIRECTORY_NAME)

    /**
     * 解压后至少要有声学和语言模型，避免空目录被当成可用。
     */
    private fun isModelReady(directory: File): Boolean {
        return directory.isDirectory &&
            File(directory, "am/final.mdl").exists() &&
            File(directory, "graph/HCLr.fst").exists()
    }

    private fun downloadArchive(target: File) {
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Vosk model download HTTP status $status.")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 只允许解压到模型根目录内，避免 zip 路径穿越。
     */
    private fun unzipArchive(archive: File, destination: File) {
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = File(destination, entry.name).canonicalFile
                require(target.path.startsWith(destination.canonicalPath + File.separator)) {
                    "Vosk model archive contained an unsafe path."
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private companion object {
        const val TAG = "WhereVosk"
        const val MODELS_DIRECTORY = "vosk"
        const val MODEL_DIRECTORY_NAME = "vosk-model-small-cn-0.22"
        const val ARCHIVE_NAME = "vosk-model-small-cn-0.22.zip"
        const val MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 120_000
    }
}
