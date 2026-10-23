package com.aimajong.app.model

/**
 * 完整牌局當前狀態
 */
data class GameState(
    val handTiles: List<MahjongTile> = emptyList(),
    val flowers: List<MahjongTile> = emptyList(),
    val melds: List<Meld> = emptyList(),
    val discards: Map<PlayerSeat, List<MahjongTile>> = mapOf(
        PlayerSeat.SELF to emptyList(),
        PlayerSeat.XIA_JIA to emptyList(),
        PlayerSeat.DUI_JIA to emptyList(),
        PlayerSeat.SHANG_JIA to emptyList()
    ),
    val otherMelds: Map<PlayerSeat, List<Meld>> = mapOf(
        PlayerSeat.XIA_JIA to emptyList(),
        PlayerSeat.DUI_JIA to emptyList(),
        PlayerSeat.SHANG_JIA to emptyList()
    ),
    val currentTurn: Int = 1,
    val activeSeat: PlayerSeat = PlayerSeat.SELF,
    val lastDiscard: Pair<PlayerSeat, MahjongTile>? = null,
    val history: List<GameActionRecord> = emptyList(),
    val isGameOver: Boolean = false
) {
    /**
     * 自己手牌張數（不含已副露的吃碰槓牌與花牌）
     */
    val concealedHandCount: Int get() = handTiles.size

    /**
     * 總共已看見的某張牌的數量（手牌 + 所有人牌河 + 所有吃碰槓明牌）
     */
    fun countVisibleTile(tile: MahjongTile): Int {
        var count = 0
        count += handTiles.count { it.id == tile.id }
        discards.values.forEach { river ->
            count += river.count { it.id == tile.id }
        }
        melds.forEach { m ->
            count += m.tiles.count { it.id == tile.id }
        }
        otherMelds.values.forEach { meldList ->
            meldList.forEach { m ->
                count += m.tiles.count { it.id == tile.id }
            }
        }
        return count
    }

    /**
     * 某張牌在牌山中可能還剩下的數量（最多 4 張）
     */
    fun remainingCount(tile: MahjongTile): Int {
        val maxTotal = if (tile.isFlower) 1 else 4
        return (maxTotal - countVisibleTile(tile)).coerceAtLeast(0)
    }
}

/**
 * AI 策略推薦結果
 */
data class AIDecisionResult(
    val recommendedAction: String, // 例如 "打 九萬" 或 "碰 六筒"
    val optionsProbability: List<DecisionChoiceProbability>,
    val explanationNote: String = ""
)

data class DecisionChoiceProbability(
    val actionText: String,
    val probability: Float // 0.0 ~ 1.0
)
