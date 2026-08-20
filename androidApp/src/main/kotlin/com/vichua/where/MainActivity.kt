package com.vichua.where

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vichua.where.core.model.DevicePlatform
import com.vichua.where.ui.WhereApp

/**
 * 承载共享 Compose 根界面，Android 平台逻辑通过独立模块注入。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WhereApplication).container
        val photoPickerGateway = AndroidPhotoPickerGateway(this)
        setContent {
            WhereApp(
                hasActiveHouseholdUseCase = container.hasActiveHouseholdUseCase,
                initializeHouseholdUseCase = container.initializeHouseholdUseCase,
                loadHomeSnapshotUseCase = container.loadHomeSnapshotUseCase,
                loadItemCreationContextUseCase = container.loadItemCreationContextUseCase,
                loadLatestItemDraftUseCase = container.loadLatestItemDraftUseCase,
                saveItemDraftUseCase = container.saveItemDraftUseCase,
                discardLatestItemDraftUseCase = container.discardLatestItemDraftUseCase,
                importItemPhotoUseCase = container.importItemPhotoUseCase,
                photoPickerGateway = photoPickerGateway,
                resolveMediaPath = container.resolveMediaPath,
                discardImportedPhotos = container.discardImportedPhotos,
                createManualItemUseCase = container.createManualItemUseCase,
                searchItemsUseCase = container.searchItemsUseCase,
                loadItemDetailUseCase = container.loadItemDetailUseCase,
                updateItemProfileUseCase = container.updateItemProfileUseCase,
                loadMoveItemContextUseCase = container.loadMoveItemContextUseCase,
                moveItemUseCase = container.moveItemUseCase,
                loadLocationTreeUseCase = container.loadLocationTreeUseCase,
                createLocationUseCase = container.createLocationUseCase,
                renameLocationUseCase = container.renameLocationUseCase,
                deleteEmptyLocationUseCase = container.deleteEmptyLocationUseCase,
                suggestedDeviceName = container.suggestedDeviceName,
                devicePlatform = DevicePlatform.ANDROID,
            )
        }
    }
}
