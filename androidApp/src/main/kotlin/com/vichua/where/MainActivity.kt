package com.vichua.where

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vichua.where.ui.WhereApp

/**
 * 承载共享 Compose 根界面，Android 平台逻辑通过独立模块注入。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhereApp()
        }
    }
}
