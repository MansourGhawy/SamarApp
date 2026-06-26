package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import com.example.data.local.AppDatabase
import com.example.data.local.AppSettings
import com.example.data.GoogleDriveSyncHelper
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AutoBackupWorker"

        fun scheduleDailyBackupWorker(context: Context) {
            val workManager = WorkManager.getInstance(context)
            
            // Smarter, power & data defensive constraints
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Require network connection to minimize cloud upload failures
                .setRequiresBatteryNotLow(true)                // Save device battery health
                .build()

            val dailyWorkRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "MizanDailyBackup",
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyWorkRequest
            )
        }
        
        fun cancelDailyBackupWorker(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork("MizanDailyBackup")
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        try {
            val db = AppDatabase.getDatabase(context)
            val settings = db.settingsDao().getSettingsDirect() ?: AppSettings()
            
            if (!settings.isAutoBackupEnabled) {
                Log.d(TAG, "Auto-backup feature is disabled in user settings.")
                return Result.success()
            }
            
            val commitments = db.commitmentDao().getAllCommitmentsFlow().first()
            val transactions = db.transactionDao().getAllTransactionsFlow().first()
            val deletedItems = db.deletedItemDao().getAllDeletedItemsDirect()

            val habayebCustomers = db.habayebDao().getAllCustomersDirect()
            val habayebTransactions = db.habayebDao().getAllTransactionsDirect()

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

            val deletedItemsArr = JSONArray()
            for (item in deletedItems) {
                val itemObj = JSONObject()
                itemObj.put("id", item.id)
                itemObj.put("sourceSystem", item.sourceSystem)
                itemObj.put("originalTableName", item.originalTableName)
                itemObj.put("jsonData", item.jsonData)
                itemObj.put("deletedAt", item.deletedAt)
                deletedItemsArr.put(itemObj)
            }
            root.put("deleted_items", deletedItemsArr)

            val jsonStr = root.toString(2)

            val sdfMonth = SimpleDateFormat("yyyy-MM", Locale.US)
            val monthStr = sdfMonth.format(Date())
            
            val appPrivateDir = context.getExternalFilesDir(null) ?: context.filesDir
            val mainDir = File(appPrivateDir, "Mizan_Backups")
            val targetDir = File(mainDir, monthStr)

            if (!targetDir.exists()) {
                val created = targetDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "Could not create local backup directories.")
                    sendBackupFailureNotification(context, false)
                    return Result.failure()
                }
            }

            val sdfName = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
            val dateStr = sdfName.format(Date())
            val file = File(targetDir, "Mzd_$dateStr.mzd")

            var localWrittenSuccessfully = false
            try {
                // Safeguarded Buffered writer to ensure clean closing of streams
                file.bufferedWriter().use { writer ->
                    writer.write(jsonStr)
                }
                if (file.exists() && file.length() > 1024) { // Real IO validation (must be more than 1KB)
                    localWrittenSuccessfully = true
                }
            } catch (writeEx: Exception) {
                Log.e(TAG, "Error writing data payload to local storage path", writeEx)
            }

            if (localWrittenSuccessfully) {
                // Dual Cloud Sync
                val syncHelper = GoogleDriveSyncHelper(context)
                val isLinked = !syncHelper.getStoredRefreshToken().isNullOrEmpty()
                var cloudSynced = false
                
                if (isLinked) {
                    // Upload this specific file!
                    cloudSynced = syncHelper.uploadBackupToDriveWithFilename("Mzd_$dateStr.mzd", jsonStr)
                    
                    if (!cloudSynced) {
                        Log.w(TAG, "Google Cloud synchronization failed. Retrying in subsequent WorkManager cycles.")
                        // Reschedule background work cleanly using WorkManager periodic retry policy
                        return Result.retry()
                    }
                }
                
                // Absolutely last step: send success notification!
                sendBackupNotification(context, cloudSynced)
                return Result.success()
            } else {
                Log.e(TAG, "Local file write verification failed.")
                sendBackupFailureNotification(context, false)
                return Result.retry() // Retry locally as well since write could be transient
            }

        } catch (e: Exception) {
            Log.e(TAG, "Defensive rescue: unexpected background execution error in AutoBackupWorker", e)
            sendBackupFailureNotification(context, false)
            
            // Check if failure is related to network or file system IO
            return if (e is IOException) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun sendBackupNotification(context: Context, cloudSynced: Boolean) {
        val channelId = "mizan_backup_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(com.example.R.string.autobackup_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(com.example.R.string.autobackup_channel_desc)
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
            context.getString(com.example.R.string.autobackup_notification_title_cloud)
        } else {
            context.getString(com.example.R.string.autobackup_notification_title_local)
        }

        val text = if (cloudSynced) {
            context.getString(com.example.R.string.autobackup_notification_text_cloud)
        } else {
            context.getString(com.example.R.string.autobackup_notification_text_local)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    private fun sendBackupFailureNotification(context: Context, isPermissionIssue: Boolean) {
        val channelId = "mizan_backup_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(com.example.R.string.autobackup_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(com.example.R.string.autobackup_channel_desc)
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

        val title = context.getString(com.example.R.string.autobackup_notification_title_failure)
        val text = if (isPermissionIssue) {
            context.getString(com.example.R.string.autobackup_notification_text_permission)
        } else {
            context.getString(com.example.R.string.autobackup_notification_text_failure)
        }
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }
}
