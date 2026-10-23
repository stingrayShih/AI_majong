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
     */
    fun startNewGame(initialHand: List<MahjongTile> = emptyList(), dealerSeat: PlayerSeat = PlayerSeat.SELF) {
        historySnapshots.clear()
        val cleanHand = initialHand.filter { !it.isFlower }
        val initialFlowers = initialHand.filter { it.isFlower }

        _gameState.value = GameState(
            dealerSeat = dealerSeat,
            handTiles = cleanHand.sorted(),
            flowers = initialFlowers,
            currentTurn = 1,
            currentTurnPlayer = dealerSeat,
            phase = TurnPhase.AWAITING_DISCARD,
            justMeldedThisTurn = false
        )
        triggerAIDecision()
    }

    fun setDealer(seat: PlayerSeat) {
        saveSnapshot()
        _gameState.update { s ->
            s.copy(dealerSeat = seat)
        }
        triggerAIDecision()
    }

    /**
     * 自己摸牌 (手牌加 1 張；摸到花牌則自動補花，不進手牌)
     */
    fun onDrawTile(tile: MahjongTile) {
        val current = _gameState.value
        if (current.isGameOver) return
        if (current.currentTurnPlayer != PlayerSeat.SELF || current.phase != TurnPhase.AWAITING_DRAW) return
        if (current.remainingCount(tile) <= 0) return

        saveSnapshot()

        if (tile.isFlower) {
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
                    justMeldedThisTurn = false, // 正常摸牌，可胡可打
                    history = s.history + record
                )
            }
            triggerAIDecision()
        }
    }

    /**
     * 玩家打牌 (打牌後重設副露標記)
     */
    fun onDiscardTile(seat: PlayerSeat, tile: MahjongTile) {
        val current = _gameState.value
        if (current.isGameOver) return
        if (tile.isFlower) return
        if (current.currentTurnPlayer != seat || current.phase != TurnPhase.AWAITING_DISCARD) return
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

            s.copy(
                handTiles = newHand,
                discards = newDiscards,
                lastDiscard = Pair(seat, tile),
                phase = TurnPhase.AWAITING_CALL_RESPONSE,
                justMeldedThisTurn = false, // 打出牌後重設
                history = s.history + record
            )
        }

        triggerAIDecision()
    }

    /**
     * 鳴牌判定通過
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
                currentTurn = s.currentTurn + 1,
                justMeldedThisTurn = false
            )
        }

        triggerAIDecision()
    }

    /**
     * 執行吃/碰/明槓（標記【當回合剛鳴牌，該玩家此時嚴格禁止宣告胡牌】）
     */
    fun onMeldAction(seat: PlayerSeat, meld: Meld) {
        val current = _gameState.value
        if (current.phase != TurnPhase.AWAITING_CALL_RESPONSE) return
        if (meld.triggerTile?.isFlower == true) return
        if (current.lastDiscard?.first == seat) return

        saveSnapshot()

        _gameState.update { s ->
            if (seat == PlayerSeat.SELF) {
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

                val nextPhase = if (meld.type == ActionType.GANG_MING) TurnPhase.AWAITING_DRAW else TurnPhase.AWAITING_DISCARD

                s.copy(
                    handTiles = newHand.sorted(),
                    melds = s.melds + meld,
                    currentTurnPlayer = PlayerSeat.SELF,
                    phase = nextPhase,
                    justMeldedThisTurn = true, // 剛吃碰槓，當回合不能胡牌！
                    history = s.history + record
                )
            } else {
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
                    justMeldedThisTurn = true, // 剛吃碰槓，當回合不能胡牌！
                    history = s.history + record
                )
            }
        }

        triggerAIDecision()
    }

    /**
     * 自己暗槓
     */
    fun onAnGang(tile: MahjongTile) {
        val current = _gameState.value
        if (tile.isFlower) return
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

            s.copy(
                handTiles = newHand.sorted(),
                melds = s.melds + meld,
                phase = TurnPhase.AWAITING_DRAW,
                justMeldedThisTurn = true,
                history = s.history + record
            )
        }

        triggerAIDecision()
    }

    /**
     * 他家打牌
     */
    fun onOtherTurnAction(seat: PlayerSeat, discardedTile: MahjongTile) {
        val current = _gameState.value
        if (current.isGameOver) return
        if (discardedTile.isFlower) return
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
                justMeldedThisTurn = false,
                history = s.history + record
            )
        }

        triggerAIDecision()
    }

    /**
     * 宣告胡牌 (規則防呆：吃碰槓的那一家當回合不能胡牌)
     */
    fun onDeclareHu(winnerSeat: PlayerSeat, loserSeat: PlayerSeat?): Boolean {
        val current = _gameState.value
        if (current.isGameOver) return false

        // 規則校驗：如果胡牌者是當前回合玩家，且剛吃碰槓副露過，嚴格禁止胡牌！
        if (winnerSeat == current.currentTurnPlayer && current.justMeldedThisTurn) {
            return false
        }

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
        return true
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

                if (fromSeat == PlayerSeat.SELF) {
                    _aiDecision.value = AIDecisionResult(
                        recommendedAction = "你已打出 [${discardedTile.name}]",
                        optionsProbability = emptyList(),
                        explanationNote = "等待他家是否有吃碰槓反應；若他家皆未鳴牌，請點擊下方淺色【過】換下家摸牌"
                    )
                    _isLoadingDecision.value = false
                    return@launch
                }

                val choices = mutableListOf<DecisionChoiceProbability>()

                if (!discardedTile.isFlower) {
                    if (state.canPeng(discardedTile)) {
                        choices.add(DecisionChoiceProbability("碰 ${discardedTile.name}", 0.76f))
                    }

                    if (fromSeat == PlayerSeat.SHANG_JIA) {
                        val chiOptions = state.getChiCombinations(discardedTile)
                        chiOptions.forEach { (t1, t2) ->
                            choices.add(DecisionChoiceProbability("吃 [${t1.name}, ${t2.name}]", 0.65f))
                        }
                    }

                    if (state.canMingGang(discardedTile)) {
                        choices.add(DecisionChoiceProbability("明槓 ${discardedTile.name}", 0.88f))
                    }
                }

                // 抓放槍榮和
                choices.add(DecisionChoiceProbability("抓放槍 (榮和)", 0.95f))
                choices.add(DecisionChoiceProbability("過 (不鳴牌)", 0.22f))

                val best = choices.maxByOrNull { it.probability }
                _aiDecision.value = AIDecisionResult(
                    recommendedAction = "針對 ${fromSeat.title} 的 ${discardedTile.name}：建議 ${best?.actionText}",
                    optionsProbability = choices.sortedByDescending { it.probability },
                    explanationNote = if (fromSeat == PlayerSeat.SHANG_JIA) {
                        "上家出牌：可吃、可碰、可槓、可抓放槍"
                    } else {
                        "${fromSeat.title}出牌：非上家不可吃，僅可碰/槓/抓放槍"
                    }
                )
                _isLoadingDecision.value = false
                return@launch
            }

            // 2. 輪到自己出牌階段 (AWAITING_DISCARD)
            if (state.currentTurnPlayer == PlayerSeat.SELF && state.phase == TurnPhase.AWAITING_DISCARD) {
                val hand = state.handTiles
                val anGangOptions = state.getAnGangOptions()
                val choices = mutableListOf<DecisionChoiceProbability>()

                if (anGangOptions.isNotEmpty()) {
                    anGangOptions.forEach { tile ->
                        choices.add(DecisionChoiceProbability("暗槓 ${tile.name}", 0.93f))
                    }
                }

                val candidates = hand.filter { !it.isFlower }.distinctBy { it.id }.takeLast(4)
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
                    explanationNote = when {
                        state.justMeldedThisTurn -> "剛剛完成吃/碰/槓副露，當回合必須打出一張牌，嚴格禁止胡牌！"
                        anGangOptions.isNotEmpty() -> "💡 偵測到可暗槓牌型！可選擇暗槓並槓尾補牌"
                        else -> "輪到你的出牌回合，點擊手牌即可打出"
                    }
                )
            } else if (state.currentTurnPlayer == PlayerSeat.SELF && state.phase == TurnPhase.AWAITING_DRAW) {
                _aiDecision.value = AIDecisionResult(
                    recommendedAction = "輪到你摸牌",
                    optionsProbability = emptyList(),
                    explanationNote = "請點擊下方「摸牌」按鈕摸入本巡牌面 (若摸到花牌將自動補花)"
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
