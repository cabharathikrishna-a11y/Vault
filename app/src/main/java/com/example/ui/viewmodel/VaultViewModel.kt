package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.crypto.VaultCrypto
import com.example.data.model.DecryptedPasswordHistory
import com.example.data.model.DecryptedVaultItem
import com.example.data.model.FamilyVault
import com.example.data.model.PasswordHistory
import com.example.data.model.UserProfile
import com.example.data.model.VaultItem
import com.example.data.model.VaultPayload
import com.example.data.repository.FirestoreVaultRepository
import com.example.generator.PasswordGenerator
import com.example.generator.PasswordGeneratorConfig
import com.example.generator.PasswordStrength
import com.example.security.SecurityManager
import com.example.data.security.BreachChecker
import com.example.data.security.BreachCheckResult
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

enum class VaultSetupState {
    LOADING,
    NO_FAMILY,
    LOCKED_PASSPHRASE,
    UNLOCKED
}

data class GeneratorUiState(
    val config: PasswordGeneratorConfig = PasswordGeneratorConfig(),
    val currentPassword: String = "",
    val strength: PasswordStrength = PasswordStrength.STRONG,
    val entropy: Double = 80.0
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirestoreVaultRepository(application.applicationContext)
    val securityManager = SecurityManager(application.applicationContext)
    private val auth = Firebase.auth

    // Vault Master Encryption Key (held in memory only)
    private var activeSecretKey: SecretKey? = null

    private val _setupState = MutableStateFlow(VaultSetupState.LOADING)
    val setupState: StateFlow<VaultSetupState> = _setupState.asStateFlow()

    private val _currentFamily = MutableStateFlow<FamilyVault?>(null)
    val currentFamily: StateFlow<FamilyVault?> = _currentFamily.asStateFlow()

    // App Lock (PIN / Biometrics)
    private val _isAppLocked = MutableStateFlow(securityManager.isPinSet())
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Dark Mode Setting
    private val _darkModeSetting = MutableStateFlow(securityManager.getDarkModeSetting())
    val darkModeSetting: StateFlow<String> = _darkModeSetting.asStateFlow()

    // Search and Category Filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Raw items from Firestore / Room
    private val _rawFamilyItems = MutableStateFlow<List<VaultItem>>(emptyList())
    private val _rawPrivateItems = MutableStateFlow<List<VaultItem>>(emptyList())

    // Decrypted items exposed to UI
    private val _decryptedItems = MutableStateFlow<List<DecryptedVaultItem>>(emptyList())
    val decryptedItems: StateFlow<List<DecryptedVaultItem>> = _decryptedItems.asStateFlow()

    // Password History
    private val _rawPasswordHistory = MutableStateFlow<List<PasswordHistory>>(emptyList())
    private val _decryptedPasswordHistory = MutableStateFlow<List<DecryptedPasswordHistory>>(emptyList())
    val decryptedPasswordHistory: StateFlow<List<DecryptedPasswordHistory>> = _decryptedPasswordHistory.asStateFlow()

    // Password Generator
    private val _generatorState = MutableStateFlow(GeneratorUiState())
    val generatorState: StateFlow<GeneratorUiState> = _generatorState.asStateFlow()

    // Breach Monitor State for Generator
    private val _generatorBreachResult = MutableStateFlow<BreachCheckResult?>(null)
    val generatorBreachResult: StateFlow<BreachCheckResult?> = _generatorBreachResult.asStateFlow()

    // Vault-wide Breach Audit State
    private val _isAuditingBreaches = MutableStateFlow(false)
    val isAuditingBreaches: StateFlow<Boolean> = _isAuditingBreaches.asStateFlow()

    private val _auditResults = MutableStateFlow<Map<String, BreachCheckResult>>(emptyMap())
    val auditResults: StateFlow<Map<String, BreachCheckResult>> = _auditResults.asStateFlow()

    // UI Feedback Message / Error
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Loading / Async Processing indicator (e.g. key derivation, creating vault)
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // App Update State
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private var userProfileJob: Job? = null
    private var familyJob: Job? = null
    private var familyItemsJob: Job? = null
    private var privateItemsJob: Job? = null
    private var passwordHistoryJob: Job? = null

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            loadUserProfileAndFamily(user.uid)
        } else {
            userProfileJob?.cancel()
            familyJob?.cancel()
            familyItemsJob?.cancel()
            privateItemsJob?.cancel()
            passwordHistoryJob?.cancel()
            _setupState.value = VaultSetupState.LOADING
            _currentFamily.value = null
            activeSecretKey = null
            securityManager.clearSavedMasterPassphrase()
            _decryptedItems.value = emptyList()
            _decryptedPasswordHistory.value = emptyList()
        }
    }

    init {
        generateNewPassword()
        checkForAppUpdate()
        auth.addAuthStateListener(authListener)
        val current = auth.currentUser
        if (current != null) {
            loadUserProfileAndFamily(current.uid)
        }
    }

    fun checkForAppUpdate(customUrl: String? = null) {
        viewModelScope.launch {
            val info = AppUpdateManager.checkForUpdate(getApplication(), customUrl)
            if (info.hasUpdate) {
                _updateInfo.value = info
            } else if (customUrl != null) {
                _statusMessage.value = "App is up to date (v${AppUpdateManager.getCurrentVersionName(getApplication())})"
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        recomputeFilteredItems()
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
        recomputeFilteredItems()
    }

    fun setDarkModeSetting(setting: String) {
        securityManager.setDarkModeSetting(setting)
        _darkModeSetting.value = setting
    }

    fun unlockAppWithPin(pin: String): Boolean {
        return if (securityManager.verifyPin(pin)) {
            _isAppLocked.value = false
            true
        } else {
            false
        }
    }

    fun unlockAppWithBiometric() {
        _isAppLocked.value = false
    }

    fun lockApp() {
        if (securityManager.isPinSet()) {
            _isAppLocked.value = true
        }
    }

    fun skipLoadingToSetup() {
        if (_setupState.value == VaultSetupState.LOADING) {
            _setupState.value = VaultSetupState.NO_FAMILY
        }
    }

    private suspend fun tryAutoUnlockWithSavedPassphrase(family: FamilyVault): Boolean {
        if (activeSecretKey != null) return true
        val savedPassphrase = securityManager.getSavedMasterPassphrase() ?: return false
        return try {
            val (isValid, derivedKey) = withContext(Dispatchers.Default) {
                val key = VaultCrypto.deriveKey(savedPassphrase, family.salt)
                val checkHash = VaultCrypto.generateKeyCheckHashFromKey(key)
                Pair(checkHash == family.keyCheckHash, key)
            }
            if (isValid && derivedKey != null) {
                activeSecretKey = derivedKey
                _setupState.value = VaultSetupState.UNLOCKED
                observeVaultStreams(family.id)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun loadUserProfileAndFamily(uid: String = auth.currentUser?.uid ?: "") {
        if (uid.isBlank()) {
            _setupState.value = VaultSetupState.NO_FAMILY
            return
        }
        userProfileJob?.cancel()
        _setupState.value = VaultSetupState.LOADING
        userProfileJob = viewModelScope.launch {
            // Check local DB first for instantaneous load
            val local = repository.getLocalFamilyOnce()
            if (local != null) {
                _currentFamily.value = local
                val unlocked = tryAutoUnlockWithSavedPassphrase(local)
                if (!unlocked) {
                    _setupState.value = VaultSetupState.LOCKED_PASSPHRASE
                }
            }

            // Timeout safeguard: if still LOADING after 2500ms, unlock to NO_FAMILY setup screen so user is never stuck
            val timeoutJob = launch {
                kotlinx.coroutines.delay(2500)
                if (_setupState.value == VaultSetupState.LOADING) {
                    _setupState.value = VaultSetupState.NO_FAMILY
                }
            }

            try {
                repository.observeUserProfile(uid).collect { profile ->
                    timeoutJob.cancel()
                    if (profile == null || profile.familyId.isBlank()) {
                        if (_currentFamily.value == null) {
                            _setupState.value = VaultSetupState.NO_FAMILY
                        }
                    } else {
                        observeFamily(profile.familyId)
                    }
                }
            } catch (e: Exception) {
                timeoutJob.cancel()
                if (_setupState.value == VaultSetupState.LOADING) {
                    _setupState.value = VaultSetupState.NO_FAMILY
                }
            }
        }
    }

    private fun observeFamily(familyId: String) {
        familyJob?.cancel()
        familyJob = viewModelScope.launch {
            repository.observeFamily(familyId).collect { family ->
                _currentFamily.value = family
                if (family == null) {
                    _setupState.value = VaultSetupState.NO_FAMILY
                } else {
                    if (activeSecretKey != null) {
                        _setupState.value = VaultSetupState.UNLOCKED
                        observeVaultStreams(familyId)
                    } else {
                        val unlocked = tryAutoUnlockWithSavedPassphrase(family)
                        if (!unlocked) {
                            _setupState.value = VaultSetupState.LOCKED_PASSPHRASE
                        }
                    }
                }
            }
        }
    }

    private fun observeVaultStreams(familyId: String) {
        val uid = auth.currentUser?.uid ?: return
        familyItemsJob?.cancel()
        familyItemsJob = viewModelScope.launch {
            repository.observeFamilyVaultItems(familyId).collect { items ->
                _rawFamilyItems.value = items
                decryptAllItems()
            }
        }
        privateItemsJob?.cancel()
        privateItemsJob = viewModelScope.launch {
            repository.observePrivateVaultItems(uid).collect { items ->
                _rawPrivateItems.value = items
                decryptAllItems()
            }
        }
        passwordHistoryJob?.cancel()
        passwordHistoryJob = viewModelScope.launch {
            repository.observePasswordHistory(familyId).collect { history ->
                _rawPasswordHistory.value = history
                decryptPasswordHistory()
            }
        }
    }

    fun createFamilyVault(name: String, passphrase: String) {
        if (name.isBlank() || passphrase.length < 6) {
            _statusMessage.value = "Family name is required and master passphrase must be at least 6 characters."
            return
        }

        _isProcessing.value = true
        viewModelScope.launch {
            try {
                // Derive key and verification hash asynchronously off the UI thread
                val (salt, derivedKey, keyCheckHash) = withContext(Dispatchers.Default) {
                    val s = VaultCrypto.generateSalt()
                    val key = VaultCrypto.deriveKey(passphrase, s)
                    val checkHash = VaultCrypto.generateKeyCheckHashFromKey(key)
                    Triple(s, key, checkHash)
                }

                val result = repository.createFamily(name, salt, keyCheckHash)
                if (result.isSuccess) {
                    activeSecretKey = derivedKey
                    securityManager.saveMasterPassphrase(passphrase)
                    val family = result.getOrNull()
                    _currentFamily.value = family
                    _setupState.value = VaultSetupState.UNLOCKED
                    _statusMessage.value = "Family Vault '$name' created securely!"
                    family?.id?.let { observeVaultStreams(it) }
                } else {
                    _statusMessage.value = "Failed to create vault: ${result.exceptionOrNull()?.localizedMessage}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error creating vault: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun joinFamilyVault(familyId: String, passphrase: String) {
        if (familyId.isBlank() || passphrase.isBlank()) {
            _statusMessage.value = "Enter valid Family ID and Master Passphrase"
            return
        }

        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val result = repository.joinFamily(familyId.trim())
                if (result.isSuccess) {
                    val family = result.getOrThrow()
                    val (isValid, derivedKey) = withContext(Dispatchers.Default) {
                        val key = VaultCrypto.deriveKey(passphrase, family.salt)
                        val checkHash = VaultCrypto.generateKeyCheckHashFromKey(key)
                        if (checkHash == family.keyCheckHash) {
                            Pair(true, key)
                        } else {
                            Pair(false, null)
                        }
                    }

                    if (isValid && derivedKey != null) {
                        activeSecretKey = derivedKey
                        securityManager.saveMasterPassphrase(passphrase)
                        _currentFamily.value = family
                        _setupState.value = VaultSetupState.UNLOCKED
                        _statusMessage.value = "Joined ${family.name} successfully!"
                        observeVaultStreams(family.id)
                    } else {
                        _statusMessage.value = "Incorrect Family Master Passphrase."
                    }
                } else {
                    _statusMessage.value = "Could not find Family Vault with ID: $familyId"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error joining vault: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun unlockWithPassphrase(passphrase: String, onResult: (Boolean) -> Unit = {}): Boolean {
        val family = _currentFamily.value ?: return false
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val (isValid, key) = withContext(Dispatchers.Default) {
                    val k = VaultCrypto.deriveKey(passphrase, family.salt)
                    val checkHash = VaultCrypto.generateKeyCheckHashFromKey(k)
                    Pair(checkHash == family.keyCheckHash, k)
                }
                if (isValid) {
                    activeSecretKey = key
                    securityManager.saveMasterPassphrase(passphrase)
                    _setupState.value = VaultSetupState.UNLOCKED
                    decryptAllItems()
                    decryptPasswordHistory()
                    onResult(true)
                } else {
                    _statusMessage.value = "Incorrect Master Passphrase. Please try again."
                    onResult(false)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Unlock failed: ${e.localizedMessage}"
                onResult(false)
            } finally {
                _isProcessing.value = false
            }
        }
        return true
    }

    private fun decryptAllItems() {
        val key = activeSecretKey ?: return
        val allRaw = _rawFamilyItems.value + _rawPrivateItems.value

        viewModelScope.launch(Dispatchers.Default) {
            val decryptedList = allRaw.mapNotNull { item ->
                try {
                    val title = VaultCrypto.decrypt(item.encryptedTitle, key)
                    val payloadJson = VaultCrypto.decrypt(item.encryptedPayload, key)
                    val payload = VaultPayload.fromJsonString(payloadJson)
                    DecryptedVaultItem(
                        id = item.id,
                        familyId = item.familyId,
                        isPrivate = item.isPrivate,
                        itemType = item.itemType,
                        title = title,
                        payload = payload,
                        createdByUid = item.createdByUid,
                        createdByName = item.createdByName,
                        memberUids = item.memberUids,
                        hasAttachment = item.hasAttachment,
                        attachmentType = item.attachmentType,
                        updatedAt = item.updatedAt
                    )
                } catch (e: Exception) {
                    null
                }
            }
            _decryptedItems.value = decryptedList
        }
    }

    private fun decryptPasswordHistory() {
        val key = activeSecretKey ?: return
        val listRaw = _rawPasswordHistory.value

        viewModelScope.launch(Dispatchers.Default) {
            val list = listRaw.mapNotNull { hist ->
                try {
                    val pwd = VaultCrypto.decrypt(hist.encryptedPassword, key)
                    DecryptedPasswordHistory(
                        id = hist.id,
                        familyId = hist.familyId,
                        password = pwd,
                        length = hist.length,
                        optionsSummary = hist.optionsSummary,
                        generatedByUid = hist.generatedByUid,
                        generatedByName = hist.generatedByName,
                        createdAt = hist.createdAt
                    )
                } catch (e: Exception) {
                    null
                }
            }
            _decryptedPasswordHistory.value = list
        }
    }

    private fun recomputeFilteredItems() {
        // Triggered by search or category filter updates
    }

    fun saveVaultItem(
        id: String = "",
        title: String,
        payload: VaultPayload,
        itemType: String,
        isPrivate: Boolean
    ) {
        val key = activeSecretKey
        if (key == null) {
            _statusMessage.value = "Vault is locked. Unlock before adding items."
            return
        }
        val family = _currentFamily.value
        val familyId = family?.id ?: "personal_vault"
        val uid = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName ?: "Family Member"

        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val (encryptedTitle, encryptedPayload) = withContext(Dispatchers.Default) {
                    val encTitle = VaultCrypto.encrypt(title, key)
                    val encPayload = VaultCrypto.encrypt(payload.toJsonString(), key)
                    Pair(encTitle, encPayload)
                }

                val memberUids = if (isPrivate) listOf(uid) else (family?.memberUids ?: listOf(uid))

                val hasAttachment = payload.attachmentBase64.isNotBlank() ||
                                    payload.attachmentFrontBase64.isNotBlank() ||
                                    payload.attachmentBackBase64.isNotBlank()

                val item = VaultItem(
                    id = id,
                    familyId = familyId,
                    isPrivate = isPrivate,
                    itemType = itemType,
                    encryptedTitle = encryptedTitle,
                    encryptedPayload = encryptedPayload,
                    createdByUid = uid,
                    createdByName = userName,
                    memberUids = memberUids,
                    hasAttachment = hasAttachment,
                    attachmentType = payload.mimeType.ifBlank { if (hasAttachment) "image" else "" }
                )

                val result = repository.saveVaultItem(item)
                if (result.isSuccess) {
                    _statusMessage.value = if (id.isBlank()) "Saved '$title' to Vault!" else "Updated '$title'!"
                } else {
                    _statusMessage.value = "Failed to save: ${result.exceptionOrNull()?.localizedMessage}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Encryption error: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deleteVaultItem(item: DecryptedVaultItem) {
        viewModelScope.launch {
            val result = repository.deleteVaultItem(item.familyId, item.id, item.isPrivate)
            if (result.isSuccess) {
                _statusMessage.value = "Deleted '${item.title}'"
            } else {
                _statusMessage.value = "Failed to delete: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    // Password Generator methods
    fun updateGeneratorConfig(newConfig: PasswordGeneratorConfig) {
        _generatorState.value = _generatorState.value.copy(config = newConfig)
        generateNewPassword()
    }

    fun generateNewPassword() {
        val config = _generatorState.value.config
        val pwd = PasswordGenerator.generate(config)
        val (strength, entropy) = PasswordGenerator.calculateStrength(pwd)
        _generatorState.value = _generatorState.value.copy(
            currentPassword = pwd,
            strength = strength,
            entropy = entropy
        )
        _generatorBreachResult.value = null
    }

    fun checkGeneratorPasswordBreach() {
        val pwd = _generatorState.value.currentPassword
        if (pwd.isBlank()) return
        viewModelScope.launch {
            _generatorBreachResult.value = BreachChecker.checkPassword(pwd)
        }
    }

    fun auditVaultForBreaches() {
        val items = _decryptedItems.value.filter { it.payload.password.isNotBlank() }
        if (items.isEmpty()) {
            _statusMessage.value = "No passwords stored in vault to audit."
            return
        }
        _isAuditingBreaches.value = true
        viewModelScope.launch {
            val results = mutableMapOf<String, BreachCheckResult>()
            for (item in items) {
                val res = BreachChecker.checkPassword(item.payload.password)
                results[item.id] = res
            }
            _auditResults.value = results
            _isAuditingBreaches.value = false
            val compromisedCount = results.values.count { it.isPwned }
            _statusMessage.value = if (compromisedCount == 0) {
                "Audit complete: All ${items.size} vault passwords are safe from known leaks!"
            } else {
                "Alert: $compromisedCount vault password(s) found in known public data breaches!"
            }
        }
    }

    fun syncGeneratedPasswordToHistory(password: String) {
        val key = activeSecretKey ?: return
        val family = _currentFamily.value ?: return
        val uid = auth.currentUser?.uid ?: return
        val name = auth.currentUser?.displayName ?: "Family Member"

        viewModelScope.launch {
            try {
                val encPwd = VaultCrypto.encrypt(password, key)
                val config = _generatorState.value.config
                val history = PasswordHistory(
                    id = "",
                    familyId = family.id,
                    encryptedPassword = encPwd,
                    length = password.length,
                    optionsSummary = config.summary(),
                    generatedByUid = uid,
                    generatedByName = name,
                    memberUids = family.memberUids
                )
                repository.addPasswordHistory(history)
            } catch (_: Exception) {}
        }
    }

    fun resetAppAndLocalData(onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isProcessing.value = true
                val currentUid = auth.currentUser?.uid ?: ""
                val familyId = _currentFamily.value?.id

                // 1. Delete user profile, private items, and family associations from remote Firestore
                if (currentUid.isNotBlank()) {
                    repository.deleteUserRemoteData(currentUid, familyId)
                }

                // 2. Cancel active background jobs
                userProfileJob?.cancel()
                familyJob?.cancel()
                familyItemsJob?.cancel()
                privateItemsJob?.cancel()
                passwordHistoryJob?.cancel()

                // 3. Wipe local Room database and local security preferences (PIN, biometrics, saved passphrase)
                repository.clearLocalDatabase()
                securityManager.resetAllLocalData()

                // 4. Clear in-memory state
                activeSecretKey = null
                _currentFamily.value = null
                _rawFamilyItems.value = emptyList()
                _rawPrivateItems.value = emptyList()
                _rawPasswordHistory.value = emptyList()
                _decryptedItems.value = emptyList()
                _decryptedPasswordHistory.value = emptyList()
                _isAppLocked.value = false
                _setupState.value = VaultSetupState.NO_FAMILY

                // 5. Sign out Firebase Auth
                auth.signOut()

                _isProcessing.value = false
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "App reset complete. User data completely deleted."
                    onComplete()
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Reset error: ${e.localizedMessage}"
                }
            }
        }
    }
}
