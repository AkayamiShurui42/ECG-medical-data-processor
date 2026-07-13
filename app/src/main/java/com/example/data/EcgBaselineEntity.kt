package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ecg_baselines")
data class EcgBaselineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceType: String, // "Loop Recorder", "12-Lead", "Single-Lead", "Holter Monitor", "Wearable Devices"
    
    // Key ECG metrics
    val pWaveDuration: Int,   // ms
    val prInterval: Int,      // ms
    val qrsDuration: Int,     // ms
    val qtInterval: Int,      // ms
    val stElevation: Float,   // mV
    val tWaveMorphology: String, // "Upright", "Inverted", "Biphasic", "Flat"
    
    // Echocardiogram metrics
    val ejectionFraction: Float,  // %
    val lvIdd: Float,              // mm (Left Ventricular Internal Diameter End-Diastole)
    val leftAtrialSize: Float,     // mm
    val ivsThickness: Float,       // mm (Interventricular Septal Thickness)
    val pwThickness: Float,        // mm (Posterior Wall Thickness)
    val valveFunction: String,     // "Normal", "Mild regurgitation", "Moderate regurgitation", "Severe regurgitation", "Mild stenosis", "Moderate stenosis", "Severe stenosis"
    
    val traceDataJson: String = "" // Optional wave points JSON
)

@Dao
interface EcgBaselineDao {
    @Query("SELECT * FROM ecg_baselines ORDER BY timestamp DESC")
    fun getAllBaselines(): Flow<List<EcgBaselineEntity>>

    @Query("SELECT * FROM ecg_baselines WHERE id = :id LIMIT 1")
    suspend fun getBaselineById(id: Long): EcgBaselineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(baseline: EcgBaselineEntity): Long

    @Query("DELETE FROM ecg_baselines WHERE id = :id")
    suspend fun deleteBaselineById(id: Long)

    @Query("DELETE FROM ecg_baselines")
    suspend fun clearAllBaselines()
}
