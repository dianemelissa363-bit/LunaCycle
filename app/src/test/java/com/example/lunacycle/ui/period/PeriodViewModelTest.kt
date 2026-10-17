package com.example.lunacycle.ui.period

import com.example.lunacycle.data.PeriodDao
import com.example.lunacycle.data.PeriodRecordEntity
import com.example.lunacycle.data.PeriodRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakePeriodDao
    private lateinit var repository: PeriodRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakePeriodDao()
        repository = PeriodRepository(fakeDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_startsLoading_andCollectsRecordsFlow() = runTest(testDispatcher) {
        val viewModel = PeriodViewModel(repository)

        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.records.isEmpty())

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.records.isEmpty())
        assertFalse(state.isSaving)
        assertNull(state.errorMessage)
        assertNull(state.successMessage)
    }

    @Test
    fun addRecord_success_updatesRecordsAndSuccessMessage() = runTest(testDispatcher) {
        val viewModel = PeriodViewModel(repository)
        advanceUntilIdle()

        val start = LocalDate.of(2026, 10, 1)
        val end = LocalDate.of(2026, 10, 5)
        viewModel.addRecord(startDate = start, endDate = end, note = "  第一天轻微不适  ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertNull(state.errorMessage)
        assertEquals("已保存经期记录", state.successMessage)
        assertEquals(1, state.records.size)
        assertEquals(start, state.records[0].startDate)
        assertEquals(end, state.records[0].endDate)
        assertEquals("第一天轻微不适", state.records[0].note)
    }

    @Test
    fun addRecord_failure_setsErrorMessageAndResetsIsSaving() = runTest(testDispatcher) {
        val viewModel = PeriodViewModel(repository)
        advanceUntilIdle()

        viewModel.addRecord(
            startDate = LocalDate.of(2026, 10, 10),
            endDate = LocalDate.of(2026, 10, 5)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertNull(state.successMessage)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("结束日期不能早于开始日期"))
        assertTrue(state.records.isEmpty())
    }

    @Test
    fun updateRecord_successAndFailure_updatesStateAccurately() = runTest(testDispatcher) {
        val viewModel = PeriodViewModel(repository)
        advanceUntilIdle()

        viewModel.addRecord(
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 5)
        )
        advanceUntilIdle()
        val id = viewModel.uiState.value.records.first().id

        viewModel.updateRecord(
            id = id,
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 6),
            note = "更新备注"
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("已更新经期记录", viewModel.uiState.value.successMessage)
        assertEquals(LocalDate.of(2026, 9, 6), viewModel.uiState.value.records.first().endDate)
        assertEquals("更新备注", viewModel.uiState.value.records.first().note)

        viewModel.updateRecord(
            id = 9999L,
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 5)
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.successMessage)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun deleteRecord_successAndFailure_updatesStateAccurately() = runTest(testDispatcher) {
        val viewModel = PeriodViewModel(repository)
        advanceUntilIdle()

        viewModel.addRecord(
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 4)
        )
        advanceUntilIdle()
        val id = viewModel.uiState.value.records.first().id

        viewModel.deleteRecord(id)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.records.isEmpty())
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("已删除经期记录", viewModel.uiState.value.successMessage)

        viewModel.deleteRecord(id)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.successMessage)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun duplicateSubmission_isIgnoredWhileMutexIsLocked() = runTest(testDispatcher) {
        val viewModel = PeriodViewModel(repository)
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        fakeDao.insertGate = gate

        // 发起第一次请求并推进调度器使其进入协程获取锁并挂起在 insertGate
        viewModel.addRecord(startDate = LocalDate.of(2026, 10, 1))
        runCurrent()
        assertTrue(viewModel.uiState.value.isSaving)

        // 在第一次请求未完成（锁被占用）期间发起第二次重复提交，应被 tryLock() 拦截忽略
        viewModel.addRecord(startDate = LocalDate.of(2026, 9, 1))
        runCurrent()

        // 放行第一次请求
        gate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(1, viewModel.uiState.value.records.size)
        assertEquals(LocalDate.of(2026, 10, 1), viewModel.uiState.value.records.first().startDate)
    }

    private class FakePeriodDao : PeriodDao {
        private val records = mutableListOf<PeriodRecordEntity>()
        private val recordsFlow = MutableStateFlow<List<PeriodRecordEntity>>(emptyList())
        private var nextId = 1L
        var insertGate: CompletableDeferred<Unit>? = null

        override fun observeAllRecords(): Flow<List<PeriodRecordEntity>> = recordsFlow

        override suspend fun getRecordById(id: Long): PeriodRecordEntity? {
            return records.find { it.id == id }
        }

        override suspend fun insert(record: PeriodRecordEntity): Long {
            insertGate?.await()
            val inserted = record.copy(id = nextId++)
            records.add(inserted)
            emitSorted()
            return inserted.id
        }

        override suspend fun update(record: PeriodRecordEntity): Int {
            val index = records.indexOfFirst { it.id == record.id }
            if (index == -1) return 0
            records[index] = record
            emitSorted()
            return 1
        }

        override suspend fun deleteById(id: Long): Int {
            val removed = records.removeIf { it.id == id }
            if (removed) {
                emitSorted()
                return 1
            }
            return 0
        }

        override suspend fun countOverlappingRecords(
            newStart: LocalDate,
            newEnd: LocalDate?,
            excludeId: Long
        ): Int {
            return records.count { existing ->
                existing.id != excludeId &&
                    (newEnd == null || !existing.startDate.isAfter(newEnd)) &&
                    (existing.endDate == null || !existing.endDate.isBefore(newStart))
            }
        }

        private fun emitSorted() {
            recordsFlow.value = records.sortedWith(
                compareByDescending<PeriodRecordEntity> { it.startDate }
                    .thenByDescending { it.id }
            )
        }
    }
}