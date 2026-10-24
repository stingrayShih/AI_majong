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

data class HuPromptData(
    val tile: MahjongTile,
    val seat: PlayerSeat,
    val isQiangGang: Boolean = false
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    val settingsRepo = SettingsRepository(application)
    val vllmApiClient = VllmApiClient()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _aiDecision = MutableStateFlow<AIDecisionResult?>(null)
    val aiDecision: StateFlow<AIDecisionResult?> = _aiDecision.asStateFlow()

    private val _isLoadingDecision = MutableStateFlow(false)
    val isLoadingDecision: StateFlow<Boolean> = _isLoadingDecision.asStateFlow()

    private val _showSelfHuPrompt = MutableStateFlow<HuPromptData?>(null)
    val showSelfHuPrompt: StateFlow<HuPromptData?> = _showSelfHuPrompt.asStateFlow()

    private val _pendingQiangGang = MutableStateFlow<Pair<PlayerSeat, Meld>?>(null)

    private val historySnapshots = mutableListOf<GameState>()

    val canUndo: Boolean get() = historySnapshots.isNotEmpty()

    private fun saveSnapshot() {
        historySnapshots.add(_gameState.value)
    }

    fun undoLastAction(): Boolean {
        if (historySnapshots.isNotEmpty()) {
            _showSelfHuPrompt.value = null
            _pendingQiangGang.value = null
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

        _showSelfHuPrompt.value = null
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

        _showSelfHuPrompt.value = null
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
                triggerTile = null,
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
     * 自己補槓 (碰牌後摸到第 4 張牌進行明槓/加槓)
     */
    fun onBuGang(tile: MahjongTile) {
        val current = _gameState.value
        if (current.isGameOver) return
        if (current.currentTurnPlayer != PlayerSeat.SELF || current.phase != TurnPhase.AWAITING_DISCARD) return
        val matchingMeld = current.melds.find { it.type == ActionType.PENG && it.tiles.firstOrNull()?.id == tile.id } ?: return
        if (!current.handTiles.any { it.id == tile.id }) return

        saveSnapshot()

        _gameState.update { s ->
            val newHand = s.handTiles.toMutableList()
            val idx = newHand.indexOfFirst { it.id == tile.id }
            if (idx != -1) newHand.removeAt(idx)

            val updatedMelds = s.melds.map { m ->
                if (m === matchingMeld) {
                    m.copy(
                        type = ActionType.GANG_BU,
                        tiles = m.tiles + tile
                    )
                } else {
                    m
                }
            }

            val record = GameActionRecord(
                turnNumber = s.currentTurn,
                seat = PlayerSeat.SELF,
                actionType = ActionType.GANG_BU,
                tile = tile,
                extraTiles = listOf(tile)
            )

            s.copy(
                handTiles = newHand.sorted(),
                melds = updatedMelds,
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
     * 他家宣告補槓 (加槓/明槓)
     */
    fun onOtherBuGang(seat: PlayerSeat, meld: Meld) {
        val current = _gameState.value
        if (current.isGameOver) return
        if (current.currentTurnPlayer != seat) return

        val buGangTile = meld.tiles.firstOrNull() ?: meld.triggerTile ?: return

        // 搶槓判定：自己手牌是否能搶這張補槓牌胡牌
        if (current.canSelfQiangGang(buGangTile)) {
            _pendingQiangGang.value = Pair(seat, meld)
            _showSelfHuPrompt.value = HuPromptData(
                tile = buGangTile,
                seat = seat,
                isQiangGang = true
            )
            _aiDecision.value = AIDecisionResult(
                recommendedAction = "🏆 可以搶槓胡牌！",
                optionsProbability = listOf(DecisionChoiceProbability("搶槓胡牌", 1.0f)),
                explanationNote = "【${seat.title}】宣告補槓 [${buGangTile.name}]，手牌已達成搶槓胡牌條件！"
            )
            return
        }

        // 若無法搶槓，直接執行補槓
        executeOtherBuGang(seat, meld)
    }

    private fun executeOtherBuGang(seat: PlayerSeat, meld: Meld) {
        saveSnapshot()
        _gameState.update { s ->
            val list = s.otherMelds[seat] ?: emptyList()
            val buGangTile = meld.tiles.firstOrNull() ?: meld.triggerTile ?: MahjongTile.findById("wan_1")!!
            val updatedList = list.map { m ->
                if (m === meld) {
                    m.copy(
                        type = ActionType.GANG_BU,
                        tiles = m.tiles + buGangTile
                    )
                } else {
                    m
                }
            }
            val newOtherMelds = s.otherMelds.toMutableMap().apply { put(seat, updatedList) }

            val record = GameActionRecord(
                turnNumber = s.currentTurn,
                seat = seat,
                actionType = ActionType.GANG_BU,
                tile = buGangTile,
                extraTiles = listOf(buGangTile)
            )

            s.copy(
                otherMelds = newOtherMelds,
                currentTurnPlayer = seat,
                phase = TurnPhase.AWAITING_DRAW, // 槓尾補牌
                justMeldedThisTurn = true,
                history = s.history + record
            )
        }
        triggerAIDecision()
    }

    /**
     * 使用者主動選擇「否，不胡 (過水 / 考慮其他動作 / 放棄搶槓)」
     */
    fun onDeclineSelfHu() {
        val pending = _pendingQiangGang.value
        _showSelfHuPrompt.value = null
        _pendingQiangGang.value = null

        if (pending != null) {
            executeOtherBuGang(pending.first, pending.second)
        } else {
            proceedWithCallResponseDecision(isSelfHuDeclined = true)
        }
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

        val pending = _pendingQiangGang.value

        // 若是自己宣告胡牌，嚴格驗證是否符合正規 5 面子 + 1 雀頭胡牌牌型
        if (winnerSeat == PlayerSeat.SELF) {
            val isHuLegal = if (pending != null && loserSeat == pending.first) {
                // 搶槓胡牌
                val buGangTile = pending.second.tiles.firstOrNull() ?: pending.second.triggerTile ?: return false
                current.canSelfQiangGang(buGangTile)
            } else if (loserSeat == null) {
                // 自摸胡牌
                current.canSelfHuOnDraw()
            } else {
                // 抓放槍胡牌
                val tile = current.lastDiscard?.second ?: return false
                current.canSelfHuOnDiscard(tile)
            }
            if (!isHuLegal) {
                return false
            }
        }

        _showSelfHuPrompt.value = null
        _pendingQiangGang.value = null
        saveSnapshot()

        _gameState.update { s ->
            val winningTile = pending?.second?.tiles?.firstOrNull() ?: s.lastDiscard?.second
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
            } else if (pending != null) {
                "${winnerSeat.title} 搶槓胡牌！(${loserSeat.title} 補槓被搶)"
            } else {
                "${winnerSeat.title} 胡牌！(${loserSeat.title} 放槍)"
            }
        )
        return true
    }

    /**
     * 處理他家打牌後的鳴牌決策流程
     * @param isSelfHuDeclined 若使用者方才在「是否胡牌」對話框點選「否」，則不再提示或詢問胡牌
     */
    private fun proceedWithCallResponseDecision(isSelfHuDeclined: Boolean = false) {
        viewModelScope.launch {
            val state = _gameState.value
            if (state.isGameOver) return@launch
            if (state.handTiles.isEmpty()) return@launch

            val lastDiscard = state.lastDiscard ?: return@launch
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

            // 1. 若自己可以胡牌，且尚未被使用者主動點選「否」：
            // 直接跳出確認對話框，AI 決策直接建議抓放槍，絕不呼叫 AI 模型詢問！
            if (!isSelfHuDeclined && state.canSelfHuOnDiscard(discardedTile)) {
                _showSelfHuPrompt.value = HuPromptData(
                    tile = discardedTile,
                    seat = fromSeat,
                    isQiangGang = false
                )
                _aiDecision.value = AIDecisionResult(
                    recommendedAction = "🏆 可以胡牌！建議抓放槍 (榮和)",
                    optionsProbability = listOf(DecisionChoiceProbability("抓放槍 (榮和)", 1.0f)),
                    explanationNote = "手牌已達標準 5 面子 + 1 雀頭胡牌型態，放槍牌為 [${discardedTile.name}]！"
                )
                _isLoadingDecision.value = false
                return@launch
            }

            // 2. 自己不能胡牌，或使用者剛剛點選了「否 (不胡)」：
            // 候選決策清單中「絕不包含胡牌」！
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

            // 3. 如果沒有任何吃、碰、槓動作可以做：
            // 完全不用問 AI 模型，直接建議「過」！
            if (candidateChoices.isEmpty()) {
                _aiDecision.value = AIDecisionResult(
                    recommendedAction = "建議 過",
                    optionsProbability = listOf(DecisionChoiceProbability("過 (不鳴牌)", 1.0f)),
                    explanationNote = "手牌未達吃/碰/槓條件，換下一家摸牌"
                )
                _isLoadingDecision.value = false
                return@launch
            }

            // 4. 有別的動作可以做 (吃/碰/槓)：加入「過」選項後詢問 AI 模型！
            candidateChoices.add("過 (不鳴牌)")
            _isLoadingDecision.value = true

            // 嘗試呼叫真實 vLLM API
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
        }
    }

    /**
     * 觸發 AI 決策分析（真串接 vLLM d3-flash API，失敗時自動容錯回退本地引擎）
     */
    fun triggerAIDecision() {
        viewModelScope.launch {
            val state = _gameState.value
            if (state.isGameOver) return@launch
            if (state.handTiles.isEmpty()) return@launch

            // 1. 他家出牌後的【吃碰槓胡】鳴牌判定階段
            if (state.phase == TurnPhase.AWAITING_CALL_RESPONSE && state.lastDiscard != null) {
                proceedWithCallResponseDecision(isSelfHuDeclined = false)
                return@launch
            }

            _isLoadingDecision.value = true

            // 2. 輪到自己出牌階段 (AWAITING_DISCARD)
            if (state.currentTurnPlayer == PlayerSeat.SELF && state.phase == TurnPhase.AWAITING_DISCARD) {
                // 檢查是否自摸！若已自摸直接推薦自摸胡牌，無須詢問 AI 打哪張牌
                if (state.canSelfHuOnDraw()) {
                    _aiDecision.value = AIDecisionResult(
                        recommendedAction = "🏆 自摸胡牌！",
                        optionsProbability = listOf(DecisionChoiceProbability("自摸胡牌", 1.0f)),
                        explanationNote = "手牌已達標準 5 面子 + 1 雀頭自摸胡牌型態！可直接點擊下方【自摸胡牌】"
                    )
                    _isLoadingDecision.value = false
                    return@launch
                }

                val hand = state.handTiles
                val anGangOptions = state.getAnGangOptions()
                val buGangOptions = state.getBuGangOptions()
                val candidateChoices = mutableListOf<String>()

                if (anGangOptions.isNotEmpty()) {
                    anGangOptions.forEach { tile ->
                        candidateChoices.add("暗槓 ${tile.name}")
                    }
                }

                if (buGangOptions.isNotEmpty()) {
                    buGangOptions.forEach { tile ->
                        candidateChoices.add("補槓 ${tile.name}")
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
                        val prob = if (opt.startsWith("暗槓") || opt.startsWith("補槓")) 0.93f
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
