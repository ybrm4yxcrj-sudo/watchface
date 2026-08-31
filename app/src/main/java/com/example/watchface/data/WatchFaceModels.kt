package com.example.watchface.data

enum class WatchFaceState {
    ACTIVE,
    DIM
}

enum class ImageRotationMode(val label: String, val description: String) {
    FIXED("固定第一张", "固定显示目录中的第一张图片"),
    HOURLY("按小时轮换", "每小时更换一张图片 (hour % count)"),
    ON_WAKE("唤醒时轮换", "每次从 DIM 唤醒到 ACTIVE 时随机换图")
}

enum class LuxTier(val displayName: String) {
    NIGHT("夜间 / 暗室 (< 10 lux)"),
    ROOM("室内普通 (10 ~ 200 lux)"),
    SUN("户外强光 (> 200 lux)")
}
