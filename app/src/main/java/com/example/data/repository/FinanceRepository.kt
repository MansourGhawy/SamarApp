package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow

class FinanceRepository(private val database: AppDatabase) {

    private val settingsDao = database.settingsDao()
    private val commitmentDao = database.commitmentDao()
    private val transactionDao = database.transactionDao()
    private val customCategoryDao = database.customCategoryDao()
    private val productDao = database.productDao()
    private val makhzanTransactionDao = database.makhzanTransactionDao()
    private val auditLogDao = database.auditLogDao()

    // Flow Exposures
    val settingsFlow: Flow<AppSettings?> = settingsDao.getSettingsFlow()
    val commitmentsFlow: Flow<List<FixedCommitment>> = commitmentDao.getAllCommitmentsFlow()
    val transactionsFlow: Flow<List<TransactionDb>> = transactionDao.getAllTransactionsFlow()
    val customCategoriesFlow: Flow<List<CustomCategory>> = customCategoryDao.getAllCustomCategoriesFlow()
    val productsFlow: Flow<List<ProductEntity>> = productDao.getAllProductsFlow()
    val makhzanTransactionsFlow: Flow<List<MakhzanTransactionEntity>> = makhzanTransactionDao.getAllMakhzanTransactionsFlow()
    val auditLogsFlow: Flow<List<AuditLogEntity>> = auditLogDao.getAllAuditLogsFlow()

    // Audit Log Saving (No deletion allowed!)
    suspend fun saveAuditLog(log: AuditLogEntity) {
        auditLogDao.insertAuditLog(log)
    }


    // Settings
    suspend fun getSettingsDirect(): AppSettings? = settingsDao.getSettingsDirect()
    suspend fun saveSettings(settings: AppSettings) {
        settingsDao.insertOrUpdateSettings(settings)
    }

    // Commitments
    suspend fun saveCommitment(commitment: FixedCommitment) {
        commitmentDao.insertCommitment(commitment)
    }
    suspend fun updateCommitments(commitments: List<FixedCommitment>) {
        commitmentDao.updateCommitments(commitments)
    }
    suspend fun deleteCommitment(name: String) {
        commitmentDao.deleteCommitment(name)
    }
    suspend fun clearCommitments() {
        commitmentDao.clearAllCommitments()
    }

    // Transactions
    suspend fun saveTransaction(transaction: TransactionDb) {
        transactionDao.insertTransaction(transaction)
    }
    suspend fun deleteTransaction(transaction: TransactionDb) {
        transactionDao.deleteTransaction(transaction)
    }
    suspend fun deleteTransactionById(id: String) {
        transactionDao.deleteTransactionById(id)
    }
    suspend fun clearTransactions() {
        transactionDao.clearAllTransactions()
    }

    // Custom Categories
    suspend fun saveCustomCategory(category: CustomCategory) {
        customCategoryDao.insertCategory(category)
    }
    suspend fun deleteCustomCategory(category: CustomCategory) {
        customCategoryDao.deleteCategory(category)
    }
    suspend fun clearCustomCategories() {
        customCategoryDao.clearAllCustomCategories()
    }

    // Makhzan (Inventory) Products
    suspend fun saveProduct(product: ProductEntity): Long {
        return productDao.insertProduct(product)
    }
    suspend fun updateProduct(product: ProductEntity) {
        productDao.updateProduct(product)
    }
    suspend fun deleteProduct(product: ProductEntity) {
        productDao.deleteProduct(product)
    }
    suspend fun deleteProductById(id: Long) {
        productDao.deleteProductById(id)
    }
    suspend fun clearProducts() {
        productDao.clearAllProducts()
    }

    // Makhzan Transactions
    suspend fun saveMakhzanTransaction(transaction: MakhzanTransactionEntity) {
        makhzanTransactionDao.insertTransaction(transaction)
    }
    suspend fun deleteMakhzanTransaction(transaction: MakhzanTransactionEntity) {
        makhzanTransactionDao.deleteTransaction(transaction)
    }
    suspend fun clearMakhzanTransactions() {
        makhzanTransactionDao.clearAllTransactions()
    }

    // Clean critical master reset
    suspend fun deleteAllData() {
        transactionDao.clearAllTransactions()
        commitmentDao.clearAllCommitments()
        customCategoryDao.clearAllCustomCategories()
        productDao.clearAllProducts()
        makhzanTransactionDao.clearAllTransactions()
        // Reset settings
        settingsDao.insertOrUpdateSettings(AppSettings())
    }
}
