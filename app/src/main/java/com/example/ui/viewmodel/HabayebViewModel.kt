package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.entities.AppSettings
import com.example.data.local.entities.DeletedItemEntity
import com.example.data.local.entities.HabayebCustomer
import com.example.data.local.entities.HabayebTransaction
import com.example.data.local.entities.TransactionDb
import com.example.domain.LicenseManager
import com.example.ui.state.CustomerUiState
import com.example.ui.state.CustomersUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * HabayebViewModel handles all Customer & Debt (حسابات الحبايب) logic.
 * Cleanly isolated from FinanceViewModel.
 *
 * It manages:
 * - Reactive and memory-safe StateFlow of customer lists and net debts.
 * - Reactive StateFlow metrics: Total Credit ("لي عند الناس"), Total Debit ("علي للناس"), and Net Debt Balance ("الصافي").
 * - Full Arabic character search normalization.
 * - Thread-safe insert, update, delete, and soft delete actions on Dispatchers.IO.
 * - Dynamic error mapping to localized string resources.
 */
class HabayebViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val habayebDao = database.habayebDao()
    private val ledgerDao = database.ledgerDao()
    private val settingsDao = database.settingsDao()
    private val deletedItemDao = database.deletedItemDao()

    private val sharedPrefs = application.getSharedPreferences("mizan_prefs", Context.MODE_PRIVATE)

    // --- Core Flows ---
    val settingsState: StateFlow<AppSettings> = settingsDao.getSettingsFlow()
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val habayebCustomersState: StateFlow<List<HabayebCustomer>> = habayebDao.getAllCustomersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habayebTransactionsState: StateFlow<List<HabayebTransaction>> = habayebDao.getAllTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Privacy Mode State ---
    val isPrivacyModeEnabled = MutableStateFlow(true)

    fun togglePrivacyMode() {
        isPrivacyModeEnabled.value = !isPrivacyModeEnabled.value
    }

    // --- Shared Preference Settings ---
    private val _linkHabayebDebtsState = MutableStateFlow(sharedPrefs.getBoolean("link_habayeb_debts", false))
    val linkHabayebDebtsState = _linkHabayebDebtsState.asStateFlow()

    fun toggleLinkHabayebDebts(enabled: Boolean) {
        _linkHabayebDebtsState.value = enabled
        sharedPrefs.edit().putBoolean("link_habayeb_debts", enabled).apply()
    }

    // --- Licensing & Transaction Limit States ---
    private val _activationTrigger = MutableStateFlow(0)
    val showActivationRequired = MutableStateFlow(false)

    val deviceIdState: StateFlow<String> = flow {
        emit(LicenseManager.getOrGenerateUnifiedDeviceId(getApplication()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LicenseManager.getOrGenerateUnifiedDeviceId(getApplication()))

    val isActivatedState: StateFlow<Boolean> = combine(deviceIdState, _activationTrigger) { deviceId, _ ->
        val prefs = getApplication<Application>().getSharedPreferences("mizan_sec_prefs", Context.MODE_PRIVATE)
        val enteredCode = prefs.getString("m_act_code", "") ?: ""
        if (enteredCode.isBlank()) {
            false
        } else {
            LicenseManager.verifyActivationCode(deviceId, enteredCode)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val totalTransactionsCount: StateFlow<Int> = combine(
        ledgerDao.getAllTransactionsFlow(),
        habayebTransactionsState
    ) { main, habayeb ->
        main.size + habayeb.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun isTrialExpired(): Boolean {
        val cap = LicenseManager.getSecureLimitVal()
        val count = totalTransactionsCount.value
        val activated = isActivatedState.value
        return !activated && count >= cap
    }

    // --- Customer UI States & Accounting Calculations ---
    val customersUiState: StateFlow<CustomersUiState> = combine(
        habayebCustomersState,
        habayebTransactionsState
    ) { customers, transactions ->
        val txsByCustomer = transactions.groupBy { it.customerId }
        val customerStates = customers.map { customer ->
            val custTxs = txsByCustomer[customer.id] ?: emptyList()
            var owedByThem = 0.0
            var paymentByThem = 0.0
            var owedToThem = 0.0
            var paymentToThem = 0.0
            for (tx in custTxs) {
                when (tx.type) {
                    "OWED_BY_THEM" -> owedByThem += tx.amount
                    "PAYMENT_BY_THEM" -> paymentByThem += tx.amount
                    "OWED_TO_THEM" -> owedToThem += tx.amount
                    "PAYMENT_TO_THEM" -> paymentToThem += tx.amount
                }
            }
            val netDebt = (owedByThem - paymentByThem) - (owedToThem - paymentToThem)
            val lastTxTime = custTxs.maxOfOrNull { it.timestamp } ?: customer.createdAt
            CustomerUiState(
                id = customer.id,
                name = customer.name,
                phone = customer.phone,
                notes = customer.notes,
                createdAt = customer.createdAt,
                totalTransactions = custTxs.size,
                netDebt = netDebt,
                lastTransactionTimestamp = lastTxTime,
                originalCustomer = customer
            )
        }
        val totalOwedByThem = customerStates.filter { it.netDebt > 0.0 }.sumOf { it.netDebt }
        val totalOwedToThem = customerStates.filter { it.netDebt < 0.0 }.sumOf { kotlin.math.abs(it.netDebt) }

        CustomersUiState(
            customers = customerStates,
            totalOwedByThem = totalOwedByThem,
            totalOwedToThem = totalOwedToThem,
            isLoading = false
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomersUiState())

    // Metrics Exposing Flow
    val habayebOwedByThemTotalState: StateFlow<Double> = customersUiState
        .map { it.totalOwedByThem }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val habayebOwedToThemTotalState: StateFlow<Double> = customersUiState
        .map { it.totalOwedToThem }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val habayebNetBalanceState: StateFlow<Double> = combine(
        habayebOwedByThemTotalState,
        habayebOwedToThemTotalState
    ) { credit, debit ->
        credit - debit
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // --- Thread-Safe Core Customers & Debts Mutations (IO-Bound) ---

    fun saveHabayebCustomer(
        customer: HabayebCustomer,
        initialAmount: Double,
        initialType: String,
        customTimestamp: Long = System.currentTimeMillis() / 1000,
        initialDetails: String = ""
    ) {
        if (initialAmount > 0.0 && isTrialExpired()) {
            showActivationRequired.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val transaction = if (initialAmount > 0.0) {
                    val txId = "dtx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
                    HabayebTransaction(
                        id = txId,
                        customerId = customer.id,
                        type = initialType,
                        amount = initialAmount,
                        timestamp = customTimestamp,
                        description = initialDetails.ifEmpty { customer.notes }
                    )
                } else null

                habayebDao.insertCustomerWithOpeningTransaction(customer, transaction)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.habayeb_toast_save_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("HabayebViewModel", "Error in saveHabayebCustomer: ${e.message}", e)
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

    fun addHabayebTransaction(
        customerId: String,
        type: String,
        amount: Double,
        desc: String,
        timestamp: Long = System.currentTimeMillis() / 1000,
        linkedMainTxId: String? = null
    ) {
        if (isTrialExpired()) {
            showActivationRequired.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val txId = "dtx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
                val transaction = HabayebTransaction(
                    id = txId,
                    customerId = customerId,
                    type = type,
                    amount = amount,
                    timestamp = timestamp,
                    description = desc,
                    linkedMainTxId = linkedMainTxId
                )
                habayebDao.insertTransaction(transaction)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.habayeb_toast_tx_save_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("HabayebViewModel", "Error in addHabayebTransaction: ${e.message}", e)
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
                val id = presetId ?: "tx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
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
                android.util.Log.e("HabayebViewModel", "Error in addTransaction: ${e.message}", e)
            }
        }
    }

    fun updateHabayebCustomerName(customerId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                habayebDao.updateCustomerName(customerId, newName)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.habayeb_toast_update_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("HabayebViewModel", "Error in updateHabayebCustomerName: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.toast_operation_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun deleteHabayebCustomer(customerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val customer = habayebCustomersState.value.find { it.id == customerId }
                val customerTxs = habayebDao.getAllTransactionsDirect().filter { it.customerId == customerId }

                if (customer != null) {
                    softDeleteHabayebBundleToTrash(customer, customerTxs)
                }

                habayebDao.deleteCustomerAndTransactions(customerId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.habayeb_toast_delete_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("HabayebViewModel", "Error in deleteHabayebCustomer: ${e.message}", e)
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

    fun deleteMultipleHabayebCustomers(customerIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allTxs = habayebDao.getAllTransactionsDirect()
                for (id in customerIds) {
                    val customer = habayebCustomersState.value.find { it.id == id }
                    val customerTxs = allTxs.filter { it.customerId == id }
                    if (customer != null) {
                        softDeleteHabayebBundleToTrash(customer, customerTxs)
                    }
                    habayebDao.deleteCustomerAndTransactions(id)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.habayeb_toast_delete_success),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("HabayebViewModel", "Error in deleteMultipleHabayebCustomers: ${e.message}", e)
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

    fun deleteHabayebTransaction(txId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tx = habayebDao.getTransactionById(txId)
                if (tx != null) {
                    softDeleteHabayebTransactionToTrash(tx)
                }
                if (tx?.linkedMainTxId != null) {
                    val linkedTx = ledgerDao.getAllTransactionsFlow().first().find { it.id == tx.linkedMainTxId }
                    if (linkedTx != null) {
                        softDeleteTransactionToTrash(linkedTx)
                    }
                    ledgerDao.deleteTransactionById(tx.linkedMainTxId)
                }
                habayebDao.deleteTransactionById(txId)
            } catch (e: Exception) {
                android.util.Log.e("HabayebViewModel", "Error in deleteHabayebTransaction: ${e.message}", e)
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

    // --- Private Helper Methods ---

    private suspend fun softDeleteHabayebBundleToTrash(customer: HabayebCustomer, transactions: List<HabayebTransaction>) {
        val jsonData = JSONObject().apply {
            put("customer", JSONObject().apply {
                put("id", customer.id)
                put("name", customer.name)
                put("phone", customer.phone)
                put("notes", customer.notes)
                put("createdAt", customer.createdAt)
            })
            val txsArray = JSONArray()
            transactions.forEach { tx ->
                txsArray.put(JSONObject().apply {
                    put("id", tx.id)
                    put("customerId", tx.customerId)
                    put("type", tx.type)
                    put("amount", tx.amount)
                    put("timestamp", tx.timestamp)
                    put("description", tx.description)
                    put("linkedMainTxId", tx.linkedMainTxId ?: JSONObject.NULL)
                })
            }
            put("transactions", txsArray)
            put("totalTransactions", transactions.size)
            put("name", customer.name)
        }.toString()
        val trashItem = DeletedItemEntity(
            id = "bundle_${customer.id}",
            sourceSystem = "حبايب",
            originalTableName = "habayeb_bundle",
            jsonData = jsonData
        )
        deletedItemDao.insertDeletedItem(trashItem)
    }

    private suspend fun softDeleteHabayebTransactionToTrash(tx: HabayebTransaction) {
        val jsonData = JSONObject().apply {
            put("id", tx.id)
            put("customerId", tx.customerId)
            put("type", tx.type)
            put("amount", tx.amount)
            put("timestamp", tx.timestamp)
            put("description", tx.description)
            put("linkedMainTxId", tx.linkedMainTxId ?: JSONObject.NULL)
        }.toString()
        val trashItem = DeletedItemEntity(
            id = tx.id,
            sourceSystem = "حبايب",
            originalTableName = "habayeb_transactions",
            jsonData = jsonData
        )
        deletedItemDao.insertDeletedItem(trashItem)
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
}
