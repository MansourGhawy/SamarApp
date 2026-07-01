package com.example.ui.state

import androidx.compose.runtime.Immutable
import com.example.data.local.entities.HabayebCustomer
import java.math.BigDecimal

@Immutable
data class CustomerUiState(
    val id: String,
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val createdAt: Long = 0L,
    val totalTransactions: Int = 0,
    val netDebt: Double = 0.0,
    val netDebtsByCurrency: Map<String, Double> = emptyMap(),
    val lastTransactionTimestamp: Long = 0L,
    val isStable: Boolean = true,
    val originalCustomer: HabayebCustomer
)

@Immutable
data class CustomersUiState(
    val customers: List<CustomerUiState> = emptyList(),
    val totalOwedByThem: BigDecimal = BigDecimal.ZERO,
    val totalOwedToThem: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = false
)
