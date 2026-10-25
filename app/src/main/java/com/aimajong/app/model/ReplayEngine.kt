package com.aimajong.app.model

/**
 * 逐巡覆盤某一動作步數之手牌與場面快照
 */
data class ReplayStepSnapshot(
    val stepIndex: Int,
    val totalSteps: Int,
    val turnNumber: Int,
    val actingSeat: PlayerSeat?,
    val actionType: ActionType?,
    val actionTile: MahjongTile?,
    val description: String,
    val selfHand: List<MahjongTile>,
    val selfFlowers: List<MahjongTile>,
    val selfMelds: List<Meld>,
    val discards: Map<PlayerSeat, List<MahjongTile>>,
    val otherMelds: Map<PlayerSeat, List<Meld>>
)

object ReplayEngine {

    /**
     * 根據起手手牌與歷程動作列表，預先重建出整局每一步的精確手牌與場面快照
     */
    fun buildSnapshots(
        initialHand: List<MahjongTile>,
        dealerSeat: PlayerSeat,
        actions: List<GameActionRecord>
    ): List<ReplayStepSnapshot> {
        val snapshots = mutableListOf<ReplayStepSnapshot>()

        // 初始狀態 (第 0 步：開局起手)
        var curHand = initialHand.toMutableList()
        val curFlowers = mutableListOf<MahjongTile>()
        val curMelds = mutableListOf<Meld>()
        val curDiscards = mutableMapOf(
            PlayerSeat.SELF to mutableListOf<MahjongTile>(),
            PlayerSeat.XIA_JIA to mutableListOf<MahjongTile>(),
            PlayerSeat.DUI_JIA to mutableListOf<MahjongTile>(),
            PlayerSeat.SHANG_JIA to mutableListOf<MahjongTile>()
        )
        val curOtherMelds = mutableMapOf(
            PlayerSeat.XIA_JIA to mutableListOf<Meld>(),
            PlayerSeat.DUI_JIA to mutableListOf<Meld>(),
            PlayerSeat.SHANG_JIA to mutableListOf<Meld>()
        )

        val totalSteps = actions.size

        snapshots.add(
            ReplayStepSnapshot(
                stepIndex = 0,
                totalSteps = totalSteps,
                turnNumber = 1,
                actingSeat = dealerSeat,
                actionType = null,
                actionTile = null,
                description = "開局起手手牌 (莊家: ${dealerSeat.title})",
                selfHand = curHand.sorted(),
                selfFlowers = curFlowers.toList(),
                selfMelds = curMelds.toList(),
                discards = curDiscards.mapValues { it.value.toList() },
                otherMelds = curOtherMelds.mapValues { it.value.toList() }
            )
        )

        for ((index, action) in actions.withIndex()) {
            val stepNumber = index + 1
            val seat = action.seat
            val tile = action.tile
            val desc = buildStepDescription(action)

            when (action.actionType) {
                ActionType.DRAW -> {
                    if (seat == PlayerSeat.SELF && tile != null) {
                        curHand.add(tile)
                    }
                }
                ActionType.DISCARD -> {
                    if (tile != null) {
                        if (seat == PlayerSeat.SELF) {
                            val removeIdx = curHand.indexOfFirst { it.id == tile.id }
                            if (removeIdx != -1) curHand.removeAt(removeIdx)
                        }
                        curDiscards[seat]?.add(tile)
                    }
                }
                ActionType.CHI -> {
                    val chiTiles = action.extraTiles.ifEmpty { listOfNotNull(tile) }
                    val meld = Meld(ActionType.CHI, chiTiles, triggerTile = tile, targetSeat = seat)
                    if (seat == PlayerSeat.SELF) {
                        // 從手牌中移除吃牌用到的搭子 (除 triggerTile 外的 2 張)
                        val toRemove = chiTiles.toMutableList()
                        if (tile != null) toRemove.remove(tile)
                        for (rem in toRemove) {
                            val removeIdx = curHand.indexOfFirst { it.id == rem.id }
                            if (removeIdx != -1) curHand.removeAt(removeIdx)
                        }
                        curMelds.add(meld)
                    } else {
                        curOtherMelds[seat]?.add(meld)
                    }
                }
                ActionType.PENG -> {
                    val pengTiles = if (tile != null) listOf(tile, tile, tile) else action.extraTiles
                    val meld = Meld(ActionType.PENG, pengTiles, triggerTile = tile, targetSeat = seat)
                    if (seat == PlayerSeat.SELF && tile != null) {
                        // 手牌移除 2 張碰牌
                        repeat(2) {
                            val removeIdx = curHand.indexOfFirst { it.id == tile.id }
                            if (removeIdx != -1) curHand.removeAt(removeIdx)
                        }
                        curMelds.add(meld)
                    } else {
                        curOtherMelds[seat]?.add(meld)
                    }
                }
                ActionType.GANG_MING -> {
                    val gangTiles = if (tile != null) listOf(tile, tile, tile, tile) else action.extraTiles
                    val meld = Meld(ActionType.GANG_MING, gangTiles, triggerTile = tile, targetSeat = seat)
                    if (seat == PlayerSeat.SELF && tile != null) {
                        repeat(3) {
                            val removeIdx = curHand.indexOfFirst { it.id == tile.id }
                            if (removeIdx != -1) curHand.removeAt(removeIdx)
                        }
                        curMelds.add(meld)
                    } else {
                        curOtherMelds[seat]?.add(meld)
                    }
                }
                ActionType.GANG_AN -> {
                    val gangTiles = if (tile != null) listOf(tile, tile, tile, tile) else action.extraTiles
                    val meld = Meld(ActionType.GANG_AN, gangTiles, triggerTile = tile, targetSeat = seat)
                    if (seat == PlayerSeat.SELF && tile != null) {
                        repeat(4) {
                            val removeIdx = curHand.indexOfFirst { it.id == tile.id }
                            if (removeIdx != -1) curHand.removeAt(removeIdx)
                        }
                        curMelds.add(meld)
                    } else {
                        curOtherMelds[seat]?.add(meld)
                    }
                }
                ActionType.GANG_BU -> {
                    val gangTiles = if (tile != null) listOf(tile, tile, tile, tile) else action.extraTiles
                    val meld = Meld(ActionType.GANG_BU, gangTiles, triggerTile = tile, targetSeat = seat)
                    if (seat == PlayerSeat.SELF && tile != null) {
                        val removeIdx = curHand.indexOfFirst { it.id == tile.id }
                        if (removeIdx != -1) curHand.removeAt(removeIdx)
                        // 原先的碰牌升級為槓牌
                        val pengIdx = curMelds.indexOfFirst { it.type == ActionType.PENG && it.tiles.any { t -> t.id == tile.id } }
                        if (pengIdx != -1) {
                            curMelds[pengIdx] = meld
                        } else {
                            curMelds.add(meld)
                        }
                    } else {
                        curOtherMelds[seat]?.add(meld)
                    }
                }
                ActionType.FLOWER -> {
                    if (seat == PlayerSeat.SELF && tile != null) {
                        curFlowers.add(tile)
                    }
                }
                ActionType.HU -> {
                    // 結束
                }
            }

            snapshots.add(
                ReplayStepSnapshot(
                    stepIndex = stepNumber,
                    totalSteps = totalSteps,
                    turnNumber = action.turnNumber,
                    actingSeat = seat,
                    actionType = action.actionType,
                    actionTile = tile,
                    description = desc,
                    selfHand = curHand.sorted(),
                    selfFlowers = curFlowers.toList(),
                    selfMelds = curMelds.toList(),
                    discards = curDiscards.mapValues { it.value.toList() },
                    otherMelds = curOtherMelds.mapValues { it.value.toList() }
                )
            )
        }

        return snapshots
    }

    private fun buildStepDescription(action: GameActionRecord): String {
        val seatName = action.seat.title
        val tileName = action.tile?.name ?: ""
        return when (action.actionType) {
            ActionType.DRAW -> "【$seatName】摸牌" + if (action.seat == PlayerSeat.SELF) " [$tileName]" else ""
            ActionType.DISCARD -> "【$seatName】打出 [$tileName]"
            ActionType.CHI -> {
                val comb = action.extraTiles.joinToString("") { "[${it.name}]" }
                "【$seatName】吃牌 $comb"
            }
            ActionType.PENG -> "【$seatName】碰牌 [$tileName]"
            ActionType.GANG_MING -> "【$seatName】明槓 [$tileName]"
            ActionType.GANG_AN -> "【$seatName】暗槓" + if (action.seat == PlayerSeat.SELF) " [$tileName]" else ""
            ActionType.GANG_BU -> "【$seatName】補槓 [$tileName]"
            ActionType.FLOWER -> "【$seatName】補花 [$tileName]"
            ActionType.HU -> "🏆【$seatName】宣告胡牌！" + if (action.tile != null) " [$tileName]" else ""
        }
    }
}
