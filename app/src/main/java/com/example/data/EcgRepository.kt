package com.example.data

import kotlinx.coroutines.flow.Flow

class EcgRepository(
    private val ecgReportDao: EcgReportDao,
    private val ecgBaselineDao: EcgBaselineDao
) {
    val allReports: Flow<List<EcgReportEntity>> = ecgReportDao.getAllReports()
    val allBaselines: Flow<List<EcgBaselineEntity>> = ecgBaselineDao.getAllBaselines()

    suspend fun getReportById(id: Long): EcgReportEntity? {
        return ecgReportDao.getReportById(id)
    }

    suspend fun insertReport(report: EcgReportEntity): Long {
        return ecgReportDao.insertReport(report)
    }

    suspend fun deleteReportById(id: Long) {
        ecgReportDao.deleteReportById(id)
    }

    suspend fun clearAll() {
        ecgReportDao.clearAllReports()
    }

    // Baselines operations
    suspend fun getBaselineById(id: Long): EcgBaselineEntity? {
        return ecgBaselineDao.getBaselineById(id)
    }

    suspend fun insertBaseline(baseline: EcgBaselineEntity): Long {
        return ecgBaselineDao.insertBaseline(baseline)
    }

    suspend fun deleteBaselineById(id: Long) {
        ecgBaselineDao.deleteBaselineById(id)
    }

    suspend fun clearAllBaselines() {
        ecgBaselineDao.clearAllBaselines()
    }
}
