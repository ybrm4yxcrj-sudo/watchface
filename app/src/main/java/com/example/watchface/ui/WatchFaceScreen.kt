package com.example.watchface.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchface.Config
import com.example.watchface.data.ImageRotationMode
import com.example.watchface.data.LuxTier
import com.example.watchface.data.WatchFaceState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WatchFaceScreen(
    uiState: WatchFaceUiState,
    onWake: (String) -> Unit,
    onForceDim: () -> Unit,
    onNextWallpaper: () -> Unit = {},
    onSetRotationMode: (ImageRotationMode) -> Unit,
    onSetActiveTimeout: (Long) -> Unit,
    onSelectWallpaper: (Int) -> Unit,
    onRefreshWallpapers: () -> Unit,
    onSetManualLux: (Float?) -> Unit,
    onSetDimImageScale: (Float) -> Unit,
    onSetShowImageInDim: (Boolean) -> Unit,
    onSetBurnInPixelShift: (Boolean) -> Unit = {},
    onSetCircadianBrightness: (Boolean) -> Unit = {},
    onToggleSettings: (Boolean?) -> Unit
) {
    val isDim = uiState.state == WatchFaceState.DIM
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Color matrix for DIM state image dimming (Preserves vibrant colors and high clarity)
    val imageColorFilter = remember(isDim, uiState.dimImageScale, uiState.showImageInDim) {
        if (isDim) {
            val scale = if (uiState.showImageInDim) uiState.dimImageScale.coerceIn(0.2f, 1.0f) else 0.05f
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, 0f,
                        0f, scale, 0f, 0f, 0f,
                        0f, 0f, scale, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        } else {
            null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .combinedClickable(
                onClick = { onWake("触摸唤醒") },
                onDoubleClick = {
                    onNextWallpaper()
                },
                onLongClick = { onToggleSettings(true) }
            )
            .testTag("watchface_container"),
        contentAlignment = Alignment.Center
    ) {
        // 1. Wallpaper Image Layer with smooth Crossfade Transition
        Crossfade(
            targetState = uiState.currentBitmap,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            modifier = Modifier.fillMaxSize(),
            label = "wallpaper_crossfade"
        ) { targetBitmap ->
            if (targetBitmap != null) {
                val imageBitmap = remember(targetBitmap) {
                    targetBitmap.asImageBitmap()
                }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Watch Face Wallpaper",
                    contentScale = ContentScale.Crop,
                    colorFilter = imageColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("wallpaper_image")
                )
            } else {
                // Default deep AMOLED dark gradient if no image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color.Black),
                                center = Offset(200f, 200f),
                                radius = 600f
                            )
                        )
                )
            }
        }

        // 2. Dynamic AMOLED Dim overlay layer (Smoothly animated by BrightnessController)
        if (uiState.overlayAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = uiState.overlayAlpha.coerceIn(0f, 0.90f)))
            )
        }

        // 3. High-contrast ambient readability scrim for time & complications
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.70f to Color(0x77000000),
                        1.0f to Color(0xCC000000)
                    )
                )
        )

        // 4. Content Root with adaptive sizing to fit any watch screen width perfectly
        val burnInX = uiState.burnInOffset.offsetX
        val burnInY = uiState.burnInOffset.offsetY

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(burnInX.roundToInt(), burnInY.roundToInt()) }
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .testTag("watchface_content_root"),
            contentAlignment = Alignment.Center
        ) {
            val screenWidth = maxWidth
            val timeFontSize = (screenWidth.value * 0.24f).coerceIn(46f, 60f).sp
            val dateFontSize = (screenWidth.value * 0.065f).coerceIn(13f, 16f).sp

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top discreet spacer or subtle ambient indicator
                Spacer(modifier = Modifier.height(4.dp))

                // Time & Date Group (Centered, guaranteed single line, no overflow)
                val timeShiftX = uiState.burnInOffset.timeOffsetX
                val timeShiftY = uiState.burnInOffset.timeOffsetY
                val dateShiftX = uiState.burnInOffset.dateOffsetX
                val dateShiftY = uiState.burnInOffset.dateOffsetY

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    // Main Digital Clock (Clean HH:mm display, maxLines = 1, softWrap = false)
                    Text(
                        text = uiState.timeSnapshot.timeText,
                        color = Color.White,
                        fontSize = timeFontSize,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.0).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.95f),
                                offset = Offset(0f, 4f),
                                blurRadius = 16f
                            )
                        ),
                        modifier = Modifier
                            .offset { IntOffset(timeShiftX.roundToInt(), timeShiftY.roundToInt()) }
                            .testTag("time_main_text")
                    )

                    // Date & Weekday Text with micro-phase sub-pixel shift
                    Text(
                        text = uiState.timeSnapshot.dateText,
                        color = Color(0xFFF1F5F9),
                        fontSize = dateFontSize,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.3.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.95f),
                                offset = Offset(0f, 2f),
                                blurRadius = 10f
                            )
                        ),
                        modifier = Modifier
                            .offset { IntOffset(dateShiftX.roundToInt(), dateShiftY.roundToInt()) }
                            .padding(top = 4.dp)
                            .testTag("date_text")
                    )
                }

                // Bottom Battery & Status Bar - Clean battery display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .testTag("battery_status_row")
                ) {
                    val batteryPct = uiState.timeSnapshot.batteryPct
                    val batteryColor = when {
                        batteryPct <= 20 -> Color(0xFFEF4444)
                        batteryPct <= 50 -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    }

                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = "电池电量",
                        tint = batteryColor,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "$batteryPct%",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.95f),
                                offset = Offset(0f, 2f),
                                blurRadius = 8f
                            )
                        )
                    )
                }
            }
        }

        // 5. Settings & Control Drawer Sheet (for testing and customization)
        if (uiState.isSettingsOpen) {
            ModalBottomSheet(
                onDismissRequest = { onToggleSettings(false) },
                sheetState = sheetState,
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ) {
                WatchFaceSettingsContent(
                    uiState = uiState,
                    onWake = onWake,
                    onForceDim = onForceDim,
                    onSetRotationMode = onSetRotationMode,
                    onSetActiveTimeout = onSetActiveTimeout,
                    onSelectWallpaper = onSelectWallpaper,
                    onRefreshWallpapers = onRefreshWallpapers,
                    onSetManualLux = onSetManualLux,
                    onSetDimImageScale = onSetDimImageScale,
                    onSetShowImageInDim = onSetShowImageInDim,
                    onSetBurnInPixelShift = onSetBurnInPixelShift,
                    onSetCircadianBrightness = onSetCircadianBrightness,
                    onClose = { onToggleSettings(false) }
                )
            }
        }
    }
}

