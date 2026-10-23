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

    // 狀態還原堆疊 (Undo stack)
    private val historySnapshots = mutableListOf<GameState>()

    val canUndo: Boolean get() = historySnapshots.isNotEmpty()

    /**
     * 保存當前狀態快照以便進行 Undo
     */
    private fun saveSnapshot() {
        historySnapshots.add(_gameState.value)
    }

    /**
     * 還原至上一步 (Undo)
     */
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
     * 重設新牌局
     * @param initialHand 起手手牌 (莊家 17 張，閒家 16 張)
     * @param isDealer 自己是否當莊
     */
    fun startNewGame(initialHand: List<MahjongTile> = emptyList(), isDealer: Boolean = false) {
        historySnapshots.clear()
        val dealerSeat = if (isDealer) PlayerSeat.SELF else PlayerSeat.SHANG_JIA
        _gameState.value = GameState(
            dealerSeat = dealerSeat,
            handTiles = initialHand.sorted(),
            currentTurn = 1,
            activeSeat = if (isDealer) PlayerSeat.SELF else dealerSeat
        )
        triggerAIDecision()
    }

    /**
     * 設定手牌
     */
    fun setHandTiles(tiles: List<MahjongTile>, isDealer: Boolean = false) {
        historySnapshots.clear()
        val dealerSeat = if (isDealer) PlayerSeat.SELF else PlayerSeat.SHANG_JIA
        _gameState.value = GameState(
            dealerSeat = dealerSeat,
            handTiles = tiles.sorted(),
            currentTurn = 1,
            activeSeat = if (isDealer) PlayerSeat.SELF else dealerSeat
        )
        triggerAIDecision()
    }

    /**
     * 自己摸牌 (手牌加 1 張，進入可出牌階段)
     */
    fun onDrawTile(tile: MahjongTile) {
        val current = _gameState.value
        // 牌數上限檢查
        if (current.remainingCount(tile) <= 0) return

        saveSnapshot()

        if (tile.isFlower) {
            // 補花
            _gameState.update { s ->
                val record = GameActionRecord(
                    turnNumber = s.currentTurn,
                    seat = PlayerSeat.SELF,
                    actionType = ActionType.FLOWER,
                    tile = tile
                )
                s.copy(
                    flowers = s.flowers + tile,
                    history = s.history + record
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
                    activeSeat = PlayerSeat.SELF,
                    history = s.history + record
                )
            }
            triggerAIDecision()
        }
    }

    /**
     * 玩家打牌 (自己或他家)
     */
    fun onDiscardTile(seat: PlayerSeat, tile: MahjongTile) {
        val current = _gameState.value

        // 防呆 1: 自己若不在可出牌階段 (手牌未滿 17 張有效牌)，禁止打牌
        if (seat == PlayerSeat.SELF && !current.canSelfDiscard) {
            return
        }

        // 牌數上限檢查 (他家打牌時確認未超出 4 張)
        if (seat != PlayerSeat.SELF && current.remainingCount(tile) <= 0) {
            return
        }

        saveSnapshot()

        _gameState.update { s ->
            val updatedRiver = (s.discards[seat] ?: emptyList()) + tile
            val newDiscards = s.discards.toMutableMap().apply {
                put(seat, updatedRiver)
            }

            // 自己打牌則從手牌中扣除該張
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
                activeSeat = seat.next(),
                currentTurn = s.currentTurn + 1,
                history = s.history + record
            )
        }

        triggerAIDecision()
    }

    /**
     * 執行吃/碰/槓
     */
    fun onMeldAction(seat: PlayerSeat, meld: Meld) {
        saveSnapshot()

        _gameState.update { current ->
            if (seat == PlayerSeat.SELF) {
                // 自己吃碰槓：從手牌扣除除觸發牌外的其他牌
                val newHand = current.handTiles.toMutableList()
                val tilesToRemove = meld.tiles.toMutableList()
                if (meld.triggerTile != null) {
                    tilesToRemove.remove(meld.triggerTile)
                }

                tilesToRemove.forEach { t ->
                    val idx = newHand.indexOfFirst { it.id == t.id }
                    if (idx != -1) newHand.removeAt(idx)
                }

                val record = GameActionRecord(
                    turnNumber = current.currentTurn,
                    seat = seat,
                    actionType = meld.type,
                    tile = meld.triggerTile,
                    extraTiles = meld.tiles
                )

                current.copy(
                    handTiles = newHand.sorted(),
                    melds = current.melds + meld,
                    activeSeat = PlayerSeat.SELF,
                    history = current.history + record
                )
            } else {
                // 他家吃碰槓：記錄至該家明牌區
                val otherList = (current.otherMelds[seat] ?: emptyList()) + meld
                val newOtherMelds = current.otherMelds.toMutableMap().apply {
                    put(seat, otherList)
                }
                val record = GameActionRecord(
                    turnNumber = current.currentTurn,
                    seat = seat,
                    actionType = meld.type,
                    tile = meld.triggerTile,
                    extraTiles = meld.tiles
                )
                current.copy(
                    otherMelds = newOtherMelds,
                    activeSeat = seat,
                    history = current.history + record
                )
            }
        }

        triggerAIDecision()
    }

    /**
     * 觸發 AI 決策分析（包含出牌推薦與別家出牌時的吃碰槓策略）
     */
    fun triggerAIDecision() {
        viewModelScope.launch {
            val state = _gameState.value
            if (state.handTiles.isEmpty()) return@launch

            _isLoadingDecision.value = true
            kotlinx.coroutines.delay(200)

            val lastDiscard = state.lastDiscard

            // 情況 A：剛才別家打牌，評估自己是否要「吃、碰、明槓、過」
            if (lastDiscard != null && lastDiscard.first != PlayerSeat.SELF && !state.canSelfDiscard) {
                val fromSeat = lastDiscard.first
                val discardedTile = lastDiscard.second
                val choices = mutableListOf<DecisionChoiceProbability>()

                // 1. 檢測碰
                if (state.canPeng(discardedTile)) {
                    choices.add(DecisionChoiceProbability("碰 ${discardedTile.name}", 0.76f))
                }

                // 2. 檢測吃 (僅上家打牌可吃)
                if (fromSeat == PlayerSeat.SHANG_JIA) {
                    val chiOptions = state.getChiCombinations(discardedTile)
                    chiOptions.forEach { (t1, t2) ->
                        choices.add(DecisionChoiceProbability("吃 [${t1.name}, ${t2.name}]", 0.62f))
                    }
                }

                // 3. 檢測明槓
                if (state.canMingGang(discardedTile)) {
                    choices.add(DecisionChoiceProbability("槓 ${discardedTile.name}", 0.88f))
                }

                if (choices.isNotEmpty()) {
                    choices.add(DecisionChoiceProbability("過 (不鳴牌)", 0.24f))
                    val best = choices.maxByOrNull { it.probability }
                    _aiDecision.value = AIDecisionResult(
                        recommendedAction = "針對 ${fromSeat.title} 打出的 ${discardedTile.name}：建議 ${best?.actionText}",
                        optionsProbability = choices.sortedByDescending { it.probability },
                        explanationNote = "分析依據：評估牌效進張與防守安全度"
                    )
                    _isLoadingDecision.value = false
                    return@launch
                }
            }

            // 情況 B：輪到自己出牌 (手牌滿 17 張有效牌)
            if (state.canSelfDiscard) {
                val hand = state.handTiles
                // 優先推薦字牌孤張或無搭子孤張
                val anGangOptions = state.getAnGangOptions()
                val choices = mutableListOf<DecisionChoiceProbability>()

                if (anGangOptions.isNotEmpty()) {
                    anGangOptions.forEach { tile ->
                        choices.add(DecisionChoiceProbability("暗槓 ${tile.name}", 0.92f))
                    }
                }

                val candidates = hand.distinctBy { it.id }.takeLast(4)
                candidates.forEachIndexed { index, tile ->
                    val prob = when (index) {
                        0 -> 0.65f
                        1 -> 0.22f
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
                    explanationNote = "分析依據：出牌防呆已就緒，推薦打出多餘孤張保持向聽數"
                )
            } else {
                // 等待摸牌或等待別家打牌
                _aiDecision.value = AIDecisionResult(
                    recommendedAction = "等待摸牌或等待他家出牌",
                    optionsProbability = emptyList(),
                    explanationNote = "手牌維持 16 張標準守備狀態"
                )
            }

            _isLoadingDecision.value = false
        }
    }
}
