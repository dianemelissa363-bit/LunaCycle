package com.example.lunacycle.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PeriodRoomIntegrationTest {

    private lateinit var database: LunaDatabase
    private lateinit var dao: PeriodDao
    private lateinit var repository: PeriodRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            LunaDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        dao = database.periodDao()
        repository = PeriodRepository(dao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndRead_preservesDatesAndNote() = runBlocking {
        val id = repository.addRecord(
            startDate = LocalDate.parse("2026-10-01"),
            endDate = LocalDate.parse("2026-10-05"),
            note = "腹痛"
        ).getOrThrow()

        val saved = dao.getRecordById(id)

        assertEquals(LocalDate.parse("2026-10-01"), saved?.startDate)
        assertEquals(LocalDate.parse("2026-10-05"), saved?.endDate)
        assertEquals("腹痛", saved?.note)
    }

    @Test
    fun insertIfNoOverlap_rejectsOverlappingRange() = runBlocking {
        repository.addRecord(
            startDate = LocalDate.parse("2026-10-01"),
            endDate = LocalDate.parse("2026-10-05")
        ).getOrThrow()

        val result = repository.addRecord(
            startDate = LocalDate.parse("2026-10-05"),
            endDate = LocalDate.parse("2026-10-08")
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PeriodValidationException)
        assertEquals(1, dao.observeAllRecords().first().size)
    }

    @Test
    fun openEndedRecord_rejectsLaterRecord() = runBlocking {
        repository.addRecord(
            startDate = LocalDate.parse("2026-10-01"),
            endDate = null
        ).getOrThrow()

        val result = repository.addRecord(
            startDate = LocalDate.parse("2026-10-10"),
            endDate = LocalDate.parse("2026-10-12")
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PeriodValidationException)
    }

    @Test
    fun updateRecord_persistsChangesAndPreservesCreatedAt() = runBlocking {
        val id = repository.addRecord(
            startDate = LocalDate.parse("2026-10-01"),
            endDate = LocalDate.parse("2026-10-03"),
            note = "旧备注"
        ).getOrThrow()
        val before = dao.getRecordById(id)!!

        repository.updateRecord(
            id = id,
            startDate = LocalDate.parse("2026-10-02"),
            endDate = LocalDate.parse("2026-10-04"),
            note = "新备注"
        ).getOrThrow()

        val after = dao.getRecordById(id)!!
        assertEquals(LocalDate.parse("2026-10-02"), after.startDate)
        assertEquals(LocalDate.parse("2026-10-04"), after.endDate)
        assertEquals("新备注", after.note)
        assertEquals(before.createdAt, after.createdAt)
        assertTrue(after.updatedAt >= before.updatedAt)
    }

    @Test
    fun updateRecord_rejectsOverlapWithoutChangingOriginalRecord() = runBlocking {
        val firstId = repository.addRecord(
            startDate = LocalDate.parse("2026-10-01"),
            endDate = LocalDate.parse("2026-10-03"),
            note = "原记录"
        ).getOrThrow()
        repository.addRecord(
            startDate = LocalDate.parse("2026-10-10"),
            endDate = LocalDate.parse("2026-10-12")
        ).getOrThrow()

        val result = repository.updateRecord(
            id = firstId,
            startDate = LocalDate.parse("2026-10-11"),
            endDate = LocalDate.parse("2026-10-13"),
            note = "不应保存"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PeriodValidationException)
        val unchanged = dao.getRecordById(firstId)!!
        assertEquals(LocalDate.parse("2026-10-01"), unchanged.startDate)
        assertEquals("原记录", unchanged.note)
    }

    @Test
    fun deleteRecord_removesRowFromDatabase() = runBlocking {
        val id = repository.addRecord(
            startDate = LocalDate.parse("2026-10-01")
        ).getOrThrow()

        repository.deleteRecord(id).getOrThrow()

        assertEquals(null, dao.getRecordById(id))
        assertTrue(dao.observeAllRecords().first().isEmpty())
    }
}
