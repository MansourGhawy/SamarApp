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

    // Flow Exposures
    val settingsFlow: Flow<AppSettings?> = settingsDao.getSettingsFlow()
    val commitmentsFlow: Flow<List<FixedCommitment>> = commitmentDao.getAllCommitmentsFlow()
    val transactionsFlow: Flow<List<TransactionDb>> = transactionDao.getAllTransactionsFlow()
    val customCategoriesFlow: Flow<List<CustomCategory>> = customCategoryDao.getAllCustomCategoriesFlow()
    val deletedItemsFlow: Flow<List<DeletedItemEntity>> = deletedItemDao.getAllDeletedItemsFlow()

    // Deleted Items Trash
    suspend fun saveDeletedItem(item: DeletedItemEntity): Unit = withContext(Dispatchers.IO) {
        try {
            deletedItemDao.insertDeletedItem(item)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside saveDeletedItem: ${e.message}", e)
            throw e
        }
    }

    suspend fun removeDeletedItem(item: DeletedItemEntity): Unit = withContext(Dispatchers.IO) {
        try {
            deletedItemDao.deleteItem(item)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside removeDeletedItem: ${e.message}", e)
            throw e
        }
    }

    suspend fun removeDeletedItemById(id: String): Unit = withContext(Dispatchers.IO) {
        try {
            deletedItemDao.deleteItemById(id)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside removeDeletedItemById: ${e.message}", e)
            throw e
        }
    }

    suspend fun clearDeletedItems(): Unit = withContext(Dispatchers.IO) {
        try {
            deletedItemDao.clearAllDeletedItems()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside clearDeletedItems: ${e.message}", e)
            throw e
        }
    }


    // Settings
    suspend fun getSettingsDirect(): AppSettings? = withContext(Dispatchers.IO) {
        try {
            settingsDao.getSettingsDirect()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside getSettingsDirect: ${e.message}", e)
            null
        }
    }

    suspend fun saveSettings(settings: AppSettings): Unit = withContext(Dispatchers.IO) {
        try {
            settingsDao.insertOrUpdateSettings(settings)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside saveSettings: ${e.message}", e)
            throw e
        }
    }

    // Commitments
    suspend fun saveCommitment(commitment: FixedCommitment): Unit = withContext(Dispatchers.IO) {
        try {
            commitmentDao.insertCommitment(commitment)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside saveCommitment: ${e.message}", e)
            throw e
        }
    }

    suspend fun updateCommitments(commitments: List<FixedCommitment>): Unit = withContext(Dispatchers.IO) {
        try {
            commitmentDao.updateCommitments(commitments)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside updateCommitments: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteCommitment(name: String): Unit = withContext(Dispatchers.IO) {
        try {
            commitmentDao.deleteCommitment(name)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside deleteCommitment: ${e.message}", e)
            throw e
        }
    }

    suspend fun clearCommitments(): Unit = withContext(Dispatchers.IO) {
        try {
            commitmentDao.clearAllCommitments()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside clearCommitments: ${e.message}", e)
            throw e
        }
    }

    // Transactions
    suspend fun saveTransaction(transaction: TransactionDb): Unit = withContext(Dispatchers.IO) {
        try {
            transactionDao.insertTransaction(transaction)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside saveTransaction: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteTransaction(transaction: TransactionDb): Unit = withContext(Dispatchers.IO) {
        try {
            transactionDao.deleteTransaction(transaction)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside deleteTransaction: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteTransactionById(id: String): Unit = withContext(Dispatchers.IO) {
        try {
            transactionDao.deleteTransactionById(id)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside deleteTransactionById: ${e.message}", e)
            throw e
        }
    }

    suspend fun clearTransactions(): Unit = withContext(Dispatchers.IO) {
        try {
            transactionDao.clearAllTransactions()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside clearTransactions: ${e.message}", e)
            throw e
        }
    }

    // Custom Categories
    suspend fun saveCustomCategory(category: CustomCategory): Unit = withContext(Dispatchers.IO) {
        try {
            customCategoryDao.insertCategory(category)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside saveCustomCategory: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteCustomCategory(category: CustomCategory): Unit = withContext(Dispatchers.IO) {
        try {
            customCategoryDao.deleteCategory(category)
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside deleteCustomCategory: ${e.message}", e)
            throw e
        }
    }

    suspend fun clearCustomCategories(): Unit = withContext(Dispatchers.IO) {
        try {
            customCategoryDao.clearAllCustomCategories()
        } catch (e: Exception) {
            Log.e("FinanceRepository", "Error inside clearCustomCategories: ${e.message}", e)
            throw e
        }
    }

    // Clean critical master reset
    suspend fun deleteAllData(): Unit = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                transactionDao.clearAllTransactions()
                commitmentDao.clearAllCommitments()
                customCategoryDao.clearAllCustomCategories()
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
        }.toString()
        val trashItem = DeletedItemEntity(id = tx.id, sourceSystem = "حبايب", originalTableName = "habayeb_transactions", jsonData = jsonData)
        saveDeletedItem(trashItem)
    }

    suspend fun populateDefaultCategoriesIfNeeded(sharedPrefs: android.content.SharedPreferences) = withContext(Dispatchers.IO) {
        if (!sharedPrefs.getBoolean("categories_populated", false)) {
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
            sharedPrefs.edit().putBoolean("categories_populated", true).apply()
        }
    }
}
