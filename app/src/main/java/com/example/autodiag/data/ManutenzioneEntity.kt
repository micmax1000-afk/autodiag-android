package com.example.autodiag.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "manutenzione")
data class ManutenzioneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val kmIntervallo: Int = 0,
    val mesiIntervallo: Int = 0,
    val ultimoKm: Int = 0,
    val ultimaData: String = "" // formato yyyy-MM-dd
)
