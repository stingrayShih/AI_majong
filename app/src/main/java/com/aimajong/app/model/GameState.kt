package com.aimajong.app.model

/**
 * 麻將局內狀態階段
 */
enum class TurnPhase(val description: String) {
    AWAITING_DRAW("等待摸牌"),
    AWAITING_DISCARD("等待出牌"),
    AWAITING_CALL_RESPONSE("等待鳴牌判定 (吃/碰/槓/胡)"),
    GAME_OVER("牌局結束")
}

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
    val currentTurnPlayer: PlayerSeat = PlayerSeat.SELF,
    val phase: TurnPhase = TurnPhase.AWAITING_DISCARD,
    val lastDiscard: Pair<PlayerSeat, MahjongTile>? = null,
    val history: List<GameActionRecord> = emptyList(),
    val isGameOver: Boolean = false,
    val winnerSeat: PlayerSeat? = null,
    val loserSeat: PlayerSeat? = null,
    val justMeldedThisTurn: Boolean = false // 剛吃/碰/槓副露完成，當回合必須打出一張牌，嚴格禁止胡牌！
) {
    val concealedHandCount: Int get() = handTiles.size

    val totalHandWeight: Int get() = concealedHandCount + (melds.size * 3)

    val canSelfDiscard: Boolean get() =
        !isGameOver && currentTurnPlayer == PlayerSeat.SELF && phase == TurnPhase.AWAITING_DISCARD

    val canSelfDraw: Boolean get() =
        !isGameOver && currentTurnPlayer == PlayerSeat.SELF && phase == TurnPhase.AWAITING_DRAW

    val isInCallResponsePhase: Boolean get() =
        !isGameOver && phase == TurnPhase.AWAITING_CALL_RESPONSE

    val canSelfCallLastDiscard: Boolean get() =
        isInCallResponsePhase && lastDiscard != null && lastDiscard.first != PlayerSeat.SELF

    fun countVisibleTile(tile: MahjongTile): Int {
        var count = 0
        count += handTiles.count { it.id == tile.id }
        discards.values.forEach { river ->
            count += river.count { it.id == tile.id }
        }
        val allMelds = melds + otherMelds.values.flatten()
        allMelds.forEach { m ->
            count += m.tiles.count { it.id == tile.id }
            // 若該副露包含取自他人牌河的觸發牌 (triggerTile)
            // 該牌已在 discards 牌河中被計入，在此扣除 1 張避免重複計算 (Issue #2)
            if (m.triggerTile?.id == tile.id) {
                count -= 1
            }
        }
        return count.coerceAtLeast(0)
    }

    fun remainingCount(tile: MahjongTile): Int {
        val maxTotal = MahjongTile.getMaxCount(tile)
        return (maxTotal - countVisibleTile(tile)).coerceAtLeast(0)
    }

    fun canPeng(targetTile: MahjongTile): Boolean {
        if (lastDiscard?.first == PlayerSeat.SELF) return false
        return handTiles.count { it.id == targetTile.id } >= 2
    }

    /**
     * 自己是否能抓對手打出的這張牌放槍 (榮和)
     */
    fun canSelfHuOnDiscard(discardedTile: MahjongTile): Boolean {
        if (isGameOver) return false
        if (lastDiscard?.first == PlayerSeat.SELF) return false
        if (justMeldedThisTurn && currentTurnPlayer == PlayerSeat.SELF) return false
        return MahjongRuleEngine.canHu(handTiles, melds, discardedTile)
    }

    /**
     * 自己是否能搶對手補槓的牌 (搶槓胡牌)
     */
    fun canSelfQiangGang(buGangTile: MahjongTile): Boolean {
        if (isGameOver) return false
        if (justMeldedThisTurn && currentTurnPlayer == PlayerSeat.SELF) return false
        return MahjongRuleEngine.canHu(handTiles, melds, buGangTile)
    }

    /**
     * 自己摸牌後是否已達成自摸 (輪到自己出牌階段，手牌共 17 張等效牌)
     */
    fun canSelfHuOnDraw(): Boolean {
        if (isGameOver) return false
        if (justMeldedThisTurn && currentTurnPlayer == PlayerSeat.SELF) return false
        return MahjongRuleEngine.canHu(handTiles, melds, null)
    }

    /**
     * 取得自己當前手牌聽哪些牌
     */
    fun getSelfWaitingTiles(): List<MahjongTile> {
        return MahjongRuleEngine.getWaitingTiles(handTiles, melds)
    }

    fun canMingGang(targetTile: MahjongTile): Boolean {
        if (lastDiscard?.first == PlayerSeat.SELF) return false
        return handTiles.count { it.id == targetTile.id } >= 3
    }

    fun getAnGangOptions(): List<MahjongTile> {
        // 吃碰後當回合只能出牌，不可暗槓
        if (justMeldedThisTurn && currentTurnPlayer == PlayerSeat.SELF) return emptyList()
        return handTiles
            .groupBy { it.id }
            .filter { it.value.size >= 4 }
            .map { it.value.first() }
    }

    /**
     * 檢查手牌中是否有已碰牌組的第 4 張牌，可執行補槓 (加槓/明槓)
     */
    fun getBuGangOptions(): List<MahjongTile> {
        // 吃碰後當回合只能出牌，不可補槓
        if (justMeldedThisTurn && currentTurnPlayer == PlayerSeat.SELF) return emptyList()
        val pengTiles = melds.filter { it.type == ActionType.PENG }.mapNotNull { it.tiles.firstOrNull() }
        return pengTiles.filter { pengTile ->
            handTiles.any { it.id == pengTile.id }
        }
    }

    fun getChiCombinations(targetTile: MahjongTile): List<Pair<MahjongTile, MahjongTile>> {
        if (lastDiscard?.first != PlayerSeat.SHANG_JIA) return emptyList()

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

        if (r >= 3) {
            val t1 = suitTiles.find { it.rank == r - 2 }
            val t2 = suitTiles.find { it.rank == r - 1 }
            if (t1 != null && t2 != null) results.add(Pair(t1, t2))
        }

        if (r in 2..8) {
            val t1 = suitTiles.find { it.rank == r - 1 }
            val t2 = suitTiles.find { it.rank == r + 1 }
            if (t1 != null && t2 != null) results.add(Pair(t1, t2))
        }

        if (r <= 7) {
            val t1 = suitTiles.find { it.rank == r + 1 }
            val t2 = suitTiles.find { it.rank == r + 2 }
            if (t1 != null && t2 != null) results.add(Pair(t1, t2))
        }

        return results
    }

    companion object {
        /**
         * 取得某張牌所有可能的 3 張吃牌順子組合 (供他家或自己選擇具體吃法)
         */
        fun getAllPossibleChiSequences(tile: MahjongTile): List<List<MahjongTile>> {
            if (tile.suit != TileSuit.WAN && tile.suit != TileSuit.TONG && tile.suit != TileSuit.TIAO) {
                return emptyList()
            }
            val r = tile.rank
            val suit = tile.suit
            val suitTiles = MahjongTile.getTilesBySuit(suit)
            val sequences = mutableListOf<List<MahjongTile>>()

            // 情況 1: [r-2, r-1, (r)]
            if (r >= 3) {
                val t1 = suitTiles.find { it.rank == r - 2 }
                val t2 = suitTiles.find { it.rank == r - 1 }
                if (t1 != null && t2 != null) sequences.add(listOf(t1, t2, tile))
            }

            // 情況 2: [r-1, (r), r+1]
            if (r in 2..8) {
                val t1 = suitTiles.find { it.rank == r - 1 }
                val t2 = suitTiles.find { it.rank == r + 1 }
                if (t1 != null && t2 != null) sequences.add(listOf(t1, tile, t2))
            }

            // 情況 3: [(r), r+1, r+2]
            if (r <= 7) {
                val t1 = suitTiles.find { it.rank == r + 1 }
                val t2 = suitTiles.find { it.rank == r + 2 }
                if (t1 != null && t2 != null) sequences.add(listOf(tile, t1, t2))
            }

            return sequences
        }
    }
}

data class AIDecisionResult(
    val recommendedAction: String,
    val optionsProbability: List<DecisionChoiceProbability>,
    val explanationNote: String = ""
)

data class DecisionChoiceProbability(
    val actionText: String,
    val probability: Float
)
