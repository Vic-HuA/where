package com.vichua.where

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.vichua.where.core.platform.RecordedVoiceLabel
import com.vichua.where.core.platform.VoiceLabelGateway
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 用本机麦克风录制短语音名称，并用 MediaPlayer 试听。
 *
 * 识别用的原始录音不走这条接口，避免把未确认音频长期留下。
 */
class AndroidVoiceLabelGateway(
    private val activity: ComponentActivity,
) : VoiceLabelGateway {
    private val pendingPermission = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val deferred = pendingPermission.getAndSet(null) ?: return@registerForActivityResult
        deferred.complete(granted)
    }

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartedAt = 0L
    private var player: MediaPlayer? = null

    /**
     * 申请麦克风权限后开始录制到缓存文件。
     */
    override suspend fun startRecording() {
        stopPlayback()
        require(recorder == null) { "Voice label recording is already active." }
        if (!ensureMicrophonePermission()) {
            error("Microphone permission was denied.")
        }
        val file = File(activity.cacheDir, "voice-label-${System.currentTimeMillis()}.m4a")
        val mediaRecorder = createRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioSamplingRate(44_100)
        mediaRecorder.setAudioEncodingBitRate(96_000)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.setMaxDuration(MAX_DURATION_MILLIS.toInt())
        try {
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            recordingFile = file
            recordingStartedAt = System.currentTimeMillis()
        } catch (error: Exception) {
            mediaRecorder.release()
            file.delete()
            Log.w(TAG, "Unable to start voice label recording.", error)
            throw error
        }
    }

    /**
     * 停止录制并读取缓存文件；过短或失败时返回空并删除临时文件。
     */
    override suspend fun stopRecording(): RecordedVoiceLabel? = withContext(Dispatchers.IO) {
        val mediaRecorder = recorder
        val file = recordingFile
        val startedAt = recordingStartedAt
        recorder = null
        recordingFile = null
        recordingStartedAt = 0L
        if (mediaRecorder == null || file == null) {
            return@withContext null
        }
        val durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        try {
            mediaRecorder.stop()
        } catch (error: Exception) {
            Log.w(TAG, "Unable to stop voice label recording cleanly.", error)
        } finally {
            mediaRecorder.release()
        }
        if (!file.exists() || file.length() == 0L || durationMillis < MIN_DURATION_MILLIS) {
            file.delete()
            return@withContext null
        }
        val recorded = RecordedVoiceLabel(
            bytes = file.readBytes(),
            mimeType = "audio/mp4",
            durationMillis = durationMillis,
        )
        file.delete()
        recorded
    }

    /**
     * 试听尚未保存的录音字节。
     */
    override suspend fun preview(bytes: ByteArray, mimeType: String) {
        val file = File(activity.cacheDir, "voice-label-preview.m4a")
        withContext(Dispatchers.IO) {
            file.writeBytes(bytes)
        }
        play(file.absolutePath)
    }

    /**
     * 播放已保存或预览文件。
     */
    override suspend fun play(absolutePath: String) = withContext(Dispatchers.Main) {
        stopPlayback()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setOnCompletionListener {
            stopPlayback()
        }
        try {
            mediaPlayer.setDataSource(absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (error: Exception) {
            stopPlayback()
            Log.w(TAG, "Unable to play voice label.", error)
            throw error
        }
    }

    /**
     * 停止当前试听或播放，释放播放器。
     */
    override fun stopPlayback() {
        val mediaPlayer = player
        player = null
        if (mediaPlayer != null) {
            runCatching { mediaPlayer.stop() }
            mediaPlayer.release()
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

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(activity)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private companion object {
        const val TAG = "WhereVoiceLabel"
        const val MIN_DURATION_MILLIS = 400L
        const val MAX_DURATION_MILLIS = 15_000L
    }
}
