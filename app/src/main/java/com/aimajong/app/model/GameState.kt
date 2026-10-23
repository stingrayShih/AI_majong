package com.aimajong.app.model

/**
 * 完整牌局當前狀態
 */
data class GameState(
    val dealerSeat: PlayerSeat = PlayerSeat.SELF,
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
     * 自己包含副露的「有效總牌數」
     * 正常台灣 16 張：非出牌階段為 16 張；摸牌/吃碰後為 17 張
     */
    val totalHandWeight: Int get() = concealedHandCount + (melds.size * 3)

    /**
     * 當前自己是否處於「可以打牌」的狀態 (總張數為 17 張)
     */
    val canSelfDiscard: Boolean get() = totalHandWeight >= 17

    /**
     * 當前自己是否處於「需要摸牌」的狀態 (總張數為 16 張)
     */
    val canSelfDraw: Boolean get() = totalHandWeight <= 16

    /**
     * 總共已看見的某張牌的數量（手牌 + 所有人牌河 + 所有吃碰槓明牌）
     */
    fun countVisibleTile(tile: MahjongTile): Int {
        var count = 0
        count += handTiles.count { it.id == tile.id }
        count += flowers.count { it.id == tile.id }
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
     * 某張牌在牌山中可能還剩下的數量（最多 4 張，花牌最多 1 張）
     */
    fun remainingCount(tile: MahjongTile): Int {
        val maxTotal = MahjongTile.getMaxCount(tile)
        return (maxTotal - countVisibleTile(tile)).coerceAtLeast(0)
    }

    /**
     * 檢測自己手牌是否可以「碰」某張牌（手牌中至少有 2 張同牌）
     */
    fun canPeng(targetTile: MahjongTile): Boolean {
        return handTiles.count { it.id == targetTile.id } >= 2
    }

    /**
     * 檢測自己手牌是否可以「明槓」某張牌（手牌中至少有 3 張同牌）
     */
    fun canMingGang(targetTile: MahjongTile): Boolean {
        return handTiles.count { it.id == targetTile.id } >= 3
    }

    /**
     * 檢測自己手牌中是否有可以「暗槓」的牌（手牌中有 4 張同牌）
     */
    fun getAnGangOptions(): List<MahjongTile> {
        return handTiles.groupBy { it.id }
            .filter { it.value.size >= 4 }
            .map { it.value.first() }
    }

    /**
     * 檢測針對上家打出的牌，自己手牌中所有可以「吃」的 2 張組合
     */
    fun getChiCombinations(targetTile: MahjongTile): List<Pair<MahjongTile, MahjongTile>> {
        if (targetTile.suit != TileSuit.WAN &&
            targetTile.suit != TileSuit.TONG &&
            targetTile.suit != TileSuit.TIAO
        ) {
            return emptyList()
        }

        val r = targetTile.rank
        val suit = targetTile.suit
        val suitTiles = handTiles.filter { it.suit == suit }
        val results = mutableListOf<Pair<MahjongTile, MahjongTile>>()

        // 情況 1: [r-2, r-1, (r)] 例如手牌有 1,2，吃 3
        if (r >= 3) {
            val t1 = suitTiles.find { it.rank == r - 2 }
            val t2 = suitTiles.find { it.rank == r - 1 }
            if (t1 != null && t2 != null) results.add(Pair(t1, t2))
        }

        // 情況 2: [r-1, (r), r+1] 例如手牌有 2,4，吃 3
        if (r in 2..8) {
            val t1 = suitTiles.find { it.rank == r - 1 }
            val t2 = suitTiles.find { it.rank == r + 1 }
            if (t1 != null && t2 != null) results.add(Pair(t1, t2))
        }

        // 情況 3: [(r), r+1, r+2] 例如手牌有 4,5，吃 3
        if (r <= 7) {
            val t1 = suitTiles.find { it.rank == r + 1 }
            val t2 = suitTiles.find { it.rank == r + 2 }
            if (t1 != null && t2 != null) results.add(Pair(t1, t2))
        }

        return results
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
