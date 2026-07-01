package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "fixed_commitments")
data class FixedCommitment(
    @PrimaryKey val name: String,
    val targetAmount: Double,
    val currentProgress: Double,
    val orderIndex: Int = 0
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category"]),
        Index(value = ["type"])
    ]
)
data class TransactionDb(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val type: String,
    val category: String,
    val amount: Double,
    val description: String
)

@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val tabType: String,
    val iconEmoji: String
)
