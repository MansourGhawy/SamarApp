package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.AppSettings
import com.example.data.local.CustomCategory
import com.example.data.local.TransactionDb
import com.example.data.local.DeletedItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * LedgerViewModel handles all of the Daily Ledger and General Accounting logic,
 * cleanly isolated from the monolithic FinanceViewModel.
 *
 * It manages:
 * - Dynamic year/month/category filtered StateFlows of transactions.
 * - Reactive and leak-free balance calculations (Total Income, Total Expense, Net Balance).
 * - Full Arabic character search normalization.
 * - Thread-safe insert, update, delete, and soft delete transactions.
 * - Custom category creation and removal.
 * - Dynamic mapping of error states to localized resource IDs with zero hardcoded strings.
 */
class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val ledgerDao = database.ledgerDao()
    private val settingsDao = database.settingsDao()
    private val deletedItemDao = database.deletedItemDao()

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
            val normalizedQuery = normalizeArabic(query)
            transactions.filter { tx ->
                normalizeArabic(tx.description).contains(normalizedQuery, ignoreCase = true) ||
                normalizeArabic(tx.category).contains(normalizedQuery, ignoreCase = true)
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
        transactions.filter { tx ->
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = tx.timestamp * 1000
            }
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
                android.util.Log.e("LedgerViewModel", "Error in addTransaction: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_backup_export_failed),
                        android.widget.Toast.LENGTH_SHORT
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
                android.util.Log.e("LedgerViewModel", "Error in updateTransaction: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_backup_export_failed),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun deleteTransaction(tx: TransactionDb) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                softDeleteTransactionToTrash(tx)
                ledgerDao.deleteTransaction(tx)
            } catch (e: Exception) {
                android.util.Log.e("LedgerViewModel", "Error in deleteTransaction: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_delete_failed),
                        android.widget.Toast.LENGTH_SHORT
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
                    softDeleteTransactionToTrash(tx)
                }
                ledgerDao.deleteTransactionById(id)
            } catch (e: Exception) {
                android.util.Log.e("LedgerViewModel", "Error in deleteTransactionById: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_delete_failed),
                        android.widget.Toast.LENGTH_SHORT
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
                    softDeleteTransactionBundleToTrash(toDelete, bundleTitle)
                    toDelete.forEach { ledgerDao.deleteTransactionById(it.id) }
                }
            } catch (e: Exception) {
                android.util.Log.e("LedgerViewModel", "Error in deleteTransactionsBulk: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_delete_failed),
                        android.widget.Toast.LENGTH_SHORT
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
                android.util.Log.e("LedgerViewModel", "Error in saveCustomCategory: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_backup_export_failed),
                        android.widget.Toast.LENGTH_SHORT
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
                android.util.Log.e("LedgerViewModel", "Error in deleteCustomCategory: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_delete_failed),
                        android.widget.Toast.LENGTH_SHORT
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

    // --- Currency Formatter Utilities ---

    fun formatCurrency(amount: java.math.BigDecimal, symbol: String = "ر.ي"): String {
        return try {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
            val formatter = java.text.DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        } catch (e: Exception) {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
            val formatter = java.text.DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        }
    }

    fun formatDoubleCurrency(amount: Double, symbol: String = "ر.ي"): String {
        return try {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
            val formatter = java.text.DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        } catch (e: Exception) {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
            val formatter = java.text.DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        }
    }

    // --- Private Helper Methods ---

    private fun normalizeArabic(text: String): String {
        return text.replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .trim()
    }

    private suspend fun softDeleteTransactionToTrash(tx: TransactionDb) {
        val jsonData = JSONObject().apply {
            put("id", tx.id)
            put("timestamp", tx.timestamp)
            put("type", tx.type)
            put("category", tx.category)
            put("amount", tx.amount)
            put("description", tx.description)
        }.toString()
        val trashItem = DeletedItemEntity(
            id = tx.id,
            sourceSystem = "دار",
            originalTableName = "transactions",
            jsonData = jsonData
        )
        deletedItemDao.insertDeletedItem(trashItem)
    }

    private suspend fun softDeleteTransactionBundleToTrash(transactions: List<TransactionDb>, title: String) {
        val jsonData = JSONObject().apply {
            val txsArray = JSONArray()
            transactions.forEach { tx ->
                txsArray.put(JSONObject().apply {
                    put("id", tx.id)
                    put("timestamp", tx.timestamp)
                    put("type", tx.type)
                    put("category", tx.category)
                    put("amount", tx.amount)
                    put("description", tx.description)
                })
            }
            put("transactions", txsArray)
            put("totalTransactions", transactions.size)
            val totalNet = transactions.sumOf { if (it.type.equals("INCOME", ignoreCase = true)) it.amount else -it.amount }
            put("totalNet", totalNet)
            put("name", title)
        }.toString()
        val id = "dar_bundle_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(4)}"
        val trashItem = DeletedItemEntity(
            id = id,
            sourceSystem = "دار",
            originalTableName = "dar_bundle",
            jsonData = jsonData
        )
        deletedItemDao.insertDeletedItem(trashItem)
    }
}
