package com.vichua.where

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vichua.where.core.platform.HapticFeedbackGateway
import com.vichua.where.core.platform.HapticFeedbackKind

/**
 * 使用系统震动器给出主要操作和危险确认的短反馈。
 *
 * 没有震动器或系统拒绝时忽略，避免打断保存和删除流程。
 */
class AndroidHapticFeedbackGateway(
    context: Context,
) : HapticFeedbackGateway {
    private val vibrator: Vibrator? = resolveVibrator(context)

    /**
     * 设备声明具备震动器时才允许触发。
     */
    override fun isAvailable(): Boolean = vibrator?.hasVibrator() == true

    /**
     * 按档位震动一次；失败时吞掉异常，调用方无需处理降级文案。
     */
    override fun perform(kind: HapticFeedbackKind) {
        val current = vibrator ?: return
        if (!current.hasVibrator()) {
            return
        }
        try {
            current.vibrate(effectFor(kind))
        } catch (_: Exception) {
            // Unavailable vibration must not fail the calling flow.
        }
    }

    /**
     * 确认用单次短震，危险操作用两下更长的节奏。
     */
    private fun effectFor(kind: HapticFeedbackKind): VibrationEffect {
        return when (kind) {
            HapticFeedbackKind.CONFIRM -> VibrationEffect.createOneShot(
                CONFIRM_DURATION_MS,
                VibrationEffect.DEFAULT_AMPLITUDE,
            )
            HapticFeedbackKind.WARNING -> VibrationEffect.createWaveform(
                WARNING_TIMINGS_MS,
                -1,
            )
        }
    }

    /**
     * API 31 起从 VibratorManager 取默认震动器，旧版本直接取 Vibrator。
     */
    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
    }

    private companion object {
        const val CONFIRM_DURATION_MS = 40L
        val WARNING_TIMINGS_MS = longArrayOf(0L, 55L, 45L, 90L)
    }
}
