package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

object DatabaseDefaults {
    const val DEFAULT_CURRENCY_SYMBOL = "ر.ي"
}

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val currencySymbol: String = DatabaseDefaults.DEFAULT_CURRENCY_SYMBOL,
    val schoolExpensesEnabled: Boolean = true,
    val themeMode: Int = 0, // 0 = Auto, 1 = Light, 2 = Dark
    val doubleCheckExit: Boolean = true,
    val isPasscodeEnabled: Boolean = false,
    val passcodeHash: String? = null,
    val recoveryPhraseHash: String? = null,
    val recoveryHint: String? = null,
    val tempPart: String = "",
    val permPart: String = "",
    val unifiedDeviceId: String = "",
    val isFirstLaunch: Boolean = true,
    val isAutoBackupEnabled: Boolean = false,
    val isCloudSyncEnabled: Boolean = false
)

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
        Index(value = ["category"])
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

@Entity(tableName = "deleted_items")
data class DeletedItemEntity(
    @PrimaryKey val id: String,
    val sourceSystem: String,
    val originalTableName: String,
    val jsonData: String,
    val deletedAt: Long = System.currentTimeMillis()
)

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
    val linkedMainTxId: String? = null
)
