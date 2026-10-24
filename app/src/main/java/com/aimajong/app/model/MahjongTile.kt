package com.aimajong.app.model

/**
 * 台灣 16 張麻將花色定義
 */
enum class TileSuit(val displayName: String) {
    WAN("萬子"),
    TONG("筒子"),
    TIAO("條子"),
    HONOR("字牌")
}

/**
 * 台灣 16 張麻將牌面資料類別
 */
data class MahjongTile(
    val id: String,
    val suit: TileSuit,
    val rank: Int = 0,         // 1-9 for Wan/Tong/Tiao; 0 for others
    val name: String,          // 顯示名稱，如 "一萬", "紅中", "春"
    val shortCode: String,     // 簡寫代碼，如 "1m", "5p", "9s", "E", "Zhong"
    val isFlower: Boolean = false
) : Comparable<MahjongTile> {

    override fun compareTo(other: MahjongTile): Int {
        if (this.suit != other.suit) {
            return this.suit.ordinal.compareTo(other.suit.ordinal)
        }
        if (this.rank != other.rank) {
            return this.rank.compareTo(other.rank)
        }
        return this.id.compareTo(other.id)
    }

    companion object {
        private val ALL_TILES_LIST: List<MahjongTile> by lazy {
            val list = mutableListOf<MahjongTile>()

            // 1. 萬子 (1~9萬)
            val wanNames = listOf("一萬", "二萬", "三萬", "四萬", "五萬", "六萬", "七萬", "八萬", "九萬")
            for (i in 1..9) {
                list.add(MahjongTile("wan_$i", TileSuit.WAN, i, wanNames[i - 1], "${i}m"))
            }

            // 2. 筒子 (1~9筒)
            val tongNames = listOf("一筒", "二筒", "三筒", "四筒", "五筒", "六筒", "七筒", "八筒", "九筒")
            for (i in 1..9) {
                list.add(MahjongTile("tong_$i", TileSuit.TONG, i, tongNames[i - 1], "${i}p"))
            }

            // 3. 條子 (1~9條)
            val tiaoNames = listOf("一條", "二條", "三條", "四條", "五條", "六條", "七條", "八條", "九條")
            for (i in 1..9) {
                list.add(MahjongTile("tiao_$i", TileSuit.TIAO, i, tiaoNames[i - 1], "${i}s"))
            }

            // 4. 字牌 (風牌 + 三元牌)
            val windNames = listOf("東風", "南風", "西風", "北風")
            val windCodes = listOf("E", "S", "W", "N")
            val windIds = listOf("east", "south", "west", "north")
            for (i in 0..3) {
                list.add(MahjongTile(windIds[i], TileSuit.HONOR, i + 1, windNames[i], windCodes[i]))
            }

            val dragonNames = listOf("紅中", "青發", "白板")
            val dragonCodes = listOf("C", "F", "P")
            val dragonIds = listOf("zhong", "fa", "bai")
            for (i in 0..2) {
                list.add(MahjongTile(dragonIds[i], TileSuit.HONOR, i + 5, dragonNames[i], dragonCodes[i]))
            }

            list
        }

        fun getAllTiles(): List<MahjongTile> = ALL_TILES_LIST

        fun getTilesBySuit(suit: TileSuit): List<MahjongTile> {
            return ALL_TILES_LIST.filter { it.suit == suit }
        }

        fun findById(id: String): MahjongTile? {
            return ALL_TILES_LIST.find { it.id == id }
        }

        /**
         * 依名稱、簡稱、代碼模糊搜尋牌
         */
        fun findByQuery(query: String): MahjongTile? {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return null

            // 1. 完全比對 ID, Name, ShortCode
            ALL_TILES_LIST.find { 
                it.id.equals(q, ignoreCase = true) || 
                it.name.equals(q, ignoreCase = true) || 
                it.shortCode.equals(q, ignoreCase = true) 
            }?.let { return it }

            // 2. 常見別名轉換
            val normalized = q
                .replace("索", "條")
                .replace("發財", "青發")
                .replace("白身", "白板")
                .replace("1", "一").replace("2", "二").replace("3", "三")
                .replace("4", "四").replace("5", "五").replace("6", "六")
                .replace("7", "七").replace("8", "八").replace("9", "九")

            ALL_TILES_LIST.find { it.name == normalized }?.let { return it }

            // 3. 單字風牌 / 三元牌
            when (q) {
                "東" -> return findById("east")
                "南" -> return findById("south")
                "西" -> return findById("west")
                "北" -> return findById("north")
                "中" -> return findById("zhong")
                "發" -> return findById("fa")
                "白" -> return findById("bai")
            }

            return null
        }

        /**
         * 取得某張牌在全副牌中的上限總數（正規牌每種各 4 張）
         */
        fun getMaxCount(tile: MahjongTile): Int = 4
    }
}
