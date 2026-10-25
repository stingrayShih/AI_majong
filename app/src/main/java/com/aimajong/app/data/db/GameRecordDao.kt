package com.aimajong.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: GameRecordEntity)

    @Update
    suspend fun updateRecord(record: GameRecordEntity)

    @Query("SELECT * FROM game_records ORDER BY endTime DESC")
    fun getAllRecords(): Flow<List<GameRecordEntity>>

    @Query("SELECT * FROM game_records WHERE gameId = :id")
    suspend fun getRecordById(id: String): GameRecordEntity?

    @Query("DELETE FROM game_records WHERE gameId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM game_records")
    suspend fun deleteAll()
}
