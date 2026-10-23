package com.aimajong.app.model

/**
 * 牌局玩家座位定義
 */
enum class PlayerSeat(val title: String) {
    SELF("我 (本家)"),
    XIA_JIA("下家"),
    DUI_JIA("對家"),
    SHANG_JIA("上家");

    fun next(): PlayerSeat = when (this) {
        SELF -> XIA_JIA
        XIA_JIA -> DUI_JIA
        DUI_JIA -> SHANG_JIA
        SHANG_JIA -> SELF
    }

    fun previous(): PlayerSeat = when (this) {
        SELF -> SHANG_JIA
        SHANG_JIA -> DUI_JIA
        DUI_JIA -> XIA_JIA
        XIA_JIA -> SELF
    }
}
