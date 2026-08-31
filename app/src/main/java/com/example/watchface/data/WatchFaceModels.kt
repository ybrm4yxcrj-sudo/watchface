package com.example.watchface.data

enum class WatchFaceState {
    ACTIVE,
    DIM
}

enum class ImageRotationMode(val label: String, val description: String) {
    ON_WAKE("唤醒时轮换", "每次从 DIM 唤醒到 ACTIVE 时随机换图"),
    HOURLY("按小时轮换", "每小时更换一张图片 (hour % count)"),
    INTERVAL_15M("每15分钟", "每隔 15 分钟自动轮换下一张壁纸"),
    FIXED("固定壁纸", "固定显示当前选中的单张图片")
}

enum class LuxTier(val displayName: String) {
    NIGHT("夜间 / 暗室 (< 10 lux)"),
    ROOM("室内普通 (10 ~ 200 lux)"),
    SUN("户外强光 (> 200 lux)")
}
