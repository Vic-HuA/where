package com.vichua.where

import android.util.Base64
import android.util.Log
import com.vichua.where.core.model.AiProviderCredentials
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.platform.AiAssistanceGateway
import com.vichua.where.core.platform.AiAssistanceOutcome
import com.vichua.where.core.platform.AiFieldSuggestions
import com.vichua.where.core.platform.AiPhotoInput
import com.vichua.where.feature.settings.preferences.AiProviderCredentialsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Android 上的 AI 辅助适配器。
 *
 * 只有本机保存了 Key 才读取原图并请求 OpenAI 兼容接口；响应只解析建议字段，不写入日志。
 */
class AndroidAiAssistanceGateway(
    private val credentialsStore: AiProviderCredentialsStore,
    private val readPhotoBytes: suspend (String) -> ByteArray?,
) : AiAssistanceGateway {
    /**
     * 有本机 Key 时才允许发起识别。
     */
    override fun isAvailable(): Boolean = credentialsStore.load().isConfigured

    /**
     * 把这次主动选出的照片交给用户配置的接口，返回可编辑建议。
     */
    override suspend fun analyzePhotos(photos: List<AiPhotoInput>): AiAssistanceOutcome {
        if (photos.isEmpty()) {
            return AiAssistanceOutcome.NoSelectedContent
        }
        val credentials = credentialsStore.load()
        if (!credentials.isConfigured) {
            Log.i(TAG, "AI assistance key is empty; skip upload.")
            return AiAssistanceOutcome.Unavailable
        }
        return withContext(Dispatchers.IO) {
            try {
                val encodedPhotos = encodeSelectedPhotos(photos)
                if (encodedPhotos.isEmpty()) {
                    Log.w(TAG, "AI assistance could not read any selected photo bytes.")
                    return@withContext AiAssistanceOutcome.Failed
                }
                val body = buildChatRequestBody(encodedPhotos)
                val content = postChatCompletions(credentials, body)
                parseSuggestions(content) ?: AiAssistanceOutcome.Failed
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Log.w(TAG, "AI assistance request failed without uploading details.")
                AiAssistanceOutcome.Failed
            }
        }
    }

    /**
     * 原图过大时改传缩略图，避免一次请求超出常见接口限制。
     */
    private suspend fun encodeSelectedPhotos(
        photos: List<AiPhotoInput>,
    ): List<EncodedAiPhoto> {
        return photos.mapNotNull { photo ->
            val storageKey = if (photo.sizeBytes > MAX_ORIGINAL_BYTES) {
                photo.thumbnailStorageKey
            } else {
                photo.storageKey
            }
            val bytes = readPhotoBytes(storageKey) ?: return@mapNotNull null
            EncodedAiPhoto(
                role = photo.role,
                mimeType = photo.mimeType,
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            )
        }
    }

    /**
     * 构造 chat/completions JSON。不得把 Key 或原图路径写进字符串以外的日志。
     */
    private fun buildChatRequestBody(photos: List<EncodedAiPhoto>): String {
        val content = JSONArray()
        content.put(
            JSONObject()
                .put("type", "text")
                .put(
                    "text",
                    "根据这些物品相关照片给出简短中文建议。只返回 JSON：" +
                        "{\"itemName\":\"物品名称或空字符串\",\"locationDescription\":\"位置描述或空字符串\"}。" +
                        "不要编造照片里看不到的信息。",
                ),
        )
        photos.forEach { photo ->
            content.put(
                JSONObject()
                    .put("type", "text")
                    .put("text", "下一张用途：${photoRoleLabel(photo.role)}"),
            )
            content.put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put(
                            "url",
                            "data:${photo.mimeType};base64,${photo.base64}",
                        ),
                    ),
            )
        }
        return JSONObject()
            .put("model", AiProviderCredentials.DEFAULT_MODEL)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", content),
                ),
            )
            .toString()
    }

    /**
     * 向兼容接口发送一次主动识别请求。
     */
    private fun postChatCompletions(
        credentials: AiProviderCredentials,
        body: String,
    ): String {
        val url = URL("${credentials.baseUrl}/chat/completions")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${credentials.apiKey}")
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                reader.readText()
            }.orEmpty()
            if (status !in 200..299) {
                Log.w(TAG, "AI assistance HTTP status $status.")
                throw IOException("AI assistance HTTP status $status.")
            }
            extractMessageContent(response)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 从 chat 响应取出助手文本；兼容外层包一层 markdown。
     */
    private fun extractMessageContent(response: String): String {
        val root = JSONObject(response)
        val content = root
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
        return content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * 只接受名称或位置至少一个非空字段，避免把空建议当成成功。
     */
    private fun parseSuggestions(content: String): AiAssistanceOutcome.Success? {
        val json = runCatching { JSONObject(content) }.getOrNull() ?: return null
        val itemName = json.optString("itemName").trim().ifBlank { null }
        val locationDescription = json.optString("locationDescription").trim().ifBlank { null }
        if (itemName == null && locationDescription == null) {
            return null
        }
        return AiAssistanceOutcome.Success(
            AiFieldSuggestions(
                itemName = itemName,
                locationDescription = locationDescription,
            ),
        )
    }

    private fun photoRoleLabel(role: PhotoRole): String = when (role) {
        PhotoRole.ENVIRONMENT -> "环境照"
        PhotoRole.ITEM -> "物品照"
        PhotoRole.LABEL -> "标签照"
        PhotoRole.SUPPLEMENTARY -> "补充照"
    }

    private data class EncodedAiPhoto(
        val role: PhotoRole,
        val mimeType: String,
        val base64: String,
    )

    private companion object {
        const val TAG = "WhereAiAssistance"
        const val MAX_ORIGINAL_BYTES = 800_000L
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
