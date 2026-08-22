package com.vichua.where

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vichua.where.core.platform.PhotoPickerGateway
import com.vichua.where.core.platform.PickedImage
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred

/**
 * 系统相册选择和相机拍照共用入口。
 *
 * 启动器必须在 Activity 进入 STARTED 之前注册。拍照写入应用缓存，不申请相册权限。
 */
class AndroidPhotoPickerGateway(
    private val activity: ComponentActivity,
) : PhotoPickerGateway {
    private val pendingPick = AtomicReference<CompletableDeferred<PickedImage?>?>(null)
    private val pendingCapture = AtomicReference<CompletableDeferred<PickedImage?>?>(null)
    private val pendingCameraPermission = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val pendingCaptureUri = AtomicReference<Uri?>(null)
    private val pendingCaptureFile = AtomicReference<File?>(null)

    private val pickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val deferred = pendingPick.getAndSet(null) ?: return@registerForActivityResult
        deferred.complete(uri?.let(::readPickedImage))
    }

    private val captureLauncher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val deferred = pendingCapture.getAndSet(null) ?: return@registerForActivityResult
        val file = pendingCaptureFile.getAndSet(null)
        pendingCaptureUri.set(null)
        if (!saved || file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            deferred.complete(null)
            return@registerForActivityResult
        }
        val pickedImage = runCatching {
            PickedImage(bytes = file.readBytes(), mimeType = "image/jpeg")
        }.getOrNull()
        file.delete()
        deferred.complete(pickedImage)
    }

    private val cameraPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val deferred = pendingCameraPermission.getAndSet(null) ?: return@registerForActivityResult
        deferred.complete(granted)
    }

    /**
     * 打开系统选择器；用户取消或读取失败时返回空，让录入页继续手填。
     */
    override suspend fun pickImage(): PickedImage? {
        val deferred = CompletableDeferred<PickedImage?>()
        check(pendingPick.compareAndSet(null, deferred)) {
            "Photo picker is already active."
        }
        return try {
            pickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            deferred.await()
        } catch (_: Exception) {
            pendingPick.compareAndSet(deferred, null)
            null
        }
    }

    /**
     * 申请相机权限后打开系统相机；取消、拒绝权限或写入失败时返回空。
     */
    override suspend fun captureImage(): PickedImage? {
        if (!ensureCameraPermission()) {
            Log.i(TAG, "Camera permission was denied.")
            return null
        }
        val captureFile = createCaptureFile() ?: return null
        val captureUri = runCatching {
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                captureFile,
            )
        }.getOrElse { error ->
            Log.w(TAG, "Could not create camera capture URI: ${error.javaClass.simpleName}")
            captureFile.delete()
            return null
        }
        val deferred = CompletableDeferred<PickedImage?>()
        check(pendingCapture.compareAndSet(null, deferred)) {
            "Camera capture is already active."
        }
        pendingCaptureFile.set(captureFile)
        pendingCaptureUri.set(captureUri)
        return try {
            captureLauncher.launch(captureUri)
            deferred.await()
        } catch (error: Exception) {
            Log.w(TAG, "Camera capture failed: ${error.javaClass.simpleName}")
            pendingCapture.compareAndSet(deferred, null)
            pendingCaptureFile.getAndSet(null)?.delete()
            pendingCaptureUri.set(null)
            null
        }
    }

    private suspend fun ensureCameraPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            return true
        }
        val deferred = CompletableDeferred<Boolean>()
        if (!pendingCameraPermission.compareAndSet(null, deferred)) {
            return pendingCameraPermission.get()?.await() == true
        }
        return try {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            deferred.await()
        } catch (_: Exception) {
            pendingCameraPermission.compareAndSet(deferred, null)
            false
        }
    }

    private fun createCaptureFile(): File? = runCatching {
        val directory = File(activity.cacheDir, CAMERA_DIRECTORY).apply { mkdirs() }
        File.createTempFile("capture_", ".jpg", directory)
    }.onFailure { error ->
        Log.w(TAG, "Could not create camera file: ${error.javaClass.simpleName}")
    }.getOrNull()

    private fun readPickedImage(uri: Uri): PickedImage? = runCatching {
        activity.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.isEmpty()) {
                null
            } else {
                PickedImage(
                    bytes = bytes,
                    mimeType = activity.contentResolver.getType(uri),
                )
            }
        }
    }.getOrNull()

    private companion object {
        const val TAG = "WherePhotoPicker"
        const val CAMERA_DIRECTORY = "camera"
    }
}
