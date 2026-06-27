package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_items")
data class DeletedItemEntity(
    @PrimaryKey val id: String,
    val sourceSystem: String,
    val originalTableName: String,
    val jsonData: String,
    val deletedAt: Long = System.currentTimeMillis()
)
