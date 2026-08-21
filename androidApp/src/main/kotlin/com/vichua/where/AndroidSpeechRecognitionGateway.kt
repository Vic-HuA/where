package com.vichua.where

import android.Manifest
import android.app.Activity
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 通过系统语音识别把用户主动说的一句话转成文字。
 *
 * 离线只走 on-device，避免普通识别器加上离线偏好后一直不回调。
 * SpeechRecognizer 不可用时再打开系统识别页。
 */
class AndroidSpeechRecognitionGateway(
    private val activity: ComponentActivity,
) : SpeechRecognitionGateway {
    private val pendingPermission = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val activeRecognizer = AtomicReference<SpeechRecognizer?>(null)
    private val pendingOutcome = AtomicReference<CompletableDeferred<SpeechRecognitionOutcome>?>(null)
    private val speechStarted = AtomicBoolean(false)
    private val finishRequested = AtomicBoolean(false)
    private val lastPartialText = AtomicReference<String?>(null)
    private val usingSpeechActivity = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val deferred = pendingPermission.getAndSet(null) ?: return@registerForActivityResult
        deferred.complete(granted)
    }

    private val speechActivityLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (!usingSpeechActivity.getAndSet(false)) {
            return@registerForActivityResult
        }
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = matches?.firstOrNull(String::isNotBlank)?.trim()
        completeOutcome(
            when {
                result.resultCode != Activity.RESULT_OK -> SpeechRecognitionOutcome.Cancelled
                text.isNullOrBlank() -> SpeechRecognitionOutcome.NoMatch
                else -> SpeechRecognitionOutcome.Success(text)
            },
        )
    }

    /**
     * 离线必须有 on-device；云端只要系统识别服务或 on-device 其一可用。
     */
    override fun isAvailable(allowNetwork: Boolean): Boolean {
        val hasNetworkRecognizer = SpeechRecognizer.isRecognitionAvailable(activity)
        val hasOnDeviceRecognizer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)
        val hasSpeechActivity = createSpeechActivityIntent() != null
        return if (allowNetwork) {
            hasNetworkRecognizer || hasOnDeviceRecognizer || hasSpeechActivity
        } else {
            hasOnDeviceRecognizer
        }
    }

    /**
     * 在主线程创建识别器；普通识别器失败时再打开系统识别页。
     */
    override suspend fun listen(allowNetwork: Boolean): SpeechRecognitionOutcome =
        withContext(Dispatchers.Main) {
            if (!ensureMicrophonePermission()) {
                return@withContext SpeechRecognitionOutcome.PermissionDenied
            }
            if (!allowNetwork && !hasOnDeviceRecognizer()) {
                Log.i(TAG, "Offline speech requested but on-device recognizer is unavailable.")
                return@withContext SpeechRecognitionOutcome.Unavailable
            }
            val deferred = CompletableDeferred<SpeechRecognitionOutcome>()
            check(pendingOutcome.compareAndSet(null, deferred)) {
                "Speech recognition outcome is already pending."
            }
            speechStarted.set(false)
            finishRequested.set(false)
            lastPartialText.set(null)
            usingSpeechActivity.set(false)
            try {
                val recognizer = createRecognizer(allowNetwork)
                if (recognizer != null) {
                    listenWithRecognizer(recognizer, allowNetwork, deferred)
                } else if (allowNetwork && startSpeechActivity()) {
                    Log.i(TAG, "Falling back to the system speech activity.")
                } else {
                    completeOutcome(SpeechRecognitionOutcome.Unavailable)
                }
                deferred.await()
            } catch (_: Exception) {
                SpeechRecognitionOutcome.Cancelled
            } finally {
                pendingOutcome.compareAndSet(deferred, null)
                val recognizer = activeRecognizer.getAndSet(null)
                runCatching { recognizer?.cancel() }
                runCatching { recognizer?.destroy() }
            }
        }

    /**
     * 松开按住说话：停止收听并尽量交出已听到的文字。
     */
    override fun finishListening() {
        finishRequested.set(true)
        val recognizer = activeRecognizer.get()
        if (recognizer == null) {
            completeFromPartialOr(SpeechRecognitionOutcome.NoMatch)
            return
        }
        mainHandler.post {
            runCatching { recognizer.stopListening() }
            mainHandler.postDelayed(
                { completeFromPartialOr(SpeechRecognitionOutcome.NoMatch) },
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

    private suspend fun listenWithRecognizer(
        recognizer: SpeechRecognizer,
        allowNetwork: Boolean,
        deferred: CompletableDeferred<SpeechRecognitionOutcome>,
    ) {
        check(activeRecognizer.compareAndSet(null, recognizer)) {
            "Speech recognition is already active."
        }
        val noSpeechTimeout = Runnable {
            if (!speechStarted.get()) {
                Log.i(TAG, "Speech recognition ended: no speech detected.")
                completeFromPartialOr(SpeechRecognitionOutcome.NoMatch)
                runCatching { recognizer.stopListening() }
            }
        }
        val maxDurationTimeout = Runnable {
            Log.i(TAG, "Speech recognition reached the maximum listen duration.")
            runCatching { recognizer.stopListening() }
            mainHandler.postDelayed(
                { completeFromPartialOr(SpeechRecognitionOutcome.NoMatch) },
                STOP_RESULT_GRACE_MILLIS,
            )
        }
        recognizer.setRecognitionListener(OutcomeRecognitionListener())
        delay(START_LISTEN_DELAY_MILLIS)
        if (deferred.isCompleted) {
            return
        }
        recognizer.startListening(createListenIntent(allowNetwork))
        if (finishRequested.get()) {
            runCatching { recognizer.stopListening() }
        }
        mainHandler.postDelayed(noSpeechTimeout, NO_SPEECH_TIMEOUT_MILLIS)
        mainHandler.postDelayed(maxDurationTimeout, MAX_LISTEN_DURATION_MILLIS)
        try {
            deferred.await()
        } finally {
            mainHandler.removeCallbacks(noSpeechTimeout)
            mainHandler.removeCallbacks(maxDurationTimeout)
        }
    }

    private fun startSpeechActivity(): Boolean {
        val intent = createSpeechActivityIntent() ?: return false
        return runCatching {
            usingSpeechActivity.set(true)
            speechActivityLauncher.launch(intent)
            true
        }.getOrElse {
            usingSpeechActivity.set(false)
            false
        }
    }

    private fun createSpeechActivityIntent(): Intent? {
        val intent = createListenIntent(allowNetwork = true).apply {
            action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        }
        return intent.takeIf { activity.packageManager.resolveActivity(it, 0) != null }
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

    private fun hasOnDeviceRecognizer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)

    private fun createRecognizer(allowNetwork: Boolean): SpeechRecognizer? = runCatching {
        when {
            !allowNetwork && hasOnDeviceRecognizer() ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            SpeechRecognizer.isRecognitionAvailable(activity) ->
                SpeechRecognizer.createSpeechRecognizer(activity)
            hasOnDeviceRecognizer() ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            else -> null
        }
    }.onFailure { error ->
        Log.w(TAG, "Could not create speech recognizer: ${error.javaClass.simpleName}")
    }.getOrNull()

    private fun createListenIntent(allowNetwork: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, SPEECH_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, SPEECH_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1_500L,
            )
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
            if (!allowNetwork) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

    /**
     * 把系统回调收成一次结果；不把转写写进日志。
     */
    private inner class OutcomeRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "Speech recognizer is ready.")
        }

        override fun onBeginningOfSpeech() {
            speechStarted.set(true)
            Log.i(TAG, "Speech recognizer heard the beginning of speech.")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            Log.i(TAG, "Speech recognizer reached end of speech.")
        }

        override fun onError(error: Int) {
            Log.i(TAG, "Speech recognition error code $error.")
            val partial = lastPartialText.getAndSet(null)
            if (!partial.isNullOrBlank()) {
                completeOutcome(SpeechRecognitionOutcome.Success(partial))
                return
            }
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

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull(String::isNotBlank)
                ?.trim()
            if (!text.isNullOrBlank()) {
                lastPartialText.set(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun completeFromMatches(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull(String::isNotBlank)?.trim()
                ?: lastPartialText.getAndSet(null)
            completeOutcome(
                if (text.isNullOrBlank()) {
                    SpeechRecognitionOutcome.NoMatch
                } else {
                    SpeechRecognitionOutcome.Success(text)
                },
            )
        }
    }

    private companion object {
        const val TAG = "WhereSpeech"
        const val SPEECH_LANGUAGE = "zh-CN"
        const val START_LISTEN_DELAY_MILLIS = 150L
        const val NO_SPEECH_TIMEOUT_MILLIS = 8_000L
        const val MAX_LISTEN_DURATION_MILLIS = 30_000L
        const val STOP_RESULT_GRACE_MILLIS = 1_200L
    }
}
