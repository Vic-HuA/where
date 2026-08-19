package com.vichua.where

import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.vichua.where.core.platform.PhotoPickerGateway
import com.vichua.where.core.platform.PickedImage
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred

/**
 * 使用系统照片选择器读取一张本地图片，避免申请相册权限。
 *
 * 必须在 Activity 进入 STARTED 之前注册，因此启动器绑定在构造阶段。
 */
class AndroidPhotoPickerGateway(
    activity: ComponentActivity,
) : PhotoPickerGateway {
    private val pendingResult = AtomicReference<CompletableDeferred<PickedImage?>?>(null)
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val deferred = pendingResult.getAndSet(null) ?: return@registerForActivityResult
        if (uri == null) {
            deferred.complete(null)
            return@registerForActivityResult
        }
        val pickedImage = runCatching {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                PickedImage(
                    bytes = bytes,
                    mimeType = activity.contentResolver.getType(uri),
                )
            }
        }.getOrNull()
        deferred.complete(pickedImage)
    }

    /**
     * 打开系统选择器；用户取消或读取失败时返回空，让录入页继续手填。
     */
    override suspend fun pickImage(): PickedImage? {
        val deferred = CompletableDeferred<PickedImage?>()
        check(pendingResult.compareAndSet(null, deferred)) {
            "Photo picker is already active."
        }
        return try {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
            deferred.await()
        } catch (_: Exception) {
            pendingResult.compareAndSet(deferred, null)
            null
        }
    }
}
