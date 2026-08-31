package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.MyApplicationTheme
import com.example.watchface.data.WatchFaceState
import com.example.watchface.domain.TimeSnapshot
import com.example.watchface.ui.WatchFaceScreen
import com.example.watchface.ui.WatchFaceUiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun greeting_screenshot() {
        val testUiState = WatchFaceUiState(
            state = WatchFaceState.ACTIVE,
            timeSnapshot = TimeSnapshot(
                timeText = "10:32",
                secondsText = ":45",
                dateText = "周一 8月31日",
                batteryPct = 78,
                isCharging = false,
                hourOfDay = 10,
                minuteOfHour = 32
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                WatchFaceScreen(
                    uiState = testUiState,
                    onWake = {},
                    onForceDim = {},
                    onSetRotationMode = {},
                    onSetActiveTimeout = {},
                    onSelectWallpaper = {},
                    onRefreshWallpapers = {},
                    onSetManualLux = {},
                    onSetDimImageScale = {},
                    onSetShowImageInDim = {},
                    onToggleSettings = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
