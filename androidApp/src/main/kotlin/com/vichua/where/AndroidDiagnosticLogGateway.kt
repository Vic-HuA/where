package com.vichua.where

import android.util.Log
import com.vichua.where.core.platform.DiagnosticEvent
import com.vichua.where.core.platform.DiagnosticLogGateway

/**
 * 把固定诊断事件码写到 logcat，不接受自由文本以免带出家庭数据。
 */
class AndroidDiagnosticLogGateway : DiagnosticLogGateway {
    /**
     * 只输出事件码；调用方必须先确认开关已打开。
     */
    override fun record(event: DiagnosticEvent) {
        Log.i(TAG, event.code)
    }

    private companion object {
        const val TAG = "WhereDiagnostic"
    }
}
