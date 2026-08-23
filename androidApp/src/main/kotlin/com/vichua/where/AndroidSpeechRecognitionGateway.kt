package com.vichua.where

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.vichua.where.core.platform.SpeechRecognitionGateway
import com.vichua.where.core.platform.SpeechRecognitionOutcome
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * 用本机 Vosk 中文模型把按住说话转成文字。
 *
 * 不再依赖系统云端识别。首次使用会下载离线模型，之后只在本机识别。
 */
class AndroidSpeechRecognitionGateway(
    private val activity: ComponentActivity,
) : SpeechRecognitionGateway {
    private val modelStore = AndroidVoskModelStore(activity)
    private val pendingPermission = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val pendingOutcome = AtomicReference<CompletableDeferred<SpeechRecognitionOutcome>?>(null)
    private val activeService = AtomicReference<SpeechService?>(null)
    private val lastPartialText = AtomicReference<String?>(null)
    private val finishRequested = AtomicBoolean(false)
    private val loadedModel = AtomicReference<Model?>(null)

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val deferred = pendingPermission.getAndSet(null) ?: return@registerForActivityResult
        deferred.complete(granted)
    }

    /**
     * 模型已就绪，或至少还能下载模型时，都允许进入按住说话。
     */
    override fun isAvailable(allowNetwork: Boolean): Boolean = true

    override fun isEngineReady(): Boolean =
        loadedModel.get() != null || modelStore.isReady()

    /**
     * 首次使用时下载中文小模型；已解压则立即返回。
     */
    override suspend fun ensureEngine(): Boolean {
        if (loadedModel.get() != null) {
            return true
        }
        val modelDirectory = modelStore.ensureReady() ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val model = Model(modelDirectory.absolutePath)
                loadedModel.compareAndSet(null, model)
                true
            }.onFailure { error ->
                Log.w(TAG, "Could not load Vosk model: ${error.javaClass.simpleName}")
            }.getOrDefault(false)
        }
    }

    /**
     * 按住后开始听；松开由 [finishListening] 结束并尽量交出已听到的文字。
     */
    override suspend fun listen(allowNetwork: Boolean): SpeechRecognitionOutcome {
        if (!ensureMicrophonePermission()) {
            return SpeechRecognitionOutcome.PermissionDenied
        }
        if (!ensureEngine()) {
            Log.i(TAG, "Vosk engine is not ready.")
            return SpeechRecognitionOutcome.Unavailable
        }
        val model = loadedModel.get() ?: return SpeechRecognitionOutcome.Unavailable
        val deferred = CompletableDeferred<SpeechRecognitionOutcome>()
        check(pendingOutcome.compareAndSet(null, deferred)) {
            "Speech recognition outcome is already pending."
        }
        lastPartialText.set(null)
        finishRequested.set(false)
        return try {
            val started = withContext(Dispatchers.Main) {
                startSpeechService(model, deferred)
            }
            if (!started) {
                completeOutcome(SpeechRecognitionOutcome.Unavailable)
            }
            deferred.await()
        } catch (_: Exception) {
            SpeechRecognitionOutcome.Cancelled
        } finally {
            pendingOutcome.compareAndSet(deferred, null)
            stopService()
        }
    }

    /**
     * 松开后停止收听，优先交出最终结果，没有则用部分结果。
     */
    override fun finishListening() {
        finishRequested.set(true)
        val service = activeService.get()
        if (service == null) {
            completeFromPartialOr(SpeechRecognitionOutcome.NoMatch)
            return
        }
        runCatching { service.stop() }
    }

    /**
     * 立即停止当前识别，不保存录音。
     */
    override fun cancel() {
        completeOutcome(SpeechRecognitionOutcome.Cancelled)
        stopService()
    }

    private fun startSpeechService(
        model: Model,
        deferred: CompletableDeferred<SpeechRecognitionOutcome>,
    ): Boolean {
        return runCatching {
            val recognizer = Recognizer(model, SAMPLE_RATE).apply {
                // 小模型单条结果常跑偏，多留几个候选再取置信度最高的。
                setMaxAlternatives(MAX_ALTERNATIVES)
                setWords(true)
            }
            val service = SpeechService(recognizer, SAMPLE_RATE)
            check(activeService.compareAndSet(null, service)) {
                "Speech service is already active."
            }
            service.startListening(VoskOutcomeListener())
            if (finishRequested.get() && !deferred.isCompleted) {
                service.stop()
            }
            true
        }.onFailure { error ->
            Log.w(TAG, "Could not start Vosk speech service: ${error.javaClass.simpleName}")
            stopService()
        }.getOrDefault(false)
    }

    private fun stopService() {
        val service = activeService.getAndSet(null) ?: return
        runCatching { service.stop() }
        runCatching { service.shutdown() }
    }

    private fun completeFromPartialOr(fallback: SpeechRecognitionOutcome) {
        val partial = lastPartialText.getAndSet(null)
        completeOutcome(
            if (partial.isNullOrBlank()) {
                fallback
            } else {
                SpeechRecognitionOutcome.Success(partial)
            },
        )
    }

    private fun completeOutcome(outcome: SpeechRecognitionOutcome) {
        val deferred = pendingOutcome.get() ?: return
        if (deferred.isCompleted) {
            return
        }
        deferred.complete(outcome)
    }

    private suspend fun ensureMicrophonePermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            return true
        }
        val deferred = CompletableDeferred<Boolean>()
        if (!pendingPermission.compareAndSet(null, deferred)) {
            return pendingPermission.get()?.await() == true
        }
        return try {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            deferred.await()
        } catch (_: Exception) {
            pendingPermission.compareAndSet(deferred, null)
            false
        }
    }

    /**
     * 只解析 Vosk 的 text/partial 字段，不把转写写进日志。
     */
    private inner class VoskOutcomeListener : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val text = extractHypothesis(hypothesis, "partial")
            if (!text.isNullOrBlank()) {
                lastPartialText.set(text)
            }
        }

        override fun onResult(hypothesis: String?) {
            val text = extractHypothesis(hypothesis, "text")
            if (!text.isNullOrBlank()) {
                lastPartialText.set(text)
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            val text = extractHypothesis(hypothesis, "text") ?: lastPartialText.getAndSet(null)
            completeOutcome(
                if (text.isNullOrBlank()) {
                    SpeechRecognitionOutcome.NoMatch
                } else {
                    SpeechRecognitionOutcome.Success(text)
                },
            )
        }

        override fun onError(exception: Exception?) {
            Log.w(TAG, "Vosk recognition error: ${exception?.javaClass?.simpleName}")
            completeFromPartialOr(SpeechRecognitionOutcome.NoMatch)
        }

        override fun onTimeout() {
            completeFromPartialOr(SpeechRecognitionOutcome.NoMatch)
        }
    }

    private fun extractHypothesis(raw: String?, field: String): String? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return runCatching {
            val json = JSONObject(raw)
            val primary = json.optString(field).trim().ifBlank { null }
            if (!primary.isNullOrBlank()) {
                return@runCatching primary
            }
            val alternatives = json.optJSONArray("alternatives") ?: return@runCatching null
            var bestText: String? = null
            var bestConfidence = Double.NEGATIVE_INFINITY
            for (index in 0 until alternatives.length()) {
                val alternative = alternatives.optJSONObject(index) ?: continue
                val text = alternative.optString("text").trim()
                if (text.isBlank()) {
                    continue
                }
                val confidence = alternative.optDouble("confidence", 0.0)
                if (bestText == null || confidence > bestConfidence) {
                    bestText = text
                    bestConfidence = confidence
                }
            }
            bestText
        }.getOrNull()
    }

    private companion object {
        const val TAG = "WhereSpeech"
        const val SAMPLE_RATE = 16_000.0f
        const val MAX_ALTERNATIVES = 5
    }
}
