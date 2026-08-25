package com.vichua.where

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vichua.where.core.model.DevicePlatform
import com.vichua.where.platform.android.AndroidVisibleDateTimeFormatter
import com.vichua.where.ui.WhereApp

/**
 * 承载共享 Compose 根界面，Android 平台逻辑通过独立模块注入。
 */
class MainActivity : ComponentActivity() {
    private lateinit var textToSpeechGateway: AndroidTextToSpeechGateway

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WhereApplication).container
        val photoPickerGateway = AndroidPhotoPickerGateway(this)
        textToSpeechGateway = AndroidTextToSpeechGateway(this)
        val hapticFeedbackGateway = AndroidHapticFeedbackGateway(this)
        val shareGateway = AndroidShareGateway(this)
        val documentGateway = AndroidDocumentGateway(this)
        val speechRecognitionGateway = AndroidSpeechRecognitionGateway(this)
        val aiAssistanceGateway = AndroidAiAssistanceGateway(
            credentialsStore = container.aiProviderCredentialsStore,
            readPhotoBytes = container.readPhotoBytes,
        )
        val diagnosticLogGateway = AndroidDiagnosticLogGateway()
        setContent {
            WhereApp(
                hasActiveHouseholdUseCase = container.hasActiveHouseholdUseCase,
                initializeHouseholdUseCase = container.initializeHouseholdUseCase,
                loadHomeSnapshotUseCase = container.loadHomeSnapshotUseCase,
                loadAllItemsUseCase = container.loadAllItemsUseCase,
                loadItemsAtLocationUseCase = container.loadItemsAtLocationUseCase,
                loadLocationUnconfirmedItemsUseCase = container.loadLocationUnconfirmedItemsUseCase,
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
                addItemPhotoUseCase = container.addItemPhotoUseCase,
                setItemPhotoCoverUseCase = container.setItemPhotoCoverUseCase,
                updateItemPhotoRoleUseCase = container.updateItemPhotoRoleUseCase,
                moveItemPhotoUseCase = container.moveItemPhotoUseCase,
                reorderItemPhotosUseCase = container.reorderItemPhotosUseCase,
                deleteItemPhotoUseCase = container.deleteItemPhotoUseCase,
                deleteItemUseCase = container.deleteItemUseCase,
                restoreDeletedItemUseCase = container.restoreDeletedItemUseCase,
                buildItemLocationSpeechUseCase = container.buildItemLocationSpeechUseCase,
                buildItemLocationShareUseCase = container.buildItemLocationShareUseCase,
                textToSpeechGateway = textToSpeechGateway,
                hapticFeedbackGateway = hapticFeedbackGateway,
                shareGateway = shareGateway,
                loadMoveItemContextUseCase = container.loadMoveItemContextUseCase,
                moveItemUseCase = container.moveItemUseCase,
                loadLocationTreeUseCase = container.loadLocationTreeUseCase,
                createLocationUseCase = container.createLocationUseCase,
                createLocationPathUseCase = container.createLocationPathUseCase,
                renameLocationUseCase = container.renameLocationUseCase,
                deleteEmptyLocationUseCase = container.deleteEmptyLocationUseCase,
                loadAccessibilityPreferencesUseCase = container.loadAccessibilityPreferencesUseCase,
                updateAccessibilityPreferencesUseCase = container.updateAccessibilityPreferencesUseCase,
                loadAppPreferencesUseCase = container.loadAppPreferencesUseCase,
                updateAppPreferencesUseCase = container.updateAppPreferencesUseCase,
                loadAiProviderCredentialsUseCase = container.loadAiProviderCredentialsUseCase,
                updateAiProviderCredentialsUseCase = container.updateAiProviderCredentialsUseCase,
                prepareVoiceSearchQueryUseCase = container.prepareVoiceSearchQueryUseCase,
                speechRecognitionGateway = speechRecognitionGateway,
                prepareAiPhotoRequestUseCase = container.prepareAiPhotoRequestUseCase,
                aiAssistanceGateway = aiAssistanceGateway,
                diagnosticLogGateway = diagnosticLogGateway,
                loadLatestBackupStatusUseCase = container.loadLatestBackupStatusUseCase,
                listManagedBackupsUseCase = container.listManagedBackupsUseCase(documentGateway),
                createEncryptedBackupUseCase = container.createEncryptedBackupUseCase(documentGateway),
                exportHouseholdDataUseCase = container.exportHouseholdDataUseCase(documentGateway),
                verifyBackupPackageUseCase = container.verifyBackupPackageUseCase(documentGateway),
                previewBackupRestoreUseCase = container.previewBackupRestoreUseCase(documentGateway),
                applyBackupRestoreUseCase = container.applyBackupRestoreUseCase,
                clearHouseholdDataUseCase = container.clearHouseholdDataUseCase,
                visibleDateTimeFormatter = AndroidVisibleDateTimeFormatter,
                suggestedDeviceName = container.suggestedDeviceName,
                devicePlatform = DevicePlatform.ANDROID,
            )
        }
    }

    override fun onDestroy() {
        if (::textToSpeechGateway.isInitialized) {
            textToSpeechGateway.shutdown()
        }
        super.onDestroy()
    }
}
