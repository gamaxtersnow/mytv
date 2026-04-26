package com.gamaxtersnow.mytv

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager

class MyApplication : Application() {
    private lateinit var displayMetrics: DisplayMetrics

    override fun onCreate() {
        super.onCreate()

        displayMetrics = DisplayMetrics()
        // 获取显示 metrics，适配不同API版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ 用法
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val currentWindowMetrics = windowManager.currentWindowMetrics
            val windowInsets = windowManager.currentWindowMetrics.windowInsets
            val insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            displayMetrics.widthPixels = currentWindowMetrics.bounds.width() - insets.left - insets.right
            displayMetrics.heightPixels = currentWindowMetrics.bounds.height() - insets.top - insets.bottom
            displayMetrics.density = resources.displayMetrics.density
            displayMetrics.densityDpi = resources.displayMetrics.densityDpi
        } else {
            // API 29及以下用法
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
        }
    }

    fun getDisplayMetrics(): DisplayMetrics {
        return displayMetrics
    }
}