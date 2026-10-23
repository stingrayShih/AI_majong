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
    val loserSeat: PlayerSeat? = null
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

    fun remainingCount(tile: MahjongTile): Int {
        val maxTotal = MahjongTile.getMaxCount(tile)
        return (maxTotal - countVisibleTile(tile)).coerceAtLeast(0)
    }

    /**
     * 碰牌：花牌不能碰；自己打出的牌不能碰
     */
    fun canPeng(targetTile: MahjongTile): Boolean {
        if (targetTile.isFlower) return false
        if (lastDiscard?.first == PlayerSeat.SELF) return false
        return handTiles.count { it.id == targetTile.id } >= 2
    }

    /**
     * 明槓：花牌不能槓；自己打出的牌不能槓
     */
    fun canMingGang(targetTile: MahjongTile): Boolean {
        if (targetTile.isFlower) return false
        if (lastDiscard?.first == PlayerSeat.SELF) return false
        return handTiles.count { it.id == targetTile.id } >= 3
    }

    /**
     * 暗槓：花牌不能暗槓
     */
    fun getAnGangOptions(): List<MahjongTile> {
        return handTiles
            .filter { !it.isFlower }
            .groupBy { it.id }
            .filter { it.value.size >= 4 }
            .map { it.value.first() }
    }

    /**
     * 吃牌：花牌不能吃
     */
    fun getChiCombinations(targetTile: MahjongTile): List<Pair<MahjongTile, MahjongTile>> {
        if (targetTile.isFlower) return emptyList()
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
