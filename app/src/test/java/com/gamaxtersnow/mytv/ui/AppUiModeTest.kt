package com.gamaxtersnow.mytv.ui

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUiModeTest {
    @Test
    fun leanbackDeviceWithoutTouchscreenResolvesToTv() {
        val mode = AppUiMode.resolve(
            hasLeanbackFeature = true,
            hasTouchscreen = false,
            smallestScreenWidthDp = 960,
            orientation = Configuration.ORIENTATION_LANDSCAPE
        )

        assertEquals(AppUiSurface.TV, mode.surface)
        assertTrue(mode.isTelevision)
        assertTrue(mode.isLandscape)
    }

    @Test
    fun touchPhonePortraitResolvesToPhonePortrait() {
        val mode = AppUiMode.resolve(
            hasLeanbackFeature = false,
            hasTouchscreen = true,
            smallestScreenWidthDp = 390,
            orientation = Configuration.ORIENTATION_PORTRAIT
        )

        assertEquals(AppUiSurface.PHONE_PORTRAIT, mode.surface)
        assertFalse(mode.isTelevision)
        assertFalse(mode.isLandscape)
    }

    @Test
    fun touchPhoneLandscapeResolvesToPhoneLandscape() {
        val mode = AppUiMode.resolve(
            hasLeanbackFeature = false,
            hasTouchscreen = true,
            smallestScreenWidthDp = 390,
            orientation = Configuration.ORIENTATION_LANDSCAPE
        )

        assertEquals(AppUiSurface.PHONE_LANDSCAPE, mode.surface)
        assertFalse(mode.isTelevision)
        assertTrue(mode.isLandscape)
    }

    @Test
    fun largeTouchDeviceWithoutLeanbackDoesNotBecomeTv() {
        val mode = AppUiMode.resolve(
            hasLeanbackFeature = false,
            hasTouchscreen = true,
            smallestScreenWidthDp = 900,
            orientation = Configuration.ORIENTATION_LANDSCAPE
        )

        assertEquals(AppUiSurface.PHONE_LANDSCAPE, mode.surface)
        assertFalse(mode.isTelevision)
    }
}
