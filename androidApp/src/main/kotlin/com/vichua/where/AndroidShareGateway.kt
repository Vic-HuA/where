package com.vichua.where

import android.content.ClipData
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.vichua.where.core.platform.ShareGateway
import com.vichua.where.core.platform.SharePayload
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过系统分享面板送出物品名称、位置和选定照片，不附带家庭数据库。
 */
class AndroidShareGateway(
    private val activity: ComponentActivity,
) : ShareGateway {
    /**
     * Android 始终可以打开系统分享选择器。
     */
    override fun isAvailable(): Boolean = true

    /**
     * 把受控私有照片转成一次性可读 URI，再交给系统选择器。
     */
    override suspend fun share(payload: SharePayload) = withContext(Dispatchers.Main) {
        val imageUris = payload.imageAbsolutePaths.map { path ->
            val file = File(path)
            require(file.isFile) { "Shared photo file does not exist." }
            FileProvider.getUriForFile(
                activity,
                FILE_PROVIDER_AUTHORITY,
                file,
            )
        }
        val shareIntent = if (imageUris.isEmpty()) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, payload.text)
            }
        } else if (imageUris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_TEXT, payload.text)
                putExtra(Intent.EXTRA_STREAM, imageUris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(activity.contentResolver, SHARE_CLIP_LABEL, imageUris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_TEXT, payload.text)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(activity.contentResolver, SHARE_CLIP_LABEL, imageUris.first()).also { clip ->
                    imageUris.drop(1).forEach { uri ->
                        clip.addItem(ClipData.Item(uri))
                    }
                }
            }
        }
        activity.startActivity(Intent.createChooser(shareIntent, SHARE_CHOOSER_TITLE))
    }

    private companion object {
        const val FILE_PROVIDER_AUTHORITY = "com.vichua.where.fileprovider"
        const val SHARE_CLIP_LABEL = "where-shared-photo"
        const val SHARE_CHOOSER_TITLE = "分享位置"
    }
}
