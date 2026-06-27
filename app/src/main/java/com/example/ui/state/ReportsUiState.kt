package com.example.ui.state

import androidx.compose.runtime.Immutable
import com.example.data.local.entities.HabayebCustomer
import com.example.data.local.entities.TransactionDb
import java.math.BigDecimal

@Immutable
data class MizanComputationResult(
    val filtered: List<TransactionDb> = emptyList(),
    val incomes: List<TransactionDb> = emptyList(),
    val expenses: List<TransactionDb> = emptyList(),
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpense: BigDecimal = BigDecimal.ZERO,
    val netSavings: BigDecimal = BigDecimal.ZERO,
    val categoryTotals: List<Pair<String, BigDecimal>> = emptyList()
)

@Immutable
data class HabayebComputationResult(
    val profiles: List<Pair<HabayebCustomer, Double>> = emptyList(),
    val filtered: List<Pair<HabayebCustomer, Double>> = emptyList()
)
