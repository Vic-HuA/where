package com.vichua.where

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.vichua.where.core.platform.SpeechRecognitionGateway
import com.vichua.where.core.platform.SpeechRecognitionOutcome
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过系统语音识别把用户主动说的一句话转成文字。
 *
 * 不保存原始录音。云端路径关闭时只创建离线识别器。
 */
class AndroidSpeechRecognitionGateway(
    private val activity: ComponentActivity,
) : SpeechRecognitionGateway {
    private val permissionAsked = AtomicBoolean(false)
    private val pendingPermission = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val activeRecognizer = AtomicReference<SpeechRecognizer?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val deferred = pendingPermission.getAndSet(null) ?: return@registerForActivityResult
        deferred.complete(granted)
    }

    /**
     * 离线识别需要系统 on-device 服务；联网识别需要系统 RecognitionService。
     */
    override fun isAvailable(allowNetwork: Boolean): Boolean =
        if (allowNetwork) {
            SpeechRecognizer.isRecognitionAvailable(activity)
        } else {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)
        }

    /**
     * 在主线程创建识别器，结束后立即销毁，避免留下录音会话。
     */
    override suspend fun listen(allowNetwork: Boolean): SpeechRecognitionOutcome =
        withContext(Dispatchers.Main) {
            if (!isAvailable(allowNetwork)) {
                return@withContext SpeechRecognitionOutcome.Unavailable
            }
            if (!ensureMicrophonePermission()) {
                return@withContext SpeechRecognitionOutcome.PermissionDenied
            }
            val recognizer = createRecognizer(allowNetwork)
                ?: return@withContext SpeechRecognitionOutcome.Unavailable
            val deferred = CompletableDeferred<SpeechRecognitionOutcome>()
            check(activeRecognizer.compareAndSet(null, recognizer)) {
                "Speech recognition is already active."
            }
            val timeout = Runnable {
                runCatching { recognizer.stopListening() }
            }
            recognizer.setRecognitionListener(
                OutcomeRecognitionListener(deferred),
            )
            return@withContext try {
                recognizer.startListening(createListenIntent(allowNetwork))
                mainHandler.postDelayed(timeout, MAX_LISTEN_DURATION_MILLIS)
                deferred.await()
            } catch (_: Exception) {
                SpeechRecognitionOutcome.Cancelled
            } finally {
                mainHandler.removeCallbacks(timeout)
                activeRecognizer.compareAndSet(recognizer, null)
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
            }
        }

    /**
     * 停止当前识别，不写出任何音频文件。
     */
    override fun cancel() {
        val recognizer = activeRecognizer.get() ?: return
        mainHandler.post {
            runCatching { recognizer.cancel() }
        }
    }

    private suspend fun ensureMicrophonePermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            return true
        }
        if (permissionAsked.get()) {
            return false
        }
        permissionAsked.set(true)
        val deferred = CompletableDeferred<Boolean>()
        check(pendingPermission.compareAndSet(null, deferred)) {
            "Microphone permission request is already active."
        }
        return try {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            deferred.await()
        } catch (_: Exception) {
            pendingPermission.compareAndSet(deferred, null)
            false
        }
    }

    private fun createRecognizer(allowNetwork: Boolean): SpeechRecognizer? = runCatching {
        if (allowNetwork) {
            SpeechRecognizer.createSpeechRecognizer(activity)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
        } else {
            null
        }
    }.getOrNull()

    private fun createListenIntent(allowNetwork: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !allowNetwork)
        }

    /**
     * 把系统回调收成一次结果；不把转写写进日志。
     */
    private class OutcomeRecognitionListener(
        private val deferred: CompletableDeferred<SpeechRecognitionOutcome>,
    ) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (deferred.isCompleted) {
                return
            }
            deferred.complete(
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        SpeechRecognitionOutcome.PermissionDenied
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    -> SpeechRecognitionOutcome.NoMatch
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    -> SpeechRecognitionOutcome.Unavailable
                    else -> SpeechRecognitionOutcome.Cancelled
                },
            )
        }

        override fun onResults(results: Bundle?) {
            completeFromMatches(results)
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun completeFromMatches(results: Bundle?) {
            if (deferred.isCompleted) {
                return
            }
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull(String::isNotBlank)
            deferred.complete(
                if (text.isNullOrBlank()) {
                    SpeechRecognitionOutcome.NoMatch
                } else {
                    SpeechRecognitionOutcome.Success(text.trim())
                },
            )
        }
    }

    private companion object {
        const val MAX_LISTEN_DURATION_MILLIS = 60_000L
    }
}
