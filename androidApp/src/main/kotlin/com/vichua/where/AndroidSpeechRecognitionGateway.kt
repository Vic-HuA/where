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
import android.util.Log
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
 * 不保存原始录音。松开或超时必须结束本轮，避免界面一直停在“正在听”。
 */
class AndroidSpeechRecognitionGateway(
    private val activity: ComponentActivity,
) : SpeechRecognitionGateway {
    private val permissionAsked = AtomicBoolean(false)
    private val pendingPermission = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val activeRecognizer = AtomicReference<SpeechRecognizer?>(null)
    private val pendingOutcome = AtomicReference<CompletableDeferred<SpeechRecognitionOutcome>?>(null)
    private val speechStarted = AtomicBoolean(false)
    private val finishRequested = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val deferred = pendingPermission.getAndSet(null) ?: return@registerForActivityResult
        deferred.complete(granted)
    }

    /**
     * 有系统识别服务即可尝试；离线优先走 on-device，没有时再试带离线偏好的普通识别器。
     */
    override fun isAvailable(allowNetwork: Boolean): Boolean {
        val hasNetworkRecognizer = SpeechRecognizer.isRecognitionAvailable(activity)
        val hasOnDeviceRecognizer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)
        return if (allowNetwork) {
            hasNetworkRecognizer || hasOnDeviceRecognizer
        } else {
            hasOnDeviceRecognizer || hasNetworkRecognizer
        }
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
            check(pendingOutcome.compareAndSet(null, deferred)) {
                "Speech recognition outcome is already pending."
            }
            speechStarted.set(false)
            finishRequested.set(false)
            val noSpeechTimeout = Runnable {
                if (!speechStarted.get()) {
                    Log.i(TAG, "Speech recognition ended: no speech detected.")
                    completeOutcome(SpeechRecognitionOutcome.NoMatch)
                    runCatching { recognizer.stopListening() }
                }
            }
            val maxDurationTimeout = Runnable {
                Log.i(TAG, "Speech recognition reached the maximum listen duration.")
                runCatching { recognizer.stopListening() }
                mainHandler.postDelayed(
                    { completeOutcome(SpeechRecognitionOutcome.NoMatch) },
                    STOP_RESULT_GRACE_MILLIS,
                )
            }
            recognizer.setRecognitionListener(OutcomeRecognitionListener())
            return@withContext try {
                recognizer.startListening(createListenIntent(allowNetwork))
                if (finishRequested.get()) {
                    runCatching { recognizer.stopListening() }
                }
                mainHandler.postDelayed(noSpeechTimeout, NO_SPEECH_TIMEOUT_MILLIS)
                mainHandler.postDelayed(maxDurationTimeout, MAX_LISTEN_DURATION_MILLIS)
                deferred.await()
            } catch (_: Exception) {
                SpeechRecognitionOutcome.Cancelled
            } finally {
                mainHandler.removeCallbacks(noSpeechTimeout)
                mainHandler.removeCallbacks(maxDurationTimeout)
                pendingOutcome.compareAndSet(deferred, null)
                activeRecognizer.compareAndSet(recognizer, null)
                runCatching { recognizer.cancel() }
                runCatching { recognizer.destroy() }
            }
        }

    /**
     * 松开按住说话：停止收听并等待本轮结果，超时仍无回调则按没听清结束。
     */
    override fun finishListening() {
        finishRequested.set(true)
        val recognizer = activeRecognizer.get() ?: return
        mainHandler.post {
            runCatching { recognizer.stopListening() }
            mainHandler.postDelayed(
                { completeOutcome(SpeechRecognitionOutcome.NoMatch) },
                STOP_RESULT_GRACE_MILLIS,
            )
        }
    }

    /**
     * 停止当前识别，不写出任何音频文件。
     */
    override fun cancel() {
        completeOutcome(SpeechRecognitionOutcome.Cancelled)
        val recognizer = activeRecognizer.get() ?: return
        mainHandler.post {
            runCatching { recognizer.cancel() }
        }
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
        val canUseOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)
        if (!allowNetwork && canUseOnDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
        } else if (SpeechRecognizer.isRecognitionAvailable(activity)) {
            SpeechRecognizer.createSpeechRecognizer(activity)
        } else if (canUseOnDevice) {
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, SPEECH_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, SPEECH_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !allowNetwork)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1_500L,
            )
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }

    /**
     * 把系统回调收成一次结果；不把转写写进日志。
     */
    private inner class OutcomeRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() {
            speechStarted.set(true)
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            Log.i(TAG, "Speech recognition error code $error.")
            completeOutcome(
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        SpeechRecognitionOutcome.PermissionDenied
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    -> SpeechRecognitionOutcome.NoMatch
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    -> SpeechRecognitionOutcome.Unavailable
                    else -> SpeechRecognitionOutcome.NoMatch
                },
            )
        }

        override fun onResults(results: Bundle?) {
            completeFromMatches(results)
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun completeFromMatches(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull(String::isNotBlank)
            completeOutcome(
                if (text.isNullOrBlank()) {
                    SpeechRecognitionOutcome.NoMatch
                } else {
                    SpeechRecognitionOutcome.Success(text.trim())
                },
            )
        }
    }

    private companion object {
        const val TAG = "WhereSpeech"
        const val SPEECH_LANGUAGE = "zh-CN"
        const val NO_SPEECH_TIMEOUT_MILLIS = 10_000L
        const val MAX_LISTEN_DURATION_MILLIS = 60_000L
        const val STOP_RESULT_GRACE_MILLIS = 1_500L
    }
}
