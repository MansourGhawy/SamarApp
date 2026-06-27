package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.entities.*

@Database(
    entities = [
        AppSettings::class,
        FixedCommitment::class,
        TransactionDb::class,
        CustomCategory::class,
        DeletedItemEntity::class,
        HabayebCustomer::class,
        HabayebTransaction::class
    ],
    version = 18,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun transactionDao(): TransactionDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun deletedItemDao(): DeletedItemDao
    
    // New unified transactional DAOs
    abstract fun ledgerDao(): LedgerDao
    abstract fun habayebDao(): HabayebDao
    abstract fun trashDao(): TrashDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Safeguard: Empty path to support legacy version 1 installs transitioning to version 2
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fixed_commitments ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN isPasscodeEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN passcodeHash TEXT")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN recoveryPhraseHash TEXT")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN recoveryHint TEXT")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `makhzan_products` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `category` TEXT NOT NULL, 
                        `purchasePrice` REAL NOT NULL, 
                        `sellingPrice` REAL NOT NULL, 
                        `quantity` INTEGER NOT NULL, 
                        `imageUrl` TEXT, 
                        `lowStockThreshold` INTEGER NOT NULL DEFAULT 5
                    )
                """)
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `makhzan_products_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `category` TEXT NOT NULL, 
                        `purchasePrice` REAL NOT NULL, 
                        `sellingPrice` REAL NOT NULL, 
                        `quantity` REAL NOT NULL, 
                        `imageUrl` TEXT, 
                        `lowStockThreshold` REAL NOT NULL DEFAULT 5.0, 
                        `unitType` TEXT NOT NULL DEFAULT 'حبة'
                    )
                """)
                db.execSQL("""
                    INSERT INTO `makhzan_products_new` (id, name, category, purchasePrice, sellingPrice, quantity, imageUrl, lowStockThreshold)
                    SELECT id, name, category, purchasePrice, sellingPrice, CAST(quantity AS REAL), imageUrl, CAST(lowStockThreshold AS REAL)
                    FROM `makhzan_products`
                """)
                db.execSQL("DROP TABLE `makhzan_products`")
                db.execSQL("ALTER TABLE `makhzan_products_new` RENAME TO `makhzan_products`")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `makhzan_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `productId` INTEGER NOT NULL, 
                        `productName` TEXT NOT NULL, 
                        `type` TEXT NOT NULL, 
                        `quantityChanged` REAL NOT NULL, 
                        `pricePerUnit` REAL NOT NULL, 
                        `timestamp` INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE makhzan_transactions ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Safeguard: Empty path to support transitional version 9 installs upgrading to version 10/11
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN tempPart TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN permPart TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN unifiedDeviceId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN isFirstLaunch INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE makhzan_products ADD COLUMN hasSubUnits INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE makhzan_products ADD COLUMN parentUnitName TEXT NOT NULL DEFAULT 'كرتون'")
                db.execSQL("ALTER TABLE makhzan_products ADD COLUMN subUnitName TEXT NOT NULL DEFAULT 'حبة'")
                db.execSQL("ALTER TABLE makhzan_products ADD COLUMN subUnitCountPerParent REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `deleted_items` (
                        `id` TEXT NOT NULL, 
                        `sourceSystem` TEXT NOT NULL, 
                        `originalTableName` TEXT NOT NULL, 
                        `jsonData` TEXT NOT NULL, 
                        `deletedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """)
            }
        }

        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN isAutoBackupEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN isCloudSyncEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE makhzan_products ADD COLUMN barcode TEXT")
            }
        }

        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_makhzan_products_category` ON `makhzan_products` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_makhzan_transactions_productId` ON `makhzan_transactions` (`productId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_makhzan_transactions_timestamp` ON `makhzan_transactions` (`timestamp`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val current = INSTANCE
                if (current != null) {
                    current
                } else {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "mizan_al_dar_db"
                    )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, 
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, 
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, 
                        MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                        MIGRATION_17_18
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }
}
