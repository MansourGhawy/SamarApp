package com.example.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.local.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FinanceRepository(internal val database: AppDatabase) {

    private val settingsDao = database.settingsDao()
    private val commitmentDao = database.commitmentDao()
    private val transactionDao = database.transactionDao()
    private val customCategoryDao = database.customCategoryDao()
    private val deletedItemDao = database.deletedItemDao()
    private val habayebDao = database.habayebDao()

    // Flow Exposures
    val settingsFlow: Flow<AppSettings?> = settingsDao.getSettingsFlow()
    val commitmentsFlow: Flow<List<FixedCommitment>> = commitmentDao.getAllCommitmentsFlow()
    val transactionsFlow: Flow<List<TransactionDb>> = transactionDao.getAllTransactionsFlow()
    val customCategoriesFlow: Flow<List<CustomCategory>> = customCategoryDao.getAllCustomCategoriesFlow()
    val deletedItemsFlow: Flow<List<DeletedItemEntity>> = deletedItemDao.getAllDeletedItemsFlow()
    val habayebCustomersFlow: Flow<List<HabayebCustomer>> = habayebDao.getAllCustomersFlow()
    val habayebTransactionsFlow: Flow<List<HabayebTransaction>> = habayebDao.getAllTransactionsFlow()

    fun getTransactionsForCustomerFlow(customerId: String): Flow<List<HabayebTransaction>> = 
        habayebDao.getTransactionsForCustomerFlow(customerId)

    // Deleted Items Trash
    suspend fun saveDeletedItem(item: DeletedItemEntity) = deletedItemDao.insertDeletedItem(item)
    suspend fun removeDeletedItem(item: DeletedItemEntity) = deletedItemDao.deleteItem(item)
    suspend fun removeDeletedItemById(id: String) = deletedItemDao.deleteItemById(id)
    suspend fun clearDeletedItems() = deletedItemDao.clearAllDeletedItems()

    // Settings
    suspend fun getSettingsDirect(): AppSettings? = settingsDao.getSettingsDirect()
    suspend fun saveSettings(settings: AppSettings) = settingsDao.insertOrUpdateSettings(settings)

    // Commitments
    suspend fun saveCommitment(commitment: FixedCommitment) = commitmentDao.insertCommitment(commitment)
    suspend fun updateCommitments(commitments: List<FixedCommitment>) = commitmentDao.updateCommitments(commitments)
    suspend fun deleteCommitment(name: String) = commitmentDao.deleteCommitment(name)
    suspend fun clearCommitments() = commitmentDao.clearAllCommitments()

    // Transactions
    suspend fun getTransactionById(id: String): TransactionDb? = transactionDao.getTransactionById(id)
    suspend fun saveTransaction(transaction: TransactionDb) = transactionDao.insertTransaction(transaction)
    suspend fun deleteTransaction(transaction: TransactionDb) = transactionDao.deleteTransaction(transaction)
    suspend fun deleteTransactionById(id: String) = transactionDao.deleteTransactionById(id)
    suspend fun clearTransactions() = transactionDao.clearAllTransactions()

    // Custom Categories
    suspend fun saveCustomCategory(category: CustomCategory) = customCategoryDao.insertCategory(category)
    suspend fun deleteCustomCategory(category: CustomCategory) = customCategoryDao.deleteCategory(category)
    suspend fun clearCustomCategories() = customCategoryDao.clearAllCustomCategories()

    // Habayeb (حسابات الحبايب) operations
    suspend fun insertCustomer(customer: HabayebCustomer) = habayebDao.insertCustomer(customer)
    suspend fun updateCustomer(customer: HabayebCustomer) = habayebDao.updateCustomer(customer)
    suspend fun insertCustomerWithOpeningTransaction(customer: HabayebCustomer, transaction: HabayebTransaction?) = 
        habayebDao.insertCustomerWithOpeningTransaction(customer, transaction)
    suspend fun deleteCustomerAndTransactions(customerId: String) = habayebDao.deleteCustomerAndTransactions(customerId)
    suspend fun updateCustomerName(id: String, newName: String) = habayebDao.updateCustomerName(id, newName)
    suspend fun insertHabayebTransaction(transaction: HabayebTransaction) = habayebDao.insertTransaction(transaction)
    suspend fun deleteHabayebTransaction(transaction: HabayebTransaction) = habayebDao.deleteTransaction(transaction)
    suspend fun deleteHabayebTransactionById(id: String) = habayebDao.deleteTransactionById(id)
    suspend fun getHabayebTransactionById(id: String): HabayebTransaction? = habayebDao.getTransactionById(id)
    suspend fun getAllCustomersDirect(): List<HabayebCustomer> = habayebDao.getAllCustomersDirect()
    suspend fun getAllTransactionsDirect(): List<HabayebTransaction> = habayebDao.getAllTransactionsDirect()
    suspend fun clearAllCustomers() = habayebDao.clearAllCustomers()
    suspend fun clearAllTransactions() = habayebDao.clearAllTransactions()

    // Clean critical master reset
    suspend fun deleteAllData(): Unit = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                transactionDao.clearAllTransactions()
                commitmentDao.clearAllCommitments()
                customCategoryDao.clearAllCustomCategories()
                deletedItemDao.clearAllDeletedItems()
                habayebDao.clearAllCustomers()
                habayebDao.clearAllTransactions()
                settingsDao.insertOrUpdateSettings(AppSettings(isFirstLaunch = false))
            }
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside deleteAllData: ${e.message}", e)
            throw e
        }
    }

    suspend fun softDeleteCommitmentToTrash(fc: FixedCommitment) = withContext(Dispatchers.IO) {
        val jsonData = JSONObject().apply {
            put("name", fc.name)
            put("targetAmount", fc.targetAmount)
            put("currentProgress", fc.currentProgress)
            put("orderIndex", fc.orderIndex)
        }.toString()
        val trashItem = DeletedItemEntity(id = "fc_${fc.name}", sourceSystem = "دار", originalTableName = "fixed_commitments", jsonData = jsonData)
        saveDeletedItem(trashItem)
    }

    suspend fun softDeleteHabayebBundleToTrash(customer: HabayebCustomer, transactions: List<HabayebTransaction>) = withContext(Dispatchers.IO) {
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
                    put("is_foreign", tx.is_foreign)
                    put("currency_code", tx.currency_code)
                    put("foreign_amount", tx.foreign_amount)
                    put("exchange_rate", tx.exchange_rate)
                    put("is_rate_calculated", tx.is_rate_calculated)
                    put("equivalent_amount", tx.equivalent_amount)
                })
            }
            put("transactions", txsArray)
            put("totalTransactions", transactions.size)
            put("name", customer.name) // For easy display
        }.toString()
        val trashItem = DeletedItemEntity(id = "bundle_${customer.id}", sourceSystem = "حبايب", originalTableName = "habayeb_bundle", jsonData = jsonData)
        saveDeletedItem(trashItem)
    }

    suspend fun softDeleteHabayebCustomerToTrash(customer: HabayebCustomer) = withContext(Dispatchers.IO) {
        val jsonData = JSONObject().apply {
            put("id", customer.id)
            put("name", customer.name)
            put("phone", customer.phone)
            put("notes", customer.notes)
            put("createdAt", customer.createdAt)
        }.toString()
        val trashItem = DeletedItemEntity(id = "cust_${customer.id}", sourceSystem = "حبايب", originalTableName = "habayeb_customers", jsonData = jsonData)
        saveDeletedItem(trashItem)
    }

    suspend fun softDeleteTransactionToTrash(tx: TransactionDb) = withContext(Dispatchers.IO) {
        val jsonData = JSONObject().apply {
            put("id", tx.id)
            put("timestamp", tx.timestamp)
            put("type", tx.type)
            put("category", tx.category)
            put("amount", tx.amount)
            put("description", tx.description)
        }.toString()
        val trashItem = DeletedItemEntity(id = tx.id, sourceSystem = "دار", originalTableName = "transactions", jsonData = jsonData)
        saveDeletedItem(trashItem)
    }

    suspend fun softDeleteTransactionBundleToTrash(transactions: List<TransactionDb>, title: String) = withContext(Dispatchers.IO) {
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
            val totalNet = transactions.sumOf { if (it.type == "INCOME") it.amount else -it.amount }
            put("totalNet", totalNet)
            put("name", title)
        }.toString()
        val id = "dar_bundle_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
        val trashItem = DeletedItemEntity(id = id, sourceSystem = "دار", originalTableName = "dar_bundle", jsonData = jsonData)
        saveDeletedItem(trashItem)
    }

    suspend fun softDeleteHabayebTransactionToTrash(tx: HabayebTransaction) = withContext(Dispatchers.IO) {
        val jsonData = JSONObject().apply {
            put("id", tx.id)
            put("customerId", tx.customerId)
            put("type", tx.type)
            put("amount", tx.amount)
            put("timestamp", tx.timestamp)
            put("description", tx.description)
            put("linkedMainTxId", tx.linkedMainTxId ?: JSONObject.NULL)
            put("is_foreign", tx.is_foreign)
            put("currency_code", tx.currency_code)
            put("foreign_amount", tx.foreign_amount)
            put("exchange_rate", tx.exchange_rate)
            put("is_rate_calculated", tx.is_rate_calculated)
            put("equivalent_amount", tx.equivalent_amount)
        }.toString()
        val trashItem = DeletedItemEntity(id = tx.id, sourceSystem = "حبايب", originalTableName = "habayeb_transactions", jsonData = jsonData)
        saveDeletedItem(trashItem)
    }

    suspend fun populateDefaultCategoriesIfNeeded(shouldPopulate: Boolean) = withContext(Dispatchers.IO) {
        if (shouldPopulate) {
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
            defaults.forEach { saveCustomCategory(it) }
        }
    }
}
