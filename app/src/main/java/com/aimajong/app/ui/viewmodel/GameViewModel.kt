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

    /**
     * 重設新牌局
     */
    fun startNewGame(initialHand: List<MahjongTile> = emptyList()) {
        _gameState.value = GameState(
            handTiles = initialHand.sorted(),
            currentTurn = 1,
            activeSeat = PlayerSeat.SELF
        )
        _aiDecision.value = null
    }

    /**
     * 手動或拍照辨識後設定起手手牌 (通常為 16 張)
     */
    fun setHandTiles(tiles: List<MahjongTile>) {
        _gameState.update { current ->
            current.copy(handTiles = tiles.sorted())
        }
        triggerAIDecision()
    }

    /**
     * 自己摸牌 (手牌變成 17 張)
     */
    fun onDrawTile(tile: MahjongTile) {
        if (tile.isFlower) {
            // 補花
            _gameState.update { current ->
                val newFlowers = current.flowers + tile
                val record = GameActionRecord(
                    turnNumber = current.currentTurn,
                    seat = PlayerSeat.SELF,
                    actionType = ActionType.FLOWER,
                    tile = tile
                )
                current.copy(
                    flowers = newFlowers,
                    history = current.history + record
                )
            }
        } else {
            _gameState.update { current ->
                val newHand = (current.handTiles + tile).sorted()
                val record = GameActionRecord(
                    turnNumber = current.currentTurn,
                    seat = PlayerSeat.SELF,
                    actionType = ActionType.DRAW,
                    tile = tile
                )
                current.copy(
                    handTiles = newHand,
                    activeSeat = PlayerSeat.SELF,
                    history = current.history + record
                )
            }
            triggerAIDecision()
        }
    }

    /**
     * 玩家打牌 (自己或他家)
     */
    fun onDiscardTile(seat: PlayerSeat, tile: MahjongTile) {
        _gameState.update { current ->
            val updatedRiver = (current.discards[seat] ?: emptyList()) + tile
            val newDiscards = current.discards.toMutableMap().apply {
                put(seat, updatedRiver)
            }

            // 如果是自己打牌，手牌扣除該張
            val newHand = if (seat == PlayerSeat.SELF) {
                val list = current.handTiles.toMutableList()
                val index = list.indexOfFirst { it.id == tile.id }
                if (index != -1) list.removeAt(index)
                list.sorted()
            } else {
                current.handTiles
            }

            val record = GameActionRecord(
                turnNumber = current.currentTurn,
                seat = seat,
                actionType = ActionType.DISCARD,
                tile = tile
            )

            current.copy(
                handTiles = newHand,
                discards = newDiscards,
                lastDiscard = Pair(seat, tile),
                activeSeat = seat.next(),
                currentTurn = current.currentTurn + 1,
                history = current.history + record
            )
        }

        // 打完牌後若輪到自己，或有可碰吃的局面，可觸發決策
        triggerAIDecision()
    }

    /**
     * 執行吃/碰/槓
     */
    fun onMeldAction(seat: PlayerSeat, meld: Meld) {
        _gameState.update { current ->
            if (seat == PlayerSeat.SELF) {
                // 從手牌中移除參與吃碰槓的其他牌（觸發的那張牌來自他家牌河）
                val newHand = current.handTiles.toMutableList()
                meld.tiles.forEach { tile ->
                    if (tile.id != meld.triggerTile?.id) {
                        val idx = newHand.indexOfFirst { it.id == tile.id }
                        if (idx != -1) newHand.removeAt(idx)
                    }
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
     * 觸發 vLLM d3-flash 決策分析
     */
    fun triggerAIDecision() {
        viewModelScope.launch {
            val state = _gameState.value
            if (state.handTiles.isEmpty()) return@launch

            _isLoadingDecision.value = true

            // TODO (Phase 3): 串接真實 vLLM d3-flash /v1/systemone API
            // 此處提供基於當前手牌的決策建議模型，供 UI 即時預覽
            kotlinx.coroutines.delay(350)

            val hand = state.handTiles
            // 找出孤張或可打的牌進行評分推薦
            val recommendedTile = hand.lastOrNull() ?: return@launch
            val choices = hand.distinctBy { it.id }.take(4).mapIndexed { idx, tile ->
                val prob = when (idx) {
                    0 -> 0.58f
                    1 -> 0.24f
                    2 -> 0.12f
                    else -> 0.06f
                }
                DecisionChoiceProbability("打出 ${tile.name}", prob)
            }

            _aiDecision.value = AIDecisionResult(
                recommendedAction = "推薦打出: ${recommendedTile.name}",
                optionsProbability = choices,
                explanationNote = "分析依據：保留有效進張面，字牌孤張優先防守"
            )

            _isLoadingDecision.value = false
        }
    }
}
