package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val currencySymbol: String = "ر.ي",
    val userRole: String = "الزوجة",
    val guardianNumber: String = "+967774004399",
    val guardianRelation: String = "الزوج",
    val schoolExpensesEnabled: Boolean = true,
    val themeMode: Int = 0, // 0 = تلقائي مع النظام, 1 = نهاري دائماً, 2 = ليلي دائماً
    val doubleCheckExit: Boolean = true, // عدم إظهار هذه الرسالة مجدداً (false if checked)
    val isPasscodeEnabled: Boolean = false,
    val passcodeHash: String? = null,
    val recoveryPhraseHash: String? = null,
    val recoveryHint: String? = null,
    val tempPart: String = "",
    val permPart: String = "",
    val unifiedDeviceId: String = "",
    val isFirstLaunch: Boolean = true
)

@Entity(tableName = "fixed_commitments")
data class FixedCommitment(
    @PrimaryKey val name: String, // e.g., "الإيجار", "صيانة الدار", "قسط السيارة"
    val targetAmount: Double,
    val currentProgress: Double,
    val orderIndex: Int = 0
)

@Entity(tableName = "transactions")
data class TransactionDb(
    @PrimaryKey val id: String, // e.g., tx_yyyy_mm_dd_uuid
    val timestamp: Long, // Epoch seconds
    val type: String, // "INCOME" (الوارد) or "EXPENSE" (المنصرف)
    val category: String, // e.g., "المطبخ - دقيق"
    val amount: Double,
    val description: String
)

@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val tabType: String, // "أغذية الدار" or "فواتير الدار" or "العائلة" or "أخرى"
    val iconEmoji: String
)

@Entity(tableName = "makhzan_products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val purchasePrice: Double,
    val sellingPrice: Double,
    val quantity: Double,
    val imageUrl: String? = null,
    val lowStockThreshold: Double = 5.0,
    val unitType: String = "حبة",
    val hasSubUnits: Boolean = false,
    val parentUnitName: String = "كرتون",
    val subUnitName: String = "حبة",
    val subUnitCountPerParent: Double = 1.0
)

@Entity(tableName = "makhzan_transactions")
data class MakhzanTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val type: String, // "وارد" or "صادر"
    val quantityChanged: Double,
    val pricePerUnit: Double,
    val timestamp: Long,
    val note: String = ""
)

@Entity(tableName = "deleted_items")
data class DeletedItemEntity(
    @PrimaryKey val id: String, // Can use original ID if it's string, or generate a UUID
    val sourceSystem: String, // e.g. "دار", "حبايب", "مخزن"
    val originalTableName: String, 
    val jsonData: String, // serialized object
    val deletedAt: Long = System.currentTimeMillis()
)

