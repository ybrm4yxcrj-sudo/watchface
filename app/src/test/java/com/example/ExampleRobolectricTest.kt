package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.watchface.Config
import com.example.watchface.data.ImageRotationMode
import com.example.watchface.data.LuxTier
import com.example.watchface.data.WatchFaceState
import com.example.watchface.domain.BurnInGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RoboConfig

@RunWith(RobolectricTestRunner::class)
@RoboConfig(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("OPPO Watch 2 表盘", appName)
    }

    @Test
    fun `verify burn-in 2D Lissajous space-filling offset calculations`() {
        val guard = BurnInGuard(Config.BURN_IN_RADIUS_DP)
        val offset0 = guard.getNextOffset(1.0f)
        assertEquals(0, offset0.step)
        assertTrue(offset0.isEnabled)
        // Ensure offsets remain safely within the bounded screen safe-zone limit
        assertTrue(kotlin.math.abs(offset0.offsetX) <= Config.BURN_IN_RADIUS_DP + 1.0f)
        assertTrue(kotlin.math.abs(offset0.offsetY) <= Config.BURN_IN_RADIUS_DP + 1.0f)

        // Multiple steps advance step correctly and generate smooth offsets
        val offset15 = (1..15).map { guard.getNextOffset(1.0f) }.last()
        assertEquals(15, offset15.step)
        assertTrue(kotlin.math.abs(offset15.offsetX) <= Config.BURN_IN_RADIUS_DP + 1.0f)
        assertTrue(kotlin.math.abs(offset15.offsetY) <= Config.BURN_IN_RADIUS_DP + 1.0f)

        // When disabled, offsets are strictly zero
        guard.setEnabled(false)
        val disabledOffset = guard.getNextOffset(1.0f)
        assertEquals(0f, disabledOffset.offsetX, 0.001f)
        assertEquals(0f, disabledOffset.offsetY, 0.001f)
    }

    @Test
    fun `verify config parameters for oppo watch 2`() {
        assertEquals(15_000L, Config.ACTIVE_TIMEOUT_MS)
        assertEquals(402, Config.SCREEN_WIDTH_PX)
        assertEquals(476, Config.SCREEN_HEIGHT_PX)
        assertEquals(0.85f, Config.IMAGE_DIM_SCALE, 0.01f)
        assertEquals(3, Config.IMAGE_CACHE_SIZE)
    }

    @Test
    fun `verify rotation modes and state values`() {
        assertEquals(4, ImageRotationMode.values().size)
        assertEquals(2, WatchFaceState.values().size)
        assertEquals(3, LuxTier.values().size)
    }
}
