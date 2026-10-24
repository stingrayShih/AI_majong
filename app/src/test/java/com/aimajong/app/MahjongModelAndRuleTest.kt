package com.aimajong.app

import com.aimajong.app.model.*
import com.aimajong.app.network.VllmApiClient
import org.junit.Assert.*
import org.junit.Test

class MahjongModelAndRuleTest {

    @Test
    fun test34TilesPurity() {
        val allTiles = MahjongTile.getAllTiles()
        // 台灣麻將純化：34 種合法牌 (9萬 + 9筒 + 9條 + 4風 + 3三元)
        assertEquals(34, allTiles.size)

        // 驗證沒有任何花牌
        assertTrue(allTiles.none { it.isFlower })

        // 驗證每一種牌上限為 4 張
        allTiles.forEach { tile ->
            assertEquals(4, MahjongTile.getMaxCount(tile))
        }

        // 驗證花色分類數量
        assertEquals(9, MahjongTile.getTilesBySuit(TileSuit.WAN).size)
        assertEquals(9, MahjongTile.getTilesBySuit(TileSuit.TONG).size)
        assertEquals(9, MahjongTile.getTilesBySuit(TileSuit.TIAO).size)
        assertEquals(7, MahjongTile.getTilesBySuit(TileSuit.HONOR).size)
    }

    @Test
    fun testRuleEngineAnGangAndChiPeng() {
        val east = MahjongTile.findById("east")!!
        val wan1 = MahjongTile.findById("wan_1")!!
        val wan2 = MahjongTile.findById("wan_2")!!
        val wan3 = MahjongTile.findById("wan_3")!!

        val state = GameState(
            handTiles = listOf(east, east, east, east, wan1, wan2),
            currentTurnPlayer = PlayerSeat.SELF,
            phase = TurnPhase.AWAITING_DISCARD
        )

        // 驗證可暗槓東風
        val anGangOptions = state.getAnGangOptions()
        assertEquals(1, anGangOptions.size)
        assertEquals("east", anGangOptions[0].id)

        // 若剛吃碰副露當回合 (justMeldedThisTurn = true)，規則禁止暗槓
        val stateJustMelded = state.copy(justMeldedThisTurn = true)
        assertTrue(stateJustMelded.getAnGangOptions().isEmpty())

        // 上家打出三萬，驗證能吃 [一萬, 二萬]
        val stateWithDiscard = state.copy(
            lastDiscard = Pair(PlayerSeat.SHANG_JIA, wan3),
            phase = TurnPhase.AWAITING_CALL_RESPONSE
        )
        val chiCombinations = stateWithDiscard.getChiCombinations(wan3)
        assertEquals(1, chiCombinations.size)
        assertEquals("wan_1", chiCombinations[0].first.id)
        assertEquals("wan_2", chiCombinations[0].second.id)
    }

    @Test
    fun testNormalizeModelName() {
        val client = VllmApiClient()

        // 簡短名稱自動補上前綴
        assertEquals("vllm-sr/d3", client.normalizeModelName("d3"))
        assertEquals("vllm-sr/d3-flash", client.normalizeModelName("d3-flash"))
        assertEquals("vllm-sr/d3-mini", client.normalizeModelName("d3-mini"))

        // 原本就有前綴則保留
        assertEquals("vllm-sr/d3", client.normalizeModelName("vllm-sr/d3"))
        assertEquals("vllm-sr/d3-flash", client.normalizeModelName("vllm-sr/d3-flash"))

        // 空字串預設為官方旗艦
        assertEquals("vllm-sr/d3", client.normalizeModelName(""))
    }

    @Test
    fun testIssue001BuGangOption() {
        val zhong = MahjongTile.findById("zhong")!!
        val fa = MahjongTile.findById("fa")!!

        // 玩家先前已碰了紅中 (3張)，手牌中摸到第 4 張紅中
        val pengMeld = Meld(
            type = ActionType.PENG,
            tiles = listOf(zhong, zhong, zhong),
            triggerTile = zhong,
            targetSeat = PlayerSeat.DUI_JIA
        )

        val state = GameState(
            handTiles = listOf(zhong, fa),
            melds = listOf(pengMeld),
            currentTurnPlayer = PlayerSeat.SELF,
            phase = TurnPhase.AWAITING_DISCARD
        )

        val buGangOptions = state.getBuGangOptions()
        assertEquals(1, buGangOptions.size)
        assertEquals("zhong", buGangOptions[0].id)

        // 若剛吃碰副露當回合 (justMeldedThisTurn = true)，規則禁止補槓
        val stateJustMelded = state.copy(justMeldedThisTurn = true)
        assertTrue(stateJustMelded.getBuGangOptions().isEmpty())
    }

