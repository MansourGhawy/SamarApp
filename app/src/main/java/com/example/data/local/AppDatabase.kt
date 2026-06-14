package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppSettings::class,
        FixedCommitment::class,
        TransactionDb::class,
        CustomCategory::class,
        ProductEntity::class,
        MakhzanTransactionEntity::class,
        AuditLogEntity::class,
        DeletedItemEntity::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun transactionDao(): TransactionDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun productDao(): ProductDao
    abstract fun makhzanTransactionDao(): MakhzanTransactionDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun deletedItemDao(): DeletedItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mizan_al_dar_db"
                )
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, 
                    MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_10_11, 
                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
                )
                .fallbackToDestructiveMigration() // Simple approach for prototyping
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
