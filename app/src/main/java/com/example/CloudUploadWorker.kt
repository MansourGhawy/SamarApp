package com.example

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import com.example.data.GoogleDriveSyncHelper
import java.io.File

class CloudUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CloudUploadWorker"
        const val KEY_FILE_PATH = "backup_file_path"
        const val KEY_FILE_NAME = "backup_file_name"

        fun enqueueUpload(context: Context, filePath: String, fileName: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = Data.Builder()
                .putString(KEY_FILE_PATH, filePath)
                .putString(KEY_FILE_NAME, fileName)
                .build()

            val uploadWorkRequest = OneTimeWorkRequestBuilder<CloudUploadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(uploadWorkRequest)
            Log.d(TAG, "Enqueued cloud upload for $fileName to trigger when internet is connected")
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()

        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "Backup file not found at path: $filePath")
                return Result.failure()
            }

            val syncHelper = GoogleDriveSyncHelper(context)
            val isLinked = !syncHelper.getStoredRefreshToken().isNullOrEmpty()
            if (!isLinked) {
                Log.d(TAG, "Google Drive not linked. Skipping cloud upload.")
                return Result.success()
            }

            val jsonStr = file.readText()
            val success = syncHelper.uploadBackupToDriveWithFilename(fileName, jsonStr)
            if (success) {
                Log.d(TAG, "Successfully uploaded backup $fileName to Google Drive in background")
                return Result.success()
            } else {
                Log.e(TAG, "Cloud upload failed, retrying...")
                return Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during cloud upload", e)
            return Result.retry()
        }
    }
}