@Composable
fun WatchFaceSettingsContent(
    uiState: WatchFaceUiState,
    onWake: (String) -> Unit,
    onForceDim: () -> Unit,
    onSetRotationMode: (ImageRotationMode) -> Unit,
    onSetActiveTimeout: (Long) -> Unit,
    onSelectWallpaper: (Int) -> Unit,
    onRefreshWallpapers: () -> Unit,
    onSetManualLux: (Float?) -> Unit,
    onSetDimImageScale: (Float) -> Unit,
    onSetShowImageInDim: (Boolean) -> Unit,
    onSetBurnInPixelShift: (Boolean) -> Unit = {},
    onSetCircadianBrightness: (Boolean) -> Unit = {},
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "OPPO Watch 2 表盘控制台",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "AOD 常亮与传感器模拟调优",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
            }
        }

        // Section 1: State Switching & Live Status
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "状态机控制 (当前: ${if (uiState.state == WatchFaceState.ACTIVE) "ACTIVE 亮屏" else "DIM 压暗常亮"})",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onWake("控制台手动亮屏") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.state == WatchFaceState.ACTIVE) Color(0xFF0284C7) else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_wake_active")
                    ) {
                        Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("唤醒 ACTIVE")
                    }

                    Button(
                        onClick = { onForceDim() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.state == WatchFaceState.DIM) Color(0xFF6366F1) else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_force_dim")
                    ) {
                        Icon(Icons.Default.Nightlight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("进入 DIM")
                    }
                }

                Text(
                    text = "上次触发: ${uiState.lastWakeReason} | 蒙层Alpha: ${String.format("%.2f", uiState.overlayAlpha)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // Section 2: Active Timeout Duration
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "无操作自动压暗超时",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5_000L to "5秒", 10_000L to "10秒", 15_000L to "15秒(推荐)", 30_000L to "30秒").forEach { (ms, label) ->
                        FilterChip(
                            selected = uiState.activeTimeoutMs == ms,
                            onClick = { onSetActiveTimeout(ms) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF334155),
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }
            }
        }

        // Section 3: Wallpaper Rotation Mode & Picker
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "背景壁纸与轮换模式",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(onClick = onRefreshWallpapers) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新外部目录", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                }

                // Mode Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ImageRotationMode.values().forEach { mode ->
                        FilterChip(
                            selected = uiState.rotationMode == mode,
                            onClick = { onSetRotationMode(mode) },
                            label = { Text(mode.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF334155),
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }

                // Wallpaper List
                Text(
                    text = "当前壁纸: ${uiState.currentWallpaper?.title ?: "无"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    uiState.allWallpapers.forEachIndexed { index, wp ->
                        val isSelected = uiState.currentWallpaper?.id == wp.id
                        Surface(
                            onClick = { onSelectWallpaper(index) },
                            color = if (isSelected) Color(0xFF0369A1) else Color(0xFF334155),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = wp.title,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else Color(0xFFE2E8F0)
                                )
                                Text(
                                    text = if (wp.isExternal) "/sdcard/WatchFace" else "内置预设",
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color(0xFFBAE6FD) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 4: AOD / DIM State Wallpaper Brightness & Visibility
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "熄屏常亮显示壁纸图片",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Switch(
                        checked = uiState.showImageInDim,
                        onCheckedChange = { onSetShowImageInDim(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }

                Text(
                    text = "熄屏常亮与亮屏界面保持完全一致的排版、壁纸与元素，仅由 AMOLED 屏幕亮度控制功耗。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                // Brightness Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0.50f to "柔和 50%",
                        0.70f to "标准 70%(推荐)",
                        0.85f to "高亮 85%",
                        1.00f to "100% 原亮"
                    ).forEach { (scale, label) ->
                        FilterChip(
                            selected = (uiState.dimImageScale - scale).let { it > -0.05f && it < 0.05f },
                            onClick = { onSetDimImageScale(scale) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF334155),
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }
            }
        }

        // Section 5: Ambient Light Sensor & Simulation
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "环境光自适应 (档位: ${uiState.luxTier.displayName})",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "平滑光照值: ${uiState.smoothedLux.roundToInt()} lux (原始: ${uiState.rawLux.roundToInt()} lux)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                // Quick test buttons for lux
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { onSetManualLux(5f) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("夜间 5lx", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { onSetManualLux(80f) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("室内 80lx", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { onSetManualLux(350f) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("户外 350lx", fontSize = 10.sp)
                    }
                    Button(
                        onClick = { onSetManualLux(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("物理传感器", fontSize = 10.sp)
                    }
                }
            }
        }

        // Section 6: OLED Burn-In Guard & Pixel Shifting (防烧屏像素微偏移)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "防烧屏像素微偏移 (Pixel Shifting)",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Switch(
                        checked = uiState.isBurnInPixelShiftEnabled,
                        onCheckedChange = { onSetBurnInPixelShift(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }

                Text(
                    text = "采用 2D 李萨如空间填充曲线 (3:4) 与子像素微抖动算法，每分钟平滑平移，时间与日期独立错相漂移，彻底消除 AMOLED 固定像素点老化。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "• 全局偏移: dx=${String.format("%.1f", uiState.burnInOffset.offsetX)}px, dy=${String.format("%.1f", uiState.burnInOffset.offsetY)}px (周期步数: ${uiState.burnInOffset.step % 120}/120)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = "• 时间子项偏移: dx=${String.format("%.1f", uiState.burnInOffset.timeOffsetX)}px, dy=${String.format("%.1f", uiState.burnInOffset.timeOffsetY)}px",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = "• 日期子项偏移: dx=${String.format("%.1f", uiState.burnInOffset.dateOffsetX)}px, dy=${String.format("%.1f", uiState.burnInOffset.dateOffsetY)}px",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = "• 算法状态: ${uiState.burnInOffset.algorithmName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }

        // Section 7: Circadian Diurnal Auto-Brightness (随时间自动昼夜调光)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "全局亮度随时间自动调整",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Switch(
                        checked = uiState.isCircadianBrightnessEnabled,
                        onCheckedChange = { onSetCircadianBrightness(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }

                Text(
                    text = "结合 24 小时生物节律与环境光双重联动：深夜 (23:00-05:30) 自动降低亮度至 45% 深度护眼并加倍保护 OLED 发光像素；清晨与日落平滑升降；白天维持 100% 充沛可视度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "• 当前时段: ${uiState.circadianProfile.periodName} (${uiState.circadianProfile.description})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF59E0B)
                        )
                        Text(
                            text = "• 昼夜调光系数: ${(uiState.circadianProfile.circadianFactor * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = "• 加速度计: 俯仰 ${String.format("%.1f", uiState.pitchAngle)}° | 运动模长 ${String.format("%.2f", uiState.motionMagnitudeDev)} m/s²",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
