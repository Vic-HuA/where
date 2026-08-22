package com.vichua.where

import android.util.Base64
import android.util.Log
import com.vichua.where.core.model.AiProviderCredentials
import com.vichua.where.core.model.AiProviderVendor
import com.vichua.where.core.model.PhotoRole
import com.vichua.where.core.platform.AiAssistanceGateway
import com.vichua.where.core.platform.AiAssistanceOutcome
import com.vichua.where.core.platform.AiConnectionTestOutcome
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
     * 用当前填写发一次最小文本请求，确认 Key、地址和模型能通。
     */
    override suspend fun testConnection(
        credentials: AiProviderCredentials,
    ): AiConnectionTestOutcome {
        if (!credentials.isConfigured) {
            return AiConnectionTestOutcome.Incomplete
        }
        return withContext(Dispatchers.IO) {
            try {
                when (credentials.vendor) {
                    AiProviderVendor.ANTHROPIC -> {
                        postJson(
                            url = anthropicMessagesUrl(credentials.baseUrl),
                            body = buildAnthropicTestBody(credentials.model),
                            headers = mapOf(
                                "x-api-key" to credentials.apiKey,
                                "anthropic-version" to ANTHROPIC_VERSION,
                                "Content-Type" to "application/json",
                            ),
                        )
                    }
                    AiProviderVendor.OPENAI,
                    AiProviderVendor.CUSTOM,
                    -> {
                        postJson(
                            url = "${credentials.baseUrl}/chat/completions",
                            body = buildOpenAiTestBody(credentials.model),
                            headers = mapOf(
                                "Authorization" to "Bearer ${credentials.apiKey}",
                                "Content-Type" to "application/json",
                            ),
                        )
                    }
                }
                Log.i(TAG, "AI connection test succeeded.")
                AiConnectionTestOutcome.Success
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                Log.w(TAG, "AI connection test failed with status only.")
                AiConnectionTestOutcome.Failed(userFacingConnectionError(error))
            } catch (_: Exception) {
                Log.w(TAG, "AI connection test failed without uploading details.")
                AiConnectionTestOutcome.Failed("接口没有返回可用结果，请检查地址和模型。")
            }
        }
    }

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
                val content = when (credentials.vendor) {
                    AiProviderVendor.ANTHROPIC -> {
                        postJson(
                            url = anthropicMessagesUrl(credentials.baseUrl),
                            body = buildAnthropicRequestBody(credentials.model, encodedPhotos),
                            headers = mapOf(
                                "x-api-key" to credentials.apiKey,
                                "anthropic-version" to ANTHROPIC_VERSION,
                                "Content-Type" to "application/json",
                            ),
                        ).let(::extractAnthropicContent)
                    }
                    AiProviderVendor.OPENAI,
                    AiProviderVendor.CUSTOM,
                    -> {
                        postJson(
                            url = "${credentials.baseUrl}/chat/completions",
                            body = buildOpenAiRequestBody(credentials.model, encodedPhotos),
                            headers = mapOf(
                                "Authorization" to "Bearer ${credentials.apiKey}",
                                "Content-Type" to "application/json",
                            ),
                        ).let(::extractOpenAiContent)
                    }
                }
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
     * 构造不含照片的最小 OpenAI 兼容请求，只用来确认连通。
     */
    private fun buildOpenAiTestBody(model: String): String =
        JSONObject()
            .put("model", model)
            .put("max_tokens", 8)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", "Reply with OK."),
                ),
            )
            .toString()

    /**
     * 构造不含照片的最小 Anthropic 兼容请求，只用来确认连通。
     */
    private fun buildAnthropicTestBody(model: String): String =
        JSONObject()
            .put("model", model)
            .put("max_tokens", 8)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", "Reply with OK."),
                ),
            )
            .toString()

    /**
     * 把连通失败收成可展示的中文原因，不包含 Key 或响应正文。
     */
    private fun userFacingConnectionError(error: IOException): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("HTTP status 401") || message.contains("HTTP status 403") ->
                "Key 未被接受，请检查密钥。"
            message.contains("HTTP status 404") ->
                "接口地址或模型不存在，请检查填写。"
            message.contains("HTTP status 429") ->
                "接口暂时限流，请稍后重试。"
            message.contains("HTTP status") ->
                "接口拒绝了这次测试，请检查地址和模型。"
            else ->
                "无法连接接口，请检查网络和地址。"
        }
    }

    /**
     * 构造 OpenAI 兼容 chat/completions JSON。
     */
    private fun buildOpenAiRequestBody(
        model: String,
        photos: List<EncodedAiPhoto>,
    ): String {
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
            .put("model", model)
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
     * 构造 Anthropic messages JSON，图片走 base64 source。
     */
    private fun buildAnthropicRequestBody(
        model: String,
        photos: List<EncodedAiPhoto>,
    ): String {
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
                    .put("type", "image")
                    .put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", photo.mimeType)
                            .put("data", photo.base64),
                    ),
            )
        }
        return JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
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
     * Anthropic 官方根地址没有 /v1 时补上，已经带 /v1 的中转地址不再重复。
     */
    private fun anthropicMessagesUrl(baseUrl: String): String {
        return if (baseUrl.endsWith("/v1")) {
            "$baseUrl/messages"
        } else {
            "$baseUrl/v1/messages"
        }
    }

    /**
     * 发送一次主动识别请求，不把 Key 或正文写入日志。
     */
    private fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doInput = true
            doOutput = true
            headers.forEach { (name, value) ->
                setRequestProperty(name, value)
            }
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
            response
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 从 OpenAI 兼容 chat 响应取出助手文本。
     */
    private fun extractOpenAiContent(response: String): String {
        val content = JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
        return unwrapMarkdownJson(content)
    }

    /**
     * 从 Anthropic messages 响应取出第一段文本。
     */
    private fun extractAnthropicContent(response: String): String {
        val blocks = JSONObject(response).getJSONArray("content")
        for (index in 0 until blocks.length()) {
            val block = blocks.getJSONObject(index)
            if (block.optString("type") == "text") {
                return unwrapMarkdownJson(block.getString("text").trim())
            }
        }
        return unwrapMarkdownJson("")
    }

    private fun unwrapMarkdownJson(content: String): String =
        content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

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
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MAX_ORIGINAL_BYTES = 800_000L
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
