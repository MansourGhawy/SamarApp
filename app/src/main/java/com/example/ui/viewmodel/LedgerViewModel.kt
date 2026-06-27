package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.entities.AppSettings
import com.example.data.local.entities.CustomCategory
import com.example.data.local.entities.TransactionDb
import com.example.data.repository.FinanceRepository
import com.example.domain.StringUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LedgerViewModel handles all of the Daily Ledger and General Accounting logic,
 * cleanly isolated from the monolithic FinanceViewModel.
 *
 * It manages:
 * - Dynamic year/month/category filtered StateFlows of transactions.
 * - Reactive and leak-free balance calculations (Total Income, Total Expense, Net Balance).
 * - Full Arabic character search normalization via centralized StringUtils.
 * - Thread-safe insert, update, delete, and soft delete transactions via FinanceRepository.
 * - Custom category creation and removal.
 * - Dynamic mapping of error states to localized resource IDs with zero hardcoded strings.
 */
class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val ledgerDao = database.ledgerDao()
    private val settingsDao = database.settingsDao()
    private val repository = FinanceRepository(database)

    // --- Core Database Flows ---
    val settingsState: StateFlow<AppSettings> = settingsDao.getSettingsFlow()
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val transactionsState: StateFlow<List<TransactionDb>> = ledgerDao.getAllTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customCategoriesState: StateFlow<List<CustomCategory>> = ledgerDao.getAllCustomCategoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Search Query and Normalized Character Processing ---
    val searchQuery = MutableStateFlow("")

    val searchResultsState: StateFlow<List<TransactionDb>> = combine(
        transactionsState,
        searchQuery
    ) { transactions, query ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val normalizedQuery = StringUtils.normalizeArabic(query)
            transactions.filter { tx ->
                StringUtils.normalizeArabic(tx.description).contains(normalizedQuery, ignoreCase = true) ||
                StringUtils.normalizeArabic(tx.category).contains(normalizedQuery, ignoreCase = true)
            }.sortedByDescending { it.timestamp }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Interactive Filtering States ---
    val selectedYear = MutableStateFlow<Int?>(null)
    val selectedMonth = MutableStateFlow<Int?>(null)
    val selectedCategory = MutableStateFlow<String?>(null)

    // Combined filtered transactions stream
    val filteredTransactionsState: StateFlow<List<TransactionDb>> = combine(
        transactionsState,
        selectedYear,
        selectedMonth,
        selectedCategory
    ) { transactions, year, month, category ->
        val calendar = java.util.Calendar.getInstance()
        transactions.filter { tx ->
            calendar.timeInMillis = tx.timestamp * 1000
            val txYear = calendar.get(java.util.Calendar.YEAR)
            val txMonth = calendar.get(java.util.Calendar.MONTH) + 1

            val yearMatches = year == null || txYear == year
            val monthMatches = month == null || txMonth == month
            val categoryMatches = category == null || tx.category.trim() == category.trim()

            yearMatches && monthMatches && categoryMatches
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- General Balance and Dynamic Accounting Calculations ---
    val totalIncomeState: StateFlow<Double> = filteredTransactionsState
        .map { txList ->
            txList.filter { it.type.equals("INCOME", ignoreCase = true) }.sumOf { it.amount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpenseState: StateFlow<Double> = filteredTransactionsState
        .map { txList ->
            txList.filter { it.type.equals("EXPENSE", ignoreCase = true) }.sumOf { it.amount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netBalanceState: StateFlow<Double> = combine(totalIncomeState, totalExpenseState) { income, expense ->
        income - expense
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // --- Thread-Safe Core Ledger Mutations (IO-Bound) ---

    fun addTransaction(
        type: String,
        category: String,
        amount: Double,
        description: String,
        timestamp: Long = System.currentTimeMillis() / 1000,
        presetId: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = presetId ?: "tx_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
                val tx = TransactionDb(
                    id = id,
                    timestamp = timestamp,
                    type = type,
                    category = category,
                    amount = amount,
                    description = description
                )
                ledgerDao.insertTransaction(tx)
            } catch (e: Exception) {
                Log.e("LedgerViewModel", "Error in addTransaction: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun updateTransaction(tx: TransactionDb) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ledgerDao.insertTransaction(tx)
            } catch (e: Exception) {
                Log.e("LedgerViewModel", "Error in updateTransaction: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun deleteTransaction(tx: TransactionDb) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.softDeleteTransactionToTrash(tx)
                ledgerDao.deleteTransaction(tx)
            } catch (e: Exception) {
                Log.e("LedgerViewModel", "Error in deleteTransaction: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_delete_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun deleteTransactionById(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tx = transactionsState.value.find { it.id == id }
                if (tx != null) {
                    repository.softDeleteTransactionToTrash(tx)
                }
                ledgerDao.deleteTransactionById(id)
            } catch (e: Exception) {
                Log.e("LedgerViewModel", "Error in deleteTransactionById: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_delete_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun deleteTransactionsBulk(ids: List<String>, bundleTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allTxs = transactionsState.value
                val toDelete = allTxs.filter { ids.contains(it.id) }
                if (toDelete.isNotEmpty()) {
                    repository.softDeleteTransactionBundleToTrash(toDelete, bundleTitle)
                    toDelete.forEach { ledgerDao.deleteTransactionById(it.id) }
                }
            } catch (e: Exception) {
                Log.e("LedgerViewModel", "Error in deleteTransactionsBulk: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_delete_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // --- Category Actions ---

    fun saveCustomCategory(name: String, tabType: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ledgerDao.insertCategory(CustomCategory(name = name, tabType = tabType, iconEmoji = emoji))
            } catch (e: Exception) {
                Log.e("LedgerViewModel", "Error in saveCustomCategory: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun deleteCustomCategory(customCategory: CustomCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ledgerDao.deleteCategory(customCategory)
            } catch (e: Exception) {
                Log.e("LedgerViewModel", "Error in deleteCustomCategory: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_delete_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // --- Interactive Filtering Actions ---

    fun selectYear(year: Int?) {
        selectedYear.value = year
    }

    fun selectMonth(month: Int?) {
        selectedMonth.value = month
    }

    fun selectCategory(category: String?) {
        selectedCategory.value = category
    }

    fun clearFilters() {
        selectedYear.value = null
        selectedMonth.value = null
        selectedCategory.value = null
    }
}
