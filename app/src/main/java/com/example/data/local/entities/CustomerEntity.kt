package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "habayeb_customers")
data class HabayebCustomer(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val notes: String,
    val createdAt: Long
)

@Entity(
    tableName = "habayeb_transactions",
    foreignKeys = [
        ForeignKey(
            entity = HabayebCustomer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["timestamp"]),
        Index(value = ["linkedMainTxId"])
    ]
)
data class HabayebTransaction(
    @PrimaryKey val id: String,
    val customerId: String,
    val type: String,
    val amount: Double,
    val timestamp: Long,
    val description: String,
    val linkedMainTxId: String? = null,
    val is_foreign: Boolean = false,
    val currency_code: String = "DEFAULT",
    val foreign_amount: Double = 0.0,
    val exchange_rate: Double = 1.0,
    val is_rate_calculated: Boolean = false,
    val equivalent_amount: Double = 0.0
)
