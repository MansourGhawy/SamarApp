package com.example.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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
}
