package com.aimajong.app.data.db

import android.content.Context
import com.aimajong.app.model.GameActionRecord
import com.aimajong.app.model.GameState
import com.aimajong.app.model.MahjongTile
import com.aimajong.app.model.Meld
import com.aimajong.app.model.PlayerSeat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 牌局歷史儲存庫 (Room DB 封裝與 JSON 序列化)
 */
class GameRecordRepository(context: Context) {

    private val dao: GameRecordDao = AppDatabase.getInstance(context).gameRecordDao()
    private val gson = Gson()

    val allRecords: Flow<List<GameRecordEntity>> = dao.getAllRecords()

    suspend fun getRecordById(id: String): GameRecordEntity? = withContext(Dispatchers.IO) {
        dao.getRecordById(id)
    }

    suspend fun saveGameRecord(
        gameId: String,
        startTime: Long,
        endTime: Long,
        gameState: GameState,
        initialHandTiles: List<MahjongTile>,
        isDrawGame: Boolean = false,
        isQiangGang: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val winner = gameState.winnerSeat
        val loser = gameState.loserSeat
        val isZimo = winner != null && loser == null && !isDrawGame

        val winningTile = if (isDrawGame) null else (gameState.lastDiscard?.second ?: gameState.handTiles.lastOrNull())

        val summaryTitle = when {
            isDrawGame -> "流局 (無人胡牌)"
            winner == PlayerSeat.SELF && isZimo -> "自家 自摸胡牌 🎉"
            winner == PlayerSeat.SELF && loser != null -> "自家 胡牌 (抓 ${loser.title} 放槍) 🎯"
            winner != null && loser == PlayerSeat.SELF -> "自家 放槍 (${winner.title} 胡牌) 💥"
            winner != null && loser == null -> "${winner.title} 自摸胡牌"
            winner != null && loser != null -> "${winner.title} 胡牌 (${loser.title} 放槍)"
            else -> "牌局結束"
        }

        val summaryDetail = buildString {
            append("起莊: ${gameState.dealerSeat.title} / 歷經 ${gameState.currentTurn} 巡")
            if (winningTile != null && !isDrawGame) {
                append(" / 胡牌: [${winningTile.name}]")
            }
        }

        val entity = GameRecordEntity(
            gameId = gameId,
            startTime = startTime,
            endTime = endTime,
            dealerSeat = gameState.dealerSeat.name,
            winnerSeat = winner?.name,
            loserSeat = loser?.name,
            isZimo = isZimo,
            isQiangGang = isQiangGang,
            isDrawGame = isDrawGame,
            winningTileJson = if (winningTile != null) gson.toJson(winningTile) else null,
            initialHandJson = gson.toJson(initialHandTiles),
            finalHandJson = gson.toJson(gameState.handTiles),
            finalMeldsJson = gson.toJson(gameState.melds),
            finalFlowersJson = gson.toJson(gameState.flowers),
            actionsJson = gson.toJson(gameState.history),
            totalTurns = gameState.currentTurn,
            summaryTitle = summaryTitle,
            summaryDetail = summaryDetail
        )

        dao.insertRecord(entity)
    }

    suspend fun deleteRecord(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun clearAllRecords() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    // JSON 反序列化輔助方法 (供覆盤與歷史畫面解析)
    fun parseTiles(json: String?): List<MahjongTile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<MahjongTile>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseMelds(json: String?): List<Meld> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<Meld>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseActions(json: String?): List<GameActionRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<GameActionRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseSingleTile(json: String?): MahjongTile? {
        if (json.isNullOrBlank()) return null
        return try {
            gson.fromJson(json, MahjongTile::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
