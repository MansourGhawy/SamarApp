package com.example.ui.state

import androidx.compose.runtime.Immutable
import com.example.data.local.entities.TransactionDb

@Immutable
data class MainLedgerUiState(
    val transactions: List<TransactionDb> = emptyList(),
    val totalCash: Double = 0.0,
    val isSearching: Boolean = false,
    val isLoading: Boolean = false
)
