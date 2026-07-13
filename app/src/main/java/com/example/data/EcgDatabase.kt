package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ecg_reports")
data class EcgReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val rhythmType: String,
    val heartRate: Int,
    val strokeVolume: Float,
    val ejectionFraction: Float,
    val lvCavitySize: Float,
    val findings: String,
    val traceDataJson: String,  // JSON serialized list of float values for the waveform
    val annotationsJson: String, // JSON serialized details of wave segments (P, QRS, T)
    val artifactsJson: String,   // JSON serialized artifacts with their respective clinical status
    val literatureRefsJson: String // JSON list of scientific citations
)

@Dao
interface EcgReportDao {
    @Query("SELECT * FROM ecg_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<EcgReportEntity>>

    @Query("SELECT * FROM ecg_reports WHERE id = :id LIMIT 1")
    suspend fun getReportById(id: Long): EcgReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: EcgReportEntity): Long

    @Query("DELETE FROM ecg_reports WHERE id = :id")
    suspend fun deleteReportById(id: Long)

    @Query("DELETE FROM ecg_reports")
    suspend fun clearAllReports()
}

@Database(entities = [EcgReportEntity::class, EcgBaselineEntity::class], version = 2, exportSchema = false)
abstract class EcgDatabase : RoomDatabase() {
    abstract fun ecgReportDao(): EcgReportDao
    abstract fun ecgBaselineDao(): EcgBaselineDao
}
