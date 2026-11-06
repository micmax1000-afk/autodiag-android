package com.example.autodiag.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ManutenzioneDao {

    @Query("SELECT * FROM manutenzione ORDER BY nome")
    fun getAll(): Flow<List<ManutenzioneEntity>>

    @Query("SELECT COUNT(*) FROM manutenzione")
    suspend fun count(): Int

    @Insert
    suspend fun insert(item: ManutenzioneEntity)

    @Update
    suspend fun update(item: ManutenzioneEntity)

    @Query("DELETE FROM manutenzione WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM manutenzione")
    suspend fun clearAll()
}
