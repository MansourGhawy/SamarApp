package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.GoogleDriveSyncHelper
import com.example.data.CloudSyncState
import com.example.data.repository.FinanceRepository
import com.example.domain.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.UUID
import java.security.MessageDigest

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val habayebDao: HabayebDao

    val googleDriveSyncHelper: GoogleDriveSyncHelper
    val googleDriveSyncState: StateFlow<CloudSyncState>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FinanceRepository(database)
        habayebDao = HabayebDatabase.getDatabase(application).habayebDao()
        googleDriveSyncHelper = GoogleDriveSyncHelper(application)
        googleDriveSyncState = googleDriveSyncHelper.syncState
        checkAppIntegrity()
    }

    private var hasCheckedIntegrity = false

    private fun checkAppIntegrity() {
        if (hasCheckedIntegrity) return
        hasCheckedIntegrity = true
        val context = getApplication<Application>()
        val packageName = context.packageName
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    packageInfo.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                }
                if (signatures != null && signatures.isNotEmpty()) {
                    val firstSig = signatures.firstOrNull()
                    if (firstSig != null) {
                        val md = java.security.MessageDigest.getInstance("SHA-256")
                        val certBytes = firstSig.toByteArray()
                        val fingerprint = md.digest(certBytes).joinToString("") { "%02X".format(it) }
                        
                        android.util.Log.i("MIZAN_SEC", "Application Signing Handshake: $fingerprint")
                    }
                }
            } catch (e: Exception) {
                // Log silently
            }
        }
    }

    // State Flows from Repository
    val settingsState: StateFlow<AppSettings> = repository.settingsFlow
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val commitmentsState: StateFlow<List<FixedCommitment>> = repository.commitmentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionsState: StateFlow<List<TransactionDb>> = repository.transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customCategoriesState: StateFlow<List<CustomCategory>> = repository.customCategoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productsState: StateFlow<List<ProductEntity>> = repository.productsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val makhzanTransactionsState: StateFlow<List<MakhzanTransactionEntity>> = repository.makhzanTransactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedItemsFlow: Flow<List<DeletedItemEntity>> = repository.deletedItemsFlow

    // --- Safe License Activation Logic ---
    fun getOrGenerateUnifiedDeviceId(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("makhzan_prefs", Context.MODE_PRIVATE)
        var deviceId = sharedPrefs.getString("unified_device_id", "")
        
        if (deviceId.isNullOrBlank()) {
            val tempPart = java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            )
            val permPart = if (!androidId.isNullOrBlank()) {
                androidId.take(8).uppercase()
            } else {
                "A1B2C3D4"
            }
            deviceId = "MZ-$tempPart-$permPart"
            sharedPrefs.edit().putString("unified_device_id", deviceId).apply()
        }
        return deviceId
    }

    private val _activationTrigger = MutableStateFlow(0)
    val showActivationRequired = MutableStateFlow(false)

    // Privacy Mode State
    val isPrivacyModeEnabled = MutableStateFlow(true)
    fun togglePrivacyMode() {
        isPrivacyModeEnabled.value = !isPrivacyModeEnabled.value
    }

    val deviceIdState: StateFlow<String> = flow {
        emit(getOrGenerateUnifiedDeviceId(getApplication()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), getOrGenerateUnifiedDeviceId(getApplication()))

    val isActivatedState: StateFlow<Boolean> = combine(deviceIdState, _activationTrigger) { deviceId, _ ->
        val prefs = getApplication<Application>().getSharedPreferences("mizan_sec_prefs", Context.MODE_PRIVATE)
        val enteredCode = prefs.getString("m_act_code", "") ?: ""
        if (enteredCode.isBlank()) {
            false
        } else {
            verifyActivationCode(deviceId, enteredCode)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun decryptSalt(): String {
        val mask = 0x7F
        val obfuscatedSalt = byteArrayOf(
            50, 22, 5, 30, 17, 62, 19, 59, 30, 13, 
            44, 26, 28, 10, 13, 26, 44, 30, 19, 11, 
            77, 79, 77, 73, 32, 50, 30, 17, 12, 16, 
            10, 13
        )
        val decrypted = ByteArray(obfuscatedSalt.size)
        for (i in obfuscatedSalt.indices) {
            decrypted[i] = (obfuscatedSalt[i].toInt() xor mask).toByte()
        }
        return String(decrypted, Charsets.UTF_8)
    }

    private fun getSecureLimitVal(): Int {
        val mask1 = 0xE6F2
        val mask2 = 0xE696
        return mask1 xor mask2 // results in 100 without a static 100 literal in compiled bytecode
    }

    private fun getPrefixTemp(): String {
        return String(byteArrayOf(65, 67, 84, 45, 84, 45), Charsets.UTF_8) // "ACT-T-"
    }

    private fun getPrefixPerm(): String {
        return String(byteArrayOf(65, 67, 84, 45, 80, 45), Charsets.UTF_8) // "ACT-P-"
    }

    fun activateLicense(code: String): Boolean {
        val cleanCode = code.trim().uppercase()
        val deviceId = getOrGenerateUnifiedDeviceId(getApplication())
        
        val isValid = verifyActivationCode(deviceId, cleanCode)
        if (isValid) {
            val prefs = getApplication<Application>().getSharedPreferences("mizan_sec_prefs", Context.MODE_PRIVATE)
            val isPermanentCode = cleanCode.startsWith(getPrefixPerm())
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
        val cap = getSecureLimitVal()
        val count = totalTransactionsCount.value
        val activated = isActivatedState.value
        return !activated && count >= cap
    }

    private fun verifyActivationCode(deviceId: String, enteredCode: String): Boolean {
        val cleanEntered = enteredCode.trim().uppercase()
        val parts = deviceId.split("-")
        val tempPart = if (parts.size >= 3) parts[1] else ""
        val permPart = if (parts.size >= 3) parts[2] else ""

        val tempPrefix = getPrefixTemp()
        val permPrefix = getPrefixPerm()

        if (cleanEntered.startsWith(tempPrefix)) {
            val enteredPayload = cleanEntered.substring(tempPrefix.length)
            val salt = decryptSalt()
            val combined = tempPart + salt
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(combined.toByteArray(Charsets.UTF_8))
            val shaResult = bytes.joinToString("") { "%02x".format(it) }.uppercase()
            return enteredPayload == shaResult.take(8)
        } else if (cleanEntered.startsWith(permPrefix)) {
            val enteredPayload = cleanEntered.substring(permPrefix.length)
            val salt = decryptSalt()
            val combined = permPart + salt
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(combined.toByteArray(Charsets.UTF_8))
            val shaResult = bytes.joinToString("") { "%02x".format(it) }.uppercase()
            return enteredPayload == shaResult.take(8)
        }
        return false
    }

    val makhzanCapitalState: StateFlow<Double> = productsState
        .map { products ->
            products.sumOf { it.purchasePrice * it.quantity }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // --- Habayeb Debts ---
    val habayebCustomersState: StateFlow<List<HabayebCustomer>> = habayebDao.getAllCustomersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habayebTransactionsState: StateFlow<List<HabayebTransaction>> = habayebDao.getAllTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTransactionsCount: StateFlow<Int> = combine(
        transactionsState,
        makhzanTransactionsState,
        habayebTransactionsState
    ) { main, makhzan, habayeb ->
        main.size + makhzan.size + habayeb.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val sharedPrefs = application.getSharedPreferences("mizan_prefs", Context.MODE_PRIVATE)

    private val _linkHabayebDebtsState = MutableStateFlow(sharedPrefs.getBoolean("link_habayeb_debts", false))
    val linkHabayebDebtsState = _linkHabayebDebtsState.asStateFlow()

    fun toggleLinkHabayebDebts(enabled: Boolean) {
        _linkHabayebDebtsState.value = enabled
        sharedPrefs.edit().putBoolean("link_habayeb_debts", enabled).apply()
    }

    val habayebOwedByThemTotalState: StateFlow<Double> = combine(habayebCustomersState, habayebTransactionsState) { customers, transactions ->
        var total = 0.0
        for (customer in customers) {
            val txs = transactions.filter { it.customerId == customer.id }
            val owedByThem = txs.filter { it.type == "OWED_BY_THEM" }.sumOf { it.amount }
            val paymentByThem = txs.filter { it.type == "PAYMENT_BY_THEM" }.sumOf { it.amount }
            val owedToThem = txs.filter { it.type == "OWED_TO_THEM" }.sumOf { it.amount }
            val paymentToThem = txs.filter { it.type == "PAYMENT_TO_THEM" }.sumOf { it.amount }
            
            val netDebt = (owedByThem - paymentByThem) - (owedToThem - paymentToThem)
            if (netDebt > 0.0) {
                total += netDebt
            }
        }
        total
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val habayebOwedToThemTotalState: StateFlow<Double> = combine(habayebCustomersState, habayebTransactionsState) { customers, transactions ->
        var total = 0.0
        for (customer in customers) {
            val txs = transactions.filter { it.customerId == customer.id }
            val owedByThem = txs.filter { it.type == "OWED_BY_THEM" }.sumOf { it.amount }
            val paymentByThem = txs.filter { it.type == "PAYMENT_BY_THEM" }.sumOf { it.amount }
            val owedToThem = txs.filter { it.type == "OWED_TO_THEM" }.sumOf { it.amount }
            val paymentToThem = txs.filter { it.type == "PAYMENT_TO_THEM" }.sumOf { it.amount }
            
            val netDebt = (owedByThem - paymentByThem) - (owedToThem - paymentToThem)
            if (netDebt < 0.0) {
                total += kotlin.math.abs(netDebt)
            }
        }
        total
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun saveHabayebCustomer(customer: HabayebCustomer, initialAmount: Double, initialType: String, customTimestamp: Long = System.currentTimeMillis() / 1000) {
        if (initialAmount > 0.0 && isTrialExpired()) {
            showActivationRequired.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            habayebDao.insertCustomer(customer)
            if (initialAmount > 0.0) {
                val txId = "dtx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
                val transaction = HabayebTransaction(
                    id = txId,
                    customerId = customer.id,
                    type = initialType,
                    amount = initialAmount,
                    timestamp = customTimestamp,
                    description = "الرصيد الافتتاحي المعاملة الأولى"
                )
                habayebDao.insertTransaction(transaction)
            }
        }
    }

    fun addHabayebTransaction(customerId: String, type: String, amount: Double, desc: String, timestamp: Long = System.currentTimeMillis() / 1000, linkedMainTxId: String? = null) {
        if (isTrialExpired()) {
            showActivationRequired.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
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
        }
    }

    fun updateHabayebCustomerName(customerId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldCustomer = habayebCustomersState.value.find { it.id == customerId }
            habayebDao.updateCustomerName(customerId, newName)
        }
    }

    fun deleteHabayebCustomer(customerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val customer = habayebCustomersState.value.find { it.id == customerId }
            if (customer != null) {
                softDeleteHabayebCustomerToTrash(customer)
            }
            val customerTxs = habayebDao.getAllTransactionsDirect().filter { it.customerId == customerId }
            customerTxs.forEach { softDeleteHabayebTransactionToTrash(it) }

            habayebDao.deleteCustomerById(customerId)
            habayebDao.deleteTransactionsByCustomer(customerId)
        }
    }

    fun deleteMultipleHabayebCustomers(customerIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val namesList = mutableListOf<String>()
            val allTxs = habayebDao.getAllTransactionsDirect()
            for (id in customerIds) {
                val customer = habayebCustomersState.value.find { it.id == id }
                if (customer != null) {
                    namesList.add(customer.name)
                    softDeleteHabayebCustomerToTrash(customer)
                }
                val customerTxs = allTxs.filter { it.customerId == id }
                customerTxs.forEach { softDeleteHabayebTransactionToTrash(it) }

                habayebDao.deleteCustomerById(id)
                habayebDao.deleteTransactionsByCustomer(id)
            }

            val textDesc = "تم حذف حسابات العملاء المحددة: (${namesList.joinToString(", ")}) نهائياً مع كافة الحركات المالية التابعة لها"
        }
    }

    fun deleteHabayebTransaction(txId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tx = habayebDao.getTransactionById(txId)
            if (tx != null) {
                softDeleteHabayebTransactionToTrash(tx)
            }
            if (tx?.linkedMainTxId != null) {
                val linkedTx = transactionsState.value.find { it.id == tx.linkedMainTxId }
                if (linkedTx != null) {
                    softDeleteTransactionToTrash(linkedTx)
                }
                repository.deleteTransactionById(tx.linkedMainTxId)
            }
            habayebDao.deleteTransactionById(txId)

            if (tx != null) {
                val customer = habayebCustomersState.value.find { it.id == tx.customerId }
                val textDesc = "تم حذف معاملة للعميل (${customer?.name ?: tx.customerId}): بقيمة ${formatDoubleCurrency(tx.amount)} (${tx.description})"
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

    // Backups list from local directory
    private val _localBackups = MutableStateFlow<List<File>>(emptyList())
    val localBackups: StateFlow<List<File>> = _localBackups.asStateFlow()

    init {
        refreshLocalBackups()

        // Generate Unified Long Device ID parts if not yet set
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val dId = getOrGenerateUnifiedDeviceId(context)
                val parts = dId.split("-")
                val temp = if (parts.size >= 3) parts[1] else ""
                val perm = if (parts.size >= 3) parts[2] else ""
                
                repository.settingsFlow.first().let { current ->
                    val existing = current ?: AppSettings()
                    if (existing.unifiedDeviceId != dId) {
                        val updated = existing.copy(
                            tempPart = temp,
                            permPart = perm,
                            unifiedDeviceId = dId
                        )
                        repository.saveSettings(updated)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val prefs = application.getSharedPreferences("mizan_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("categories_populated", false)) {
            viewModelScope.launch(Dispatchers.IO) {
                val defaults = listOf(
                    CustomCategory(name = "دقيق", tabType = "أغذية الدار", iconEmoji = "🌾"),
                    CustomCategory(name = "سكر", tabType = "أغذية الدار", iconEmoji = "🍬"),
                    CustomCategory(name = "أرز", tabType = "أغذية الدار", iconEmoji = "🍚"),
                    CustomCategory(name = "بهارات", tabType = "أغذية الدار", iconEmoji = "🌶️"),
                    CustomCategory(name = "بقوليات", tabType = "أغذية الدار", iconEmoji = "🫘"),
                    CustomCategory(name = "شاي", tabType = "أغذية الدار", iconEmoji = "☕"),
                    CustomCategory(name = "خضار", tabType = "أغذية الدار", iconEmoji = "🛒"),
                    CustomCategory(name = "غاز", tabType = "فواتير الدار", iconEmoji = "🔥"),
                    CustomCategory(name = "كهرباء", tabType = "فواتير الدار", iconEmoji = "⚡"),
                    CustomCategory(name = "ماء", tabType = "فواتير الدار", iconEmoji = "💧"),
                    CustomCategory(name = "إنترنت ورصيد", tabType = "فواتير الدار", iconEmoji = "🌐"),
                    CustomCategory(name = "حليب", tabType = "العائلة", iconEmoji = "🍼"),
                    CustomCategory(name = "حفاظات", tabType = "العائلة", iconEmoji = "👶"),
                    CustomCategory(name = "مصروف مدرسي", tabType = "العائلة", iconEmoji = "🎒"),
                    CustomCategory(name = "أخرى", tabType = "أخرى ومخصص", iconEmoji = "📁"),
                    CustomCategory(name = "ادخار", tabType = "أخرى ومخصص", iconEmoji = "🏦"),
                    CustomCategory(name = "طوارئ", tabType = "أخرى ومخصص", iconEmoji = "🚨"),
                    CustomCategory(name = "علاج ودواء", tabType = "أخرى ومخصص", iconEmoji = "💊"),
                    CustomCategory(name = "أثاث ومستلزمات", tabType = "أخرى ومخصص", iconEmoji = "🛋️")
                )
                defaults.forEach { repository.saveCustomCategory(it) }
                prefs.edit().putBoolean("categories_populated", true).apply()
            }
        }
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(BigDecimal.ZERO, BigDecimal.ZERO))

    // Group transactions into months and days with forwarded balance calculations
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
        return (settings.passcodeHash != null && hashed == settings.passcodeHash) || 
               (settings.recoveryPhraseHash != null && hashed == settings.recoveryPhraseHash)
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

            val typeAr = if (type == "INCOME") "الوارد" else "المنصرف"
            val baseDesc = "تم إضافة حركة مالية ($typeAr): بقيمة ${formatDoubleCurrency(amount)} تحت تصنيف ($category)"
            val textDesc = if (description.isNotBlank()) "$baseDesc - التفاصيل: $description" else baseDesc
            val txStr = "المعرف: ${id}, النوع: ${typeAr}, التصنيف: ${category}, المبلغ: ${amount}, البيان: ${description}"
        }
    }

    fun permanentlyDeleteDeletedItem(item: DeletedItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeDeletedItem(item)
        }
    }

    fun restoreDeletedItem(item: DeletedItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = JSONObject(item.jsonData)
                when (item.originalTableName) {
                    "transactions" -> {
                        val tx = TransactionDb(
                            id = root.getString("id"),
                            timestamp = root.getLong("timestamp"),
                            type = root.getString("type"),
                            category = root.getString("category"),
                            amount = root.getDouble("amount"),
                            description = root.getString("description")
                        )
                        repository.saveTransaction(tx)
                    }
                    "habayeb_transactions" -> {
                        val linkedId = if (root.has("linkedMainTxId") && !root.isNull("linkedMainTxId")) {
                            root.getString("linkedMainTxId")
                        } else null
                        
                        val tx = HabayebTransaction(
                            id = root.getString("id"),
                            customerId = root.getString("customerId"),
                            type = root.getString("type"),
                            amount = root.getDouble("amount"),
                            timestamp = root.getLong("timestamp"),
                            description = root.getString("description"),
                            linkedMainTxId = linkedId
                        )
                        habayebDao.insertTransaction(tx)
                    }
                    "makhzan_transactions" -> {
                        val tx = MakhzanTransactionEntity(
                            id = root.getLong("id"),
                            productId = root.getLong("productId"),
                            productName = root.getString("productName"),
                            type = root.getString("type"),
                            quantityChanged = root.getDouble("quantityChanged"),
                            pricePerUnit = root.getDouble("pricePerUnit"),
                            timestamp = root.getLong("timestamp"),
                            note = root.optString("note", "")
                        )
                        repository.saveMakhzanTransaction(tx)
                    }
                    "fixed_commitments" -> {
                        val fc = FixedCommitment(
                            name = root.getString("name"),
                            targetAmount = root.getDouble("targetAmount"),
                            currentProgress = root.getDouble("currentProgress"),
                            orderIndex = root.optInt("orderIndex", 0)
                        )
                        repository.saveCommitment(fc)
                    }
                    "makhzan_products" -> {
                        val product = ProductEntity(
                            id = root.optLong("id", 0L),
                            name = root.getString("name"),
                            category = root.getString("category"),
                            purchasePrice = root.getDouble("purchasePrice"),
                            sellingPrice = root.getDouble("sellingPrice"),
                            quantity = root.getDouble("quantity"),
                            imageUrl = if (root.isNull("imageUrl")) null else root.optString("imageUrl", null),
                            lowStockThreshold = root.optDouble("lowStockThreshold", 5.0),
                            unitType = root.optString("unitType", "حبة"),
                            hasSubUnits = root.optBoolean("hasSubUnits", false),
                            parentUnitName = root.optString("parentUnitName", "كرتون"),
                            subUnitName = root.optString("subUnitName", "حبة"),
                            subUnitCountPerParent = root.optDouble("subUnitCountPerParent", 1.0)
                        )
                        repository.saveProduct(product)
                    }
                    "habayeb_customers" -> {
                        val customer = HabayebCustomer(
                            id = root.getString("id"),
                            name = root.getString("name"),
                            phone = root.getString("phone"),
                            notes = root.getString("notes"),
                            createdAt = root.getLong("createdAt")
                        )
                        habayebDao.insertCustomer(customer)
                    }
                }
                // Once restored, remove from trash
                repository.removeDeletedItem(item)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearDeletedItems()
        }
    }

    private suspend fun softDeleteCommitmentToTrash(fc: FixedCommitment) {
        val jsonData = JSONObject().apply {
            put("name", fc.name)
            put("targetAmount", fc.targetAmount)
            put("currentProgress", fc.currentProgress)
            put("orderIndex", fc.orderIndex)
        }.toString()
        val trashItem = DeletedItemEntity(id = "fc_${fc.name}", sourceSystem = "دار", originalTableName = "fixed_commitments", jsonData = jsonData)
        repository.saveDeletedItem(trashItem)
    }

    private suspend fun softDeleteProductToTrash(product: ProductEntity) {
        val jsonData = JSONObject().apply {
            put("id", product.id)
            put("name", product.name)
            put("category", product.category)
            put("purchasePrice", product.purchasePrice)
            put("sellingPrice", product.sellingPrice)
            put("quantity", product.quantity)
            put("imageUrl", product.imageUrl ?: JSONObject.NULL)
            put("lowStockThreshold", product.lowStockThreshold)
            put("unitType", product.unitType)
            put("hasSubUnits", product.hasSubUnits)
            put("parentUnitName", product.parentUnitName)
            put("subUnitName", product.subUnitName)
            put("subUnitCountPerParent", product.subUnitCountPerParent)
        }.toString()
        val trashItem = DeletedItemEntity(id = "prod_${product.id}", sourceSystem = "مخزن", originalTableName = "makhzan_products", jsonData = jsonData)
        repository.saveDeletedItem(trashItem)
    }

    private suspend fun softDeleteHabayebCustomerToTrash(customer: HabayebCustomer) {
        val jsonData = JSONObject().apply {
            put("id", customer.id)
            put("name", customer.name)
            put("phone", customer.phone)
            put("notes", customer.notes)
            put("createdAt", customer.createdAt)
        }.toString()
        val trashItem = DeletedItemEntity(id = "cust_${customer.id}", sourceSystem = "حبايب", originalTableName = "habayeb_customers", jsonData = jsonData)
        repository.saveDeletedItem(trashItem)
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
        val trashItem = DeletedItemEntity(id = tx.id, sourceSystem = "دار", originalTableName = "transactions", jsonData = jsonData)
        repository.saveDeletedItem(trashItem)
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
        val trashItem = DeletedItemEntity(id = tx.id, sourceSystem = "حبايب", originalTableName = "habayeb_transactions", jsonData = jsonData)
        repository.saveDeletedItem(trashItem)
    }

    private suspend fun softDeleteMakhzanTransactionToTrash(tx: MakhzanTransactionEntity) {
        val jsonData = JSONObject().apply {
            put("id", tx.id)
            put("productId", tx.productId)
            put("productName", tx.productName)
            put("type", tx.type)
            put("quantityChanged", tx.quantityChanged)
            put("pricePerUnit", tx.pricePerUnit)
            put("timestamp", tx.timestamp)
            put("note", tx.note)
        }.toString()
        val trashItem = DeletedItemEntity(id = tx.id.toString(), sourceSystem = "مخزن", originalTableName = "makhzan_transactions", jsonData = jsonData)
        repository.saveDeletedItem(trashItem)
    }

    fun deleteTransaction(tx: TransactionDb) {
        viewModelScope.launch(Dispatchers.IO) {
            softDeleteTransactionToTrash(tx)
            repository.deleteTransaction(tx)

            val typeAr = if (tx.type == "INCOME") "الوارد" else "المنصرف"
            val textDesc = "تم حذف حركة مالية ($typeAr): بقيمة ${formatDoubleCurrency(tx.amount)} تحت تصنيف (${tx.category})"
            val txStr = "المعرف: ${tx.id}, النوع: ${typeAr}, التصنيف: ${tx.category}, المبلغ: ${tx.amount}, البيان: ${tx.description}"
        }
    }

    fun deleteTransactionById(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tx = transactionsState.value.find { it.id == id }
            if (tx != null) {
                softDeleteTransactionToTrash(tx)
            }
            repository.deleteTransactionById(id)

            if (tx != null) {
                val typeAr = if (tx.type == "INCOME") "الوارد" else "المنصرف"
                val textDesc = "تم حذف حركة مالية ($typeAr): بقيمة ${formatDoubleCurrency(tx.amount)} تحت تصنيف (${tx.category})"
                val txStr = "المعرف: ${tx.id}, النوع: ${typeAr}, التصنيف: ${tx.category}, المبلغ: ${tx.amount}, البيان: ${tx.description}"
            } else {
            }
        }
    }

    fun updateTransaction(tx: TransactionDb) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldTx = transactionsState.value.find { it.id == tx.id }
            repository.saveTransaction(tx)

            val typeAr = if (tx.type == "INCOME") "الوارد" else "المنصرف"
            val textDesc = "تم تعديل حركة مالية ($typeAr): القيمة الجديدة ${formatDoubleCurrency(tx.amount)} تحت تصنيف (${tx.category})"
            val oldTxStr = oldTx?.let {
                val oldTypeAr = if (it.type == "INCOME") "الوارد" else "المنصرف"
                "المعرف: ${it.id}, النوع: ${oldTypeAr}, التصنيف: ${it.category}, المبلغ: ${it.amount}, البيان: ${it.description}"
            }
            val newTxStr = "المعرف: ${tx.id}, النوع: ${typeAr}, التصنيف: ${tx.category}, المبلغ: ${tx.amount}, البيان: ${tx.description}"
        }
    }

    fun saveCommitment(name: String, targetAmount: Double, currentProgress: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val isEdit = commitmentsState.value.any { it.name == name }
            val count = commitmentsState.value.size
            val fc = FixedCommitment(name, targetAmount, currentProgress, count)
            repository.saveCommitment(fc)

            val textDesc = if (isEdit) {
                "تم تعديل الالتزام: $name بقيمة مستهدفة جديدة ${formatDoubleCurrency(targetAmount)}"
            } else {
                "تم إضافة التزام جديد لتدبير الدار: $name بمبلغ مستهدف ${formatDoubleCurrency(targetAmount)}"
            }
        }
    }

    fun updateCommitmentDirectly(commitment: FixedCommitment) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldFc = commitmentsState.value.find { it.name == commitment.name }
            repository.saveCommitment(commitment)

            val textDesc = "تم تعديل رصيد الالتزام (${commitment.name}): المتوفر حالياً أصبح ${formatDoubleCurrency(commitment.currentProgress)} من أصل ${formatDoubleCurrency(commitment.targetAmount)}"
            val oldStr = oldFc?.let { "الاسم: ${it.name}, المستهدف: ${it.targetAmount}, المتوفر: ${it.currentProgress}" }
            val newStr = "الاسم: ${commitment.name}, المستهدف: ${commitment.targetAmount}, المتوفر: ${commitment.currentProgress}"
        }
    }

    fun reorderCommitment(commitment: FixedCommitment, toPosition: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = commitmentsState.value.toMutableList()
            // Make sure list is sorted by orderIndex first
            currentList.sortBy { it.orderIndex }
            
            val targetIndex = (toPosition - 1).coerceIn(0, currentList.size - 1)
            val currentIndex = currentList.indexOfFirst { it.name == commitment.name }
            
            if (currentIndex != -1 && currentIndex != targetIndex) {
                val item = currentList.removeAt(currentIndex)
                currentList.add(targetIndex, item)
                
                // Update orderIndex for all
                val updatedList = currentList.mapIndexed { index, fc -> 
                    fc.copy(orderIndex = index)
                }
                
                repository.updateCommitments(updatedList)
            }
        }
    }

    fun deleteCommitment(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldFc = commitmentsState.value.find { it.name == name }
            if (oldFc != null) {
                softDeleteCommitmentToTrash(oldFc)
            }
            repository.deleteCommitment(name)

            val oldStr = oldFc?.let { "الاسم: ${it.name}, المستهدف: ${it.targetAmount}, المتوفر: ${it.currentProgress}" }
        }
    }

    fun saveCustomCategory(name: String, tabType: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val isEdit = customCategoriesState.value.any { it.name == name }
            repository.saveCustomCategory(CustomCategory(name = name, tabType = tabType, iconEmoji = emoji))

            val actionName = if (isEdit) "تعديل" else "إضافة"
            val textDesc = "تم $actionName تصنيف مخصص للدار: $emoji $name في مجموعة ($tabType)"
        }
    }

    fun deleteCustomCategory(customCategory: CustomCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCustomCategory(customCategory)

            val textDesc = "تم حذف التصنيف المخصص للدار: ${customCategory.iconEmoji} ${customCategory.name} من مجموعة (${customCategory.tabType})"
        }
    }

    fun saveProduct(
        name: String,
        category: String,
        purchasePrice: Double,
        sellingPrice: Double,
        quantity: Double,
        unitType: String,
        lowStockThreshold: Double = 5.0,
        imageUrl: String? = null,
        note: String = "",
        hasSubUnits: Boolean = false,
        parentUnitName: String = "كرتون",
        subUnitName: String = "حبة",
        subUnitCountPerParent: Double = 1.0
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val product = ProductEntity(
                name = name,
                category = category,
                purchasePrice = purchasePrice,
                sellingPrice = sellingPrice,
                quantity = quantity,
                lowStockThreshold = lowStockThreshold,
                unitType = unitType,
                imageUrl = imageUrl,
                hasSubUnits = hasSubUnits,
                parentUnitName = parentUnitName,
                subUnitName = subUnitName,
                subUnitCountPerParent = subUnitCountPerParent
            )
            val newId = repository.saveProduct(product)

            val textDesc = "تم إضافة منتج جديد للمخزن: $name بقيمة شراء ${formatDoubleCurrency(purchasePrice)} وسعر بيع ${formatDoubleCurrency(sellingPrice)}"
            val pStr = "الاسم: $name, التصنيف: $category, سعر الشراء: $purchasePrice, سعر البيع: $sellingPrice, الكمية الابتدائية: $quantity $unitType"

            // Save the opening transaction note entered by the user
            val initTx = MakhzanTransactionEntity(
                productId = newId,
                productName = name,
                type = "وارد",
                quantityChanged = quantity,
                pricePerUnit = purchasePrice,
                timestamp = System.currentTimeMillis(),
                note = note.ifBlank { "رصيد تأسيسي للمنتج" }
            )
            repository.saveMakhzanTransaction(initTx)
        }
    }

    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldProd = productsState.value.find { it.id == product.id }
            repository.updateProduct(product)

            val textDesc = "تم تعديل بيانات المنتج في المخزن: ${product.name}، الكمية الحالية: ${product.quantity} ${product.unitType}"
            val oldStr = oldProd?.let { "الاسم: ${it.name}, التصنيف: ${it.category}, سعر الشراء: ${it.purchasePrice}, سعر البيع: ${it.sellingPrice}, الكمية: ${it.quantity} ${it.unitType}" }
            val newStr = "الاسم: ${product.name}, التصنيف: ${product.category}, سعر الشراء: ${product.purchasePrice}, سعر البيع: ${product.sellingPrice}, الكمية: ${product.quantity} ${product.unitType}"
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            softDeleteProductToTrash(product)
            repository.deleteProduct(product)

            val textDesc = "تم حذف منتج من المخزن نهائياً: ${product.name}"
            val oldStr = "الاسم: ${product.name}, التصنيف: ${product.category}, سعر الشراء: ${product.purchasePrice}, سعر البيع: ${product.sellingPrice}, الكمية: ${product.quantity} ${product.unitType}"
        }
    }

    fun addProductStock(product: ProductEntity, addQty: Double, note: String = "") {
        if (isTrialExpired()) {
            showActivationRequired.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val updated = product.copy(quantity = product.quantity + addQty)
            repository.updateProduct(updated)

            val makhzanTx = MakhzanTransactionEntity(
                productId = product.id,
                productName = product.name,
                type = "وارد",
                quantityChanged = addQty,
                pricePerUnit = product.purchasePrice,
                timestamp = System.currentTimeMillis(),
                note = note
            )
            repository.saveMakhzanTransaction(makhzanTx)
        }
    }

    fun registerProductSale(product: ProductEntity, saleQty: Double, syncToMizan: Boolean, note: String = "") {
        if (isTrialExpired()) {
            showActivationRequired.value = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val finalSaleQty = if (saleQty > product.quantity) product.quantity else saleQty
            if (finalSaleQty <= 0.0) return@launch
            
            // 1. Update product stock
            val updated = product.copy(quantity = product.quantity - finalSaleQty)
            repository.updateProduct(updated)

            val makhzanTx = MakhzanTransactionEntity(
                productId = product.id,
                productName = product.name,
                type = "صادر",
                quantityChanged = finalSaleQty,
                pricePerUnit = product.sellingPrice,
                timestamp = System.currentTimeMillis(),
                note = note
            )
            repository.saveMakhzanTransaction(makhzanTx)

            // 2. Auto sync to mizan al-dar
            if (syncToMizan) {
                val totalAmount = product.sellingPrice * finalSaleQty
                val unitSuffix = if (product.unitType == "كيلو") "كجم" else "حبة"
                val desc = "مبيعات بضائع: ${product.name} (كمية: $finalSaleQty $unitSuffix)"
                // Create a transaction in main DB
                val txId = "tx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
                val newTx = TransactionDb(
                    id = txId,
                    timestamp = System.currentTimeMillis() / 1000,
                    type = "INCOME",
                    category = "مبيعات المخزن",
                    amount = totalAmount,
                    description = desc
                )
                repository.saveTransaction(newTx)
            }
        }
    }

    fun deleteMakhzanTransaction(tx: MakhzanTransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            softDeleteMakhzanTransactionToTrash(tx)
            val product = repository.productsFlow.first().find { it.id == tx.productId }
            if (product != null) {
                val newQty = if (tx.type == "وارد") {
                    product.quantity - tx.quantityChanged
                } else {
                    product.quantity + tx.quantityChanged
                }
                repository.updateProduct(product.copy(quantity = newQty.coerceAtLeast(0.0)))
            }
            repository.deleteMakhzanTransaction(tx)
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllData()
            habayebDao.clearAllCustomers()
            habayebDao.clearAllTransactions()
            refreshLocalBackups()
        }
    }

    fun clearLocalCopyAndWipeMemory(context: Context) {
        deleteAllData()
    }

    // --- Backup & Restore (.mzd) ---

    // Get Directory for local backups
    fun getBackupDirectory(): File {
        val context = getApplication<android.app.Application>()
        val rootDir = context.getExternalFilesDir(null)
        val backupDir = File(rootDir, "MizanAlDar/Backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        return backupDir
    }

    fun refreshLocalBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = getBackupDirectory()
            val files = dir.listFiles { _, name -> name.endsWith(".mzd") }?.toList() ?: emptyList()
            _localBackups.value = files.sortedByDescending { it.lastModified() }
        }
    }

    fun handleGoogleOAuthCode(code: String, email: String? = null, redirectUri: String = "", onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = googleDriveSyncHelper.handleAuthorizationCode(code, email, redirectUri)
            onComplete?.invoke(success)
        }
    }

    fun backupToGoogleDriveDirect(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = habayebDao.getAllCustomersDirect()
                val habayebTxs = habayebDao.getAllTransactionsDirect()
                val productsList = repository.productsFlow.first()
                val makhzanTxList = repository.makhzanTransactionsFlow.first()
                val deletedItems = repository.deletedItemsFlow.first()
                val jsonStr = exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, productsList, makhzanTxList, deletedItems)
                val success = googleDriveSyncHelper.uploadBackupToDrive(jsonStr)
                launch(Dispatchers.Main) {
                    onComplete?.invoke(success)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun restoreFromGoogleDriveDirect(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonStr = googleDriveSyncHelper.downloadBackupFromDrive()
            if (jsonStr != null) {
                executeMasterRestore(jsonStr, context) { success, _ ->
                    onComplete(success)
                }
            } else {
                onComplete(false)
            }
        }
    }

    fun googleDriveLogout() {
        googleDriveSyncHelper.logout()
    }

    // Expose backup JSON for clipboard operations
    fun getBackupJsonForClipboard(onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = habayebDao.getAllCustomersDirect()
                val habayebTxs = habayebDao.getAllTransactionsDirect()
                val productsList = repository.productsFlow.first()
                val makhzanTxList = repository.makhzanTransactionsFlow.first()
                val deletedItems = repository.deletedItemsFlow.first()
                val jsonStr = exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, productsList, makhzanTxList, deletedItems)
                launch(Dispatchers.Main) {
                    onComplete(jsonStr)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Export internal database state to .mzd custom file
    fun createLocalBackup(context: Context, onComplete: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = habayebDao.getAllCustomersDirect()
                val habayebTxs = habayebDao.getAllTransactionsDirect()
                val productsList = repository.productsFlow.first()
                val makhzanTxList = repository.makhzanTransactionsFlow.first()
                val deletedItems = repository.deletedItemsFlow.first()

                val jsonStr = exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, productsList, makhzanTxList, deletedItems)
                val dir = getBackupDirectory()
                val timestamp = System.currentTimeMillis() / 1000
                val file = File(dir, "mzd_backup_${timestamp}.mzd")
                file.writeText(jsonStr)

                refreshLocalBackups()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "تم حفظ النسخة الاحتياطية بنجاح 💾", Toast.LENGTH_SHORT).show()
                    onComplete(file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "فشل حفظ النسخة: ${e.message}", Toast.LENGTH_LONG).show()
                    onComplete(null)
                }
            }
        }
    }

    // Master Restore Engine - Centralized Master Import Function
    fun executeMasterRestore(rawJsonString: String, context: Context, onComplete: (Boolean, AppSettings?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. JSON Decoding & Verification
                val root = JSONObject(rawJsonString)

                // 2. Database Wipe (Clear Mizan and Habayeb simultaneously)
                repository.clearTransactions()
                repository.clearCommitments()
                repository.clearCustomCategories()
                repository.clearProducts()
                habayebDao.clearAllCustomers()
                habayebDao.clearAllTransactions()
                repository.clearDeletedItems()

                // 3. Mizan Al-Dar Restore (settings, fixed_commitments, transactions)
                val currentLocalSettings = repository.settingsFlow.first() ?: AppSettings()
                val data = importBackupFromJson(rawJsonString)
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

                repository.saveSettings(restoredSettings)
                for (fc in restoredCommitments) {
                    repository.saveCommitment(fc)
                }
                for (tx in restoredTransactions) {
                    repository.saveTransaction(tx)
                }

                // 4. Smart Habayeb Restore (with Null-Safety and check of the debts key)
                if (root.has("habayeb_debts") && !root.isNull("habayeb_debts")) {
                    val jsonHabayebObj = root.optJSONObject("habayeb_debts")
                    if (jsonHabayebObj != null) {
                        val custArr = jsonHabayebObj.optJSONArray("customers")
                        if (custArr != null) {
                            for (i in 0 until custArr.length()) {
                                val obj = custArr.getJSONObject(i)
                                val customer = HabayebCustomer(
                                    id = obj.getString("id"),
                                    name = obj.getString("name"),
                                    phone = obj.optString("phone", ""),
                                    notes = obj.optString("notes", ""),
                                    createdAt = obj.optLong("created_at", System.currentTimeMillis() / 1000)
                                )
                                habayebDao.insertCustomer(customer)
                            }
                        }
                        val txArr = jsonHabayebObj.optJSONArray("debt_transactions")
                        if (txArr != null) {
                            for (i in 0 until txArr.length()) {
                                val obj = txArr.getJSONObject(i)
                                val transaction = HabayebTransaction(
                                    id = obj.getString("id"),
                                    customerId = obj.getString("customer_id"),
                                    type = obj.getString("type"),
                                    amount = obj.getDouble("amount"),
                                    timestamp = obj.getLong("timestamp"),
                                    description = obj.optString("description", ""),
                                    linkedMainTxId = obj.optString("linked_main_tx_id", null)
                                )
                                habayebDao.insertTransaction(transaction)
                            }
                        }
                    }
                }

                // 4.5. Makhzan Inventory Restore (Null-Safe, supporting backward compatibility)
                if (root.has("makhzan_inventory") && !root.isNull("makhzan_inventory")) {
                    val jsonMakhzanObj = root.optJSONObject("makhzan_inventory")
                    if (jsonMakhzanObj != null) {
                        val productsArr = jsonMakhzanObj.optJSONArray("products")
                        if (productsArr != null) {
                            for (i in 0 until productsArr.length()) {
                                val obj = productsArr.getJSONObject(i)
                                val product = ProductEntity(
                                    id = obj.optLong("id", 0L),
                                    name = obj.getString("name"),
                                    category = obj.getString("category"),
                                    purchasePrice = obj.getDouble("purchase_price"),
                                    sellingPrice = obj.getDouble("selling_price"),
                                    quantity = obj.optDouble("quantity", 0.0),
                                    imageUrl = obj.optString("image_url", "").takeIf { it.isNotEmpty() },
                                    lowStockThreshold = obj.optDouble("low_stock_threshold", 5.0),
                                    unitType = obj.optString("unit_type", "حبة"),
                                    hasSubUnits = obj.optBoolean("has_sub_units", false),
                                    parentUnitName = obj.optString("parent_unit_name", "كرتون"),
                                    subUnitName = obj.optString("sub_unit_name", "حبة"),
                                    subUnitCountPerParent = obj.optDouble("sub_unit_count_per_parent", 1.0)
                                )
                                repository.saveProduct(product)
                            }
                        }
                        
                        val makhzanTxArr = jsonMakhzanObj.optJSONArray("transactions")
                        if (makhzanTxArr != null) {
                            for (i in 0 until makhzanTxArr.length()) {
                                val obj = makhzanTxArr.getJSONObject(i)
                                val tx = MakhzanTransactionEntity(
                                    id = obj.optLong("id", 0L),
                                    productId = obj.getLong("product_id"),
                                    productName = obj.getString("product_name"),
                                    type = obj.getString("type"),
                                    quantityChanged = obj.getDouble("quantity_changed"),
                                    pricePerUnit = obj.getDouble("price_per_unit"),
                                    timestamp = obj.getLong("timestamp"),
                                    note = obj.optString("note", "")
                                )
                                repository.saveMakhzanTransaction(tx)
                            }
                        }
                    }
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

                // 5. State Refresh / State Reload and Notify
                refreshLocalBackups()

                launch(Dispatchers.Main) {
                    Toast.makeText(context, "تمت استعادة كافة البيانات بنجاح بنسبة ١٠٠٪ 🎉 (الحسابات الرئيسية والعملاء بالتزامن)", Toast.LENGTH_SHORT).show()
                    onComplete(true, restoredSettings)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "تعذر استيراد البيانات. يرجى التأكد من اختيار ملف (.mzd) صحيح.", Toast.LENGTH_LONG).show()
                    onComplete(false, null)
                }
            }
        }
    }

    // Classic delegate for any other components
    fun restoreFromMzdContent(jsonContent: String, context: Context, onComplete: (Boolean) -> Unit) {
        executeMasterRestore(jsonContent, context) { success, _ ->
            onComplete(success)
        }
    }

    // Restore from local file directly with Master Restore Engine integration
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
                        Toast.makeText(context, "الملف لم يعد موجوداً", Toast.LENGTH_SHORT).show()
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

    // Utility Serializers to avoid library mismatch
    private fun exportBackupToJson(
        settings: AppSettings,
        commitments: List<FixedCommitment>,
        transactions: List<TransactionDb>,
        habayebCustomers: List<HabayebCustomer> = emptyList(),
        habayebTransactions: List<HabayebTransaction> = emptyList(),
        products: List<ProductEntity> = emptyList(),
        makhzanTransactions: List<MakhzanTransactionEntity> = emptyList(),
        deletedItems: List<DeletedItemEntity> = emptyList()
    ): String {
        val root = JSONObject()

        val metadata = JSONObject()
        metadata.put("app_name", "Mizan Al-Dar")
        metadata.put("app_version", "1.1.0")
        metadata.put("backup_timestamp", System.currentTimeMillis() / 1000)
        metadata.put("security_hash", "security_" + (settings.hashCode() + transactions.size * 31).toString())
        root.put("metadata", metadata)

        val settingsObj = JSONObject()
        settingsObj.put("currency_symbol", settings.currencySymbol)
        settingsObj.put("user_role", settings.userRole)
        settingsObj.put("guardian_number", settings.guardianNumber)
        settingsObj.put("guardian_relation", settings.guardianRelation)
        settingsObj.put("school_expenses_enabled", settings.schoolExpensesEnabled)
        settingsObj.put("theme_mode", settings.themeMode)
        // Strictly prevent exporting any security / activation fields
        root.put("settings", settingsObj)

        val commitmentsArr = JSONArray()
        for (fc in commitments) {
            val fcObj = JSONObject()
            fcObj.put("name", fc.name)
            fcObj.put("target_amount", fc.targetAmount)
            fcObj.put("current_progress", fc.currentProgress)
            fcObj.put("order_index", fc.orderIndex)
            commitmentsArr.put(fcObj)
        }
        root.put("fixed_commitments", commitmentsArr)

        val transactionsArr = JSONArray()
        for (tx in transactions) {
            val txObj = JSONObject()
            txObj.put("id", tx.id)
            txObj.put("timestamp", tx.timestamp)
            txObj.put("type", tx.type)
            txObj.put("category", tx.category)
            txObj.put("amount", tx.amount)
            txObj.put("description", tx.description)
            transactionsArr.put(txObj)
        }
        root.put("transactions", transactionsArr)

        val habayebObj = JSONObject()
        val habayebCustomersArr = JSONArray()
        for (c in habayebCustomers) {
            val cObj = JSONObject()
            cObj.put("id", c.id)
            cObj.put("name", c.name)
            cObj.put("phone", c.phone)
            cObj.put("notes", c.notes)
            cObj.put("created_at", c.createdAt)
            habayebCustomersArr.put(cObj)
        }
        habayebObj.put("customers", habayebCustomersArr)

        val habayebTxsArr = JSONArray()
        for (t in habayebTransactions) {
            val tObj = JSONObject()
            tObj.put("id", t.id)
            tObj.put("customer_id", t.customerId)
            tObj.put("type", t.type)
            tObj.put("amount", t.amount)
            tObj.put("timestamp", t.timestamp)
            tObj.put("description", t.description)
            tObj.put("linked_main_tx_id", t.linkedMainTxId)
            habayebTxsArr.put(tObj)
        }
        habayebObj.put("debt_transactions", habayebTxsArr)
        root.put("habayeb_debts", habayebObj)

        // Makhzan Array
        val makhzanObj = JSONObject()
        val productsArr = JSONArray()
        for (p in products) {
            val pObj = JSONObject()
            pObj.put("id", p.id)
            pObj.put("name", p.name)
            pObj.put("category", p.category)
            pObj.put("purchase_price", p.purchasePrice)
            pObj.put("selling_price", p.sellingPrice)
            pObj.put("quantity", p.quantity)
            pObj.put("image_url", p.imageUrl ?: "")
            pObj.put("low_stock_threshold", p.lowStockThreshold)
            pObj.put("unit_type", p.unitType)
            pObj.put("has_sub_units", p.hasSubUnits)
            pObj.put("parent_unit_name", p.parentUnitName)
            pObj.put("sub_unit_name", p.subUnitName)
            pObj.put("sub_unit_count_per_parent", p.subUnitCountPerParent)
            productsArr.put(pObj)
        }
        makhzanObj.put("products", productsArr)

        val makhzanTxsArr = JSONArray()
        for (tx in makhzanTransactions) {
            val txObj = JSONObject()
            txObj.put("id", tx.id)
            txObj.put("product_id", tx.productId)
            txObj.put("product_name", tx.productName)
            txObj.put("type", tx.type)
            txObj.put("quantity_changed", tx.quantityChanged)
            txObj.put("price_per_unit", tx.pricePerUnit)
            txObj.put("timestamp", tx.timestamp)
            txObj.put("note", tx.note)
            makhzanTxsArr.put(txObj)
        }
        makhzanObj.put("transactions", makhzanTxsArr)
        root.put("makhzan_inventory", makhzanObj)

        val deletedItemsArr = JSONArray()
        for (di in deletedItems) {
            val diObj = JSONObject()
            diObj.put("id", di.id)
            diObj.put("sourceSystem", di.sourceSystem)
            diObj.put("originalTableName", di.originalTableName)
            diObj.put("jsonData", di.jsonData)
            diObj.put("deletedAt", di.deletedAt)
            deletedItemsArr.put(diObj)
        }
        root.put("deleted_items", deletedItemsArr)

        return root.toString(2)
    }

    private fun importBackupFromJson(jsonString: String): Triple<AppSettings, List<FixedCommitment>, List<TransactionDb>> {
        val root = JSONObject(jsonString)

        val settingsObj = root.optJSONObject("settings")
        val settings = if (settingsObj != null) {
            AppSettings(
                currencySymbol = settingsObj.optString("currency_symbol", "ر.ي"),
                userRole = settingsObj.optString("user_role", "الزوجة"),
                guardianNumber = settingsObj.optString("guardian_number", "+967774004399"),
                guardianRelation = settingsObj.optString("guardian_relation", "الزوج"),
                schoolExpensesEnabled = settingsObj.optBoolean("school_expenses_enabled", true),
                themeMode = settingsObj.optInt("theme_mode", 0)
            )
        } else {
            AppSettings()
        }

        val commitmentsList = mutableListOf<FixedCommitment>()
        val commitmentsArr = root.optJSONArray("fixed_commitments")
        if (commitmentsArr != null) {
            for (i in 0 until commitmentsArr.length()) {
                val obj = commitmentsArr.getJSONObject(i)
                commitmentsList.add(
                    FixedCommitment(
                        name = obj.getString("name"),
                        targetAmount = obj.getDouble("target_amount"),
                        currentProgress = obj.getDouble("current_progress"),
                        orderIndex = obj.optInt("order_index", i)
                    )
                )
            }
        }

        val transactionsList = mutableListOf<TransactionDb>()
        val transactionsArr = root.optJSONArray("transactions")
        if (transactionsArr != null) {
            for (i in 0 until transactionsArr.length()) {
                val obj = transactionsArr.getJSONObject(i)
                transactionsList.add(
                    TransactionDb(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        type = obj.getString("type"),
                        category = obj.getString("category"),
                        amount = obj.getDouble("amount"),
                        description = obj.optString("description", "")
                    )
                )
            }
        }

        return Triple(settings, commitmentsList, transactionsList)
    }

    // Format utility for prices in Arabic
    fun formatCurrency(amount: BigDecimal, symbol: String = "ر.ي"): String {
        return try {
            val formatter = DecimalFormat("#,##0")
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        } catch (e: Exception) {
            val formatter = DecimalFormat("#,##0")
            val formatted = formatter.format(amount)
            "$formatted $symbol"
        }
    }

    fun formatDoubleCurrency(amount: Double, symbol: String = "ر.ي"): String {
        val formatter = DecimalFormat("#,##0")
        val formatted = formatter.format(amount)
        return "$formatted $symbol"
    }

    // Formulate a beautiful, personalized, warm WhatsApp message based on family relationship context
    fun generateWhatsappMessage(userRole: String, guardianRelation: String, missingText: String): String {
        return "السلام عليكم، هذه قائمة بالنواقص والاحتياجات المسجلة:\n" +
               "$missingText\n" +
               "مع خالص التحيات."
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

// Helper function to share the backup file using FileProvider
fun shareBackupFile(context: Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "حفظ أو مشاركة ملف النسخة 💾"))
    } catch (e: Exception) {
        Toast.makeText(context, "فشل مشاركة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// Helper to launch or download Google Drive app from store
fun openGoogleDriveApp(context: Context) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.docs")
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            val playIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.apps.docs"))
            context.startActivity(playIntent)
        }
    } catch (e: Exception) {
        try {
            val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.docs"))
            context.startActivity(webIntent)
        } catch (ex: Exception) {
            Toast.makeText(context, "تعذر فتح متجر التطبيقات.", Toast.LENGTH_SHORT).show()
        }
    }
}
