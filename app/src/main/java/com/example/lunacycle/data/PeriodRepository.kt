package com.example.lunacycle.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class PeriodValidationException(message: String) : IllegalArgumentException(message)

class PeriodNotFoundException(message: String) : NoSuchElementException(message)

class PeriodRepository(private val periodDao: PeriodDao) {

    companion object {
        const val MAX_NOTE_LENGTH = 1000
    }

    val allRecords: Flow<List<PeriodRecordEntity>> =
        periodDao.observeAllRecords()

    suspend fun getRecordById(id: Long): Result<PeriodRecordEntity> =
        runCatchingCancellable {
            if (id <= 0L) {
                throw PeriodValidationException("无效的记录 ID：$id")
            }

            periodDao.getRecordById(id)
                ?: throw PeriodNotFoundException("未找到 ID 为 $id 的经期记录")
        }

    suspend fun addRecord(
        startDate: LocalDate,
        endDate: LocalDate? = null,
        note: String? = null
    ): Result<Long> = runCatchingCancellable {
        validateDateOrder(startDate, endDate)?.let { throw it }

        val normalizedNote = normalizeAndValidateNote(note).getOrElse {
            throw it
        }

        val now = System.currentTimeMillis()

        val entity = PeriodRecordEntity(
            id = 0L,
            startDate = startDate,
            endDate = endDate,
            note = normalizedNote,
            createdAt = now,
            updatedAt = now
        )

        periodDao.insertIfNoOverlap(entity)
    }

    suspend fun updateRecord(
        id: Long,
        startDate: LocalDate,
        endDate: LocalDate? = null,
        note: String? = null
    ): Result<Unit> = runCatchingCancellable {
        if (id <= 0L) {
            throw PeriodValidationException("无效的记录 ID：$id")
        }

        validateDateOrder(startDate, endDate)?.let { throw it }

        val normalizedNote = normalizeAndValidateNote(note).getOrElse {
            throw it
        }

        periodDao.updateIfNoOverlap(
            id = id,
            startDate = startDate,
            endDate = endDate,
            note = normalizedNote,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteRecord(id: Long): Result<Unit> =
        runCatchingCancellable {
            if (id <= 0L) {
                throw PeriodValidationException("无效的记录 ID：$id")
            }

            val deletedRows = periodDao.deleteById(id)

            if (deletedRows != 1) {
                throw PeriodNotFoundException(
                    "删除失败：未找到 ID 为 $id 的经期记录"
                )
            }

            Unit
        }

    private fun validateDateOrder(
        startDate: LocalDate,
        endDate: LocalDate?
    ): PeriodValidationException? {
        if (endDate != null && endDate.isBefore(startDate)) {
            return PeriodValidationException("结束日期不能早于开始日期")
        }

        return null
    }

    private fun normalizeAndValidateNote(
        rawNote: String?
    ): Result<String?> {
        if (rawNote == null) {
            return Result.success(null)
        }

        val trimmed = rawNote.trim()

        if (trimmed.isEmpty()) {
            return Result.success(null)
        }

        if (trimmed.length > MAX_NOTE_LENGTH) {
            return Result.failure(
                PeriodValidationException(
                    "备注长度超出限制：当前 ${trimmed.length} 字符，" +
                        "最多允许 $MAX_NOTE_LENGTH 字符"
                )
            )
        }

        return Result.success(trimmed)
    }

    private suspend fun <T> runCatchingCancellable(
        block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
