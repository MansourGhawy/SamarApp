package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import com.example.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.local.entities.*
import com.example.data.GoogleDriveSyncHelper
import com.example.data.CloudSyncState
import com.example.data.CloudBackupFile
import com.example.data.repository.FinanceRepository
import com.example.domain.DateUtils
import com.example.data.serialization.MzdBackupSerializer
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.UUID

sealed class UiEvent {
    data class ShowToast(val messageRes: Int, val isLong: Boolean = false) : UiEvent()
    data class ShareFile(val file: File) : UiEvent()
    data class OpenGoogleDriveApp(val appId: String = "com.google.android.apps.docs") : UiEvent()
}

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val trashDao: TrashDao
    private val deletedItemDao: DeletedItemDao

    private val _uiEventChannel = kotlinx.coroutines.channels.Channel<UiEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val uiEventFlow = _uiEventChannel.receiveAsFlow()

    private fun sendUiEvent(event: UiEvent) {
        viewModelScope.launch {
            _uiEventChannel.send(event)
        }
    }

    val googleDriveSyncHelper = GoogleDriveSyncHelper(application)
    val googleDriveSyncState: StateFlow<CloudSyncState> = googleDriveSyncHelper.syncState

    private val _cloudBackupsList = MutableStateFlow<List<CloudBackupFile>>(emptyList())
    val cloudBackupsList: StateFlow<List<CloudBackupFile>> = _cloudBackupsList.asStateFlow()

    private val _isFetchingCloudBackups = MutableStateFlow(false)
    val isFetchingCloudBackups: StateFlow<Boolean> = _isFetchingCloudBackups.asStateFlow()

    private val _localBackups = MutableStateFlow<List<File>>(emptyList())
    val localBackups: StateFlow<List<File>> = _localBackups.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FinanceRepository(database)
        trashDao = database.trashDao()
        deletedItemDao = database.deletedItemDao()

        val prefs = application.getSharedPreferences(FinanceConstants.PREFS_NAME, Context.MODE_PRIVATE)
        viewModelScope.launch {
            val shouldPopulate = !prefs.getBoolean("categories_populated", false)
            repository.populateDefaultCategoriesIfNeeded(shouldPopulate)
            if (shouldPopulate) {
                prefs.edit().putBoolean("categories_populated", true).apply()
            }
        }
        refreshLocalBackups()
    }

    // State Flows from Repository
    private val navigationPrefs = NavigationPreferences(application)

    val tabOrderState: StateFlow<String> = navigationPrefs.tabOrderFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavigationPreferences.DEFAULT_ORDER)

    val defaultStartDestinationState: StateFlow<String> = navigationPrefs.defaultStartFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavigationPreferences.DEFAULT_START)

    fun saveTabOrder(order: String) {
        viewModelScope.launch {
            navigationPrefs.saveTabOrder(order)
        }
    }

    fun saveDefaultStart(start: String) {
        viewModelScope.launch {
            navigationPrefs.saveDefaultStart(start)
        }
    }

    val isSettingsLoaded = MutableStateFlow(false)

    val settingsState: StateFlow<AppSettings> = repository.settingsFlow
        .onEach { isSettingsLoaded.value = true }
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val commitmentsState: StateFlow<List<FixedCommitment>> = repository.commitmentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionsState: StateFlow<List<TransactionDb>> = repository.transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customCategoriesState: StateFlow<List<CustomCategory>> = repository.customCategoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedItemsFlow: Flow<List<DeletedItemEntity>> = repository.deletedItemsFlow

    // --- Safe License Activation Logic ---
    fun getOrGenerateUnifiedDeviceId(context: Context): String = com.example.domain.LicenseManager.getOrGenerateUnifiedDeviceId(context)

    val showActivationRequired = MutableStateFlow(false)

    // Privacy Mode State
    val isPrivacyModeEnabled = MutableStateFlow(true)
    fun togglePrivacyMode() {
        isPrivacyModeEnabled.value = !isPrivacyModeEnabled.value
    }

    private val _activationTrigger = MutableStateFlow(0)

    val deviceIdState: StateFlow<String> = flow {
        emit(com.example.domain.LicenseManager.getOrGenerateUnifiedDeviceId(getApplication()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.domain.LicenseManager.getOrGenerateUnifiedDeviceId(getApplication()))

    val isActivatedState: StateFlow<Boolean> = combine(deviceIdState, _activationTrigger) { deviceId, _ ->
        val prefs = getApplication<Application>().getSharedPreferences("mizan_sec_prefs", Context.MODE_PRIVATE)
        val enteredCode = prefs.getString("m_act_code", "") ?: ""
        if (enteredCode.isBlank()) {
            false
        } else {
            com.example.domain.LicenseManager.verifyActivationCode(deviceId, enteredCode)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun activateLicense(code: String): Boolean {
        val cleanCode = code.trim().uppercase()
        val deviceId = com.example.domain.LicenseManager.getOrGenerateUnifiedDeviceId(getApplication())
        
        val isValid = com.example.domain.LicenseManager.verifyActivationCode(deviceId, cleanCode)
        if (isValid) {
            val prefs = getApplication<Application>().getSharedPreferences("mizan_sec_prefs", Context.MODE_PRIVATE)
            val isPermanentCode = cleanCode.startsWith(com.example.domain.LicenseManager.getPrefixPerm())
            prefs.edit()
                .putBoolean("is_premium", true)
                .putBoolean("is_permanent", isPermanentCode)
                .putString("m_act_code", cleanCode)
                .apply()
            _activationTrigger.value += 1
        }
        return isValid
    }

    fun isTrialExpired(): Boolean {
        val cap = com.example.domain.LicenseManager.getSecureLimitVal()
        val count = totalTransactionsCount.value
        val activated = isActivatedState.value
        return !activated && count >= cap
    }

    // --- Habayeb Debts ---
    val habayebCustomersState: StateFlow<List<HabayebCustomer>> = repository.habayebCustomersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habayebTransactionsState: StateFlow<List<HabayebTransaction>> = repository.habayebTransactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTransactionsCount: StateFlow<Int> = combine(
        transactionsState,
        habayebTransactionsState
    ) { main, habayeb ->
        main.size + habayeb.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val sharedPrefs = application.getSharedPreferences(FinanceConstants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _linkHabayebDebtsState = MutableStateFlow(sharedPrefs.getBoolean(FinanceConstants.KEY_LINK_HABAYEB_DEBTS, false))
    val linkHabayebDebtsState = _linkHabayebDebtsState.asStateFlow()

    fun toggleLinkHabayebDebts(enabled: Boolean) {
        _linkHabayebDebtsState.value = enabled
        sharedPrefs.edit().putBoolean(FinanceConstants.KEY_LINK_HABAYEB_DEBTS, enabled).apply()
    }

    fun hasShownOnboarding(): Boolean {
        return sharedPrefs.getBoolean(FinanceConstants.KEY_ONBOARDING_SHOWN, false)
    }

    fun markOnboardingShown() {
        sharedPrefs.edit().putBoolean(FinanceConstants.KEY_ONBOARDING_SHOWN, true).apply()
    }

    val customersUiState: StateFlow<com.example.ui.state.CustomersUiState> = combine(
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
                val amount = BigDecimal.valueOf(tx.amount)
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
            com.example.ui.state.CustomerUiState(
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

        com.example.ui.state.CustomersUiState(
            customers = customerStates,
            totalOwedByThem = totalOwedByThem,
            totalOwedToThem = totalOwedToThem,
            isLoading = false
        )
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.ui.state.CustomersUiState())

    val habayebOwedByThemTotalState: StateFlow<BigDecimal> = customersUiState
        .map { it.totalOwedByThem }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    val habayebOwedToThemTotalState: StateFlow<BigDecimal> = customersUiState
        .map { it.totalOwedToThem }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    fun saveHabayebCustomer(customer: HabayebCustomer, initialAmount: Double, initialType: String, customTimestamp: Long = System.currentTimeMillis() / 1000, initialDetails: String = "") {
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
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_save_failed))
            }
        }
    }

    fun addHabayebTransaction(customerId: String, type: String, amount: Double, desc: String, timestamp: Long = System.currentTimeMillis() / 1000, linkedMainTxId: String? = null) {
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
                repository.insertHabayebTransaction(transaction)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_save_failed))
            }
        }
    }

    fun updateHabayebCustomerName(customerId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateCustomerName(customerId, newName)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_save_failed))
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
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_delete_failed))
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
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_delete_failed))
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
                    val linkedTx = transactionsState.value.find { it.id == tx.linkedMainTxId }
                    if (linkedTx != null) {
                        repository.softDeleteTransactionToTrash(linkedTx)
                    }
                    repository.deleteTransactionById(tx.linkedMainTxId)
                }
                repository.deleteHabayebTransactionById(txId)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_delete_failed))
            }
        }
    }

    // Search logic
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val searchResultsState: StateFlow<List<TransactionDb>> = combine(transactionsState, _searchQuery) { transactions, query ->
        if (query.isBlank()) emptyList()
        else {
            val normalizedQuery = normalizeArabic(query)
            transactions.filter { tx ->
                normalizeArabic(tx.description).contains(normalizedQuery, ignoreCase = true) ||
                normalizeArabic(tx.category).contains(normalizedQuery, ignoreCase = true)
            }.sortedByDescending { it.timestamp }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun normalizeArabic(text: String): String {
        return text.replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .trim()
    }

    // --- Calculations using BigDecimal ---

    // Calculate sum of transactions based on type (INCOME or EXPENSE)
    fun calculateSumByType(transactions: List<TransactionDb>, type: String): BigDecimal {
        var sum = BigDecimal.ZERO
        for (tx in transactions) {
            if (tx.type == type) {
                sum = sum.add(BigDecimal.valueOf(tx.amount))
            }
        }
        return sum.setScale(2, RoundingMode.HALF_EVEN)
    }

    // Current Cash Balance: sum(INCOME) - sum(EXPENSE)
    val totalCashState: StateFlow<BigDecimal> = transactionsState
        .map { txList ->
            val income = calculateSumByType(txList, "INCOME")
            val expense = calculateSumByType(txList, "EXPENSE")
            income.subtract(expense).setScale(2, RoundingMode.HALF_EVEN)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    // Today's expenses vs Yesterday's expenses for advice card
    val dailyExpenseComparisonState: StateFlow<Pair<BigDecimal, BigDecimal>> = transactionsState
        .map { txList ->
            val todayKey = DateUtils.formatDateFull(System.currentTimeMillis() / 1000)
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val yesterdayKey = DateUtils.formatDateFull(cal.timeInMillis / 1000)

            var todayExpenses = BigDecimal.ZERO
            var yesterdayExpenses = BigDecimal.ZERO

            for (tx in txList) {
                if (tx.type == "EXPENSE") {
                    val txDate = DateUtils.formatDateFull(tx.timestamp)
                    if (txDate == todayKey) {
                        todayExpenses = todayExpenses.add(BigDecimal.valueOf(tx.amount))
                    } else if (txDate == yesterdayKey) {
                        yesterdayExpenses = yesterdayExpenses.add(BigDecimal.valueOf(tx.amount))
                    }
                }
            }
            Pair(todayExpenses, yesterdayExpenses)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(BigDecimal.ZERO, BigDecimal.ZERO))

    // Group transactions into months and days with forwarded balance calculations
    
    val ledgerUiState: StateFlow<com.example.ui.state.MainLedgerUiState> = combine(
        searchResultsState,
        totalCashState,
        _searchQuery
    ) { txList, totalCash, query ->
        com.example.ui.state.MainLedgerUiState(
            transactions = txList,
            totalCash = totalCash.toDouble(),
            isSearching = query.isNotBlank(),
            isLoading = false
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.ui.state.MainLedgerUiState()
    )

    val monthlyLedgerState: StateFlow<List<MonthLedger>> = transactionsState
        .map { txList ->
            // Sort chronic ascending to compute running balances correctly, then format descending for display
            val chronicTx = txList.sortedBy { it.timestamp }
            
            // Map of "yyyy-MM" -> List of Transactions
            val groupedByMonth = chronicTx.groupBy { DateUtils.getYearMonthKey(it.timestamp) }
            
            // Sorted months chronic ascending
            val sortedMonthKeys = groupedByMonth.keys.sorted()
            
            var runningForwardedBalance = BigDecimal.ZERO
            val ledgerList = mutableListOf<MonthLedger>()
            
            for (monthKey in sortedMonthKeys) {
                val monthTx = groupedByMonth[monthKey] ?: emptyList()
                val monthName = DateUtils.getMonthNameArabic(monthTx.first().timestamp)
                
                // Group transactions inside this month by Day of Month
                // We want today's days grouped
                val groupedByDay = monthTx.groupBy { DateUtils.getDayOfMonth(it.timestamp) }
                
                // Days sorted descending (latest day first inside a month) or ascending. Let's show latest day first.
                val sortedDays = groupedByDay.keys.sortedDescending()
                
                val dayItems = mutableListOf<DayLedger>()
                var MonthIncomes = BigDecimal.ZERO
                var MonthExpenses = BigDecimal.ZERO
                
                for (day in sortedDays) {
                    val dayTx = groupedByDay[day] ?: emptyList()
                    val dayTimestamp = dayTx.first().timestamp
                    val dayDateText = DateUtils.formatDateFull(dayTimestamp)
                    val dayOfWeek = DateUtils.getDayOfWeekArabic(dayTimestamp)
                    
                    // Calc net for this day
                    var dayIncome = BigDecimal.ZERO
                    var dayExpense = BigDecimal.ZERO
                    for (tx in dayTx) {
                        if (tx.type == "INCOME") {
                            dayIncome = dayIncome.add(BigDecimal.valueOf(tx.amount))
                        } else {
                            dayExpense = dayExpense.add(BigDecimal.valueOf(tx.amount))
                        }
                    }
                    val netDay = dayIncome.subtract(dayExpense)
                    
                    dayItems.add(
                        DayLedger(
                            dayNumber = day,
                            dayOfWeek = dayOfWeek,
                            fullDate = dayDateText,
                            netAmount = netDay,
                            transactions = dayTx.sortedByDescending { it.timestamp }
                        )
                    )
                    
                    MonthIncomes = MonthIncomes.add(dayIncome)
                    MonthExpenses = MonthExpenses.add(dayExpense)
                }
                
                val currentMonthNet = MonthIncomes.subtract(MonthExpenses)
                val totalForwarded = runningForwardedBalance
                val MonthFinalBalance = totalForwarded.add(currentMonthNet)
                
                ledgerList.add(
                    MonthLedger(
                        monthKey = monthKey,
                        monthName = monthName,
                        forwardedBalance = totalForwarded,
                        netAmount = currentMonthNet,
                        finalBalance = MonthFinalBalance,
                        days = dayItems
                    )
                )
                
                // Set forwarded balance for the next month to be the final balance of this month
                runningForwardedBalance = MonthFinalBalance
            }
            
            // Return sorted descending by month so the newest month shows first
            ledgerList.sortedByDescending { it.monthKey }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Database Operations launched on Dispatchers.IO ---

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSettings(settings)
        }
    }

    fun verifyCredentials(input: String): Boolean {
        val hashed = com.example.domain.HashUtils.hashString(input.trim())
        val settings = settingsState.value
        return (settings.passcodeHash != null && com.example.domain.DatabaseSecurityGuard.secureEqual(hashed, settings.passcodeHash)) || 
               (settings.recoveryPhraseHash != null && com.example.domain.DatabaseSecurityGuard.secureEqual(hashed, settings.recoveryPhraseHash))
    }

    fun addTransaction(type: String, category: String, amount: Double, description: String, timestamp: Long = System.currentTimeMillis() / 1000, presetId: String? = null) {
        if (isTrialExpired()) {
            showActivationRequired.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val id = presetId ?: "tx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            val tx = TransactionDb(
                id = id,
                timestamp = timestamp,
                type = type,
                category = category,
                amount = amount,
                description = description
            )
            repository.saveTransaction(tx)
        }
    }

    fun permanentlyDeleteDeletedItem(item: DeletedItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.removeDeletedItem(item)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun permanentlyDeleteMultipleItems(items: List<DeletedItemEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                items.forEach { repository.removeDeletedItem(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun restoreMultipleItems(items: List<DeletedItemEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(getApplication())
                database.withTransaction {
                    items.forEach { trashDao.restoreDeletedItem(it) }
                }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), R.string.toast_restore_success, android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), R.string.toast_operation_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun restoreDeletedItem(item: DeletedItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(getApplication())
                database.withTransaction {
                    trashDao.restoreDeletedItem(item)
                }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), R.string.toast_restore_success, android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), R.string.toast_operation_failed, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.clearDeletedItems()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteTransaction(tx: TransactionDb) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.softDeleteTransactionToTrash(tx)
                repository.deleteTransaction(tx)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_delete_failed))
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
                repository.deleteTransactionById(id)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_delete_failed))
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
                    toDelete.forEach { repository.deleteTransactionById(it.id) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_delete_failed))
            }
        }
    }

    fun updateTransaction(tx: TransactionDb) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveTransaction(tx)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_operation_failed))
            }
        }
    }

    fun saveCommitment(name: String, targetAmount: Double, currentProgress: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = commitmentsState.value.size
                val fc = FixedCommitment(name, targetAmount, currentProgress, count)
                repository.saveCommitment(fc)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_save_failed))
            }
        }
    }

    fun updateCommitmentDirectly(commitment: FixedCommitment) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveCommitment(commitment)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_operation_failed))
            }
        }
    }

    fun reorderCommitment(commitment: FixedCommitment, toPosition: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentList = commitmentsState.value.toMutableList()
                currentList.sortBy { it.orderIndex }
                
                val targetIndex = (toPosition - 1).coerceIn(0, currentList.size - 1)
                val currentIndex = currentList.indexOfFirst { it.name == commitment.name }
                
                if (currentIndex != -1 && currentIndex != targetIndex) {
                    val item = currentList.removeAt(currentIndex)
                    currentList.add(targetIndex, item)
                    
                    val updatedList = currentList.mapIndexed { index, fc -> 
                        fc.copy(orderIndex = index)
                    }
                    
                    repository.updateCommitments(updatedList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteCommitment(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val oldFc = commitmentsState.value.find { it.name == name }
                if (oldFc != null) {
                    repository.softDeleteCommitmentToTrash(oldFc)
                }
                repository.deleteCommitment(name)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_delete_failed))
            }
        }
    }

    fun saveCustomCategory(name: String, tabType: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.saveCustomCategory(CustomCategory(name = name, tabType = tabType, iconEmoji = emoji))
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_save_failed))
            }
        }
    }

    fun deleteCustomCategory(customCategory: CustomCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteCustomCategory(customCategory)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_delete_failed))
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteAllData()
                refreshLocalBackups()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearLocalCopyAndWipeMemory(context: Context) {
        deleteAllData()
    }

    // --- Backup & Restore (.mzd) ---

    fun getBaseBackupDirectory(): File {
        val context = getApplication<Application>()
        val hasPublicAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasPublicAccess) {
            val publicDocDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val mainDir = File(publicDocDir, "Mizan_Backups")
            try {
                if (!mainDir.exists()) {
                    val created = mainDir.mkdirs()
                    if (created || mainDir.exists()) {
                        return mainDir
                    }
                } else {
                    return mainDir
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val appExternalDocsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) 
            ?: context.getExternalFilesDir(null)
        val fallbackMainDir = File(appExternalDocsDir, "Mizan_Backups")
        if (!fallbackMainDir.exists()) {
            fallbackMainDir.mkdirs()
        }
        return fallbackMainDir
    }

    fun getBackupDirectory(): File {
        val baseDir = getBaseBackupDirectory()
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val monthStr = sdf.format(java.util.Date())
        val targetDir = File(baseDir, monthStr)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    private fun getAllMzdFilesRecursively(rootDir: File): List<File> {
        val result = mutableListOf<File>()
        val files = rootDir.listFiles() ?: return emptyList()
        for (f in files) {
            if (f.isDirectory) {
                result.addAll(getAllMzdFilesRecursively(f))
            } else if (f.name.endsWith(".mzd")) {
                result.add(f)
            }
        }
        return result
    }

    fun refreshLocalBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseDir = getBaseBackupDirectory()
            val files = getAllMzdFilesRecursively(baseDir)
            _localBackups.value = files.sortedByDescending { it.lastModified() }
        }
    }

    fun handleGoogleOAuthCode(code: String, email: String? = null, redirectUri: String = "", onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = googleDriveSyncHelper.handleAuthorizationCode(code, email, redirectUri)
            if (success) {
                val current = settingsState.value
                repository.saveSettings(current.copy(isCloudSyncEnabled = true))
            }
            onComplete?.invoke(success)
        }
    }

    fun backupToGoogleDriveDirect(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isTrulySignedIn = googleDriveSyncHelper.isUserTrulySignedIn(getApplication())
                val refreshToken = googleDriveSyncHelper.getStoredRefreshToken()
                val isConnected = isTrulySignedIn || !refreshToken.isNullOrEmpty()
                if (!isConnected) {
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), R.string.toast_backup_export_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete?.invoke(false)
                    }
                    return@launch
                }

                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = repository.getAllCustomersDirect()
                val habayebTxs = repository.getAllTransactionsDirect()
                val deletedItems = repository.deletedItemsFlow.first()
                val jsonStr = MzdBackupSerializer.exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, deletedItems)
                
                val success = googleDriveSyncHelper.uploadBackupToDrive(jsonStr)
                launch(Dispatchers.Main) {
                    onComplete?.invoke(success)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), R.string.toast_backup_export_failed, android.widget.Toast.LENGTH_LONG).show()
                }
                launch(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun restoreFromGoogleDriveDirect(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isTrulySignedIn = googleDriveSyncHelper.isUserTrulySignedIn(context)
                val refreshToken = googleDriveSyncHelper.getStoredRefreshToken()
                val isConnected = isTrulySignedIn || !refreshToken.isNullOrEmpty()
                if (!isConnected) {
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, R.string.cloud_toast_restore_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete(false)
                    }
                    return@launch
                }

                val jsonStr = googleDriveSyncHelper.downloadBackupFromDrive()
                if (jsonStr != null) {
                    executeMasterRestore(jsonStr, context) { success, _ ->
                        onComplete(success)
                    }
                } else {
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, R.string.cloud_toast_restore_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete(false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, R.string.cloud_toast_restore_failed, android.widget.Toast.LENGTH_LONG).show()
                }
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun googleDriveLogout(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.saveSettings(current.copy(isCloudSyncEnabled = false))
        }
        googleDriveSyncHelper.logoutAsync {
            _cloudBackupsList.value = emptyList()
            onComplete?.invoke()
        }
    }

    fun fetchCloudBackupsList() {
        viewModelScope.launch {
            try {
                _isFetchingCloudBackups.value = true
                val list = googleDriveSyncHelper.listCloudBackups()
                _cloudBackupsList.value = list
                _isFetchingCloudBackups.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                _isFetchingCloudBackups.value = false
            }
        }
    }

    fun uploadBackupToGoogleDrive(onComplete: (Boolean) -> Unit) {
        val sdfName = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US)
        val dateStr = sdfName.format(java.util.Date())
        val newFileName = "Mzd_$dateStr.mzd"
        uploadBackupToGoogleDriveWithFilename(newFileName, onComplete)
    }

    fun uploadBackupToGoogleDriveWithFilename(filename: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isTrulySignedIn = googleDriveSyncHelper.isUserTrulySignedIn(getApplication())
                val refreshToken = googleDriveSyncHelper.getStoredRefreshToken()
                val isConnected = isTrulySignedIn || !refreshToken.isNullOrEmpty()
                if (!isConnected) {
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(getApplication(), R.string.toast_backup_export_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete(false)
                    }
                    return@launch
                }

                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = repository.getAllCustomersDirect()
                val habayebTxs = repository.getAllTransactionsDirect()
                val deletedItems = repository.deletedItemsFlow.first()
                val jsonStr = MzdBackupSerializer.exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, deletedItems)
                
                val success = googleDriveSyncHelper.uploadBackupToDriveWithFilename(filename, jsonStr)
                if (success) {
                    fetchCloudBackupsList()
                }
                launch(Dispatchers.Main) {
                    onComplete(success)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), R.string.toast_backup_export_failed, android.widget.Toast.LENGTH_LONG).show()
                }
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun restoreFromGoogleDriveById(context: Context, fileId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isTrulySignedIn = googleDriveSyncHelper.isUserTrulySignedIn(context)
                val refreshToken = googleDriveSyncHelper.getStoredRefreshToken()
                val isConnected = isTrulySignedIn || !refreshToken.isNullOrEmpty()
                if (!isConnected) {
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, R.string.cloud_toast_restore_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete(false)
                    }
                    return@launch
                }

                val jsonStr = googleDriveSyncHelper.downloadBackupFromDriveById(fileId)
                if (jsonStr != null) {
                    executeMasterRestore(jsonStr, context) { success, _ ->
                        onComplete(success)
                    }
                } else {
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, R.string.cloud_toast_restore_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete(false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, R.string.cloud_toast_restore_failed, android.widget.Toast.LENGTH_LONG).show()
                }
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun deleteCloudBackupById(fileId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = googleDriveSyncHelper.deleteBackupFromDriveById(fileId)
                if (success) {
                    fetchCloudBackupsList()
                }
                launch(Dispatchers.Main) {
                    onComplete(success)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun deleteMultipleCloudBackupsByIds(fileIds: List<String>, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var allSuccess = true
                for (fileId in fileIds) {
                    val success = googleDriveSyncHelper.deleteBackupFromDriveById(fileId)
                    if (!success) {
                        allSuccess = false
                    }
                }
                if (fileIds.isNotEmpty()) {
                    fetchCloudBackupsList()
                }
                launch(Dispatchers.Main) {
                    onComplete(allSuccess)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun getBackupJsonForClipboard(onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = repository.getAllCustomersDirect()
                val habayebTxs = repository.getAllTransactionsDirect()
                val deletedItems = repository.deletedItemsFlow.first()
                val jsonStr = MzdBackupSerializer.exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, deletedItems)
                launch(Dispatchers.Main) {
                    onComplete(jsonStr)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createLocalBackup(context: Context, onComplete: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = repository.getAllCustomersDirect()
                val habayebTxs = repository.getAllTransactionsDirect()
                val deletedItems = repository.deletedItemsFlow.first()
                val jsonStr = MzdBackupSerializer.exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, deletedItems)
                val dir = getBackupDirectory()
                val sdfName = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US)
                val dateStr = sdfName.format(java.util.Date())
                val fileName = "Mizan_$dateStr.mzd"
                val file = File(dir, fileName)
                file.writeText(jsonStr)
 
                if (file.exists() && file.length() > 0) {
                    refreshLocalBackups()
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, R.string.autobackup_notification_title_local, android.widget.Toast.LENGTH_SHORT).show()
                        onComplete(file)
                    }
                } else {
                    throw java.io.IOException("File verification failed.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, R.string.autobackup_notification_title_failure, android.widget.Toast.LENGTH_LONG).show()
                    onComplete(null)
                }
            }
        }
    }

    fun executeMasterRestore(rawJsonString: String, context: Context, onComplete: (Boolean, AppSettings?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = org.json.JSONObject(rawJsonString)

                val currentLocalSettings = repository.settingsFlow.first() ?: AppSettings()
                val data = MzdBackupSerializer.importBackupFromJson(rawJsonString)
                val restoredSettingsUnmerged = data.first
                val restoredSettings = restoredSettingsUnmerged.copy(
                    isPasscodeEnabled = currentLocalSettings.isPasscodeEnabled,
                    passcodeHash = currentLocalSettings.passcodeHash,
                    recoveryPhraseHash = currentLocalSettings.recoveryPhraseHash,
                    recoveryHint = currentLocalSettings.recoveryHint,
                    tempPart = currentLocalSettings.tempPart,
                    permPart = currentLocalSettings.permPart,
                    unifiedDeviceId = currentLocalSettings.unifiedDeviceId,
                    isFirstLaunch = currentLocalSettings.isFirstLaunch
                )
                val restoredCommitments = data.second
                val restoredTransactions = data.third

                val appDb = AppDatabase.getDatabase(context)
                appDb.withTransaction {
                    repository.clearTransactions()
                    repository.clearCommitments()
                    repository.clearCustomCategories()
                    repository.clearDeletedItems()

                    repository.saveSettings(restoredSettings)
                    for (fc in restoredCommitments) {
                        repository.saveCommitment(fc)
                    }
                    for (tx in restoredTransactions) {
                        repository.saveTransaction(tx)
                    }

                    if (root.has("deleted_items") && !root.isNull("deleted_items")) {
                        val deletedItemsArr = root.optJSONArray("deleted_items")
                        if (deletedItemsArr != null) {
                            for (i in 0 until deletedItemsArr.length()) {
                                val obj = deletedItemsArr.getJSONObject(i)
                                val item = DeletedItemEntity(
                                    id = obj.getString("id"),
                                    sourceSystem = obj.getString("sourceSystem"),
                                    originalTableName = obj.getString("originalTableName"),
                                    jsonData = obj.getString("jsonData"),
                                    deletedAt = obj.getLong("deletedAt")
                                )
                                repository.saveDeletedItem(item)
                            }
                        }
                    }

                    repository.clearAllCustomers()
                    repository.clearAllTransactions()

                    val jsonHabayebObj = root.optJSONObject("habayeb_debts")
                    val legacyHabayebDb = root.optJSONObject("habayeb_debts_db")

                    val custArr = jsonHabayebObj?.optJSONArray("customers")
                        ?: legacyHabayebDb?.optJSONArray("habayeb_customers")

                    if (custArr != null) {
                        for (i in 0 until custArr.length()) {
                            val obj = custArr.getJSONObject(i)
                            val customer = HabayebCustomer(
                                id = obj.optString("id", obj.optString("customer_id", "")),
                                name = obj.getString("name"),
                                phone = obj.optString("phone", ""),
                                notes = obj.optString("notes", ""),
                                createdAt = obj.optLong("created_at", obj.optLong("createdAt", System.currentTimeMillis() / 1000))
                            )
                            repository.insertCustomer(customer)
                        }
                    }
                    
                    val txArr = jsonHabayebObj?.optJSONArray("debt_transactions")
                        ?: legacyHabayebDb?.optJSONArray("habayeb_transactions")

                    if (txArr != null) {
                        for (i in 0 until txArr.length()) {
                            val obj = txArr.getJSONObject(i)
                            val transaction = HabayebTransaction(
                                id = obj.getString("id"),
                                customerId = obj.optString("customer_id", obj.optString("customerId", "")),
                                type = obj.getString("type"),
                                amount = obj.getDouble("amount"),
                                timestamp = obj.getLong("timestamp"),
                                description = obj.optString("description", ""),
                                linkedMainTxId = obj.optString("linked_main_tx_id", obj.optString("linkedMainTxId", null))
                            )
                            repository.insertHabayebTransaction(transaction)
                        }
                    }
                }

                refreshLocalBackups()

                val hasLegacy = root.has("mizan_al_dar_db") || root.has("habayeb_debts_db")
                val successMessageRes = if (hasLegacy) R.string.toast_restore_legacy_migrated else R.string.cloud_toast_restore_success

                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, successMessageRes, android.widget.Toast.LENGTH_SHORT).show()
                    onComplete(true, restoredSettings)
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, R.string.backup_schema_mismatch, android.widget.Toast.LENGTH_LONG).show()
                    onComplete(false, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, R.string.cloud_toast_restore_failed, android.widget.Toast.LENGTH_LONG).show()
                    onComplete(false, null)
                }
            }
        }
    }

    fun restoreFromMzdContent(jsonContent: String, context: Context, onComplete: (Boolean) -> Unit) {
        executeMasterRestore(jsonContent, context) { success, _ ->
            onComplete(success)
        }
    }

    fun restoreFromLocalFile(file: File, context: Context, onComplete: (Boolean, AppSettings?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    val content = file.readText()
                    executeMasterRestore(content, context) { success, restoredSettings ->
                        onComplete(success, restoredSettings)
                    }
                } else {
                    launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, R.string.cloud_toast_restore_failed, android.widget.Toast.LENGTH_SHORT).show()
                        onComplete(false, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    onComplete(false, null)
                }
            }
        }
    }

    // Format utility for prices in Arabic
    fun formatCurrency(amount: BigDecimal, symbol: String = "ر.ي"): String {
        return try {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
            val formatter = DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        } catch (e: Exception) {
            val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
            val formatter = DecimalFormat("#,##0", symbols)
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        }
    }

    fun formatDoubleCurrency(amount: Double, symbol: String = "ر.ي"): String {
        val symbols = java.text.DecimalFormatSymbols(java.util.Locale.ENGLISH)
        val formatter = DecimalFormat("#,##0", symbols)
        val formatted = formatter.format(amount)
        return "$formatted $symbol"
    }
}

// Ledger Presentation models
data class MonthLedger(
    val monthKey: String, // "yyyy-MM"
    val monthName: String, // e.g. "يونيو 2026"
    val forwardedBalance: BigDecimal, // Forwarded sum from previous month
    val netAmount: BigDecimal, // Net for this month
    val finalBalance: BigDecimal, // netAmount + forwardedBalance
    val days: List<DayLedger>
)

data class DayLedger(
    val dayNumber: Int,
    val dayOfWeek: String, // "السبت" etc
    val fullDate: String, // "2026-06-01"
    val netAmount: BigDecimal,
    val transactions: List<TransactionDb>
)
