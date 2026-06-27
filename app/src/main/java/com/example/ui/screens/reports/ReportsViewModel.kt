package com.example.ui.screens.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entities.HabayebCustomer
import com.example.data.local.entities.HabayebTransaction
import com.example.data.local.entities.TransactionDb
import com.example.ui.state.HabayebComputationResult
import com.example.ui.state.MizanComputationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.math.BigDecimal
import java.util.Calendar

class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val ledgerDao = database.ledgerDao()
    private val habayebDao = database.habayebDao()

    // Raw streams from DB
    val transactionsFlow: Flow<List<TransactionDb>> = ledgerDao.getAllTransactionsFlow()
    val habayebCustomersFlow: Flow<List<HabayebCustomer>> = habayebDao.getAllCustomersFlow()
    val habayebTransactionsFlow: Flow<List<HabayebTransaction>> = habayebDao.getAllTransactionsFlow()

    // UI Interactive Selection State
    val selectedPeriod = MutableStateFlow("MONTHLY") // DAILY, WEEKLY, MONTHLY, YEARLY, ALL, CUSTOM
    val customStartDateMs = MutableStateFlow<Long?>(null)
    val customEndDateMs = MutableStateFlow<Long?>(null)
    val customDays = MutableStateFlow<Int?>(null)

    val isChartExpanded = MutableStateFlow(true)
    val habayebSearchQuery = MutableStateFlow("")
    val activeReportTab = MutableStateFlow(0) // 0 = ميزان الدار, 1 = ديون حبايب

    // Tab 0: Asynchronous transaction filtering & categorizations
    val mizanComputationState: StateFlow<MizanComputationResult> = combine(
        transactionsFlow,
        selectedPeriod,
        customStartDateMs,
        customEndDateMs,
        customDays
    ) { transactions, period, startMs, endMs, days ->
        val currentMs = System.currentTimeMillis()
        val filtered = transactions.filter { tx ->
            val txMs = tx.timestamp * 1000L
            when (period) {
                "DAILY" -> {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = currentMs
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    txMs >= cal.timeInMillis
                }
                "WEEKLY" -> {
                    val oneWeekAgo = currentMs - (7L * 24 * 60 * 60 * 1000L)
                    txMs >= oneWeekAgo
                }
                "MONTHLY" -> {
                    val oneMonthAgo = currentMs - (30L * 24 * 60 * 60 * 1000L)
                    txMs >= oneMonthAgo
                }
                "YEARLY" -> {
                    val oneYearAgo = currentMs - (365L * 24 * 60 * 60 * 1000L)
                    txMs >= oneYearAgo
                }
                "CUSTOM" -> {
                    if (days != null) {
                        val dMs = currentMs - (days * 24L * 60 * 60 * 1000L)
                        txMs >= dMs
                    } else if (startMs != null || endMs != null) {
                        val start = startMs ?: 0L
                        val end = endMs ?: Long.MAX_VALUE
                        txMs in start..end
                    } else true
                }
                else -> true // ALL
            }
        }
        val incs = filtered.filter { it.type == "INCOME" }
        val exps = filtered.filter { it.type == "EXPENSE" }

        val totalInc = incs.fold(BigDecimal.ZERO) { acc, tx -> acc.add(BigDecimal.valueOf(tx.amount)) }
        val totalExp = exps.fold(BigDecimal.ZERO) { acc, tx -> acc.add(BigDecimal.valueOf(tx.amount)) }
        val netSav = totalInc.subtract(totalExp)

        val map = mutableMapOf<String, BigDecimal>()
        for (tx in exps) {
            val key = tx.category
            val amount = BigDecimal.valueOf(tx.amount)
            map[key] = (map[key] ?: BigDecimal.ZERO).add(amount)
        }
        val sortedCatTotals = map.toList().sortedByDescending { it.second }

        MizanComputationResult(
            filtered = filtered,
            incomes = incs,
            expenses = exps,
            totalIncome = totalInc,
            totalExpense = totalExp,
            netSavings = netSav,
            categoryTotals = sortedCatTotals
        )
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MizanComputationResult())

    // Tab 1: Asynchronous high-performance O(N + M) customer profiles construction
    val habayebComputationState: StateFlow<HabayebComputationResult> = combine(
        habayebCustomersFlow,
        habayebTransactionsFlow,
        habayebSearchQuery
    ) { customers, transactions, query ->
        val txsByCustomer = transactions.groupBy { it.customerId }
        val profiles = customers.map { customer ->
            val customerTxs = txsByCustomer[customer.id] ?: emptyList()
            var owedByThem = 0.0
            var paymentByThem = 0.0
            var owedToThem = 0.0
            var paymentToThem = 0.0
            for (tx in customerTxs) {
                when (tx.type) {
                    "OWED_BY_THEM" -> owedByThem += tx.amount
                    "PAYMENT_BY_THEM" -> paymentByThem += tx.amount
                    "OWED_TO_THEM" -> owedToThem += tx.amount
                    "PAYMENT_TO_THEM" -> paymentToThem += tx.amount
                }
            }
            val netDebt = (owedByThem - paymentByThem) - (owedToThem - paymentToThem)
            customer to netDebt
        }

        val filtered = if (query.isBlank()) {
            profiles
        } else {
            profiles.filter {
                it.first.name.contains(query, ignoreCase = true) ||
                it.first.phone.contains(query, ignoreCase = true)
            }
        }

        HabayebComputationResult(
            profiles = profiles,
            filtered = filtered
        )
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabayebComputationResult())

    // State Mutation Mutators
    fun selectPeriod(period: String) {
        selectedPeriod.value = period
    }

    fun setCustomDateRange(startMs: Long?, endMs: Long?) {
        customStartDateMs.value = startMs
        customEndDateMs.value = endMs
        customDays.value = null
    }

    fun setCustomDays(days: Int?) {
        customDays.value = days
        customStartDateMs.value = null
        customEndDateMs.value = null
    }

    fun toggleChartExpanded() {
        isChartExpanded.value = !isChartExpanded.value
    }

    fun updateHabayebSearchQuery(query: String) {
        habayebSearchQuery.value = query
    }

    fun setActiveReportTab(tab: Int) {
        activeReportTab.value = tab
    }
}
