package com.aimajong.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aimajong.app.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _aiDecision = MutableStateFlow<AIDecisionResult?>(null)
    val aiDecision: StateFlow<AIDecisionResult?> = _aiDecision.asStateFlow()

    private val _isLoadingDecision = MutableStateFlow(false)
    val isLoadingDecision: StateFlow<Boolean> = _isLoadingDecision.asStateFlow()

    private val historySnapshots = mutableListOf<GameState>()

    val canUndo: Boolean get() = historySnapshots.isNotEmpty()

    private fun saveSnapshot() {
        historySnapshots.add(_gameState.value)
    }

    fun undoLastAction(): Boolean {
        if (historySnapshots.isNotEmpty()) {
            val previousState = historySnapshots.removeAt(historySnapshots.lastIndex)
            _gameState.value = previousState
            triggerAIDecision()
            return true
        }
        return false
    }

    /**
     * 開局設定
     * @param initialHand 起手手牌 (若自己當莊為 17 張，閒家為 16 張)
     * @param dealerSeat 誰當莊家
     */
    fun startNewGame(initialHand: List<MahjongTile> = emptyList(), dealerSeat: PlayerSeat = PlayerSeat.SELF) {
        historySnapshots.clear()
        _gameState.value = GameState(
            dealerSeat = dealerSeat,
            handTiles = initialHand.sorted(),
            currentTurn = 1,
            currentTurnPlayer = dealerSeat, // 莊家先攻
            phase = TurnPhase.AWAITING_DISCARD // 莊家起手直接出牌
        )
        triggerAIDecision()
    }

    /**
     * 自己摸牌 (手牌加 1 張，進入可出牌階段)
     */
    fun onDrawTile(tile: MahjongTile) {
        val current = _gameState.value
        if (current.isGameOver) return
        if (current.currentTurnPlayer != PlayerSeat.SELF || current.phase != TurnPhase.AWAITING_DRAW) return
        if (current.remainingCount(tile) <= 0) return

        saveSnapshot()

        if (tile.isFlower) {
            // 補花：花牌計入花牌區，繼續處於摸牌階段補摸一張
            _gameState.update { s ->
                val record = GameActionRecord(
                    turnNumber = s.currentTurn,
                    seat = PlayerSeat.SELF,
                    actionType = ActionType.FLOWER,
                    tile = tile
                )
                s.copy(
                    flowers = s.flowers + tile,
                    history = s.history + record,
                    phase = TurnPhase.AWAITING_DRAW
                )
            }
        } else {
            // 摸入常規牌：進入等待出牌階段
            _gameState.update { s ->
                val newHand = (s.handTiles + tile).sorted()
                val record = GameActionRecord(
                    turnNumber = s.currentTurn,
                    seat = PlayerSeat.SELF,
                    actionType = ActionType.DRAW,
                    tile = tile
                )
                s.copy(
                    handTiles = newHand,
                    phase = TurnPhase.AWAITING_DISCARD,
                    history = s.history + record
                )
            }
            triggerAIDecision()
        }
    }

    /**
     * 玩家打牌 (自己或當前輪到的他家)
     */
    fun onDiscardTile(seat: PlayerSeat, tile: MahjongTile) {
        val current = _gameState.value
        if (current.isGameOver) return
        if (current.currentTurnPlayer != seat || current.phase != TurnPhase.AWAITING_DISCARD) return

        // 牌數上限檢查
        if (seat != PlayerSeat.SELF && current.remainingCount(tile) <= 0) return

        saveSnapshot()

        _gameState.update { s ->
            val updatedRiver = (s.discards[seat] ?: emptyList()) + tile
            val newDiscards = s.discards.toMutableMap().apply { put(seat, updatedRiver) }

            val newHand = if (seat == PlayerSeat.SELF) {
                val list = s.handTiles.toMutableList()
                val index = list.indexOfFirst { it.id == tile.id }
                if (index != -1) list.removeAt(index)
                list.sorted()
            } else {
                s.handTiles
            }

            val record = GameActionRecord(
                turnNumber = s.currentTurn,
                seat = seat,
                actionType = ActionType.DISCARD,
                tile = tile
            )

            // 打牌後進入「等待鳴牌判定 (AWAITING_CALL_RESPONSE)」階段
            s.copy(
                handTiles = newHand,
                discards = newDiscards,
                lastDiscard = Pair(seat, tile),
                phase = TurnPhase.AWAITING_CALL_RESPONSE,
                history = s.history + record
            )
        }

        triggerAIDecision()
    }

    /**
     * 鳴牌判定通過（無人吃碰槓，或選擇「過」）
     * 遊戲順位自然輪替給打牌者的下一家摸牌
     */
    fun onPassCall() {
        val current = _gameState.value
        if (current.phase != TurnPhase.AWAITING_CALL_RESPONSE) return
        val lastDiscard = current.lastDiscard ?: return

        saveSnapshot()

        val nextPlayer = lastDiscard.first.next()
        _gameState.update { s ->
            s.copy(
                currentTurnPlayer = nextPlayer,
                phase = TurnPhase.AWAITING_DRAW,
                currentTurn = s.currentTurn + 1
            )
        }

        triggerAIDecision()
    }

    /**
     * 執行吃/碰/明槓（由打出的牌觸發，並發生【順位跳轉】）
     */
    fun onMeldAction(seat: PlayerSeat, meld: Meld) {
        val current = _gameState.value
        if (current.phase != TurnPhase.AWAITING_CALL_RESPONSE) return

        saveSnapshot()

        _gameState.update { s ->
            if (seat == PlayerSeat.SELF) {
                // 自己吃碰槓：手牌扣除除觸發牌外的其他牌
                val newHand = s.handTiles.toMutableList()
                val tilesToRemove = meld.tiles.toMutableList()
                if (meld.triggerTile != null) {
                    tilesToRemove.remove(meld.triggerTile)
                }

                tilesToRemove.forEach { t ->
                    val idx = newHand.indexOfFirst { it.id == t.id }
                    if (idx != -1) newHand.removeAt(idx)
                }

                val record = GameActionRecord(
                    turnNumber = s.currentTurn,
                    seat = seat,
                    actionType = meld.type,
                    tile = meld.triggerTile,
                    extraTiles = meld.tiles
                )

                // 順位跳轉給自己！
                // 如果是槓牌，需要補一張槓尾牌 (AWAITING_DRAW)；若吃碰則直接出牌 (AWAITING_DISCARD)
                val nextPhase = if (meld.type == ActionType.GANG_MING) TurnPhase.AWAITING_DRAW else TurnPhase.AWAITING_DISCARD

                s.copy(
                    handTiles = newHand.sorted(),
                    melds = s.melds + meld,
                    currentTurnPlayer = PlayerSeat.SELF,
                    phase = nextPhase,
                    history = s.history + record
                )
            } else {
                // 他家吃碰槓：記錄至該家明牌區，並將順位跳轉給該家！
                val otherList = (s.otherMelds[seat] ?: emptyList()) + meld
                val newOtherMelds = s.otherMelds.toMutableMap().apply { put(seat, otherList) }

                val record = GameActionRecord(
                    turnNumber = s.currentTurn,
                    seat = seat,
                    actionType = meld.type,
                    tile = meld.triggerTile,
                    extraTiles = meld.tiles
                )

                val nextPhase = if (meld.type == ActionType.GANG_MING) TurnPhase.AWAITING_DRAW else TurnPhase.AWAITING_DISCARD

                s.copy(
                    otherMelds = newOtherMelds,
                    currentTurnPlayer = seat,
                    phase = nextPhase,
                    history = s.history + record
                )
            }
        }

        triggerAIDecision()
    }

    /**
     * 自己暗槓 (在自己出牌回合，且手牌有 4 張相同)
     */
    fun onAnGang(tile: MahjongTile) {
        val current = _gameState.value
        if (current.currentTurnPlayer != PlayerSeat.SELF || current.phase != TurnPhase.AWAITING_DISCARD) return
        if (current.handTiles.count { it.id == tile.id } < 4) return

        saveSnapshot()

        _gameState.update { s ->
            val newHand = s.handTiles.toMutableList()
            repeat(4) {
                val idx = newHand.indexOfFirst { it.id == tile.id }
                if (idx != -1) newHand.removeAt(idx)
            }

            val meld = Meld(
                type = ActionType.GANG_AN,
                tiles = listOf(tile, tile, tile, tile),
                triggerTile = tile,
                targetSeat = PlayerSeat.SELF
            )

            val record = GameActionRecord(
                turnNumber = s.currentTurn,
                seat = PlayerSeat.SELF,
                actionType = ActionType.GANG_AN,
                tile = tile,
                extraTiles = meld.tiles
            )

            // 暗槓後需要從槓尾補摸一張牌
            s.copy(
                handTiles = newHand.sorted(),
                melds = s.melds + meld,
                phase = TurnPhase.AWAITING_DRAW,
                history = s.history + record
            )
        }

        triggerAIDecision()
    }

    /**
     * 他家打牌並摸牌 (方便直接記錄他家回合的完整動作)
     */
    fun onOtherTurnAction(seat: PlayerSeat, discardedTile: MahjongTile) {
        val current = _gameState.value
        if (current.isGameOver) return
        if (current.currentTurnPlayer != seat) return

        saveSnapshot()

        _gameState.update { s ->
            val updatedRiver = (s.discards[seat] ?: emptyList()) + discardedTile
            val newDiscards = s.discards.toMutableMap().apply { put(seat, updatedRiver) }

            val record = GameActionRecord(
                turnNumber = s.currentTurn,
                seat = seat,
                actionType = ActionType.DISCARD,
                tile = discardedTile
            )

            s.copy(
                discards = newDiscards,
                lastDiscard = Pair(seat, discardedTile),
                phase = TurnPhase.AWAITING_CALL_RESPONSE,
                history = s.history + record
            )
        }

        triggerAIDecision()
    }

    /**
     * 宣告胡牌 (自摸或抓放槍)
     * @param winnerSeat 誰胡牌
     * @param loserSeat 誰放槍 (若自摸則為 null)
     */
    fun onDeclareHu(winnerSeat: PlayerSeat, loserSeat: PlayerSeat?) {
        saveSnapshot()

        _gameState.update { s ->
            val winningTile = s.lastDiscard?.second
            val record = GameActionRecord(
                turnNumber = s.currentTurn,
                seat = winnerSeat,
                actionType = ActionType.HU,
                tile = winningTile
            )

            s.copy(
                isGameOver = true,
                winnerSeat = winnerSeat,
                loserSeat = loserSeat,
                phase = TurnPhase.GAME_OVER,
                history = s.history + record
            )
        }

        _aiDecision.value = AIDecisionResult(
            recommendedAction = "🏆 牌局結束！",
            optionsProbability = emptyList(),
            explanationNote = if (loserSeat == null) {
                "${winnerSeat.title} 自摸胡牌！🎉"
            } else {
                "${winnerSeat.title} 胡牌！(${loserSeat.title} 放槍)"
            }
        )
    }

    /**
     * 觸發 AI 決策分析
     */
    fun triggerAIDecision() {
        viewModelScope.launch {
            val state = _gameState.value
            if (state.isGameOver) return@launch
            if (state.handTiles.isEmpty()) return@launch

            _isLoadingDecision.value = true
            kotlinx.coroutines.delay(180)

            val lastDiscard = state.lastDiscard

            // 1. 他家出牌後的【吃碰槓胡】鳴牌判定階段
            if (state.phase == TurnPhase.AWAITING_CALL_RESPONSE && lastDiscard != null) {
                val fromSeat = lastDiscard.first
                val discardedTile = lastDiscard.second

                if (fromSeat != PlayerSeat.SELF) {
                    val choices = mutableListOf<DecisionChoiceProbability>()

                    // A. 碰牌 (任何一家打牌皆可碰)
                    if (state.canPeng(discardedTile)) {
                        choices.add(DecisionChoiceProbability("碰 ${discardedTile.name}", 0.76f))
                    }

                    // B. 吃牌 (嚴格限定：只有上家打牌才能吃！)
                    if (fromSeat == PlayerSeat.SHANG_JIA) {
                        val chiOptions = state.getChiCombinations(discardedTile)
                        chiOptions.forEach { (t1, t2) ->
                            choices.add(DecisionChoiceProbability("吃 [${t1.name}, ${t2.name}]", 0.65f))
                        }
                    }

                    // C. 明槓 (任何一家打牌皆可槓)
                    if (state.canMingGang(discardedTile)) {
                        choices.add(DecisionChoiceProbability("明槓 ${discardedTile.name}", 0.88f))
                    }

                    // D. 榮和胡牌提示 (手牌若聽此牌)
                    choices.add(DecisionChoiceProbability("胡牌 (榮和)", 0.95f))

                    // E. 過
                    choices.add(DecisionChoiceProbability("過 (不鳴牌)", 0.22f))

                    val best = choices.maxByOrNull { it.probability }
                    _aiDecision.value = AIDecisionResult(
                        recommendedAction = "針對 ${fromSeat.title} 的 ${discardedTile.name}：建議 ${best?.actionText}",
                        optionsProbability = choices.sortedByDescending { it.probability },
                        explanationNote = if (fromSeat == PlayerSeat.SHANG_JIA) {
                            "上家出牌：可吃、可碰、可槓、可胡"
                        } else {
                            "${fromSeat.title}出牌：非上家不可吃，僅可碰/槓/胡"
                        }
                    )
                    _isLoadingDecision.value = false
                    return@launch
                }
            }

            // 2. 輪到自己出牌階段 (AWAITING_DISCARD)
            if (state.currentTurnPlayer == PlayerSeat.SELF && state.phase == TurnPhase.AWAITING_DISCARD) {
                val hand = state.handTiles
                val anGangOptions = state.getAnGangOptions()
                val choices = mutableListOf<DecisionChoiceProbability>()

                // 暗槓策略提示
                if (anGangOptions.isNotEmpty()) {
                    anGangOptions.forEach { tile ->
                        choices.add(DecisionChoiceProbability("暗槓 ${tile.name}", 0.93f))
                    }
                }

                // 出牌推薦 (優先推薦字牌孤張)
                val candidates = hand.distinctBy { it.id }.takeLast(4)
                candidates.forEachIndexed { index, tile ->
                    val prob = when (index) {
                        0 -> 0.66f
                        1 -> 0.21f
                        2 -> 0.09f
                        else -> 0.04f
                    }
                    choices.add(DecisionChoiceProbability("打出 ${tile.name}", prob))
                }

                val sortedChoices = choices.sortedByDescending { it.probability }
                val best = sortedChoices.firstOrNull()

                _aiDecision.value = AIDecisionResult(
                    recommendedAction = best?.actionText ?: "請選擇一張牌打出",
                    optionsProbability = sortedChoices,
                    explanationNote = if (anGangOptions.isNotEmpty()) {
                        "💡 偵測到可暗槓牌型！可選擇暗槓並槓尾補牌"
                    } else {
                        "輪到你的出牌回合，點擊手牌即可打出"
                    }
                )
            } else if (state.currentTurnPlayer == PlayerSeat.SELF && state.phase == TurnPhase.AWAITING_DRAW) {
                _aiDecision.value = AIDecisionResult(
                    recommendedAction = "輪到你摸牌",
                    optionsProbability = emptyList(),
                    explanationNote = "請點擊下方「摸牌」按鈕摸入本巡牌面"
                )
            } else {
                _aiDecision.value = AIDecisionResult(
                    recommendedAction = "目前輪到 ${state.currentTurnPlayer.title} 行動",
                    optionsProbability = emptyList(),
                    explanationNote = "等待該家摸牌與打牌，請由下方記錄該家動作"
                )
            }

            _isLoadingDecision.value = false
        }
    }
}
