package com.example.lunacycle.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PeriodDao {

    @Query("SELECT * FROM period_records ORDER BY start_date DESC, id DESC")
    fun observeAllRecords(): Flow<List<PeriodRecordEntity>>

    @Query("SELECT * FROM period_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): PeriodRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: PeriodRecordEntity): Long

    @Update
    suspend fun update(record: PeriodRecordEntity): Int

    @Query("DELETE FROM period_records WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM period_records
        WHERE id != :excludeId
          AND (:newEnd IS NULL OR start_date <= :newEnd)
          AND (end_date IS NULL OR end_date >= :newStart)
        """
    )
    suspend fun countOverlappingRecords(
        newStart: LocalDate,
        newEnd: LocalDate?,
        excludeId: Long
    ): Int

    @Transaction
    suspend fun insertIfNoOverlap(record: PeriodRecordEntity): Long {
        val overlapCount = countOverlappingRecords(
            newStart = record.startDate,
            newEnd = record.endDate,
            excludeId = -1L
        )

        if (overlapCount > 0) {
            throw PeriodValidationException("该日期区间与已存在的经期记录重叠")
        }

        return insert(record)
    }

    @Transaction
    suspend fun updateIfNoOverlap(
        id: Long,
        startDate: LocalDate,
        endDate: LocalDate?,
        note: String?,
        updatedAt: Long
    ) {
        val existingRecord = getRecordById(id)
            ?: throw PeriodNotFoundException("无法更新：未找到 ID 为 $id 的经期记录")

        val overlapCount = countOverlappingRecords(
            newStart = startDate,
            newEnd = endDate,
            excludeId = id
        )

        if (overlapCount > 0) {
            throw PeriodValidationException("修改后的日期区间与其他经期记录重叠")
        }

        val updatedEntity = existingRecord.copy(
            startDate = startDate,
            endDate = endDate,
            note = note,
            createdAt = existingRecord.createdAt,
            updatedAt = updatedAt
        )

        val updatedRows = update(updatedEntity)

        if (updatedRows != 1) {
            throw PeriodNotFoundException("更新失败：ID 为 $id 的经期记录不存在")
        }
    }
}
