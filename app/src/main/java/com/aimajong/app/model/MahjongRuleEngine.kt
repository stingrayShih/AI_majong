package com.aimajong.app.model

/**
 * 台灣 16 張麻將正規胡牌判定引擎 (標準 5 面子 + 1 雀頭，共 17 張)
 * 不支援特殊牌型 (如嚦咕嚦咕、七對子)，專注於最嚴謹之常規胡牌與聽牌判定。
 */
object MahjongRuleEngine {

    /**
     * 判定手牌 (含副露與可能的新牌) 是否符合胡牌條件
     *
     * @param handTiles 玩家手中的暗牌
     * @param melds 玩家已副露的牌組 (吃、碰、槓皆計為 1 組已完成面子)
     * @param extraTile 額外加入驗證的牌 (例如他人打出的放槍牌，若為自摸已在 handTiles 內則傳 null)
     * @return true 代表符合標準 5 面子 + 1 雀頭胡牌型態
     */
    fun canHu(
        handTiles: List<MahjongTile>,
        melds: List<Meld>,
        extraTile: MahjongTile? = null
    ): Boolean {
        val totalTiles = if (extraTile != null) handTiles + extraTile else handTiles

        // 台灣麻將 16 張規則：胡牌時總牌數 (暗牌 + 副露組數 * 3) 必須恰為 17 張
        if (totalTiles.size + (melds.size * 3) != 17) {
            return false
        }

        // 分花色檢查
        val wanTiles = totalTiles.filter { it.suit == TileSuit.WAN }
        val tongTiles = totalTiles.filter { it.suit == TileSuit.TONG }
        val tiaoTiles = totalTiles.filter { it.suit == TileSuit.TIAO }
        val honorTiles = totalTiles.filter { it.suit == TileSuit.HONOR }

        val suits = listOf(wanTiles, tongTiles, tiaoTiles, honorTiles)

        // 每個花色總張數除以 3 的餘數，必須「恰有一個花色餘 2」(包含雀頭)，其他花色餘 0 (純面子)
        val remainder2Count = suits.count { it.size % 3 == 2 }
        val remainderInvalid = suits.any { it.size % 3 != 0 && it.size % 3 != 2 }

        if (remainder2Count != 1 || remainderInvalid) {
            return false
        }

        // 萬子檢查
        if (!checkSuit(wanTiles, isNumberSuit = true)) return false

        // 筒子檢查
        if (!checkSuit(tongTiles, isNumberSuit = true)) return false

        // 條子檢查
        if (!checkSuit(tiaoTiles, isNumberSuit = true)) return false

        // 字牌檢查 (東南西北中發白不可連號順子，只能刻子或將眼)
        if (!checkSuit(honorTiles, isNumberSuit = false)) return false

        return true
    }

    /**
     * 取得當前手牌聽哪些牌 (Tenpai)
     * 當前手牌加上副露等效張數必須為 16 張。
     * 遍歷所有 34 種合法牌，若加入後 canHu 成立即為聽牌。
     */
    fun getWaitingTiles(
        handTiles: List<MahjongTile>,
        melds: List<Meld>
    ): List<MahjongTile> {
        if (handTiles.size + (melds.size * 3) != 16) {
            return emptyList()
        }

        val allDistinctTiles = MahjongTile.getAllTiles()
        val waitingList = mutableListOf<MahjongTile>()

        for (candidate in allDistinctTiles) {
            if (canHu(handTiles, melds, candidate)) {
                waitingList.add(candidate)
            }
        }

        return waitingList
    }

    /**
     * 檢查單一花色牌組是否合法
     */
    private fun checkSuit(tiles: List<MahjongTile>, isNumberSuit: Boolean): Boolean {
        if (tiles.isEmpty()) return true

        val remainder = tiles.size % 3
        if (remainder != 0 && remainder != 2) return false

        val hasPair = remainder == 2

        return if (isNumberSuit) {
            val counts = IntArray(9)
            tiles.forEach { tile ->
                if (tile.rank in 1..9) {
                    counts[tile.rank - 1]++
                }
            }
            if (hasPair) {
                canNumberMelendsWithPair(counts)
            } else {
                canNumberMeldsOnly(counts)
            }
        } else {
            // 字牌：rank 1..7 (東南西北中發白)
            val counts = IntArray(7)
            tiles.forEach { tile ->
                if (tile.rank in 1..7) {
                    counts[tile.rank - 1]++
                }
            }
            if (hasPair) {
                canHonorWithPair(counts)
            } else {
                canHonorOnly(counts)
            }
        }
    }

    /**
     * 字牌面子檢驗：只能是刻子 (張數必須全為 0 或 3)
     */
    private fun canHonorOnly(counts: IntArray): Boolean {
        for (c in counts) {
            if (c != 0 && c != 3) return false
        }
        return true
    }

    /**
     * 字牌帶雀頭檢驗：恰有一張牌為 2 張 (將眼)，其餘全為 0 或 3
     */
    private fun canHonorWithPair(counts: IntArray): Boolean {
        var pairFound = false
        for (c in counts) {
            when (c) {
                0, 3 -> Unit
                2 -> {
                    if (pairFound) return false
                    pairFound = true
                }
                else -> return false
            }
        }
        return pairFound
    }

    /**
     * 數牌帶雀頭檢驗：枚舉哪一張牌作為將眼 (2 張)，剩餘牌必須能全數組成面子 (順子或刻子)
     */
    private fun canNumberMelendsWithPair(counts: IntArray): Boolean {
        for (i in counts.indices) {
            if (counts[i] >= 2) {
                counts[i] -= 2
                val ok = canNumberMeldsOnly(counts)
                counts[i] += 2
                if (ok) return true
            }
        }
        return false
    }

    /**
     * 數牌純面子檢驗：遞迴回溯消除刻子 (AAA) 與順子 (ABC)
     */
    private fun canNumberMeldsOnly(counts: IntArray): Boolean {
        var first = -1
        for (i in counts.indices) {
            if (counts[i] > 0) {
                first = i
                break
            }
        }
        // 所有牌均已成功組裝消除
        if (first == -1) return true

        // 選擇 1: 組成刻子 (AAA)
        if (counts[first] >= 3) {
            counts[first] -= 3
            if (canNumberMeldsOnly(counts)) {
                counts[first] += 3
                return true
            }
            counts[first] += 3
        }

        // 選擇 2: 組成順子 (ABC)
        if (first <= counts.size - 3 && counts[first + 1] > 0 && counts[first + 2] > 0) {
            counts[first]--
            counts[first + 1]--
            counts[first + 2]--
            if (canNumberMeldsOnly(counts)) {
                counts[first]++
                counts[first + 1]++
                counts[first + 2]++
                return true
            }
            counts[first]++
            counts[first + 1]++
            counts[first + 2]++
        }

        return false
    }
}
