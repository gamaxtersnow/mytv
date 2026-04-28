package com.gamaxtersnow.mytv.ui

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

enum class AppUiSurface {
    TV,
    PHONE_PORTRAIT,
    PHONE_LANDSCAPE
}

data class AppUiMode(
    val surface: AppUiSurface,
    val isTelevision: Boolean,
    val isLandscape: Boolean
) {
    companion object {
        fun resolve(
            hasLeanbackFeature: Boolean,
            hasTouchscreen: Boolean,
            smallestScreenWidthDp: Int,
            orientation: Int
        ): AppUiMode {
            val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE
            val isTelevision = hasLeanbackFeature && !hasTouchscreen
            val isLargeTouchDevice = hasTouchscreen && smallestScreenWidthDp >= 600
            val surface = when {
                isTelevision -> AppUiSurface.TV
                isLargeTouchDevice && isLandscape -> AppUiSurface.PHONE_LANDSCAPE
                isLandscape -> AppUiSurface.PHONE_LANDSCAPE
                else -> AppUiSurface.PHONE_PORTRAIT
            }

            return AppUiMode(
                surface = surface,
                isTelevision = isTelevision,
                isLandscape = isLandscape
            )
        }

        fun from(context: Context): AppUiMode {
            val packageManager = context.packageManager
            val configuration = context.resources.configuration
            return resolve(
                hasLeanbackFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
                hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
                smallestScreenWidthDp = configuration.smallestScreenWidthDp,
                orientation = configuration.orientation
            )
        }
    }
}
