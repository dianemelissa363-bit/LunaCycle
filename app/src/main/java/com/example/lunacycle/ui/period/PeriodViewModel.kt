package com.example.lunacycle.ui.period

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lunacycle.data.PeriodRecordEntity
import com.example.lunacycle.data.PeriodRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate

data class PeriodUiState(
    val records: List<PeriodRecordEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class PeriodViewModel(
    private val repository: PeriodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeriodUiState())
    val uiState: StateFlow<PeriodUiState> = _uiState.asStateFlow()

    private val mutationMutex = Mutex()

    init {
        observeRecords()
    }

    private fun observeRecords() {
        viewModelScope.launch {
            repository.allRecords
                .catch { throwable ->
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = throwable.toUserMessage("加载经期记录失败")
                        )
                    }
                }
                .collect { records ->
                    _uiState.update { current ->
                        current.copy(
                            records = records,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun addRecord(
        startDate: LocalDate,
        endDate: LocalDate? = null,
        note: String? = null
    ) {
        executeMutation(
            fallbackError = "新增经期记录失败",
            successText = "已保存经期记录"
        ) {
            repository.addRecord(
                startDate = startDate,
                endDate = endDate,
                note = note
            )
        }
    }

    fun updateRecord(
        id: Long,
        startDate: LocalDate,
        endDate: LocalDate? = null,
        note: String? = null
    ) {
        executeMutation(
            fallbackError = "更新经期记录失败",
            successText = "已更新经期记录"
        ) {
            repository.updateRecord(
                id = id,
                startDate = startDate,
                endDate = endDate,
                note = note
            )
        }
    }

    fun deleteRecord(id: Long) {
        executeMutation(
            fallbackError = "删除经期记录失败",
            successText = "已删除经期记录"
        ) {
            repository.deleteRecord(id = id)
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun clearMessages() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                successMessage = null
            )
        }
    }

    private fun <T> executeMutation(
        fallbackError: String,
        successText: String,
        action: suspend () -> Result<T>
    ) {
        viewModelScope.launch {
            if (!mutationMutex.tryLock()) {
                return@launch
            }
            try {
                try {
                    _uiState.update { current ->
                        current.copy(
                            isSaving = true,
                            errorMessage = null,
                            successMessage = null
                        )
                    }

                    val result = action()
                    result.fold(
                        onSuccess = {
                            _uiState.update { current ->
                                current.copy(
                                    isSaving = false,
                                    errorMessage = null,
                                    successMessage = successText
                                )
                            }
                        },
                        onFailure = { error ->
                            if (error is CancellationException) {
                                throw error
                            }
                            _uiState.update { current ->
                                current.copy(
                                    isSaving = false,
                                    errorMessage = error.toUserMessage(fallbackError),
                                    successMessage = null
                                )
                            }
                        }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            errorMessage = e.toUserMessage(fallbackError),
                            successMessage = null
                        )
                    }
                } finally {
                    _uiState.update { current ->
                        if (current.isSaving) current.copy(isSaving = false) else current
                    }
                }
            } finally {
                mutationMutex.unlock()
            }
        }
    }

    private fun Throwable.toUserMessage(fallback: String): String {
        val detail = this.message?.trim()
        return if (!detail.isNullOrEmpty()) detail else fallback
    }
}

class PeriodViewModelFactory(
    private val repository: PeriodRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PeriodViewModel::class.java)) {
            return PeriodViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}