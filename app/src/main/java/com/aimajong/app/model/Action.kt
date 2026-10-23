package com.aimajong.app.model

/**
 * 麻將動作類型
 */
enum class ActionType(val label: String) {
    DRAW("摸牌"),
    DISCARD("打牌"),
    CHI("吃牌"),
    PENG("碰牌"),
    GANG_MING("明槓"),
    GANG_AN("暗槓"),
    GANG_BU("補槓"),
    FLOWER("補花"),
    HU("胡牌")
}

/**
 * 宣告或吃碰槓的明牌組 (Meld)
 */
data class Meld(
    val type: ActionType,
    val tiles: List<MahjongTile>,
    val triggerTile: MahjongTile? = null,
    val targetSeat: PlayerSeat? = null
)

/**
 * 局內發生的一條動作記錄
 */
data class GameActionRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val turnNumber: Int,
    val seat: PlayerSeat,
    val actionType: ActionType,
    val tile: MahjongTile? = null,
    val extraTiles: List<MahjongTile> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
