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
}
