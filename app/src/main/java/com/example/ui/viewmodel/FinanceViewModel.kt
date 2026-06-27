package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val habayebDao: HabayebDao

    private var syncSettingsViewModel: SyncSettingsViewModel? = null

    fun setSyncSettingsViewModel(vm: SyncSettingsViewModel) {
        this.syncSettingsViewModel = vm
    }

    private val _uiEventChannel = kotlinx.coroutines.channels.Channel<UiEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val uiEventFlow = _uiEventChannel.receiveAsFlow()

    private fun sendUiEvent(event: UiEvent) {
        viewModelScope.launch {
            _uiEventChannel.send(event)
        }
    }

    val googleDriveSyncHelper: GoogleDriveSyncHelper get() = syncSettingsViewModel?.googleDriveSyncHelper ?: GoogleDriveSyncHelper(getApplication())
    val googleDriveSyncState: StateFlow<CloudSyncState> get() = syncSettingsViewModel?.googleDriveSyncState ?: MutableStateFlow(CloudSyncState.Idle).asStateFlow()
    val cloudBackupsList: StateFlow<List<CloudBackupFile>> get() = syncSettingsViewModel?.cloudBackupsList ?: MutableStateFlow<List<CloudBackupFile>>(emptyList()).asStateFlow()
    val isFetchingCloudBackups: StateFlow<Boolean> get() = syncSettingsViewModel?.isFetchingCloudBackups ?: MutableStateFlow(false).asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FinanceRepository(database)
        habayebDao = database.habayebDao()

        val prefs = application.getSharedPreferences("mizan_prefs", Context.MODE_PRIVATE)
        viewModelScope.launch {
            repository.populateDefaultCategoriesIfNeeded(prefs)
        }
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

    // --- Safe License Activation Logic delegated to SyncSettingsViewModel ---
    fun getOrGenerateUnifiedDeviceId(context: Context): String = com.example.domain.LicenseManager.getOrGenerateUnifiedDeviceId(context)

    val showActivationRequired: MutableStateFlow<Boolean> get() = syncSettingsViewModel?.showActivationRequired ?: MutableStateFlow(false)

    // Privacy Mode State
    val isPrivacyModeEnabled = MutableStateFlow(true)
    fun togglePrivacyMode() {
        isPrivacyModeEnabled.value = !isPrivacyModeEnabled.value
    }

    val deviceIdState: StateFlow<String> get() = syncSettingsViewModel?.deviceIdState ?: MutableStateFlow("").asStateFlow()
    val isActivatedState: StateFlow<Boolean> get() = syncSettingsViewModel?.isActivatedState ?: MutableStateFlow(false).asStateFlow()

    fun activateLicense(code: String): Boolean = syncSettingsViewModel?.activateLicense(code) ?: false
    fun isTrialExpired(): Boolean = syncSettingsViewModel?.isTrialExpired() ?: false

    // --- Habayeb Debts ---
    val habayebCustomersState: StateFlow<List<HabayebCustomer>> = habayebDao.getAllCustomersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habayebTransactionsState: StateFlow<List<HabayebTransaction>> = habayebDao.getAllTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTransactionsCount: StateFlow<Int> = combine(
        transactionsState,
        habayebTransactionsState
    ) { main, habayeb ->
        main.size + habayeb.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val sharedPrefs = application.getSharedPreferences("mizan_prefs", Context.MODE_PRIVATE)

    private val _linkHabayebDebtsState = MutableStateFlow(sharedPrefs.getBoolean("link_habayeb_debts", false))
    val linkHabayebDebtsState = _linkHabayebDebtsState.asStateFlow()

    fun toggleLinkHabayebDebts(enabled: Boolean) {
        _linkHabayebDebtsState.value = enabled
        sharedPrefs.edit().putBoolean("link_habayeb_debts", enabled).apply()
    }

    fun hasShownOnboarding(): Boolean {
        return sharedPrefs.getBoolean("has_shown_onboarding", false)
    }

    fun markOnboardingShown() {
        sharedPrefs.edit().putBoolean("has_shown_onboarding", true).apply()
    }

    val customersUiState: StateFlow<com.example.ui.state.CustomersUiState> = combine(
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
        val totalOwedByThem = customerStates.filter { it.netDebt > 0.0 }.sumOf { it.netDebt }
        val totalOwedToThem = customerStates.filter { it.netDebt < 0.0 }.sumOf { kotlin.math.abs(it.netDebt) }

        com.example.ui.state.CustomersUiState(
            customers = customerStates,
            totalOwedByThem = totalOwedByThem,
            totalOwedToThem = totalOwedToThem,
            isLoading = false
        )
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.ui.state.CustomersUiState())

    val habayebOwedByThemTotalState: StateFlow<Double> = customersUiState
        .map { it.totalOwedByThem }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val habayebOwedToThemTotalState: StateFlow<Double> = customersUiState
        .map { it.totalOwedToThem }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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
                
                habayebDao.insertCustomerWithOpeningTransaction(customer, transaction)
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
                habayebDao.insertTransaction(transaction)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_save_failed))
            }
        }
    }

    fun updateHabayebCustomerName(customerId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                habayebDao.updateCustomerName(customerId, newName)
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
                val customerTxs = habayebDao.getAllTransactionsDirect().filter { it.customerId == customerId }
                
                if (customer != null) {
                    repository.softDeleteHabayebBundleToTrash(customer, customerTxs)
                }

                habayebDao.deleteCustomerAndTransactions(customerId)
            } catch (e: Exception) {
                e.printStackTrace()
                sendUiEvent(UiEvent.ShowToast(R.string.toast_delete_failed))
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
                        repository.softDeleteHabayebBundleToTrash(customer, customerTxs)
                    }

                    habayebDao.deleteCustomerAndTransactions(id)
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
                val tx = habayebDao.getTransactionById(txId)
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
                habayebDao.deleteTransactionById(txId)
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

    // Backups list delegated to SyncSettingsViewModel
    val localBackups: StateFlow<List<File>> get() = syncSettingsViewModel?.localBackups ?: MutableStateFlow<List<File>>(emptyList()).asStateFlow()

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
        syncSettingsViewModel?.permanentlyDeleteDeletedItem(item)
    }

    fun permanentlyDeleteMultipleItems(items: List<DeletedItemEntity>) {
        syncSettingsViewModel?.permanentlyDeleteMultipleItems(items)
    }

    fun restoreMultipleItems(items: List<DeletedItemEntity>) {
        syncSettingsViewModel?.restoreMultipleItems(items)
    }

    fun restoreDeletedItem(item: DeletedItemEntity) {
        syncSettingsViewModel?.restoreDeletedItem(item)
    }

    fun emptyTrash() {
        syncSettingsViewModel?.emptyTrash()
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
        syncSettingsViewModel?.deleteAllData()
    }

    fun clearLocalCopyAndWipeMemory(context: Context) {
        syncSettingsViewModel?.clearLocalCopyAndWipeMemory(context)
    }

    // --- Backup & Restore (.mzd) delegated to SyncSettingsViewModel ---

    fun getBaseBackupDirectory(): File = syncSettingsViewModel?.getBaseBackupDirectory() ?: File(getApplication<Application>().filesDir, "backups")

    fun getBackupDirectory(): File = syncSettingsViewModel?.getBackupDirectory() ?: File(getApplication<Application>().filesDir, "backups")

    fun refreshLocalBackups() {
        syncSettingsViewModel?.refreshLocalBackups()
    }

    fun handleGoogleOAuthCode(code: String, email: String? = null, redirectUri: String = "", onComplete: ((Boolean) -> Unit)? = null) {
        syncSettingsViewModel?.handleGoogleOAuthCode(code, email, redirectUri, onComplete)
    }

    fun backupToGoogleDriveDirect(onComplete: ((Boolean) -> Unit)? = null) {
        syncSettingsViewModel?.backupToGoogleDriveDirect(onComplete)
    }

    fun restoreFromGoogleDriveDirect(context: Context, onComplete: (Boolean) -> Unit) {
        syncSettingsViewModel?.restoreFromGoogleDriveDirect(context, onComplete)
    }

    fun googleDriveLogout(onComplete: (() -> Unit)? = null) {
        syncSettingsViewModel?.googleDriveLogout(onComplete)
    }

    fun fetchCloudBackupsList() {
        syncSettingsViewModel?.fetchCloudBackupsList()
    }

    fun uploadBackupToGoogleDrive(onComplete: (Boolean) -> Unit) {
        syncSettingsViewModel?.uploadBackupToGoogleDrive(onComplete)
    }

    fun uploadBackupToGoogleDriveWithFilename(filename: String, onComplete: (Boolean) -> Unit) {
        syncSettingsViewModel?.uploadBackupToGoogleDriveWithFilename(filename, onComplete)
    }

    fun restoreFromGoogleDriveById(context: Context, fileId: String, onComplete: (Boolean) -> Unit) {
        syncSettingsViewModel?.restoreFromGoogleDriveById(context, fileId, onComplete)
    }

    fun deleteCloudBackupById(fileId: String, onComplete: (Boolean) -> Unit) {
        syncSettingsViewModel?.deleteCloudBackupById(fileId, onComplete)
    }

    fun deleteMultipleCloudBackupsByIds(fileIds: List<String>, onComplete: (Boolean) -> Unit) {
        syncSettingsViewModel?.deleteMultipleCloudBackupsByIds(fileIds, onComplete)
    }

    fun getBackupJsonForClipboard(onComplete: (String) -> Unit) {
        syncSettingsViewModel?.getBackupJsonForClipboard(onComplete)
    }

    fun createLocalBackup(context: Context, onComplete: (File?) -> Unit) {
        syncSettingsViewModel?.createLocalBackup(context, onComplete)
    }

    fun executeMasterRestore(rawJsonString: String, context: Context, onComplete: (Boolean, AppSettings?) -> Unit) {
        syncSettingsViewModel?.executeMasterRestore(rawJsonString, context, onComplete)
    }

    fun restoreFromMzdContent(jsonContent: String, context: Context, onComplete: (Boolean) -> Unit) {
        syncSettingsViewModel?.restoreFromMzdContent(jsonContent, context, onComplete)
    }

    fun restoreFromLocalFile(file: File, context: Context, onComplete: (Boolean, AppSettings?) -> Unit) {
        syncSettingsViewModel?.restoreFromLocalFile(file, context, onComplete)
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
