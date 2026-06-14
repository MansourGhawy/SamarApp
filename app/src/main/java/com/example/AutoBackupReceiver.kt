package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.local.AppDatabase
import com.example.data.local.AppSettings
import com.example.data.local.FixedCommitment
import com.example.data.local.TransactionDb
import com.example.data.local.ProductEntity
import com.example.data.local.MakhzanTransactionEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.HabayebDatabase
import com.example.data.local.HabayebCustomer
import com.example.data.local.HabayebTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

class AutoBackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scheduleDailyBackupAlarm(context)
            return
        }

        // Trigger automatic backup
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val settings = db.settingsDao().getSettingsDirect() ?: AppSettings()
                val commitments = db.commitmentDao().getAllCommitmentsFlow().first()
                val transactions = db.transactionDao().getAllTransactionsFlow().first()
                val products = db.productDao().getAllProductsFlow().first()
                val makhzanTransactions = db.makhzanTransactionDao().getAllMakhzanTransactionsFlow().first()
                val auditLogs = db.auditLogDao().getAllAuditLogsFlow().first()

                val habayebDb = HabayebDatabase.getDatabase(context)
                val habayebCustomers = habayebDb.habayebDao().getAllCustomersDirect()
                val habayebTransactions = habayebDb.habayebDao().getAllTransactionsDirect()

                // Generate full comprehensive JSON payload
                val root = JSONObject()

                val metadata = JSONObject()
                metadata.put("app_name", "Mizan Al-Dar")
                metadata.put("app_version", "1.1.0")
                metadata.put("backup_timestamp", System.currentTimeMillis() / 1000)
                metadata.put("security_hash", "security_" + (settings.hashCode() + transactions.size * 31).toString())
                root.put("metadata", metadata)

                val settingsObj = JSONObject()
                settingsObj.put("currency_symbol", settings.currencySymbol)
                settingsObj.put("user_role", settings.userRole)
                settingsObj.put("guardian_number", settings.guardianNumber)
                settingsObj.put("guardian_relation", settings.guardianRelation)
                settingsObj.put("school_expenses_enabled", settings.schoolExpensesEnabled)
                settingsObj.put("theme_mode", settings.themeMode)
                root.put("settings", settingsObj)

                val commitmentsArr = JSONArray()
                for (fc in commitments) {
                    val fcObj = JSONObject()
                    fcObj.put("name", fc.name)
                    fcObj.put("target_amount", fc.targetAmount)
                    fcObj.put("current_progress", fc.currentProgress)
                    fcObj.put("order_index", fc.orderIndex)
                    commitmentsArr.put(fcObj)
                }
                root.put("fixed_commitments", commitmentsArr)

                val transactionsArr = JSONArray()
                for (tx in transactions) {
                    val txObj = JSONObject()
                    txObj.put("id", tx.id)
                    txObj.put("timestamp", tx.timestamp)
                    txObj.put("type", tx.type)
                    txObj.put("category", tx.category)
                    txObj.put("amount", tx.amount)
                    txObj.put("description", tx.description)
                    transactionsArr.put(txObj)
                }
                root.put("transactions", transactionsArr)

                val habayebObj = JSONObject()
                val habayebCustomersArr = JSONArray()
                for (c in habayebCustomers) {
                    val cObj = JSONObject()
                    cObj.put("id", c.id)
                    cObj.put("name", c.name)
                    cObj.put("phone", c.phone)
                    cObj.put("notes", c.notes)
                    cObj.put("created_at", c.createdAt)
                    habayebCustomersArr.put(cObj)
                }
                habayebObj.put("customers", habayebCustomersArr)

                val habayebTxsArr = JSONArray()
                for (t in habayebTransactions) {
                    val tObj = JSONObject()
                    tObj.put("id", t.id)
                    tObj.put("customer_id", t.customerId)
                    tObj.put("type", t.type)
                    tObj.put("amount", t.amount)
                    tObj.put("timestamp", t.timestamp)
                    tObj.put("description", t.description)
                    tObj.put("linked_main_tx_id", t.linkedMainTxId)
                    habayebTxsArr.put(tObj)
                }
                habayebObj.put("debt_transactions", habayebTxsArr)
                root.put("habayeb_debts", habayebObj)

                // Makhzan Array
                val makhzanObj = JSONObject()
                val productsArr = JSONArray()
                for (p in products) {
                    val pObj = JSONObject()
                    pObj.put("id", p.id)
                    pObj.put("name", p.name)
                    pObj.put("category", p.category)
                    pObj.put("purchase_price", p.purchasePrice)
                    pObj.put("selling_price", p.sellingPrice)
                    pObj.put("quantity", p.quantity)
                    pObj.put("image_url", p.imageUrl ?: "")
                    pObj.put("low_stock_threshold", p.lowStockThreshold)
                    pObj.put("unit_type", p.unitType)
                    pObj.put("has_sub_units", p.hasSubUnits)
                    pObj.put("parent_unit_name", p.parentUnitName)
                    pObj.put("sub_unit_name", p.subUnitName)
                    pObj.put("sub_unit_count_per_parent", p.subUnitCountPerParent)
                    productsArr.put(pObj)
                }
                makhzanObj.put("products", productsArr)

                val makhzanTxsArr = JSONArray()
                for (tx in makhzanTransactions) {
                    val txObj = JSONObject()
                    txObj.put("id", tx.id)
                    txObj.put("product_id", tx.productId)
                    txObj.put("product_name", tx.productName)
                    txObj.put("type", tx.type)
                    txObj.put("quantity_changed", tx.quantityChanged)
                    txObj.put("price_per_unit", tx.pricePerUnit)
                    txObj.put("timestamp", tx.timestamp)
                    txObj.put("note", tx.note)
                    makhzanTxsArr.put(txObj)
                }
                makhzanObj.put("transactions", makhzanTxsArr)
                root.put("makhzan_inventory", makhzanObj)

                // Audit Logs Array
                val auditLogsArr = JSONArray()
                for (al in auditLogs) {
                    val alObj = JSONObject()
                    alObj.put("source_system", al.sourceSystem)
                    alObj.put("action_type", al.actionType)
                    alObj.put("description", al.description)
                    alObj.put("old_value", al.oldValue ?: "")
                    alObj.put("new_value", al.newValue ?: "")
                    alObj.put("timestamp", al.timestamp)
                    auditLogsArr.put(alObj)
                }
                root.put("audit_logs", auditLogsArr)

                val jsonStr = root.toString(2)

                // Save to MizanAlDar/Backups directory
                val rootDir = context.getExternalFilesDir(null)
                val backupDir = File(rootDir, "MizanAlDar/Backups")
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }
                val timestamp = System.currentTimeMillis() / 1000
                val file = File(backupDir, "mzd_backup_auto_${timestamp}.mzd")
                file.writeText(jsonStr)

                // Handle Google Drive Sync if connected
                val syncHelper = com.example.data.GoogleDriveSyncHelper(context)
                val isLinked = !syncHelper.getStoredRefreshToken().isNullOrEmpty()
                var cloudSynced = false
                if (isLinked) {
                    cloudSynced = syncHelper.uploadBackupToDrive(jsonStr)
                }

                // Trigger a success notification to remind user
                sendBackupNotification(context, cloudSynced)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendBackupNotification(context: Context, cloudSynced: Boolean) {
        val channelId = "mizan_backup_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "النسخ الاحتياطي التلقائي",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات تذكير وعمليات النسخ الاحتياطي"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (cloudSynced) {
            "ميزان الدار - تم النسخ الاحتياطي السحابي التلقائي بنجاح 🏠☁️"
        } else {
            "ميزان الدار - تم حفظ النسخة الاحتياطية محلياً 🏠📁"
        }

        val text = if (cloudSynced) {
            "تم نسخ ومزامنة جميع بيانات الحساب والسجل في Google Drive تلقائيًا بنجاح."
        } else {
            "تم حفظ نسخة احتياطية لبياناتك تلقائياً بنجاح نهاية اليوم تماماً. يُنصح بحفظ النسخة في حسابك لتأمينها."
        }

        val bigText = if (cloudSynced) {
            "تم تفعيل النسخ الاحتياطي السحابي التلقائي اليومي.\n\n" +
            "تم بنجاح رفع كامل سجلات القيود والأرباح، الصافي، مستحقات حبايب، وتغييرات المخزن على حسابك الآمن في Google Drive لحماية بياناتك من الفقدان والضياع ☁️."
        } else {
            "تم حفظ نسخة احتياطية لبياناتك تلقائياً بنجاح نهاية اليوم تماماً.\n\n" +
            "يُنصح دائماً بمزامنة وحفظ النسخة في حسابك على Google Drive الخاص بك عبر الإعدادات للحصول على ميزة المزامنة السحابية التلقائية وحماية بياناتك من الضياع."
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    companion object {
        fun scheduleDailyBackupAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, AutoBackupReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    2001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // At the end of the day تماماً (say 11:59 PM or 23:59:00)
                val calendar = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // If time has already passed for today, schedule for tomorrow
                if (calendar.timeInMillis < System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }

                // Re-schedule daily
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
