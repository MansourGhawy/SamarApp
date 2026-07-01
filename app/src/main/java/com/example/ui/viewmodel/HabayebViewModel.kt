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
import java.math.BigDecimal
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
    private val repository = com.example.data.repository.FinanceRepository(database)
    private val ledgerDao = database.ledgerDao()
    private val settingsDao = database.settingsDao()

    private val sharedPrefs = application.getSharedPreferences(FinanceConstants.PREFS_NAME, Context.MODE_PRIVATE)

    // --- Core Flows ---
    val settingsState: StateFlow<AppSettings> = repository.settingsFlow
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val habayebCustomersState: StateFlow<List<HabayebCustomer>> = repository.habayebCustomersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habayebTransactionsState: StateFlow<List<HabayebTransaction>> = repository.habayebTransactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Privacy Mode State ---
    val isPrivacyModeEnabled = MutableStateFlow(true)

    fun togglePrivacyMode() {
        isPrivacyModeEnabled.value = !isPrivacyModeEnabled.value
    }

    // --- Shared Preference Settings ---
    private val _linkHabayebDebtsState = MutableStateFlow(sharedPrefs.getBoolean(FinanceConstants.KEY_LINK_HABAYEB_DEBTS, false))
    val linkHabayebDebtsState = _linkHabayebDebtsState.asStateFlow()

    fun toggleLinkHabayebDebts(enabled: Boolean) {
        _linkHabayebDebtsState.value = enabled
        sharedPrefs.edit().putBoolean(FinanceConstants.KEY_LINK_HABAYEB_DEBTS, enabled).apply()
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
            var owedByThem = BigDecimal.ZERO
            var paymentByThem = BigDecimal.ZERO
            var owedToThem = BigDecimal.ZERO
            var paymentToThem = BigDecimal.ZERO
            for (tx in custTxs) {
                val amountVal = if (tx.is_foreign) {
                    if (tx.is_rate_calculated) tx.equivalent_amount else 0.0
                } else {
                    tx.amount
                }
                val amount = BigDecimal.valueOf(amountVal)
                when (tx.type) {
                    HabayebTransactionType.OWED_BY_THEM.name -> owedByThem = owedByThem.add(amount)
                    HabayebTransactionType.PAYMENT_BY_THEM.name -> paymentByThem = paymentByThem.add(amount)
                    HabayebTransactionType.OWED_TO_THEM.name -> owedToThem = owedToThem.add(amount)
                    HabayebTransactionType.PAYMENT_TO_THEM.name -> paymentToThem = paymentToThem.add(amount)
                }
            }
            val netDebtBd = (owedByThem.subtract(paymentByThem)).subtract(owedToThem.subtract(paymentToThem))
            val netDebt = netDebtBd.toDouble()
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
        var totalOwedByThem = BigDecimal.ZERO
        var totalOwedToThem = BigDecimal.ZERO
        customerStates.forEach { state ->
            val bdVal = BigDecimal.valueOf(state.netDebt)
            if (state.netDebt > 0.0) {
                totalOwedByThem = totalOwedByThem.add(bdVal)
            } else if (state.netDebt < 0.0) {
                totalOwedToThem = totalOwedToThem.add(bdVal.abs())
            }
        }

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
        .map { it.totalOwedByThem.toDouble() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val habayebOwedToThemTotalState: StateFlow<Double> = customersUiState
        .map { it.totalOwedToThem.toDouble() }
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

                repository.insertCustomerWithOpeningTransaction(customer, transaction)
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
        linkedMainTxId: String? = null,
        isForeign: Boolean = false,
        currencyCode: String = "DEFAULT",
        foreignAmount: Double = 0.0,
        exchangeRate: Double = 1.0,
        isRateCalculated: Boolean = false,
        equivalentAmount: Double = 0.0
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
                    linkedMainTxId = linkedMainTxId,
                    is_foreign = isForeign,
                    currency_code = currencyCode,
                    foreign_amount = foreignAmount,
                    exchange_rate = exchangeRate,
                    is_rate_calculated = isRateCalculated,
                    equivalent_amount = equivalentAmount
                )
                repository.insertHabayebTransaction(transaction)
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

    fun updateTransactionExchangeRate(txId: String, newRate: Double, calculateRate: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tx = repository.getHabayebTransactionById(txId)
                if (tx != null) {
                    val finalRate = if (newRate <= 0.0) 1.0 else newRate
                    val finalEquivalent = tx.foreign_amount * finalRate
                    val updatedTx = tx.copy(
                        exchange_rate = finalRate,
                        is_rate_calculated = calculateRate,
                        equivalent_amount = if (calculateRate) finalEquivalent else 0.0
                    )
                    repository.insertHabayebTransaction(updatedTx)
                }
            } catch (e: Exception) {
                android.util.Log.e("HabayebViewModel", "Error updating transaction exchange rate: ${e.message}", e)
            }
        }
    }

    fun toggleTransactionRateCalculation(txId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tx = repository.getHabayebTransactionById(txId)
                if (tx != null) {
                    val nextCalculated = !tx.is_rate_calculated
                    val finalEquivalent = tx.foreign_amount * tx.exchange_rate
                    val updatedTx = tx.copy(
                        is_rate_calculated = nextCalculated,
                        equivalent_amount = if (nextCalculated) finalEquivalent else 0.0
                    )
                    repository.insertHabayebTransaction(updatedTx)
                }
            } catch (e: Exception) {
                android.util.Log.e("HabayebViewModel", "Error toggling transaction rate calculation: ${e.message}", e)
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
                repository.updateCustomerName(customerId, newName)
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
                val customerTxs = repository.getAllTransactionsDirect().filter { it.customerId == customerId }

                if (customer != null) {
                    repository.softDeleteHabayebBundleToTrash(customer, customerTxs)
                }

                repository.deleteCustomerAndTransactions(customerId)
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
                val allTxs = repository.getAllTransactionsDirect()
                for (id in customerIds) {
                    val customer = habayebCustomersState.value.find { it.id == id }
                    val customerTxs = allTxs.filter { it.customerId == id }
                    if (customer != null) {
                        repository.softDeleteHabayebBundleToTrash(customer, customerTxs)
                    }
                    repository.deleteCustomerAndTransactions(id)
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
                val tx = repository.getHabayebTransactionById(txId)
                if (tx != null) {
                    repository.softDeleteHabayebTransactionToTrash(tx)
                }
                if (tx?.linkedMainTxId != null) {
                    val linkedTx = ledgerDao.getAllTransactionsFlow().first().find { it.id == tx.linkedMainTxId }
                    if (linkedTx != null) {
                        repository.softDeleteTransactionToTrash(linkedTx)
                    }
                    ledgerDao.deleteTransactionById(tx.linkedMainTxId)
                }
                repository.deleteHabayebTransactionById(txId)
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
}
