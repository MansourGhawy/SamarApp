package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.GoogleDriveSyncHelper
import com.example.data.CloudSyncState
import com.example.data.CloudBackupFile
import com.example.data.local.*
import com.example.data.repository.FinanceRepository
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.text.DecimalFormat
import java.security.MessageDigest
import java.util.UUID

/**
 * SyncSettingsViewModel isolates and protects all Security, Backups,
 * Cloud Synchronization, Licensing, and Trash Recovery logic.
 */
class SyncSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val repository: FinanceRepository = FinanceRepository(database)
    private val habayebDao: HabayebDao = database.habayebDao()
    private val trashDao: TrashDao = database.trashDao()
    private val deletedItemDao: DeletedItemDao = database.deletedItemDao()

    val googleDriveSyncHelper = GoogleDriveSyncHelper(application)
    val googleDriveSyncState: StateFlow<CloudSyncState> = googleDriveSyncHelper.syncState

    private val _cloudBackupsList = MutableStateFlow<List<CloudBackupFile>>(emptyList())
    val cloudBackupsList: StateFlow<List<CloudBackupFile>> = _cloudBackupsList.asStateFlow()

    private val _isFetchingCloudBackups = MutableStateFlow(false)
    val isFetchingCloudBackups: StateFlow<Boolean> = _isFetchingCloudBackups.asStateFlow()

    private val _localBackups = MutableStateFlow<List<File>>(emptyList())
    val localBackups: StateFlow<List<File>> = _localBackups.asStateFlow()

    // --- Dynamic Configurations State ---
    val settingsState: StateFlow<AppSettings> = repository.settingsFlow
        .map { it ?: AppSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val deletedItemsFlow: StateFlow<List<DeletedItemEntity>> = repository.deletedItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Trial & License Configurations State ---
    private val _activationTrigger = MutableStateFlow(0)
    val showActivationRequired = MutableStateFlow(false)

    val deviceIdState: StateFlow<String> = flow {
        emit(getOrGenerateUnifiedDeviceId(getApplication()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), getOrGenerateUnifiedDeviceId(getApplication()))

    val isActivatedState: StateFlow<Boolean> = combine(deviceIdState, _activationTrigger) { deviceId, _ ->
        val prefs = getApplication<Application>().getSharedPreferences("mizan_sec_prefs", Context.MODE_PRIVATE)
        val enteredCode = prefs.getString("m_act_code", "") ?: ""
        if (enteredCode.isBlank()) {
            false
        } else {
            verifyActivationCode(deviceId, enteredCode)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val commitmentsState: StateFlow<List<FixedCommitment>> = repository.commitmentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactionsState: StateFlow<List<TransactionDb>> = repository.transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habayebTransactionsState: StateFlow<List<HabayebTransaction>> = habayebDao.getAllTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalTransactionsCount: StateFlow<Int> = combine(
        transactionsState,
        habayebTransactionsState
    ) { main, habayeb ->
        main.size + habayeb.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        checkAppIntegrity()
        refreshLocalBackups()
    }

    private var hasCheckedIntegrity = false

    private fun checkAppIntegrity() {
        if (hasCheckedIntegrity) return
        hasCheckedIntegrity = true
        val context = getApplication<Application>()
        val packageName = context.packageName
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    packageInfo.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                }
                if (signatures != null && signatures.isNotEmpty()) {
                    val firstSig = signatures.firstOrNull()
                    if (firstSig != null) {
                        val md = MessageDigest.getInstance("SHA-256")
                        val certBytes = firstSig.toByteArray()
                        val fingerprint = md.digest(certBytes).joinToString("") { "%02X".format(it) }
                        android.util.Log.i("MIZAN_SEC", "Application Signing Handshake: $fingerprint")
                    }
                }
            } catch (e: Exception) {
                // Log silently
            }
        }
    }

    // --- Device Activation Helpers ---

    fun getOrGenerateUnifiedDeviceId(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("makhzan_prefs", Context.MODE_PRIVATE)
        var deviceId = sharedPrefs.getString("unified_device_id", "")
        
        if (deviceId.isNullOrBlank()) {
            val tempPart = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            val androidId = android.provider.Settings.Secure.getString(
                context.contentResolver, 
                android.provider.Settings.Secure.ANDROID_ID
            )
            val permPart = if (!androidId.isNullOrBlank()) {
                androidId.take(8).uppercase()
            } else {
                "A1B2C3D4"
            }
            deviceId = "MZ-$tempPart-$permPart"
            sharedPrefs.edit().putString("unified_device_id", deviceId).apply()
        }
        return deviceId
    }

    private fun decryptSalt(): String {
        val mask = 0x7F
        val obfuscatedSalt = byteArrayOf(
            50, 22, 5, 30, 17, 62, 19, 59, 30, 13, 
            44, 26, 28, 10, 13, 26, 44, 30, 19, 11, 
            77, 79, 77, 73, 32, 50, 30, 17, 12, 16, 
            10, 13
        )
        val decrypted = ByteArray(obfuscatedSalt.size)
        for (i in obfuscatedSalt.indices) {
            decrypted[i] = (obfuscatedSalt[i].toInt() xor mask).toByte()
        }
        return String(decrypted, Charsets.UTF_8)
    }

    private fun getSecureLimitVal(): Int {
        val mask1 = 0xE6F2
        val mask2 = 0xE696
        return mask1 xor mask2 // results in 100 without a static 100 literal in compiled bytecode
    }

    private fun getPrefixTemp(): String {
        return String(byteArrayOf(65, 67, 84, 45, 84, 45), Charsets.UTF_8) // "ACT-T-"
    }

    private fun getPrefixPerm(): String {
        return String(byteArrayOf(65, 67, 84, 45, 80, 45), Charsets.UTF_8) // "ACT-P-"
    }

    fun activateLicense(code: String): Boolean {
        val cleanCode = code.trim().uppercase()
        val deviceId = getOrGenerateUnifiedDeviceId(getApplication())
        
        val isValid = verifyActivationCode(deviceId, cleanCode)
        if (isValid) {
            val prefs = getApplication<Application>().getSharedPreferences("mizan_sec_prefs", Context.MODE_PRIVATE)
            val isPermanentCode = cleanCode.startsWith(getPrefixPerm())
            prefs.edit()
                .putBoolean("is_premium", true)
                .putBoolean("is_permanent", isPermanentCode)
                .putString("m_act_code", cleanCode)
                .apply()
            _activationTrigger.value += 1
        }
        return isValid
    }

    fun isTrialExpired(): Boolean {
        val cap = getSecureLimitVal()
        val count = totalTransactionsCount.value
        val activated = isActivatedState.value
        return !activated && count >= cap
    }

    private fun verifyActivationCode(deviceId: String, enteredCode: String): Boolean {
        val cleanEntered = enteredCode.trim().uppercase()
        val parts = deviceId.split("-")
        val tempPart = if (parts.size >= 3) parts[1] else ""
        val permPart = if (parts.size >= 3) parts[2] else ""

        val tempPrefix = getPrefixTemp()
        val permPrefix = getPrefixPerm()

        if (cleanEntered.startsWith(tempPrefix)) {
            val enteredPayload = cleanEntered.substring(tempPrefix.length)
            val salt = decryptSalt()
            val combined = tempPart + salt
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(combined.toByteArray(Charsets.UTF_8))
            val shaResult = bytes.joinToString("") { "%02x".format(it) }.uppercase()
            return enteredPayload == shaResult.take(8)
        } else if (cleanEntered.startsWith(permPrefix)) {
            val enteredPayload = cleanEntered.substring(permPrefix.length)
            val salt = decryptSalt()
            val combined = permPart + salt
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(combined.toByteArray(Charsets.UTF_8))
            val shaResult = bytes.joinToString("") { "%02x".format(it) }.uppercase()
            return enteredPayload == shaResult.take(8)
        }
        return false
    }

    // --- Trash & Soft Delete Restorations (Atomic Transactions on Dispatchers.IO) ---

    fun restoreItemFromTrash(item: DeletedItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Atomic execution using database.withTransaction to guarantee domain data consistency
                database.withTransaction {
                    trashDao.restoreDeletedItem(item)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), R.string.toast_delete_success, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), R.string.toast_delete_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Keep direct signature for backward-compatibility with UI screens
    fun restoreDeletedItem(item: DeletedItemEntity) {
        restoreItemFromTrash(item)
    }

    fun restoreMultipleItems(items: List<DeletedItemEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                database.withTransaction {
                    items.forEach { trashDao.restoreDeletedItem(it) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), R.string.toast_delete_success, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun permanentlyDeleteDeletedItem(item: DeletedItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.removeDeletedItem(item)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun permanentlyDeleteMultipleItems(items: List<DeletedItemEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                items.forEach { repository.removeDeletedItem(it) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.clearDeletedItems()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Local Backup Paths Management ---

    fun getBaseBackupDirectory(): File {
        val context = getApplication<Application>()
        val hasPublicAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (hasPublicAccess) {
            val publicDocDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val mainDir = File(publicDocDir, "Mizan_Backups")
            try {
                if (!mainDir.exists()) {
                    val created = mainDir.mkdirs()
                    if (created || mainDir.exists()) {
                        return mainDir
                    }
                } else {
                    return mainDir
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val appExternalDocsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) 
            ?: context.getExternalFilesDir(null)
        val fallbackMainDir = File(appExternalDocsDir, "Mizan_Backups")
        if (!fallbackMainDir.exists()) {
            fallbackMainDir.mkdirs()
        }
        return fallbackMainDir
    }

    fun getBackupDirectory(): File {
        val baseDir = getBaseBackupDirectory()
        val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        val monthStr = sdf.format(java.util.Date())
        val targetDir = File(baseDir, monthStr)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    private fun getAllMzdFilesRecursively(rootDir: File): List<File> {
        val result = mutableListOf<File>()
        val files = rootDir.listFiles() ?: return emptyList()
        for (f in files) {
            if (f.isDirectory) {
                result.addAll(getAllMzdFilesRecursively(f))
            } else if (f.name.endsWith(".mzd")) {
                result.add(f)
            }
        }
        return result
    }

    fun refreshLocalBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            val baseDir = getBaseBackupDirectory()
            val files = getAllMzdFilesRecursively(baseDir)
            _localBackups.value = files.sortedByDescending { it.lastModified() }
        }
    }

    // --- Google Drive Cloud Sync Handlers ---

    fun handleGoogleOAuthCode(code: String, email: String? = null, redirectUri: String = "", onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = googleDriveSyncHelper.handleAuthorizationCode(code, email, redirectUri)
            if (success) {
                val current = settingsState.value
                repository.saveSettings(current.copy(isCloudSyncEnabled = true))
            }
            onComplete?.invoke(success)
        }
    }

    fun backupToGoogleDriveDirect(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isTrulySignedIn = googleDriveSyncHelper.isUserTrulySignedIn(getApplication())
                val refreshToken = googleDriveSyncHelper.getStoredRefreshToken()
                val isConnected = isTrulySignedIn || !refreshToken.isNullOrEmpty()
                if (!isConnected) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(getApplication(), R.string.cloud_toast_delete_failed, Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete?.invoke(false)
                    }
                    return@launch
                }

                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = habayebDao.getAllCustomersDirect()
                val habayebTxs = habayebDao.getAllTransactionsDirect()
                val deletedItems = repository.deletedItemsFlow.first()
                val jsonStr = exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, deletedItems)
                
                val success = googleDriveSyncHelper.uploadBackupToDrive(jsonStr)
                launch(Dispatchers.Main) {
                    onComplete?.invoke(success)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), R.string.cloud_toast_delete_failed, Toast.LENGTH_LONG).show()
                }
                launch(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun restoreFromGoogleDriveDirect(context: Context, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isTrulySignedIn = googleDriveSyncHelper.isUserTrulySignedIn(context)
                val refreshToken = googleDriveSyncHelper.getStoredRefreshToken()
                val isConnected = isTrulySignedIn || !refreshToken.isNullOrEmpty()
                if (!isConnected) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, R.string.cloud_toast_restore_failed, Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete(false)
                    }
                    return@launch
                }

                val jsonStr = googleDriveSyncHelper.downloadBackupFromDrive()
                if (jsonStr != null) {
                    executeMasterRestore(jsonStr, context) { success, _ ->
                        onComplete(success)
                    }
                } else {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, R.string.cloud_toast_restore_failed, Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete(false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, R.string.cloud_toast_restore_failed, Toast.LENGTH_LONG).show()
                }
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun googleDriveLogout(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.saveSettings(current.copy(isCloudSyncEnabled = false))
        }
        googleDriveSyncHelper.logoutAsync {
            _cloudBackupsList.value = emptyList()
            onComplete?.invoke()
        }
    }

    fun fetchCloudBackupsList() {
        viewModelScope.launch {
            try {
                _isFetchingCloudBackups.value = true
                val list = googleDriveSyncHelper.listCloudBackups()
                _cloudBackupsList.value = list
                _isFetchingCloudBackups.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                _isFetchingCloudBackups.value = false
            }
        }
    }

    fun uploadBackupToGoogleDrive(onComplete: (Boolean) -> Unit) {
        val sdfName = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US)
        val dateStr = sdfName.format(java.util.Date())
        val newFileName = "Mzd_$dateStr.mzd"
        uploadBackupToGoogleDriveWithFilename(newFileName, onComplete)
    }

    fun uploadBackupToGoogleDriveWithFilename(filename: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isTrulySignedIn = googleDriveSyncHelper.isUserTrulySignedIn(getApplication())
                val refreshToken = googleDriveSyncHelper.getStoredRefreshToken()
                val isConnected = isTrulySignedIn || !refreshToken.isNullOrEmpty()
                if (!isConnected) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(getApplication(), R.string.cloud_toast_delete_failed, Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete(false)
                    }
                    return@launch
                }

                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = habayebDao.getAllCustomersDirect()
                val habayebTxs = habayebDao.getAllTransactionsDirect()
                val deletedItems = repository.deletedItemsFlow.first()
                val jsonStr = exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, deletedItems)
                
                val success = googleDriveSyncHelper.uploadBackupToDriveWithFilename(filename, jsonStr)
                if (success) {
                    fetchCloudBackupsList()
                }
                launch(Dispatchers.Main) {
                    onComplete(success)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), R.string.cloud_toast_delete_failed, Toast.LENGTH_LONG).show()
                }
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun restoreFromGoogleDriveById(context: Context, fileId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isTrulySignedIn = googleDriveSyncHelper.isUserTrulySignedIn(context)
                val refreshToken = googleDriveSyncHelper.getStoredRefreshToken()
                val isConnected = isTrulySignedIn || !refreshToken.isNullOrEmpty()
                if (!isConnected) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, R.string.cloud_toast_restore_failed, Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete(false)
                    }
                    return@launch
                }

                val jsonStr = googleDriveSyncHelper.downloadBackupFromDriveById(fileId)
                if (jsonStr != null) {
                    executeMasterRestore(jsonStr, context) { success, _ ->
                        onComplete(success)
                    }
                } else {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, R.string.cloud_toast_restore_failed, Toast.LENGTH_LONG).show()
                    }
                    launch(Dispatchers.Main) {
                        onComplete(false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, R.string.cloud_toast_restore_failed, Toast.LENGTH_LONG).show()
                }
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun deleteCloudBackupById(fileId: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = googleDriveSyncHelper.deleteBackupFromDriveById(fileId)
                if (success) {
                    fetchCloudBackupsList()
                }
                launch(Dispatchers.Main) {
                    onComplete(success)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    fun deleteMultipleCloudBackupsByIds(fileIds: List<String>, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var allSuccess = true
                for (fileId in fileIds) {
                    val success = googleDriveSyncHelper.deleteBackupFromDriveById(fileId)
                    if (!success) {
                        allSuccess = false
                    }
                }
                if (fileIds.isNotEmpty()) {
                    fetchCloudBackupsList()
                }
                launch(Dispatchers.Main) {
                    onComplete(allSuccess)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }

    // --- JSON Backup Export/Import Serialization Engine ---

    fun getBackupJsonForClipboard(onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = habayebDao.getAllCustomersDirect()
                val habayebTxs = habayebDao.getAllTransactionsDirect()
                val deletedItems = repository.deletedItemsFlow.first()
                val jsonStr = exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, deletedItems)
                launch(Dispatchers.Main) {
                    onComplete(jsonStr)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createLocalBackup(context: Context, onComplete: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSettings = settingsState.value
                val commitments = repository.commitmentsFlow.first()
                val transactions = repository.transactionsFlow.first()
                val habayebCusts = habayebDao.getAllCustomersDirect()
                val habayebTxs = habayebDao.getAllTransactionsDirect()
                val deletedItems = repository.deletedItemsFlow.first()
                val jsonStr = exportBackupToJson(currentSettings, commitments, transactions, habayebCusts, habayebTxs, deletedItems)
                val dir = getBackupDirectory()
                val sdfName = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US)
                val dateStr = sdfName.format(java.util.Date())
                val fileName = "Mizan_$dateStr.mzd"
                val file = File(dir, fileName)
                file.writeText(jsonStr)
 
                if (file.exists() && file.length() > 0) {
                    refreshLocalBackups()
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, R.string.autobackup_notification_title_local, Toast.LENGTH_SHORT).show()
                        onComplete(file)
                    }
                } else {
                    throw java.io.IOException("File verification failed.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, R.string.autobackup_notification_title_failure, Toast.LENGTH_LONG).show()
                    onComplete(null)
                }
            }
        }
    }

    fun executeMasterRestore(rawJsonString: String, context: Context, onComplete: (Boolean, AppSettings?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = JSONObject(rawJsonString)

                val currentLocalSettings = repository.settingsFlow.first() ?: AppSettings()
                val data = importBackupFromJson(rawJsonString)
                val restoredSettingsUnmerged = data.first
                val restoredSettings = restoredSettingsUnmerged.copy(
                    isPasscodeEnabled = currentLocalSettings.isPasscodeEnabled,
                    passcodeHash = currentLocalSettings.passcodeHash,
                    recoveryPhraseHash = currentLocalSettings.recoveryPhraseHash,
                    recoveryHint = currentLocalSettings.recoveryHint,
                    tempPart = currentLocalSettings.tempPart,
                    permPart = currentLocalSettings.permPart,
                    unifiedDeviceId = currentLocalSettings.unifiedDeviceId,
                    isFirstLaunch = currentLocalSettings.isFirstLaunch
                )
                val restoredCommitments = data.second
                val restoredTransactions = data.third

                val appDb = AppDatabase.getDatabase(context)
                appDb.withTransaction {
                    repository.clearTransactions()
                    repository.clearCommitments()
                    repository.clearCustomCategories()
                    repository.clearDeletedItems()

                    repository.saveSettings(restoredSettings)
                    for (fc in restoredCommitments) {
                        repository.saveCommitment(fc)
                    }
                    for (tx in restoredTransactions) {
                        repository.saveTransaction(tx)
                    }

                    if (root.has("deleted_items") && !root.isNull("deleted_items")) {
                        val deletedItemsArr = root.optJSONArray("deleted_items")
                        if (deletedItemsArr != null) {
                            for (i in 0 until deletedItemsArr.length()) {
                                val obj = deletedItemsArr.getJSONObject(i)
                                val item = DeletedItemEntity(
                                    id = obj.getString("id"),
                                    sourceSystem = obj.getString("sourceSystem"),
                                    originalTableName = obj.getString("originalTableName"),
                                    jsonData = obj.getString("jsonData"),
                                    deletedAt = obj.getLong("deletedAt")
                                )
                                repository.saveDeletedItem(item)
                            }
                        }
                    }

                    habayebDao.clearAllCustomers()
                    habayebDao.clearAllTransactions()

                    val jsonHabayebObj = root.optJSONObject("habayeb_debts")
                    val legacyHabayebDb = root.optJSONObject("habayeb_debts_db")

                    val custArr = jsonHabayebObj?.optJSONArray("customers")
                        ?: legacyHabayebDb?.optJSONArray("habayeb_customers")

                    if (custArr != null) {
                        for (i in 0 until custArr.length()) {
                            val obj = custArr.getJSONObject(i)
                            val customer = HabayebCustomer(
                                id = obj.optString("id", obj.optString("customer_id", "")),
                                name = obj.getString("name"),
                                phone = obj.optString("phone", ""),
                                notes = obj.optString("notes", ""),
                                createdAt = obj.optLong("created_at", obj.optLong("createdAt", System.currentTimeMillis() / 1000))
                            )
                            habayebDao.insertCustomer(customer)
                        }
                    }
                    
                    val txArr = jsonHabayebObj?.optJSONArray("debt_transactions")
                        ?: legacyHabayebDb?.optJSONArray("habayeb_transactions")

                    if (txArr != null) {
                        for (i in 0 until txArr.length()) {
                            val obj = txArr.getJSONObject(i)
                            val transaction = HabayebTransaction(
                                id = obj.getString("id"),
                                customerId = obj.optString("customer_id", obj.optString("customerId", "")),
                                type = obj.getString("type"),
                                amount = obj.getDouble("amount"),
                                timestamp = obj.getLong("timestamp"),
                                description = obj.optString("description", ""),
                                linkedMainTxId = obj.optString("linked_main_tx_id", obj.optString("linkedMainTxId", null))
                            )
                            habayebDao.insertTransaction(transaction)
                        }
                    }
                }

                refreshLocalBackups()

                val hasLegacy = root.has("mizan_al_dar_db") || root.has("habayeb_debts_db")
                val successMessageRes = if (hasLegacy) R.string.toast_restore_legacy_migrated else R.string.cloud_toast_restore_success

                launch(Dispatchers.Main) {
                    Toast.makeText(context, successMessageRes, Toast.LENGTH_SHORT).show()
                    onComplete(true, restoredSettings)
                }
            } catch (e: org.json.JSONException) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, R.string.backup_schema_mismatch, Toast.LENGTH_LONG).show()
                    onComplete(false, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(context, R.string.cloud_toast_restore_failed, Toast.LENGTH_LONG).show()
                    onComplete(false, null)
                }
            }
        }
    }

    fun restoreFromMzdContent(jsonContent: String, context: Context, onComplete: (Boolean) -> Unit) {
        executeMasterRestore(jsonContent, context) { success, _ ->
            onComplete(success)
        }
    }

    fun restoreFromLocalFile(file: File, context: Context, onComplete: (Boolean, AppSettings?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    val content = file.readText()
                    executeMasterRestore(content, context) { success, restoredSettings ->
                        onComplete(success, restoredSettings)
                    }
                } else {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, R.string.cloud_toast_restore_failed, Toast.LENGTH_SHORT).show()
                        onComplete(false, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    onComplete(false, null)
                }
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteAllData()
                habayebDao.clearAllCustomers()
                habayebDao.clearAllTransactions()
                refreshLocalBackups()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearLocalCopyAndWipeMemory(context: Context) {
        deleteAllData()
    }

    // --- Private Serialization Engine Helpers ---

    private fun exportBackupToJson(
        settings: AppSettings,
        commitments: List<FixedCommitment>,
        transactions: List<TransactionDb>,
        habayebCustomers: List<HabayebCustomer> = emptyList(),
        habayebTransactions: List<HabayebTransaction> = emptyList(),
        deletedItems: List<DeletedItemEntity> = emptyList()
    ): String {
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
        for (di in deletedItems) {
            val diObj = JSONObject()
            diObj.put("id", di.id)
            diObj.put("sourceSystem", di.sourceSystem)
            diObj.put("originalTableName", di.originalTableName)
            diObj.put("jsonData", di.jsonData)
            diObj.put("deletedAt", di.deletedAt)
            deletedItemsArr.put(diObj)
        }
        root.put("deleted_items", deletedItemsArr)

        return root.toString(2)
    }

    private fun importBackupFromJson(jsonString: String): Triple<AppSettings, List<FixedCommitment>, List<TransactionDb>> {
        val root = JSONObject(jsonString)
        val sourceObj = if (root.has("mizan_al_dar_db")) root.getJSONObject("mizan_al_dar_db") else root

        val settingsObj = sourceObj.optJSONObject("settings")
        val settings = if (settingsObj != null) {
            AppSettings(
                currencySymbol = settingsObj.optString("currency_symbol", "ر.ي"),
                userRole = settingsObj.optString("user_role", "الزوجة"),
                guardianNumber = settingsObj.optString("guardian_number", "+967774004399"),
                guardianRelation = settingsObj.optString("guardian_relation", "الزوج"),
                schoolExpensesEnabled = settingsObj.optBoolean("school_expenses_enabled", true),
                themeMode = settingsObj.optInt("theme_mode", 0)
            )
        } else {
            AppSettings()
        }

        val commitmentsList = mutableListOf<FixedCommitment>()
        val commitmentsArr = sourceObj.optJSONArray("fixed_commitments")
        if (commitmentsArr != null) {
            for (i in 0 until commitmentsArr.length()) {
                val obj = commitmentsArr.getJSONObject(i)
                commitmentsList.add(
                    FixedCommitment(
                        name = obj.getString("name"),
                        targetAmount = obj.getDouble("target_amount"),
                        currentProgress = obj.getDouble("current_progress"),
                        orderIndex = obj.optInt("order_index", i)
                    )
                )
            }
        }

        val transactionsList = mutableListOf<TransactionDb>()
        val transactionsArr = sourceObj.optJSONArray("transactions")
        if (transactionsArr != null) {
            for (i in 0 until transactionsArr.length()) {
                val obj = transactionsArr.getJSONObject(i)
                transactionsList.add(
                    TransactionDb(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        type = obj.getString("type"),
                        category = obj.getString("category"),
                        amount = obj.getDouble("amount"),
                        description = obj.optString("description", "")
                    )
                )
            }
        }

        return Triple(settings, commitmentsList, transactionsList)
    }
}
