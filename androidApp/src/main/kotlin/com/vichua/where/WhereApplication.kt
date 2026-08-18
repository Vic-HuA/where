package com.vichua.where

import android.app.Application
import com.vichua.where.platform.android.AndroidAppContainer

/**
 * 保存 Android 进程级依赖，确保 Activity 重建时复用同一数据库实例。
 */
class WhereApplication : Application() {
    /** Android 平台依赖容器，在 Application 创建后可用。 */
    lateinit var container: AndroidAppContainer
        private set

    /**
     * 初始化进程级数据库和用例依赖。
     */
    override fun onCreate() {
        super.onCreate()
        container = AndroidAppContainer(this)
    }
}
