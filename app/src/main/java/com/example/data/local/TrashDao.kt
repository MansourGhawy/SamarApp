package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.data.local.entities.*
import org.json.JSONObject

@Dao
abstract class TrashDao {

    @Query("SELECT * FROM deleted_items ORDER BY deletedAt DESC")
    abstract fun getAllDeletedItemsFlow(): Flow<List<DeletedItemEntity>>

    @Query("SELECT * FROM deleted_items ORDER BY deletedAt DESC")
    abstract suspend fun getAllDeletedItemsDirect(): List<DeletedItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertDeletedItem(item: DeletedItemEntity)

    @Delete
    abstract suspend fun deleteItem(item: DeletedItemEntity)

    @Query("DELETE FROM deleted_items WHERE id = :id")
    abstract suspend fun deleteItemById(id: String)

    @Query("DELETE FROM deleted_items")
    abstract suspend fun clearAllDeletedItems()

    // Insert methods for restoring various entities
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTransaction(tx: TransactionDb)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertHabayebTransaction(tx: HabayebTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFixedCommitment(commitment: FixedCommitment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertHabayebCustomer(customer: HabayebCustomer)

    @Transaction
    open suspend fun restoreDeletedItem(item: DeletedItemEntity) {
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
                insertTransaction(tx)
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
                insertHabayebTransaction(tx)
            }
            "fixed_commitments" -> {
                val fc = FixedCommitment(
                    name = root.getString("name"),
                    targetAmount = root.getDouble("targetAmount"),
                    currentProgress = root.getDouble("currentProgress"),
                    orderIndex = root.optInt("orderIndex", 0)
                )
                insertFixedCommitment(fc)
            }
            "habayeb_customers" -> {
                val customer = HabayebCustomer(
                    id = root.getString("id"),
                    name = root.getString("name"),
                    phone = root.getString("phone"),
                    notes = root.getString("notes"),
                    createdAt = root.getLong("createdAt")
                )
                insertHabayebCustomer(customer)
            }
            "habayeb_bundle" -> {
                val custData = root.getJSONObject("customer")
                val customer = HabayebCustomer(
                    id = custData.getString("id"),
                    name = custData.getString("name"),
                    phone = custData.getString("phone"),
                    notes = custData.getString("notes"),
                    createdAt = custData.getLong("createdAt")
                )
                insertHabayebCustomer(customer)

                val txsArray = root.getJSONArray("transactions")
                for (i in 0 until txsArray.length()) {
                    val txObj = txsArray.getJSONObject(i)
                    val tx = HabayebTransaction(
                        id = txObj.getString("id"),
                        customerId = txObj.getString("customerId"),
                        type = txObj.getString("type"),
                        amount = txObj.getDouble("amount"),
                        timestamp = txObj.getLong("timestamp"),
                        description = txObj.getString("description"),
                        linkedMainTxId = if (txObj.has("linkedMainTxId") && !txObj.isNull("linkedMainTxId")) txObj.getString("linkedMainTxId") else null
                    )
                    insertHabayebTransaction(tx)
                }
            }
            "dar_bundle" -> {
                val fcsArray = root.getJSONArray("commitments")
                for (i in 0 until fcsArray.length()) {
                    val fcObj = fcsArray.getJSONObject(i)
                    val fc = FixedCommitment(
                        name = fcObj.getString("name"),
                        targetAmount = fcObj.getDouble("targetAmount"),
                        currentProgress = fcObj.getDouble("currentProgress"),
                        orderIndex = fcObj.optInt("orderIndex", 0)
                    )
                    insertFixedCommitment(fc)
                }
                
                val txsArray = root.getJSONArray("transactions")
                for (i in 0 until txsArray.length()) {
                    val txObj = txsArray.getJSONObject(i)
                    val tx = TransactionDb(
                        id = txObj.getString("id"),
                        timestamp = txObj.getLong("timestamp"),
                        type = txObj.getString("type"),
                        category = txObj.getString("category"),
                        amount = txObj.getDouble("amount"),
                        description = txObj.getString("description")
                    )
                    insertTransaction(tx)
                }
            }
        }
        deleteItem(item)
    }
}
