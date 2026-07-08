package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HomeResponse
import com.example.data.IptvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val homeResponse: HomeResponse) : HomeUiState()
    object Empty : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(private val repository: IptvRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadHomeData(providerId: String) {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            repository.loadHome(providerId)
                .onSuccess { response ->
                    val rows = response.rows ?: emptyList()
                    // Filter out any rows that are null or have empty items list
                    val validRows = rows.filter { row ->
                        row.items != null && row.items.isNotEmpty()
                    }
                    if (validRows.isEmpty()) {
                        _uiState.value = HomeUiState.Empty
                    } else {
                        _uiState.value = HomeUiState.Success(response.copy(rows = validRows))
                    }
                }
                .onFailure { error ->
                    _uiState.value = HomeUiState.Error(error.message ?: "Failed to connect to trending backend service.")
                }
        }
    }
}
