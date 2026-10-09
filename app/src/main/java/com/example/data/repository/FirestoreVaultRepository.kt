package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.LocalFamilyEntity
import com.example.data.local.LocalPasswordHistoryEntity
import com.example.data.local.LocalVaultItemEntity
import com.example.data.model.FamilyVault
import com.example.data.model.PasswordHistory
import com.example.data.model.UserProfile
import com.example.data.model.VaultItem
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

class FirestoreVaultRepository(
    private val db: FirebaseFirestore,
    private val database: AppDatabase? = null
) {
    // Secondary constructor resolving named database ID
    constructor(context: Context) : this(
        context.applicationContext.getString(R.string.firestore_database_id).let { dbId ->
            if (dbId.isBlank() || dbId == "(default)") {
                FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance(dbId)
            }
        },
        AppDatabase.getInstance(context)
    )

    private val auth = Firebase.auth
    private val rtdbRepo = RtdbRepository()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        try {
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build()
                )
                .build()
            db.firestoreSettings = settings
        } catch (_: Exception) {
            // Settings already initialized
        }
    }

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun clearLocalDatabase() {
        database?.clearAllTables()
    }

    suspend fun getLocalFamilyOnce(): FamilyVault? {
        val local = database?.vaultDao()?.getAnyFamilyOnce() ?: return null
        val members = mutableListOf<String>()
        try {
            val arr = JSONArray(local.memberUidsJson)
            for (i in 0 until arr.length()) members.add(arr.getString(i))
        } catch (_: Exception) {}
        return FamilyVault(
            id = local.id,
            name = local.name,
            createdBy = local.createdBy,
            memberUids = members,
            salt = local.salt,
            keyCheckHash = local.keyCheckHash
        )
    }

    fun observeFamily(familyId: String): Flow<FamilyVault?> = flow {
        if (familyId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "families/$familyId"
        emitAll(
            db.collection("families").document(familyId)
                .snapshots()
                .map { snapshot ->
                    val family = snapshot.toObject(FamilyVault::class.java)
                    if (family != null && database != null) {
                        repositoryScope.launch {
                            database.vaultDao().insertFamily(
                                LocalFamilyEntity(
                                    id = family.id,
                                    name = family.name,
                                    createdBy = family.createdBy,
                                    memberUidsJson = JSONArray(family.memberUids).toString(),
                                    salt = family.salt,
                                    keyCheckHash = family.keyCheckHash,
                                    updatedAtMs = family.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    family
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    // Non-blocking local Room fallback
                    val local = database?.vaultDao()?.getFamilyOnce(familyId)
                    if (local != null) {
                        val members = mutableListOf<String>()
                        try {
                            val arr = JSONArray(local.memberUidsJson)
                            for (i in 0 until arr.length()) members.add(arr.getString(i))
                        } catch (_: Exception) {}
                        emit(
                            FamilyVault(
                                id = local.id,
                                name = local.name,
                                createdBy = local.createdBy,
                                memberUids = members,
                                salt = local.salt,
                                keyCheckHash = local.keyCheckHash
                            )
                        )
                    } else {
                        emit(null)
                    }
                }
        )
    }

    suspend fun createFamily(name: String, salt: String, keyCheckHash: String): Result<FamilyVault> {
        val uid = requireUserId()
        val familyId = "fam_" + System.currentTimeMillis().toString(36) + "_" + (1000..9999).random()
        val family = FamilyVault(
            id = familyId,
            name = name,
            createdBy = uid,
            memberUids = listOf(uid),
            salt = salt,
            keyCheckHash = keyCheckHash
        )
        val path = "families/$familyId"

        return try {
            val payload = mapOf(
                "id" to family.id,
                "name" to family.name,
                "createdBy" to family.createdBy,
                "memberUids" to family.memberUids,
                "salt" to family.salt,
                "keyCheckHash" to family.keyCheckHash,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            // Cache locally immediately so the app is always responsive
            database?.vaultDao()?.insertFamily(
                LocalFamilyEntity(
                    id = family.id,
                    name = family.name,
                    createdBy = family.createdBy,
                    memberUidsJson = JSONArray(family.memberUids).toString(),
                    salt = family.salt,
                    keyCheckHash = family.keyCheckHash,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
            db.collection("families").document(familyId).set(payload).await()
            // Save profile affiliation
            saveUserProfile(UserProfile(userId = uid, displayName = auth.currentUser?.displayName ?: "User", email = auth.currentUser?.email ?: "", familyId = familyId))
            Result.success(family)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            // Even if network save is pending/delayed, local creation succeeded
            Result.success(family)
        }
    }

    suspend fun joinFamily(familyId: String): Result<FamilyVault> {
        val uid = requireUserId()
        val path = "families/$familyId"
        return try {
            val doc = db.collection("families").document(familyId).get().await()
            if (!doc.exists()) {
                return Result.failure(IllegalArgumentException("Family Vault ID not found."))
            }
            val family = doc.toObject(FamilyVault::class.java)
                ?: return Result.failure(IllegalStateException("Invalid family data."))

            if (!family.memberUids.contains(uid)) {
                val updatedMembers = family.memberUids + uid
                db.collection("families").document(familyId).update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            // Save affiliation in user profile
            saveUserProfile(UserProfile(userId = uid, displayName = auth.currentUser?.displayName ?: "User", email = auth.currentUser?.email ?: "", familyId = familyId))
            Result.success(family.copy(memberUids = family.memberUids + uid))
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeFamilyVaultItems(familyId: String): Flow<List<VaultItem>> = flow {
        if (familyId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val uid = requireUserId()
        val path = "families/$familyId/vault_items"
        emitAll(
            db.collection("families").document(familyId).collection("vault_items")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    val items = snapshot.toObjects(VaultItem::class.java)
                    if (database != null) {
                        repositoryScope.launch {
                            val entities = items.map { item ->
                                LocalVaultItemEntity(
                                    id = item.id,
                                    familyId = item.familyId,
                                    isPrivate = item.isPrivate,
                                    itemType = item.itemType,
                                    encryptedTitle = item.encryptedTitle,
                                    encryptedPayload = item.encryptedPayload,
                                    createdByUid = item.createdByUid,
                                    createdByName = item.createdByName,
                                    memberUidsJson = JSONArray(item.memberUids).toString(),
                                    hasAttachment = item.hasAttachment,
                                    attachmentType = item.attachmentType,
                                    updatedAtMs = item.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            }
                            database.vaultDao().insertItems(entities)
                        }
                    }
                    items
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    // One-shot non-blocking offline fallback from Room
                    val localEntities = database?.vaultDao()?.getFamilyItemsOnce(familyId) ?: emptyList()
                    emit(localEntities.map { entity ->
                        VaultItem(
                            id = entity.id,
                            familyId = entity.familyId,
                            isPrivate = entity.isPrivate,
                            itemType = entity.itemType,
                            encryptedTitle = entity.encryptedTitle,
                            encryptedPayload = entity.encryptedPayload,
                            createdByUid = entity.createdByUid,
                            createdByName = entity.createdByName,
                            hasAttachment = entity.hasAttachment,
                            attachmentType = entity.attachmentType,
                            updatedAt = Timestamp(entity.updatedAtMs / 1000, 0)
                        )
                    })
                }
        )
    }

    fun observePrivateVaultItems(userId: String): Flow<List<VaultItem>> = flow {
        val uid = requireUserId()
        if (uid != userId) {
            throw SecurityException("Cannot observe private vault of another user")
        }
        val path = "users/$userId/private_items"
        emitAll(
            db.collection("users").document(userId).collection("private_items")
                .snapshots()
                .map { snapshot ->
                    val items = snapshot.toObjects(VaultItem::class.java)
                    if (database != null) {
                        repositoryScope.launch {
                            val entities = items.map { item ->
                                LocalVaultItemEntity(
                                    id = item.id,
                                    familyId = item.familyId,
                                    isPrivate = true,
                                    itemType = item.itemType,
                                    encryptedTitle = item.encryptedTitle,
                                    encryptedPayload = item.encryptedPayload,
                                    createdByUid = item.createdByUid,
                                    createdByName = item.createdByName,
                                    memberUidsJson = JSONArray(item.memberUids).toString(),
                                    hasAttachment = item.hasAttachment,
                                    attachmentType = item.attachmentType,
                                    updatedAtMs = item.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            }
                            database.vaultDao().insertItems(entities)
                        }
                    }
                    items
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    val localEntities = database?.vaultDao()?.getPrivateItemsOnce(userId) ?: emptyList()
                    emit(localEntities.map { entity ->
                        VaultItem(
                            id = entity.id,
                            familyId = entity.familyId,
                            isPrivate = true,
                            itemType = entity.itemType,
                            encryptedTitle = entity.encryptedTitle,
                            encryptedPayload = entity.encryptedPayload,
                            createdByUid = entity.createdByUid,
                            createdByName = entity.createdByName,
                            hasAttachment = entity.hasAttachment,
                            attachmentType = entity.attachmentType,
                            updatedAt = Timestamp(entity.updatedAtMs / 1000, 0)
                        )
                    })
                }
        )
    }

    suspend fun saveVaultItem(item: VaultItem): Result<Unit> {
        val uid = requireUserId()
        val docId = if (item.id.isBlank()) "item_" + System.currentTimeMillis().toString(36) + "_" + (100..999).random() else item.id
        val updatedItem = item.copy(id = docId, createdByUid = uid)

        val docRef = if (item.isPrivate) {
            db.collection("users").document(uid).collection("private_items").document(docId)
        } else {
            db.collection("families").document(item.familyId).collection("vault_items").document(docId)
        }

        val payload = mapOf(
            "id" to updatedItem.id,
            "familyId" to updatedItem.familyId,
            "isPrivate" to updatedItem.isPrivate,
            "itemType" to updatedItem.itemType,
            "encryptedTitle" to updatedItem.encryptedTitle,
            "encryptedPayload" to updatedItem.encryptedPayload,
            "createdByUid" to updatedItem.createdByUid,
            "createdByName" to updatedItem.createdByName,
            "memberUids" to updatedItem.memberUids,
            "hasAttachment" to updatedItem.hasAttachment,
            "attachmentType" to updatedItem.attachmentType,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            // Cache locally immediately
            database?.vaultDao()?.insertItem(
                LocalVaultItemEntity(
                    id = updatedItem.id,
                    familyId = updatedItem.familyId,
                    isPrivate = updatedItem.isPrivate,
                    itemType = updatedItem.itemType,
                    encryptedTitle = updatedItem.encryptedTitle,
                    encryptedPayload = updatedItem.encryptedPayload,
                    createdByUid = updatedItem.createdByUid,
                    createdByName = updatedItem.createdByName,
                    memberUidsJson = JSONArray(updatedItem.memberUids).toString(),
                    hasAttachment = updatedItem.hasAttachment,
                    attachmentType = updatedItem.attachmentType,
                    updatedAtMs = System.currentTimeMillis(),
                    isSynced = true
                )
            )
            if (updatedItem.familyId.isNotBlank()) {
                rtdbRepo.updateLastSyncTimestamp(updatedItem.familyId, uid)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteVaultItem(familyId: String, itemId: String, isPrivate: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docRef = if (isPrivate) {
            db.collection("users").document(uid).collection("private_items").document(itemId)
        } else {
            db.collection("families").document(familyId).collection("vault_items").document(itemId)
        }

        return try {
            docRef.delete().await()
            database?.vaultDao()?.deleteItem(itemId)
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observePasswordHistory(familyId: String): Flow<List<PasswordHistory>> = flow {
        if (familyId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val uid = requireUserId()
        val path = "families/$familyId/password_history"
        emitAll(
            db.collection("families").document(familyId).collection("password_history")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    val list = snapshot.toObjects(PasswordHistory::class.java)
                    if (database != null) {
                        repositoryScope.launch {
                            val entities = list.map { hist ->
                                LocalPasswordHistoryEntity(
                                    id = hist.id,
                                    familyId = hist.familyId,
                                    encryptedPassword = hist.encryptedPassword,
                                    length = hist.length,
                                    optionsSummary = hist.optionsSummary,
                                    generatedByUid = hist.generatedByUid,
                                    generatedByName = hist.generatedByName,
                                    memberUidsJson = JSONArray(hist.memberUids).toString(),
                                    createdAtMs = hist.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            }
                            database.vaultDao().insertPasswordHistory(entities)
                        }
                    }
                    list
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    val locals = database?.vaultDao()?.getPasswordHistoryOnce(familyId) ?: emptyList()
                    emit(locals.map { l ->
                        PasswordHistory(
                            id = l.id,
                            familyId = l.familyId,
                            encryptedPassword = l.encryptedPassword,
                            length = l.length,
                            optionsSummary = l.optionsSummary,
                            generatedByUid = l.generatedByUid,
                            generatedByName = l.generatedByName,
                            createdAt = Timestamp(l.createdAtMs / 1000, 0)
                        )
                    })
                }
        )
    }

    suspend fun addPasswordHistory(history: PasswordHistory): Result<Unit> {
        val uid = requireUserId()
        val docId = "hist_" + System.currentTimeMillis().toString(36) + "_" + (100..999).random()
        val docRef = db.collection("families").document(history.familyId).collection("password_history").document(docId)

        val payload = mapOf(
            "id" to docId,
            "familyId" to history.familyId,
            "encryptedPassword" to history.encryptedPassword,
            "length" to history.length,
            "optionsSummary" to history.optionsSummary,
            "generatedByUid" to uid,
            "generatedByName" to history.generatedByName,
            "memberUids" to history.memberUids,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            docRef.set(payload).await()
            database?.vaultDao()?.insertPasswordHistoryItem(
                LocalPasswordHistoryEntity(
                    id = docId,
                    familyId = history.familyId,
                    encryptedPassword = history.encryptedPassword,
                    length = history.length,
                    optionsSummary = history.optionsSummary,
                    generatedByUid = uid,
                    generatedByName = history.generatedByName,
                    memberUidsJson = JSONArray(history.memberUids).toString(),
                    createdAtMs = System.currentTimeMillis()
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        val uid = requireUserId()
        // 1. Save user info to Realtime Database (RTDB) as requested
        rtdbRepo.saveUserInfo(
            uid = uid,
            displayName = profile.displayName,
            email = profile.email,
            familyId = profile.familyId
        )

        // 2. Also persist in Firestore
        val docRef = db.collection("users").document(uid)
        val payload = mapOf(
            "userId" to uid,
            "displayName" to profile.displayName,
            "email" to profile.email,
            "familyId" to profile.familyId,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            rtdbRepo.observeUserInfo(userId).map { rtdbProfile ->
                rtdbProfile ?: run {
                    try {
                        val snapshot = db.collection("users").document(userId).get().await()
                        if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        )
    }

    suspend fun deleteUserRemoteData(uid: String, familyId: String? = null) {
        if (uid.isBlank()) return
        try {
            // 1. Delete private items
            val privateItems = db.collection("users").document(uid).collection("private_items").get().await()
            for (doc in privateItems.documents) {
                try { doc.reference.delete().await() } catch (_: Exception) {}
            }

            // 2. Delete user profile document
            try {
                db.collection("users").document(uid).delete().await()
            } catch (_: Exception) {}

            // 3. Clean up associated family vaults
            val familyIdsToCheck = mutableSetOf<String>()
            if (!familyId.isNullOrBlank()) familyIdsToCheck.add(familyId)

            try {
                val memberFamilies = db.collection("families").whereArrayContains("memberUids", uid).get().await()
                for (doc in memberFamilies.documents) {
                    familyIdsToCheck.add(doc.id)
                }
            } catch (_: Exception) {}

            for (famId in familyIdsToCheck) {
                try {
                    val famDoc = db.collection("families").document(famId).get().await()
                    if (famDoc.exists()) {
                        val createdBy = famDoc.getString("createdBy") ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val memberUids = (famDoc.get("memberUids") as? List<String>) ?: emptyList()
                        val updatedMembers = memberUids.filter { it != uid }

                        // Delete items created by this user
                        val items = db.collection("families").document(famId).collection("vault_items")
                            .whereEqualTo("createdByUid", uid).get().await()
                        for (itemDoc in items.documents) {
                            try { itemDoc.reference.delete().await() } catch (_: Exception) {}
                        }

                        val histories = db.collection("families").document(famId).collection("password_history")
                            .whereEqualTo("generatedByUid", uid).get().await()
                        for (histDoc in histories.documents) {
                            try { histDoc.reference.delete().await() } catch (_: Exception) {}
                        }

                        if (createdBy == uid || updatedMembers.isEmpty()) {
                            // Delete all items in family vault before deleting family doc
                            val allItems = db.collection("families").document(famId).collection("vault_items").get().await()
                            for (itemDoc in allItems.documents) {
                                try { itemDoc.reference.delete().await() } catch (_: Exception) {}
                            }
                            val allHistories = db.collection("families").document(famId).collection("password_history").get().await()
                            for (histDoc in allHistories.documents) {
                                try { histDoc.reference.delete().await() } catch (_: Exception) {}
                            }
                            db.collection("families").document(famId).delete().await()
                        } else {
                            db.collection("families").document(famId).update(
                                mapOf(
                                    "memberUids" to updatedMembers,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
