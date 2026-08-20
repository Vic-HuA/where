package com.vichua.where

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vichua.where.core.platform.TextToSpeechGateway
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 使用系统 TTS 朗读用户主动触发的物品位置，引擎不可用时失败而不崩溃详情页。
 */
class AndroidTextToSpeechGateway(
    context: Context,
) : TextToSpeechGateway {
    private val initialized = CompletableDeferred<Boolean>()
    private val textToSpeech = TextToSpeech(context.applicationContext) { status ->
        initialized.complete(status == TextToSpeech.SUCCESS)
    }

    /**
     * 初始化完成前先允许尝试，避免用户刚进入详情就因引擎尚未就绪被误判为不可用。
     */
    override fun isAvailable(): Boolean {
        if (!initialized.isCompleted) {
            return true
        }
        return initialized.getCompleted() && textToSpeech.engines.isNotEmpty()
    }

    /**
     * 初始化成功后按中文朗读；失败时抛出明确错误供界面降级提示。
     */
    override suspend fun speak(text: String) {
        require(text.isNotBlank()) { "Spoken text must not be blank." }
        val ready = initialized.await()
        if (!ready || !isAvailable()) {
            error("Text to speech is unavailable.")
        }
        textToSpeech.language = resolveChineseLocale()
        suspendCancellableCoroutine { continuation ->
            textToSpeech.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Text to speech failed."),
                            )
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Text to speech failed."),
                            )
                        }
                    }
                },
            )
            continuation.invokeOnCancellation {
                textToSpeech.stop()
            }
            val queued = textToSpeech.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                ITEM_LOCATION_UTTERANCE_ID,
            )
            if (queued != TextToSpeech.SUCCESS && continuation.isActive) {
                continuation.resumeWithException(
                    IllegalStateException("Text to speech rejected the utterance."),
                )
            }
        }
    }

    /**
     * 停止当前朗读，离开详情页时避免继续读出物品信息。
     */
    override fun stop() {
        textToSpeech.stop()
    }

    /**
     * 释放系统引擎，避免 Activity 销毁后继续持有回调。
     */
    fun shutdown() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    /**
     * 优先使用简体中文；设备没有该语言包时回退到系统默认语言。
     */
    private fun resolveChineseLocale(): Locale {
        val chinese = Locale.SIMPLIFIED_CHINESE
        return if (textToSpeech.isLanguageAvailable(chinese) >= TextToSpeech.LANG_AVAILABLE) {
            chinese
        } else {
            Locale.getDefault()
        }
    }

    private companion object {
        const val ITEM_LOCATION_UTTERANCE_ID = "item-location"
    }
}
