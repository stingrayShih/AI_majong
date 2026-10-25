package com.aimajong.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 牌局歷史持久化實體 (Room Entity)
 */
@Entity(tableName = "game_records")
data class GameRecordEntity(
    @PrimaryKey
    val gameId: String,
    val startTime: Long,
    val endTime: Long,
    val dealerSeat: String,            // PlayerSeat.name (SELF, XIA_JIA, DUI_JIA, SHANG_JIA)
    val winnerSeat: String?,
    val loserSeat: String?,
    val isZimo: Boolean,
    val isQiangGang: Boolean,
    val isDrawGame: Boolean,
    val winningTileJson: String?,      // MahjongTile serialized
    val initialHandJson: String,       // List<MahjongTile> serialized
    val finalHandJson: String,         // List<MahjongTile> serialized
    val finalMeldsJson: String,        // List<Meld> serialized
    val finalFlowersJson: String,      // List<MahjongTile> serialized
    val actionsJson: String,           // List<GameActionRecord> serialized
    val totalTurns: Int,
    val summaryTitle: String,          // 例如："自家 自摸胡牌", "下家 放槍 (自家胡牌)", "流局"
    val summaryDetail: String          // 例如："莊家: 我家 / 歷經 12 巡 / 胡牌: 1筒"
)
