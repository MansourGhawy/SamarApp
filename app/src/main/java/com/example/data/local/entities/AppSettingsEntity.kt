package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

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
