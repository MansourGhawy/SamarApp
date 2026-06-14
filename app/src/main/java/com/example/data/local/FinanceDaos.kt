package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: AppSettings)
}

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM fixed_commitments ORDER BY orderIndex ASC")
    fun getAllCommitmentsFlow(): Flow<List<FixedCommitment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommitment(commitment: FixedCommitment)

    @Update
    suspend fun updateCommitments(commitments: List<FixedCommitment>)

    @Query("DELETE FROM fixed_commitments WHERE name = :name")
    suspend fun deleteCommitment(name: String)

    @Query("DELETE FROM fixed_commitments")
    suspend fun clearAllCommitments()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionDb>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionDb)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionDb)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()
}

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories")
    fun getAllCustomCategoriesFlow(): Flow<List<CustomCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CustomCategory)

    @Delete
    suspend fun deleteCategory(category: CustomCategory)

    @Query("DELETE FROM custom_categories")
    suspend fun clearAllCustomCategories()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM makhzan_products ORDER BY id DESC")
    fun getAllProductsFlow(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM makhzan_products WHERE id = :id")
    suspend fun deleteProductById(id: Long)

    @Query("DELETE FROM makhzan_products")
    suspend fun clearAllProducts()
}

@Dao
interface MakhzanTransactionDao {
    @Query("SELECT * FROM makhzan_transactions ORDER BY timestamp DESC")
    fun getAllMakhzanTransactionsFlow(): Flow<List<MakhzanTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: MakhzanTransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: MakhzanTransactionEntity)

    @Query("DELETE FROM makhzan_transactions")
    suspend fun clearAllTransactions()
}

@Dao
interface DeletedItemDao {
    @Query("SELECT * FROM deleted_items ORDER BY deletedAt DESC")
    fun getAllDeletedItemsFlow(): Flow<List<DeletedItemEntity>>

    @Query("SELECT * FROM deleted_items ORDER BY deletedAt DESC")
    suspend fun getAllDeletedItemsDirect(): List<DeletedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedItem(item: DeletedItemEntity)

    @Delete
    suspend fun deleteItem(item: DeletedItemEntity)

    @Query("DELETE FROM deleted_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM deleted_items")
    suspend fun clearAllDeletedItems()
}

