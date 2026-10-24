package com.aimajong.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aimajong.app.data.SettingsRepository
import com.aimajong.app.model.*
import com.aimajong.app.network.VllmApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    val settingsRepo = SettingsRepository(application)
    val vllmApiClient = VllmApiClient()

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

        _gameState.value = GameState(
            dealerSeat = dealerSeat,
            handTiles = initialHand.sorted(),
            flowers = emptyList(),
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
     * 自己摸牌
     */
    fun onDrawTile(tile: MahjongTile) {
        val current = _gameState.value
        if (current.isGameOver) return
        if (current.currentTurnPlayer != PlayerSeat.SELF || current.phase != TurnPhase.AWAITING_DRAW) return
        if (current.remainingCount(tile) <= 0) return

        saveSnapshot()

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
                justMeldedThisTurn = false,
                history = s.history + record
            )
        }
        triggerAIDecision()
    }

    /**
     * 玩家打牌
     */
    fun onDiscardTile(seat: PlayerSeat, tile: MahjongTile) {
        val current = _gameState.value
        if (current.isGameOver) return
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
                justMeldedThisTurn = false,
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
     * 執行吃/碰/明槓
     */
    fun onMeldAction(seat: PlayerSeat, meld: Meld) {
        val current = _gameState.value
        if (current.phase != TurnPhase.AWAITING_CALL_RESPONSE) return
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
                    justMeldedThisTurn = true,
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
                    justMeldedThisTurn = true,
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
     * 宣告胡牌
     */
    fun onDeclareHu(winnerSeat: PlayerSeat, loserSeat: PlayerSeat?): Boolean {
        val current = _gameState.value
        if (current.isGameOver) return false

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
     * 觸發 AI 決策分析（真串接 vLLM d3-flash API，失敗時自動容錯回退本地引擎）
     */
    fun triggerAIDecision() {
        viewModelScope.launch {
            val state = _gameState.value
            if (state.isGameOver) return@launch
            if (state.handTiles.isEmpty()) return@launch

            _isLoadingDecision.value = true

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

                val candidateChoices = mutableListOf<String>()

                if (state.canPeng(discardedTile)) {
                    candidateChoices.add("碰 ${discardedTile.name}")
                }

                if (fromSeat == PlayerSeat.SHANG_JIA) {
                    val chiOptions = state.getChiCombinations(discardedTile)
                    chiOptions.forEach { (t1, t2) ->
                        candidateChoices.add("吃 [${t1.name}, ${t2.name}]")
                    }
                }

                if (state.canMingGang(discardedTile)) {
                    candidateChoices.add("明槓 ${discardedTile.name}")
                }

                candidateChoices.add("抓放槍 (榮和)")
                candidateChoices.add("過 (不鳴牌)")

                // 嘗試呼叫真實 vLLM d3-flash API
                val apiResult = vllmApiClient.requestDecision(
                    baseUrl = settingsRepo.serverBaseUrl,
                    apiKey = settingsRepo.apiKey,
                    modelName = settingsRepo.modelName,
                    state = state,
                    candidateChoices = candidateChoices
                )

                if (apiResult.isSuccess) {
                    val res = apiResult.getOrNull()!!
                    _aiDecision.value = res.copy(
                        explanationNote = "vLLM ${settingsRepo.modelName} (27B) 決策推論"
                    )
                } else if (settingsRepo.useMockFallback) {
                    // 本地智能備援
                    val mockChoices = candidateChoices.mapIndexed { idx, opt ->
                        val prob = when {
                            opt.startsWith("抓放槍") -> 0.95f
                            opt.startsWith("碰") -> 0.76f
                            opt.startsWith("吃") -> 0.65f
                            opt.startsWith("明槓") -> 0.88f
                            else -> 0.22f
                        }
                        DecisionChoiceProbability(opt, prob)
                    }.sortedByDescending { it.probability }

                    _aiDecision.value = AIDecisionResult(
                        recommendedAction = "建議 ${mockChoices.first().actionText}",
                        optionsProbability = mockChoices,
                        explanationNote = "（本地引擎備援，vLLM 未連線）"
                    )
                } else {
                    _aiDecision.value = AIDecisionResult(
                        recommendedAction = "vLLM 連線失敗",
                        optionsProbability = emptyList(),
                        explanationNote = apiResult.exceptionOrNull()?.message ?: "連線錯誤"
                    )
                }

                _isLoadingDecision.value = false
                return@launch
            }

            // 2. 輪到自己出牌階段 (AWAITING_DISCARD)
            if (state.currentTurnPlayer == PlayerSeat.SELF && state.phase == TurnPhase.AWAITING_DISCARD) {
                val hand = state.handTiles
                val anGangOptions = state.getAnGangOptions()
                val candidateChoices = mutableListOf<String>()

                if (anGangOptions.isNotEmpty()) {
                    anGangOptions.forEach { tile ->
                        candidateChoices.add("暗槓 ${tile.name}")
                    }
                }

                // 手牌中所有牌型皆為可打出的候選選項
                val candidates = hand.distinctBy { it.id }
                candidates.forEach { tile ->
                    candidateChoices.add("打出 ${tile.name}")
                }

                val apiResult = vllmApiClient.requestDecision(
                    baseUrl = settingsRepo.serverBaseUrl,
                    apiKey = settingsRepo.apiKey,
                    modelName = settingsRepo.modelName,
                    state = state,
                    candidateChoices = candidateChoices
                )

                if (apiResult.isSuccess) {
                    val res = apiResult.getOrNull()!!
                    _aiDecision.value = res.copy(
                        explanationNote = "vLLM ${settingsRepo.modelName} 決策推論"
                    )
                } else if (settingsRepo.useMockFallback) {
                    val mockChoices = candidateChoices.mapIndexed { index, opt ->
                        val prob = if (opt.startsWith("暗槓")) 0.93f
                        else when (index) {
                            0 -> 0.45f
                            1 -> 0.25f
                            2 -> 0.15f
                            else -> 0.15f / (candidateChoices.size - 3).coerceAtLeast(1)
                        }
                        DecisionChoiceProbability(opt, prob)
                    }.sortedByDescending { it.probability }

                    _aiDecision.value = AIDecisionResult(
                        recommendedAction = mockChoices.first().actionText,
                        optionsProbability = mockChoices,
                        explanationNote = if (anGangOptions.isNotEmpty()) {
                            "💡 偵測到可暗槓牌型！可選擇暗槓並槓尾補牌"
                        } else {
                            "（本地引擎備援，vLLM 未連線）"
                        }
                    )
                } else {
                    _aiDecision.value = AIDecisionResult(
                        recommendedAction = "vLLM 連線失敗",
                        optionsProbability = emptyList(),
                        explanationNote = apiResult.exceptionOrNull()?.message ?: "連線錯誤"
                    )
                }
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