    @Test
    fun testIssue002TileCountDeduplication() {
        val wan5 = MahjongTile.findById("wan_5")!!
        val wan2 = MahjongTile.findById("wan_2")!!
        val wan3 = MahjongTile.findById("wan_3")!!
        val wan4 = MahjongTile.findById("wan_4")!!
        val tiao8 = MahjongTile.findById("tiao_8")!!

        // 情境 1: 對家打出五萬，自己碰牌。
        // 牌河中對家有 1 張五萬；副露中有 3 張五萬 (triggerTile = 五萬)。
        // 總看見張數應為 3 張（因為牌河的 1 張就是副露裡拿到的那一張），而不是 1 + 3 = 4。
        val pengMeld = Meld(
            type = ActionType.PENG,
            tiles = listOf(wan5, wan5, wan5),
            triggerTile = wan5,
            targetSeat = PlayerSeat.DUI_JIA
        )
        val discards = mapOf(
            PlayerSeat.DUI_JIA to listOf(wan5),
            PlayerSeat.SHANG_JIA to emptyList<MahjongTile>(),
            PlayerSeat.XIA_JIA to emptyList<MahjongTile>(),
            PlayerSeat.SELF to emptyList<MahjongTile>()
        )
        val state1 = GameState(
            handTiles = emptyList(),
            melds = listOf(pengMeld),
            discards = discards
        )

        assertEquals(3, state1.countVisibleTile(wan5))
        assertEquals(1, state1.remainingCount(wan5)) // 4 - 3 = 1 張未現

        // 情境 2: 上家打出三萬，自己吃牌 [二萬, 三萬, 四萬]。
        // 牌河中上家有 1 張三萬；副露中有 [二萬, 三萬, 四萬] (triggerTile = 三萬)。
        // 看見的三萬應為 1 張 (非 2 張)。
        val chiMeld = Meld(
            type = ActionType.CHI,
            tiles = listOf(wan2, wan3, wan4),
            triggerTile = wan3,
            targetSeat = PlayerSeat.SHANG_JIA
        )
        val state2 = GameState(
            handTiles = emptyList(),
            melds = listOf(chiMeld),
            discards = mapOf(
                PlayerSeat.SHANG_JIA to listOf(wan3)
            )
        )
        assertEquals(1, state2.countVisibleTile(wan3))
        assertEquals(3, state2.remainingCount(wan3)) // 4 - 1 = 3 張未現

        // 情境 3: 下家明槓八條 (由上家打出)。
        // 上家牌河有八條，下家副露有 4 張八條 (triggerTile = 八條)。
        // 八條看見張數應為 4 張 (非 5 張)。
        val gangMeld = Meld(
            type = ActionType.GANG_MING,
            tiles = listOf(tiao8, tiao8, tiao8, tiao8),
            triggerTile = tiao8,
            targetSeat = PlayerSeat.SHANG_JIA
        )
        val state3 = GameState(
            handTiles = emptyList(),
            otherMelds = mapOf(
                PlayerSeat.XIA_JIA to listOf(gangMeld)
            ),
            discards = mapOf(
                PlayerSeat.SHANG_JIA to listOf(tiao8)
            )
        )
        assertEquals(4, state3.countVisibleTile(tiao8))
        assertEquals(0, state3.remainingCount(tiao8)) // 4 - 4 = 0 張未現
    }

