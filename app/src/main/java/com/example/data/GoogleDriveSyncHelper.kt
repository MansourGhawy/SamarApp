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
import java.io.IOException
import java.net.URLEncoder
import com.example.data.local.AppDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


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
    object SessionExpired : CloudSyncState()
}

data class CloudBackupFile(
    val id: String,
    val name: String,
    val size: Long,
    val createdTime: String
)

class GoogleDriveSyncHelper(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveSyncHelper"
    }

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
            Log.e(TAG, "Error creating EncryptedSharedPreferences, using fallback SharedPreferences.", e)
            context.getSharedPreferences("google_drive_sync_prefs", Context.MODE_PRIVATE)
        }
    }

    private val client = OkHttpClient()

    private val _syncState = MutableStateFlow<CloudSyncState>(CloudSyncState.Idle)
    val syncState: StateFlow<CloudSyncState> = _syncState.asStateFlow()

    // Real App Credentials (Web Client ID is required for server exchange and avoids DEVELOPER_ERROR 10)
    val clientId = "26835689808-sjs5e4qpsoahq7j4s4bkb24odl82kehs.apps.googleusercontent.com"
    val clientSecret = "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"
    val scope = "https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email"

    init {
        // If we already have a refresh token, consider ourselves authenticated
        val email = getStoredEmail()
        val refreshToken = getStoredRefreshToken()
        if (!email.isNullOrEmpty() && !refreshToken.isNullOrEmpty()) {
            _syncState.value = CloudSyncState.Authenticated(email)
        }

        // Force Reset On Reinstall Mismatch: check memory and cache
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val settings = db.settingsDao().getSettingsDirect()
                        // If we are in a mismatch state (Google profile signed in but settings turned off, or fresh install)
                        if (getStoredRefreshToken().isNullOrEmpty() && (settings == null || !settings.isCloudSyncEnabled)) {
                            Log.d(TAG, "Force Reset On Reinstall Mismatch detected. Executing silent logout.")
                            getGoogleSignInClient(context).signOut()
                            sharedPrefs.edit().clear().apply()
                            _syncState.value = CloudSyncState.Idle
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to resolve reinstall mismatch in init", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uncaught error checking getLastSignedInAccount in init", e)
        }
    }

    fun isUserTrulySignedIn(context: Context): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val requiredScope = Scope("https://www.googleapis.com/auth/drive.file")
        return account != null && GoogleSignIn.hasPermissions(account, requiredScope)
    }

    private suspend fun disableCloudSyncInSettings() {
        try {
            val db = AppDatabase.getDatabase(context)
            val settings = db.settingsDao().getSettingsDirect()
            if (settings != null && settings.isCloudSyncEnabled) {
                db.settingsDao().insertOrUpdateSettings(settings.copy(isCloudSyncEnabled = false))
                Log.d(TAG, "Successfully deactivated isCloudSyncEnabled in DB due to Security/Auth failure.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed disabling cloud sync from helper", e)
        }
    }


    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/drive.appdata"),
                Scope("https://www.googleapis.com/auth/drive.file")
            )
            .requestServerAuthCode(clientId, true)
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
        try {
            val signInClient = getGoogleSignInClient(context)
            signInClient.revokeAccess()
            signInClient.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "Error during Google Sign-In silent logout/revoke", e)
        }
        sharedPrefs.edit().clear().apply()
        _syncState.value = CloudSyncState.Idle
    }

    fun logoutAsync(onComplete: () -> Unit) {
        try {
            val signInClient = getGoogleSignInClient(context)
            signInClient.revokeAccess().addOnCompleteListener {
                signInClient.signOut().addOnCompleteListener {
                    sharedPrefs.edit().clear().apply()
                    _syncState.value = CloudSyncState.Idle
                    onComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during deep revokeAccess and signOut on logoutAsync", e)
            sharedPrefs.edit().clear().apply()
            _syncState.value = CloudSyncState.Idle
            onComplete()
        }
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

            // Safe socket calling + closing streams with use block to prevent connection pool exhaustion
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawBody = response.body?.string() ?: ""
                    val json = JSONObject(rawBody)
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
                    Log.e(TAG, "Authorization code exchange failed, response is not successful.")
                    _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_link_failed))
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network or server failure during authorization code exchange", e)
            _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_network_unstable))
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

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawBody = response.body?.string() ?: ""
                    val json = JSONObject(rawBody)
                    json.optString("email", null)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed retrieving user account info safely", e)
            null
        }
    }

    /**
     * Refreshes the Access Token using the refresh token if expired
     */
    private suspend fun refreshAccessTokenIfNeeded(): String? = withContext(Dispatchers.IO) {
        val refreshToken = getStoredRefreshToken()
        if (refreshToken.isNullOrEmpty()) {
            _syncState.value = CloudSyncState.SessionExpired
            return@withContext null
        }
        
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

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawBody = response.body?.string() ?: ""
                    val json = JSONObject(rawBody)
                    val accessToken = json.getString("access_token")
                    val expiresIn = json.optLong("expires_in", 3600L)

                    storeTokens(accessToken, refreshToken, expiresIn)
                    accessToken
                } else {
                    Log.e(TAG, "AccessToken refresh failed: status code ${response.code}")
                    _syncState.value = CloudSyncState.SessionExpired
                    // Clear invalid credentials locally
                    sharedPrefs.edit()
                        .remove("access_token")
                        .remove("refresh_token")
                        .remove("token_expiry")
                        .remove("email")
                        .apply()
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing credentials or renewing access tokens", e)
            _syncState.value = CloudSyncState.SessionExpired
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
            _syncState.value = CloudSyncState.SessionExpired
            return@withContext false
        }

        // Keep local mirror always matching
        try {
            val mirrorFile = File(context.filesDir, "google_drive_mirror.mzd")
            mirrorFile.bufferedWriter().use { writer ->
                writer.write(backupJsonContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing local cache mirror file securely", e)
        }

        try {
            // Search inside appDataFolder for latest .mzd
            val searchUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&orderBy=createdTime+desc&q=" +
                    URLEncoder.encode("name contains 'Mzd_' and name contains '.mzd' and trashed = false", "UTF-8")

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            var existingFileId: String? = null
            var searchSuccess = false
            var isAuthError = false

            client.newCall(searchRequest).execute().use { searchResponse ->
                if (searchResponse.isSuccessful) {
                    val rawBody = searchResponse.body?.string() ?: ""
                    val searchResult = JSONObject(rawBody)
                    val files = searchResult.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        existingFileId = files.getJSONObject(0).getString("id")
                    }
                    searchSuccess = true
                } else {
                    if (searchResponse.code == 401 || searchResponse.code == 403) {
                        isAuthError = true
                    }
                }
            }

            if (!searchSuccess) {
                if (isAuthError) {
                    _syncState.value = CloudSyncState.SessionExpired
                    disableCloudSyncInSettings()
                    logout()
                } else {
                    _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_server_failed))
                }
                return@withContext false
            }

            val sdfName = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US)
            val dateStr = sdfName.format(java.util.Date())
            val newFileName = "Mzd_$dateStr.mzd"

            var success = false
            if (existingFileId != null) {
                // Override/Update existing file
                val updateUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
                val mediaBody = backupJsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())

                val updateRequest = Request.Builder()
                    .url(updateUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .patch(mediaBody)
                    .build()

                client.newCall(updateRequest).execute().use { updateResponse ->
                    success = updateResponse.isSuccessful
                    if (!success) {
                        Log.e(TAG, "Failed patching file on Google Drive.")
                    } else {
                        val metaUrl = "https://www.googleapis.com/drive/v3/files/$existingFileId"
                        val metaJson = JSONObject()
                        metaJson.put("name", newFileName)
                        val metaBody = metaJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                        val metaRequest = Request.Builder()
                            .url(metaUrl)
                            .header("Authorization", "Bearer $accessToken")
                            .patch(metaBody)
                            .build()
                        client.newCall(metaRequest).execute().use { /* automatically closed */ }
                    }
                }
            } else {
                // Create new file inside appDataFolder
                val createMetaUrl = "https://www.googleapis.com/drive/v3/files"
                val metaJson = JSONObject()
                metaJson.put("name", newFileName)
                metaJson.put("parents", org.json.JSONArray().put("appDataFolder"))
                metaJson.put("mimeType", "application/octet-stream")
                val metaBody = metaJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val createMetaRequest = Request.Builder()
                    .url(createMetaUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .post(metaBody)
                    .build()

                client.newCall(createMetaRequest).execute().use { createMetaResponse ->
                    if (createMetaResponse.isSuccessful) {
                        val rawBody = createMetaResponse.body?.string() ?: ""
                        val createdFile = JSONObject(rawBody)
                        val newFileId = createdFile.getString("id")

                        // Patch media contents
                        val uploadMediaUrl = "https://www.googleapis.com/upload/drive/v3/files/$newFileId?uploadType=media"
                        val fileBody = backupJsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())

                        val uploadMediaRequest = Request.Builder()
                            .url(uploadMediaUrl)
                            .header("Authorization", "Bearer $accessToken")
                            .patch(fileBody)
                            .build()

                        client.newCall(uploadMediaRequest).execute().use { uploadMediaResponse ->
                            success = uploadMediaResponse.isSuccessful
                        }
                    } else {
                        Log.e(TAG, "Failed creating upload metadata structure on Drive.")
                        success = false
                    }
                }
            }

            if (success) {
                _syncState.value = CloudSyncState.Success
                kotlinx.coroutines.delay(1200)
                _syncState.value = CloudSyncState.Authenticated(email ?: "account@google.com")
                true
            } else {
                _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_server_failed))
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uncaught networking error during background cloud synchronization", e)
            _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_server_failed))
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
            _syncState.value = CloudSyncState.SessionExpired
            return@withContext null
        }

        try {
            // Search inside appDataFolder for latest .mzd backup
            val searchUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&orderBy=createdTime+desc&q=" +
                    URLEncoder.encode("name contains 'Mzd_' and name contains '.mzd' and trashed = false", "UTF-8")

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            var existingFileId: String? = null
            var searchSuccess = false
            var isAuthError = false

            client.newCall(searchRequest).execute().use { searchResponse ->
                if (searchResponse.isSuccessful) {
                    val rawBody = searchResponse.body?.string() ?: ""
                    val searchResult = JSONObject(rawBody)
                    val files = searchResult.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        existingFileId = files.getJSONObject(0).getString("id")
                    }
                    searchSuccess = true
                } else {
                    if (searchResponse.code == 401 || searchResponse.code == 403) {
                        isAuthError = true
                    }
                }
            }

            if (!searchSuccess) {
                if (isAuthError) {
                    _syncState.value = CloudSyncState.SessionExpired
                    disableCloudSyncInSettings()
                    logout()
                } else {
                    _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_server_failed))
                }
                return@withContext null
            }

            if (existingFileId == null) {
                _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_backups_not_found))
                return@withContext null
            }

            // Download media payload
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$existingFileId?alt=media"
            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(downloadRequest).execute().use { downloadResponse ->
                if (downloadResponse.isSuccessful) {
                    val content = downloadResponse.body?.string()
                    _syncState.value = CloudSyncState.Authenticated(email ?: "account@google.com")
                    content
                } else {
                    if (downloadResponse.code == 401 || downloadResponse.code == 403) {
                        _syncState.value = CloudSyncState.SessionExpired
                        disableCloudSyncInSettings()
                        logout()
                    } else {
                        _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_server_failed))
                    }
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uncaught networking error during download processing", e)
            _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_server_failed))
            null
        }
    }

    /**
     * Lists all .mzd backups in Google Drive inside appDataFolder
     */
    suspend fun listCloudBackups(): List<CloudBackupFile> = withContext(Dispatchers.IO) {
        val accessToken = refreshAccessTokenIfNeeded() ?: return@withContext emptyList()
        try {
            val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder" +
                    "&fields=" + URLEncoder.encode("files(id,name,size,createdTime)", "UTF-8") +
                    "&q=" + URLEncoder.encode("name contains '.mzd' and trashed = false", "UTF-8")
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
                
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawBody = response.body?.string() ?: ""
                    val json = JSONObject(rawBody)
                    val filesArray = json.optJSONArray("files") ?: return@withContext emptyList()
                    val list = mutableListOf<CloudBackupFile>()
                    for (i in 0 until filesArray.length()) {
                        val obj = filesArray.getJSONObject(i)
                        val id = obj.getString("id")
                        val name = obj.getString("name")
                        val size = obj.optLong("size", 0L)
                        val createdTime = obj.optString("createdTime", "")
                        list.add(CloudBackupFile(id, name, size, createdTime))
                    }
                    list.sortedByDescending { it.name }
                } else {
                    if (response.code == 401 || response.code == 403) {
                        _syncState.value = CloudSyncState.SessionExpired
                        disableCloudSyncInSettings()
                        logout()
                    }
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing remote cloud backups", e)
            emptyList()
        }
    }

    /**
     * Uploads a .mzd backup to Google Drive inside appDataFolder with a specific filename
     */
    suspend fun uploadBackupToDriveWithFilename(filename: String, backupJsonContent: String): Boolean = withContext(Dispatchers.IO) {
        _syncState.value = CloudSyncState.Syncing
        val accessToken = refreshAccessTokenIfNeeded()
        val email = getStoredEmail()

        if (accessToken == null) {
            _syncState.value = CloudSyncState.SessionExpired
            return@withContext false
        }

        try {
            val createMetaUrl = "https://www.googleapis.com/drive/v3/files"
            val metaJson = JSONObject()
            metaJson.put("name", filename)
            metaJson.put("parents", org.json.JSONArray().put("appDataFolder"))
            metaJson.put("mimeType", "application/octet-stream")
            val metaBody = metaJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val createMetaRequest = Request.Builder()
                .url(createMetaUrl)
                .header("Authorization", "Bearer $accessToken")
                .post(metaBody)
                .build()

            var success = false
            client.newCall(createMetaRequest).execute().use { createMetaResponse ->
                if (createMetaResponse.isSuccessful) {
                    val rawBody = createMetaResponse.body?.string() ?: ""
                    val createdFile = JSONObject(rawBody)
                    val newFileId = createdFile.getString("id")

                    // Patch media contents
                    val uploadMediaUrl = "https://www.googleapis.com/upload/drive/v3/files/$newFileId?uploadType=media"
                    val fileBody = backupJsonContent.toRequestBody("application/json; charset=utf-8".toMediaType())

                    val uploadMediaRequest = Request.Builder()
                        .url(uploadMediaUrl)
                        .header("Authorization", "Bearer $accessToken")
                        .patch(fileBody)
                        .build()

                    client.newCall(uploadMediaRequest).execute().use { uploadMediaResponse ->
                        success = uploadMediaResponse.isSuccessful
                    }
                } else {
                    Log.e(TAG, "Failed creating file metadata on Drive.")
                }
            }

            if (success) {
                _syncState.value = CloudSyncState.Success
                kotlinx.coroutines.delay(1200)
                _syncState.value = CloudSyncState.Authenticated(email ?: "account@google.com")
                true
            } else {
                _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_server_failed))
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing file creation with specialized filename on Drive", e)
            _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_server_failed))
            false
        }
    }

    /**
     * Downloads/Restores a specific backup file from Google Drive inside appDataFolder by its ID
     */
    suspend fun downloadBackupFromDriveById(fileId: String): String? = withContext(Dispatchers.IO) {
        _syncState.value = CloudSyncState.Syncing
        val accessToken = refreshAccessTokenIfNeeded()
        val email = getStoredEmail()

        if (accessToken == null) {
            _syncState.value = CloudSyncState.SessionExpired
            return@withContext null
        }

        try {
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(downloadRequest).execute().use { downloadResponse ->
                if (downloadResponse.isSuccessful) {
                    val content = downloadResponse.body?.string()
                    _syncState.value = CloudSyncState.Authenticated(email ?: "account@google.com")
                    content
                } else {
                    if (downloadResponse.code == 401 || downloadResponse.code == 403) {
                        _syncState.value = CloudSyncState.SessionExpired
                        disableCloudSyncInSettings()
                        logout()
                    } else {
                        _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_server_failed))
                    }
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading custom file content by database ID", e)
            _syncState.value = CloudSyncState.Error(context.getString(com.example.R.string.gdrive_error_server_failed))
            null
        }
    }

    /**
     * Deletes a backup from Google Drive by its file ID
     */
    suspend fun deleteBackupFromDriveById(fileId: String): Boolean = withContext(Dispatchers.IO) {
        val accessToken = refreshAccessTokenIfNeeded() ?: return@withContext false
        try {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
                
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing file from remote drive folder", e)
            false
        }
    }
}
