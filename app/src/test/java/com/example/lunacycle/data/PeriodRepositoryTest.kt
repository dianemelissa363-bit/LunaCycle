package com.example.lunacycle.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * A lightweight in-memory DAO fake for testing repository rules without Android or a real database.
 */
private class FakePeriodDao : PeriodDao {
    private val records = MutableStateFlow<List<PeriodRecordEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAllRecords(): Flow<List<PeriodRecordEntity>> = records.asStateFlow()

    override suspend fun getRecordById(id: Long): PeriodRecordEntity? =
        records.value.firstOrNull { it.id == id }

    override suspend fun insert(record: PeriodRecordEntity): Long {
        val id = if (record.id == 0L) nextId++ else record.id
        records.value = records.value + record.copy(id = id)
        return id
    }

    override suspend fun update(record: PeriodRecordEntity): Int {
        if (records.value.none { it.id == record.id }) return 0
        records.value = records.value.map { if (it.id == record.id) record else it }
        return 1
    }

    override suspend fun deleteById(id: Long): Int {
        val oldSize = records.value.size
        records.value = records.value.filterNot { it.id == id }
        return oldSize - records.value.size
    }

    override suspend fun countOverlappingRecords(
        newStart: LocalDate,
        newEnd: LocalDate?,
        excludeId: Long
    ): Int = records.value.count { existing ->
        existing.id != excludeId &&
            (newEnd == null || !existing.startDate.isAfter(newEnd)) &&
            (existing.endDate == null || !existing.endDate.isBefore(newStart))
    }

    override suspend fun insertIfNoOverlap(record: PeriodRecordEntity): Long {
        if (countOverlappingRecords(record.startDate, record.endDate, -1L) > 0) {
            throw PeriodValidationException("该日期区间与已存在的经期记录重叠")
        }
        return insert(record)
    }

    override suspend fun updateIfNoOverlap(
        id: Long,
        startDate: LocalDate,
        endDate: LocalDate?,
        note: String?,
        updatedAt: Long
    ) {
        val existing = getRecordById(id)
            ?: throw PeriodNotFoundException("无法更新：未找到 ID 为 $id 的经期记录")

        if (countOverlappingRecords(startDate, endDate, id) > 0) {
            throw PeriodValidationException("修改后的日期区间与其他经期记录重叠")
        }

        update(
            existing.copy(
                startDate = startDate,
                endDate = endDate,
                note = note,
                updatedAt = updatedAt
            )
        )
    }
}

class PeriodRepositoryTest {
    private val dao = FakePeriodDao()
    private val repository = PeriodRepository(dao)

    @org.junit.Test
    fun addRecord_trimsNoteAndStoresRecord() = kotlinx.coroutines.runBlocking {
        val id = repository.addRecord(
            startDate = LocalDate.parse("2026-10-01"),
            endDate = LocalDate.parse("2026-10-05"),
            note = "  腹痛  "
        ).getOrThrow()

        val saved = repository.getRecordById(id).getOrThrow()
        org.junit.Assert.assertEquals("腹痛", saved.note)
        org.junit.Assert.assertEquals(LocalDate.parse("2026-10-01"), saved.startDate)
        org.junit.Assert.assertEquals(LocalDate.parse("2026-10-05"), saved.endDate)
    }

    @org.junit.Test
    fun addRecord_rejectsEndDateBeforeStartDate() = kotlinx.coroutines.runBlocking {
        val result = repository.addRecord(
            startDate = LocalDate.parse("2026-10-05"),
            endDate = LocalDate.parse("2026-10-04")
        )

        org.junit.Assert.assertTrue(result.isFailure)
        org.junit.Assert.assertTrue(result.exceptionOrNull() is PeriodValidationException)
    }

    @org.junit.Test
    fun addRecord_rejectsOverlappingDateRange() = kotlinx.coroutines.runBlocking {
        repository.addRecord(
            startDate = LocalDate.parse("2026-10-01"),
            endDate = LocalDate.parse("2026-10-05")
        ).getOrThrow()

        val overlapping = repository.addRecord(
            startDate = LocalDate.parse("2026-10-05"),
            endDate = LocalDate.parse("2026-10-08")
        )

        org.junit.Assert.assertTrue(overlapping.isFailure)
        org.junit.Assert.assertTrue(
            overlapping.exceptionOrNull() is PeriodValidationException
        )
    }

    @org.junit.Test
    fun addRecord_rejectsNoteLongerThanLimit() = kotlinx.coroutines.runBlocking {
        val result = repository.addRecord(
            startDate = LocalDate.parse("2026-10-01"),
            note = "x".repeat(PeriodRepository.MAX_NOTE_LENGTH + 1)
        )

        org.junit.Assert.assertTrue(result.isFailure)
        org.junit.Assert.assertTrue(result.exceptionOrNull() is PeriodValidationException)
    }

    @org.junit.Test
    fun updateRecord_changesNoteAndDates() = kotlinx.coroutines.runBlocking {
        val id = repository.addRecord(
            startDate = LocalDate.parse("2026-10-01"),
            endDate = LocalDate.parse("2026-10-03"),
            note = "旧备注"
        ).getOrThrow()

        repository.updateRecord(
            id = id,
            startDate = LocalDate.parse("2026-10-02"),
            endDate = LocalDate.parse("2026-10-04"),
            note = "新备注"
        ).getOrThrow()

        val updated = repository.getRecordById(id).getOrThrow()
        org.junit.Assert.assertEquals(LocalDate.parse("2026-10-02"), updated.startDate)
        org.junit.Assert.assertEquals("新备注", updated.note)
    }

    @org.junit.Test
    fun deleteRecord_removesExistingRecord() = kotlinx.coroutines.runBlocking {
        val id = repository.addRecord(
            startDate = LocalDate.parse("2026-10-01")
        ).getOrThrow()

        repository.deleteRecord(id).getOrThrow()

        val result = repository.getRecordById(id)
        org.junit.Assert.assertTrue(result.isFailure)
        org.junit.Assert.assertTrue(result.exceptionOrNull() is PeriodNotFoundException)
    }
}