    @Test
    fun testMahjongRuleEngineStandardHuAndWaiting() {
        val m1 = MahjongTile.findByQuery("一萬")!!
        val m2 = MahjongTile.findByQuery("二萬")!!
        val m3 = MahjongTile.findByQuery("三萬")!!
        val m4 = MahjongTile.findByQuery("四萬")!!
        val m5 = MahjongTile.findByQuery("五萬")!!
        val m6 = MahjongTile.findByQuery("六萬")!!

        val s7 = MahjongTile.findByQuery("七條")!!
        val s8 = MahjongTile.findByQuery("八條")!!
        val s9 = MahjongTile.findByQuery("九條")!!

        val p1 = MahjongTile.findByQuery("一筒")!!
        val p2 = MahjongTile.findByQuery("二筒")!!
        val p3 = MahjongTile.findByQuery("三筒")!!
        val p4 = MahjongTile.findByQuery("四筒")!!
        val p5 = MahjongTile.findByQuery("五筒")!!
        val p6 = MahjongTile.findByQuery("六筒")!!
        val p9 = MahjongTile.findByQuery("九筒")!!
        val east = MahjongTile.findByQuery("東風")!!

        // 1. 標準 17 張門清胡牌 (5 面子 + 1 雀頭)
        val validHand17 = listOf(
            m1, m2, m3,
            m4, m5, m6,
            s7, s8, s9,
            p1, p1, p1,
            p2, p2, p2,
            p9, p9
        )
        val state1 = GameState(handTiles = validHand17, currentTurnPlayer = PlayerSeat.SELF, phase = TurnPhase.AWAITING_DISCARD)
        assertTrue(state1.canSelfHuOnDraw())

        // 2. 缺少雀頭不可胡 (將其中一張九筒換成東風，無將牌)
        val invalidHand17 = listOf(
            m1, m2, m3,
            m4, m5, m6,
            s7, s8, s9,
            p1, p1, p1,
            p2, p2, p2,
            p9, east
        )
        val state2 = GameState(handTiles = invalidHand17, currentTurnPlayer = PlayerSeat.SELF, phase = TurnPhase.AWAITING_DISCARD)
        assertFalse(state2.canSelfHuOnDraw())

        // 3. 雙頭聽牌：聽 3 筒與 6 筒 (手牌 16 張)
        val waitingHand16 = listOf(
            m1, m2, m3,
            m4, m5, m6,
            s7, s8, s9,
            p1, p1, p1,
            p4, p5,
            p9, p9
        )
        val state3 = GameState(
            handTiles = waitingHand16,
            lastDiscard = Pair(PlayerSeat.SHANG_JIA, p3),
            phase = TurnPhase.AWAITING_CALL_RESPONSE
        )
        val waitingTiles = state3.getSelfWaitingTiles()
        assertTrue(waitingTiles.any { it.id == "tong_3" })
        assertTrue(waitingTiles.any { it.id == "tong_6" })

        // 4. 驗證抓放槍：上家打出 3 筒可抓放槍，打出 1 筒則不可抓放槍
        assertTrue(state3.canSelfHuOnDiscard(p3))
        assertFalse(state3.canSelfHuOnDiscard(p1))
    }

    @Test
    fun testQiangGangDetection() {
        val m1 = MahjongTile.findByQuery("一萬")!!
        val m2 = MahjongTile.findByQuery("二萬")!!
        val m3 = MahjongTile.findByQuery("三萬")!!
        val m4 = MahjongTile.findByQuery("四萬")!!
        val m5 = MahjongTile.findByQuery("五萬")!!
        val m6 = MahjongTile.findByQuery("六萬")!!

        val s7 = MahjongTile.findByQuery("七條")!!
        val s8 = MahjongTile.findByQuery("八條")!!
        val s9 = MahjongTile.findByQuery("九條")!!

        val p1 = MahjongTile.findByQuery("一筒")!!
        val p4 = MahjongTile.findByQuery("四筒")!!
        val p5 = MahjongTile.findByQuery("五筒")!!
        val p3 = MahjongTile.findByQuery("三筒")!!
        val p6 = MahjongTile.findByQuery("六筒")!!
        val p9 = MahjongTile.findByQuery("九筒")!!
        val east = MahjongTile.findByQuery("東風")!!

        // 自己聽 3 筒與 6 筒 (16張)
        val waitingHand16 = listOf(
            m1, m2, m3,
            m4, m5, m6,
            s7, s8, s9,
            p1, p1, p1,
            p4, p5,
            p9, p9
        )
        val state = GameState(
            handTiles = waitingHand16,
            currentTurnPlayer = PlayerSeat.DUI_JIA,
            phase = TurnPhase.AWAITING_DISCARD
        )

        // 對家宣告補槓 3 筒 -> 自己可以搶槓胡牌！
        assertTrue(state.canSelfQiangGang(p3))
        // 對家宣告補槓 6 筒 -> 自己也可以搶槓胡牌！
        assertTrue(state.canSelfQiangGang(p6))
        // 對家宣告補槓 東風 -> 無法搶槓
        assertFalse(state.canSelfQiangGang(east))
    }
}
