package com.example.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "habayeb_customers")
data class HabayebCustomer(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val notes: String,
    val createdAt: Long
)

@Entity(tableName = "habayeb_transactions")
data class HabayebTransaction(
    @PrimaryKey val id: String,
    val customerId: String,
    val type: String, // "OWED_BY_THEM", "PAYMENT_BY_THEM", "OWED_TO_THEM", "PAYMENT_TO_THEM"
    val amount: Double,
    val timestamp: Long,
    val description: String,
    val linkedMainTxId: String? = null
)

@Dao
interface HabayebDao {
    @Query("SELECT * FROM habayeb_customers ORDER BY createdAt DESC")
    fun getAllCustomersFlow(): Flow<List<HabayebCustomer>>

    @Query("SELECT * FROM habayeb_customers")
    suspend fun getAllCustomersDirect(): List<HabayebCustomer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: HabayebCustomer)

    @Query("UPDATE habayeb_customers SET name = :newName WHERE id = :id")
    suspend fun updateCustomerName(id: String, newName: String)

    @Delete
    suspend fun deleteCustomer(customer: HabayebCustomer)

    @Query("DELETE FROM habayeb_customers WHERE id = :id")
    suspend fun deleteCustomerById(id: String)

    @Query("SELECT * FROM habayeb_transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<HabayebTransaction>>

    @Query("SELECT * FROM habayeb_transactions WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getTransactionsForCustomerFlow(customerId: String): Flow<List<HabayebTransaction>>

    @Query("SELECT * FROM habayeb_transactions")
    suspend fun getAllTransactionsDirect(): List<HabayebTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: HabayebTransaction)

    @Delete
    suspend fun deleteTransaction(transaction: HabayebTransaction)

    @Query("DELETE FROM habayeb_transactions WHERE customerId = :customerId")
    suspend fun deleteTransactionsByCustomer(customerId: String)

    @Query("DELETE FROM habayeb_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("SELECT * FROM habayeb_transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): HabayebTransaction?

    @Query("DELETE FROM habayeb_customers")
    suspend fun clearAllCustomers()

    @Query("DELETE FROM habayeb_transactions")
    suspend fun clearAllTransactions()
}

@Database(entities = [HabayebCustomer::class, HabayebTransaction::class], version = 1, exportSchema = false)
abstract class HabayebDatabase : RoomDatabase() {
    abstract fun habayebDao(): HabayebDao

    companion object {
        @Volatile
        private var INSTANCE: HabayebDatabase? = null

        fun getDatabase(context: Context): HabayebDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HabayebDatabase::class.java,
                    "habayeb_debts_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
