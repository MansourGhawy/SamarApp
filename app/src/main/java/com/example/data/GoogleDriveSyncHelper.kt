package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/**
 * State of the Cloud Sync flow
 */
sealed class CloudSyncState {
    object Idle : CloudSyncState()
    object Authenticating : CloudSyncState()
    data class Authenticated(val email: String) : CloudSyncState()
    object Syncing : CloudSyncState()
    object Success : CloudSyncState()
    data class Error(val message: String) : CloudSyncState()
}

class GoogleDriveSyncHelper(private val context: Context) {

    // Using EncryptedSharedPreferences for securing user's refresh token
    private val sharedPrefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "secure_google_drive_sync_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("GoogleDriveSync", "Error creating EncryptedSharedPreferences, using fallback", e)
            context.getSharedPreferences("google_drive_sync_prefs", Context.MODE_PRIVATE)
        }
    }

    private val client = OkHttpClient()

    private val _syncState = MutableStateFlow<CloudSyncState>(CloudSyncState.Idle)
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    // Real App Credentials (Web Client ID is required for server exchange and avoids DEVELOPER_ERROR 10)
    val clientId = "26835689808-sjs5e4qpsoahq7j4s4bkb24odl82kehs.apps.googleusercontent.com"
    val clientSecret = "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"
    val scope = "https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/userinfo.email"

    init {
        // If we already have a refresh token, consider ourselves authenticated
        val email = getStoredEmail()
        val refreshToken = getStoredRefreshToken()
        if (!email.isNullOrEmpty() && !refreshToken.isNullOrEmpty()) {
            _syncState.value = CloudSyncState.Authenticated(email)
        }
    }

    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
            .requestServerAuthCode("26835689808-sjs5e4qpsoahq7j4s4bkb24odl82kehs.apps.googleusercontent.com")
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getAuthUrl(): String {
        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=${URLEncoder.encode(clientId, "UTF-8")}" +
                "&redirect_uri=${URLEncoder.encode("http://localhost/oauth2callback", "UTF-8")}" +
                "&response_type=code" +
                "&scope=${URLEncoder.encode(scope, "UTF-8")}" +
                "&prompt=consent" +
                "&access_type=offline"
    }

    fun logout() {
        sharedPrefs.edit().clear().apply()
        _syncState.value = CloudSyncState.Idle
    }

    private fun storeTokens(accessToken: String, refreshToken: String?, expiresInSec: Long) {
        val editor = sharedPrefs.edit()
        editor.putString("access_token", accessToken)
        if (refreshToken != null) {
            editor.putString("refresh_token", refreshToken)
        }
        editor.putLong("token_expiry", System.currentTimeMillis() + (expiresInSec * 1000))
        editor.apply()
    }

    private fun storeEmail(email: String) {
        sharedPrefs.edit().putString("email", email).apply()
    }

    fun getStoredAccessToken(): String? = sharedPrefs.getString("access_token", null)
    fun getStoredRefreshToken(): String? = sharedPrefs.getString("refresh_token", null)
    fun getStoredEmail(): String? = sharedPrefs.getString("email", null)

    private fun isTokenExpired(): Boolean {
        val expiry = sharedPrefs.getLong("token_expiry", 0)
        // Add a 5 minutes buffer
        return System.currentTimeMillis() >= (expiry - 300_000)
    }

    /**
     * Completes OAuth exchange by exchanging authorization code for credentials
     */
    suspend fun handleAuthorizationCode(code: String, inputEmail: String? = null, redirectUri: String = ""): Boolean = withContext(Dispatchers.IO) {
        _syncState.value = CloudSyncState.Authenticating
        try {
            val requestBody = FormBody.Builder()
                .add("code", code)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", redirectUri)
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", "").takeIf { it.isNotEmpty() } ?: getStoredRefreshToken()
                val expiresIn = json.optLong("expires_in", 3600L)

                storeTokens(accessToken, refreshToken, expiresIn)

                // Fetch user email to show personalized status
                val email = inputEmail ?: fetchUserEmail(accessToken) ?: "account@google.com"
                storeEmail(email)

                _syncState.value = CloudSyncState.Authenticated(email)
                true
            } else {
                val errorMsg = response.body?.string() ?: "Unknown OAuth code exchange error"
                Log.e("GoogleDriveSync", "Exchange failed: $errorMsg")
                _syncState.value = CloudSyncState.Error("فشلت عملية ربط الحساب: $errorMsg")
                false
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveSync", "Error exchanging code", e)
            _syncState.value = CloudSyncState.Error("الشبكة غير مستقرة. يرجى التحقق من اتصالك وإعادة محاولة الربط.")
            false
        }
    }

    /**
     * Fetches user email to personalize the sync panel
     */
    private suspend fun fetchUserEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v2/userinfo")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                json.optString("email", null)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Refreshes the Access Token using the refresh token if expired
     */
    private suspend fun refreshAccessTokenIfNeeded(): String? = withContext(Dispatchers.IO) {
        val refreshToken = getStoredRefreshToken() ?: return@withContext null
        if (!isTokenExpired()) {
            val currentToken = getStoredAccessToken()
            if (!currentToken.isNullOrEmpty()) {
                return@withContext currentToken
            }
        }

        try {
            val requestBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                val accessToken = json.getString("access_token")
                val expiresIn = json.optLong("expires_in", 3600L)

                storeTokens(accessToken, refreshToken, expiresIn)
                accessToken
            } else {
                Log.e("GoogleDriveSync", "AccessToken refresh failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveSync", "Error refreshing access token", e)
            null
        }
    }

    /**
     * Uploads or updates `.mzd` backup in Google Drive inside appDataFolder
     */
    suspend fun uploadBackupToDrive(backupJsonContent: String): Boolean = withContext(Dispatchers.IO) {
        _syncState.value = CloudSyncState.Syncing
        val accessToken = refreshAccessTokenIfNeeded()
        val email = getStoredEmail()

        if (accessToken == null) {
            _syncState.value = CloudSyncState.Error("تعذر تحديث صلاحيات الوصول. يرجى إعادة تسجيل الدخول.")
            return@withContext false
        }

        // Keep local mirror always matching
        try {
            val mirrorFile = File(context.filesDir, "google_drive_mirror.mzd")
            mirrorFile.writeText(backupJsonContent)
        } catch (e: Exception) {
            Log.e("GoogleDriveSync", "Error writing local mirror file", e)
        }

        try {
            // Search inside appDataFolder for Mizan_Backup.mzd
            val searchUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=" +
                    URLEncoder.encode("name = 'Mizan_Backup.mzd' and trashed = false", "UTF-8")

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val searchResponse = client.newCall(searchRequest).execute()
            var existingFileId: String? = null

            if (searchResponse.isSuccessful) {
                val searchResult = JSONObject(searchResponse.body?.string() ?: "")
                val files = searchResult.getJSONArray("files")
                if (files.length() > 0) {
                    existingFileId = files.getJSONObject(0).getString("id")
                }
            } else {
                _syncState.value = CloudSyncState.Error("تعذر الاتصال بخوادم Google، يرجى التحقق من جودة الشبكة وإعادة المحاولة.")
                return@withContext false
            }

            val success: Boolean
            if (existingFileId != null) {
                // Override/Update existing file
                val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
                val mediaBody = backupJsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())

                val updateRequest = Request.Builder()
                    .url(updateUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .patch(mediaBody)
                    .build()

                val updateResponse = client.newCall(updateRequest).execute()
                success = updateResponse.isSuccessful
                if (!success) {
                    Log.e("GoogleDriveSync", "Failed to patch file on Drive: ${updateResponse.body?.string()}")
                }
            } else {
                // Create new file inside appDataFolder
                val createMetaUrl = "https://www.googleapis.com/drive/v3/files"
                val metaJson = JSONObject()
                metaJson.put("name", "Mizan_Backup.mzd")
                metaJson.put("parents", org.json.JSONArray().put("appDataFolder"))
                metaJson.put("mimeType", "application/octet-stream")
                val metaBody = metaJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val createMetaRequest = Request.Builder()
                    .url(createMetaUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .post(metaBody)
                    .build()

                val createMetaResponse = client.newCall(createMetaRequest).execute()
                if (createMetaResponse.isSuccessful) {
                    val createdFile = JSONObject(createMetaResponse.body?.string() ?: "")
                    val newFileId = createdFile.getString("id")

                    // Patch media contents
                    val uploadMediaUrl = "https://www.googleapis.com/upload/drive/v3/files/$newFileId?uploadType=media"
                    val fileBody = backupJsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())

                    val uploadMediaRequest = Request.Builder()
                        .url(uploadMediaUrl)
                        .header("Authorization", "Bearer $accessToken")
                        .patch(fileBody)
                        .build()

                    val uploadMediaResponse = client.newCall(uploadMediaRequest).execute()
                    success = uploadMediaResponse.isSuccessful
                } else {
                    Log.e("GoogleDriveSync", "Failed to create metadata: ${createMetaResponse.body?.string()}")
                    success = false
                }
            }

            if (success) {
                _syncState.value = CloudSyncState.Authenticated(email ?: "account@google.com")
                true
            } else {
                _syncState.value = CloudSyncState.Error("تعذر الاتصال بخوادم Google، يرجى التحقق من جودة الشبكة وإعادة المحاولة.")
                false
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveSync", "Error during cloud sync", e)
            _syncState.value = CloudSyncState.Error("تعذر الاتصال بخوادم Google، يرجى التحقق من جودة الشبكة وإعادة المحاولة.")
            false
        }
    }

    /**
     * Downloads/Restores the latest backup file from Google Drive inside appDataFolder
     */
    suspend fun downloadBackupFromDrive(): String? = withContext(Dispatchers.IO) {
        _syncState.value = CloudSyncState.Syncing
        val accessToken = refreshAccessTokenIfNeeded()
        val email = getStoredEmail()

        if (accessToken == null) {
            _syncState.value = CloudSyncState.Error("تعذر تحديث صلاحيات الوصول. يرجى إعادة تسجيل الدخول.")
            return@withContext null
        }

        try {
            // Search inside appDataFolder for Mizan_Backup.mzd
            val searchUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=" +
                    URLEncoder.encode("name = 'Mizan_Backup.mzd' and trashed = false", "UTF-8")

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val searchResponse = client.newCall(searchRequest).execute()
            var existingFileId: String? = null

            if (searchResponse.isSuccessful) {
                val searchResult = JSONObject(searchResponse.body?.string() ?: "")
                val files = searchResult.getJSONArray("files")
                if (files.length() > 0) {
                    existingFileId = files.getJSONObject(0).getString("id")
                }
            } else {
                _syncState.value = CloudSyncState.Error("تعذر الاتصال بخوادم Google، يرجى التحقق من جودة الشبكة وإعادة المحاولة.")
                return@withContext null
            }

            if (existingFileId == null) {
                _syncState.value = CloudSyncState.Error("لم يتم العثور على أي ملف نسخ احتياطي باسم Mizan_Backup.mzd على حسابك.")
                return@withContext null
            }

            // Download media payload
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$existingFileId?alt=media"
            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val downloadResponse = client.newCall(downloadRequest).execute()
            if (downloadResponse.isSuccessful) {
                val content = downloadResponse.body?.string()
                _syncState.value = CloudSyncState.Authenticated(email ?: "account@google.com")
                content
            } else {
                _syncState.value = CloudSyncState.Error("تعذر الاتصال بخوادم Google، يرجى التحقق من جودة الشبكة وإعادة المحاولة.")
                null
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveSync", "Error downloading from Drive", e)
            _syncState.value = CloudSyncState.Error("تعذر الاتصال بخوادم Google، يرجى التحقق من جودة الشبكة وإعادة المحاولة.")
            null
        }
    }
}
