package com.aimajong.app

import com.aimajong.app.model.*
import org.junit.Assert.*
import org.junit.Test

class ReplayEngineTest {

    @Test
    fun testReplayStepReconstruction() {
        val wan1 = MahjongTile.findById("wan_1")!!
        val wan2 = MahjongTile.findById("wan_2")!!
        val wan3 = MahjongTile.findById("wan_3")!!
        val tong5 = MahjongTile.findById("tong_5")!!
        val east = MahjongTile.findById("east")!!

        val initialHand = listOf(wan1, wan2, tong5, east)
        val actions = listOf(
            GameActionRecord(
                turnNumber = 1,
                seat = PlayerSeat.SELF,
                actionType = ActionType.DRAW,
                tile = wan3
            ),
            GameActionRecord(
                turnNumber = 1,
                seat = PlayerSeat.SELF,
                actionType = ActionType.DISCARD,
                tile = east
            ),
            GameActionRecord(
                turnNumber = 2,
                seat = PlayerSeat.XIA_JIA,
                actionType = ActionType.DISCARD,
                tile = tong5
            ),
            GameActionRecord(
                turnNumber = 2,
                seat = PlayerSeat.SELF,
                actionType = ActionType.HU,
                tile = tong5
            )
        )

        val snapshots = ReplayEngine.buildSnapshots(
            initialHand = initialHand,
            dealerSeat = PlayerSeat.SELF,
            actions = actions
        )

        // 包含起手 0 步 + 4 步動作 = 5 筆快照
        assertEquals(5, snapshots.size)

        // Step 0: 起手
        assertEquals(0, snapshots[0].stepIndex)
        assertEquals(4, snapshots[0].selfHand.size)
        assertTrue(snapshots[0].discards[PlayerSeat.SELF]!!.isEmpty())

        // Step 1: 自家摸入 wan_3 -> 手牌變 5 張
        assertEquals(1, snapshots[1].stepIndex)
        assertEquals(5, snapshots[1].selfHand.size)
        assertTrue(snapshots[1].selfHand.any { it.id == "wan_3" })

        // Step 2: 自家打出 east -> 手牌變 4 張，自家牌河有 east
        assertEquals(2, snapshots[2].stepIndex)
        assertEquals(4, snapshots[2].selfHand.size)
        assertFalse(snapshots[2].selfHand.any { it.id == "east" })
        assertEquals(1, snapshots[2].discards[PlayerSeat.SELF]!!.size)
        assertEquals("east", snapshots[2].discards[PlayerSeat.SELF]!![0].id)

        // Step 3: 下家打出 tong_5 -> 下家牌河有 tong_5
        assertEquals(3, snapshots[3].stepIndex)
        assertEquals(1, snapshots[3].discards[PlayerSeat.XIA_JIA]!!.size)
        assertEquals("tong_5", snapshots[3].discards[PlayerSeat.XIA_JIA]!![0].id)

        // Step 4: 自家宣告胡牌
        assertEquals(4, snapshots[4].stepIndex)
        assertEquals(ActionType.HU, snapshots[4].actionType)
        assertTrue(snapshots[4].description.contains("胡牌"))
    }

    @Test
    fun testReplayChiPengReconstruction() {
        val tiao2 = MahjongTile.findById("tiao_2")!!
        val tiao3 = MahjongTile.findById("tiao_3")!!
        val tiao4 = MahjongTile.findById("tiao_4")!!
        val zhong = MahjongTile.findById("zhong")!!

        val initialHand = listOf(tiao2, tiao3, zhong, zhong)
        val actions = listOf(
            GameActionRecord(
                turnNumber = 1,
                seat = PlayerSeat.SHANG_JIA,
                actionType = ActionType.DISCARD,
                tile = tiao4
            ),
            GameActionRecord(
                turnNumber = 1,
                seat = PlayerSeat.SELF,
                actionType = ActionType.CHI,
                tile = tiao4,
                extraTiles = listOf(tiao2, tiao3, tiao4)
            )
        )

        val snapshots = ReplayEngine.buildSnapshots(
            initialHand = initialHand,
            dealerSeat = PlayerSeat.SHANG_JIA,
            actions = actions
        )

        assertEquals(3, snapshots.size)

        // Step 2: 自家吃牌 -> 手牌移除 tiao2, tiao3，副露增加吃牌搭子
        val step2 = snapshots[2]
        assertEquals(2, step2.selfHand.size) // 剩 2 張 zhong
        assertEquals(1, step2.selfMelds.size)
        assertEquals(ActionType.CHI, step2.selfMelds[0].type)
        assertEquals(3, step2.selfMelds[0].tiles.size)
    }
}
