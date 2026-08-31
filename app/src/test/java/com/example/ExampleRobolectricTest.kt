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
    fun `verify burn-in circular orbital offset calculations`() {
        val guard = BurnInGuard(Config.BURN_IN_RADIUS_DP)
        val offset0 = guard.getNextOffset(1.0f)
        assertEquals(0, offset0.step)
        // Step 0: angle = 0 -> x = radius * cos(0) = radius, y = radius * sin(0) = 0
        assertEquals(Config.BURN_IN_RADIUS_DP, offset0.offsetX, 0.01f)
        assertEquals(0f, offset0.offsetY, 0.01f)

        val offset15 = (1..15).map { guard.getNextOffset(1.0f) }.last()
        assertEquals(15, offset15.step)
        // Step 15: angle = (15/60)*2*PI = PI/2 -> x ~ 0, y ~ radius
        assertEquals(0f, offset15.offsetX, 0.1f)
        assertEquals(Config.BURN_IN_RADIUS_DP, offset15.offsetY, 0.1f)
    }

    @Test
    fun `verify config parameters for oppo watch 2`() {
        assertEquals(15_000L, Config.ACTIVE_TIMEOUT_MS)
        assertEquals(402, Config.SCREEN_WIDTH_PX)
        assertEquals(476, Config.SCREEN_HEIGHT_PX)
        assertEquals(0.70f, Config.IMAGE_DIM_SCALE, 0.01f)
        assertEquals(3, Config.IMAGE_CACHE_SIZE)
    }

    @Test
    fun `verify rotation modes and state values`() {
        assertEquals(3, ImageRotationMode.values().size)
        assertEquals(2, WatchFaceState.values().size)
        assertEquals(3, LuxTier.values().size)
    }
}
