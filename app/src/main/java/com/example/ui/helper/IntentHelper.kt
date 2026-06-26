package com.example.ui.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

// Helper function to share the backup file using FileProvider
fun shareBackupFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "حفظ أو مشاركة ملف النسخة 💾"))
    } catch (e: Exception) {
        Toast.makeText(context, "فشل مشاركة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// Helper to launch or download Google Drive app from store
fun openGoogleDriveApp(context: Context) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.docs")
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.docs"))
            context.startActivity(playIntent)
        }
    } catch (e: Exception) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.docs"))
            context.startActivity(webIntent)
        } catch (ex: Exception) {
            Toast.makeText(context, "تعذر فتح متجر التطبيقات.", Toast.LENGTH_SHORT).show()
        }
    }
}
