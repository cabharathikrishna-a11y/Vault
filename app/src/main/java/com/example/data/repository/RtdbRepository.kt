package com.example.data.repository

import android.util.Log
import com.example.data.model.UserProfile
import com.example.update.AppUpdateInfo
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RtdbRepository(
    rtdbUrl: String = "https://familyvault2007-default-rtdb.asia-southeast1.firebasedatabase.app"
) {
    private val db = Firebase.database(rtdbUrl)
    private val TAG = "RtdbRepository"

    suspend fun saveUserInfo(
        uid: String,
        displayName: String,
        email: String,
        familyId: String
    ): Result<Unit> {
        if (uid.isBlank()) return Result.failure(IllegalArgumentException("User ID is required"))
        return try {
            val userRef = db.getReference("users").child(uid)
            val updates = mapOf(
                "userId" to uid,
                "displayName" to displayName,
                "email" to email,
                "familyId" to familyId,
                "lastSeenMs" to System.currentTimeMillis(),
                "lastUpdatedMs" to System.currentTimeMillis()
            )
            userRef.updateChildren(updates).await()
            Log.d(TAG, "User info saved in RTDB for $uid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user info in RTDB: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun observeUserInfo(uid: String): Flow<UserProfile?> = callbackFlow {
        if (uid.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val userRef = db.getReference("users").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val displayName = snapshot.child("displayName").getValue(String::class.java) ?: ""
                    val email = snapshot.child("email").getValue(String::class.java) ?: ""
                    val familyId = snapshot.child("familyId").getValue(String::class.java) ?: ""
                    trySend(UserProfile(userId = uid, displayName = displayName, email = email, familyId = familyId))
                } else {
                    trySend(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "observeUserInfo cancelled: ${error.message}")
                trySend(null)
            }
        }

        userRef.addValueEventListener(listener)
        awaitClose { userRef.removeEventListener(listener) }
    }

    suspend fun updateLastSyncTimestamp(familyId: String, updatedByUid: String): Result<Unit> {
        if (familyId.isBlank()) return Result.success(Unit)
        return try {
            val lastUpdatedRef = db.getReference("last_updated").child(familyId)
            val updates = mapOf(
                "lastUpdatedMs" to System.currentTimeMillis(),
                "updatedBy" to updatedByUid
            )
            lastUpdatedRef.setValue(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update last sync timestamp in RTDB: ${e.message}")
            Result.failure(e)
        }
    }

    fun observeLastSyncTimestamp(familyId: String): Flow<Long> = callbackFlow {
        if (familyId.isBlank()) {
            trySend(0L)
            close()
            return@callbackFlow
        }

        val lastUpdatedRef = db.getReference("last_updated").child(familyId).child("lastUpdatedMs")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val timestamp = snapshot.getValue(Long::class.java) ?: 0L
                trySend(timestamp)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(0L)
            }
        }

        lastUpdatedRef.addValueEventListener(listener)
        awaitClose { lastUpdatedRef.removeEventListener(listener) }
    }

    suspend fun getAppUpdateInfo(): Result<AppUpdateInfo> {
        return try {
            val snapshot = db.getReference("app_update").get().await()
            if (snapshot.exists()) {
                val versionCode = (snapshot.child("versionCode").getValue(Long::class.java)
                    ?: snapshot.child("version_code").getValue(Long::class.java) ?: 0L).toInt()
                val versionName = snapshot.child("versionName").getValue(String::class.java)
                    ?: snapshot.child("version_name").getValue(String::class.java) ?: "1.0.0"
                val apkUrl = snapshot.child("apkUrl").getValue(String::class.java)
                    ?: snapshot.child("apk_url").getValue(String::class.java) ?: ""
                val releaseNotes = snapshot.child("releaseNotes").getValue(String::class.java)
                    ?: snapshot.child("notes").getValue(String::class.java) ?: "New release available."
                val forceUpdate = snapshot.child("forceUpdate").getValue(Boolean::class.java) ?: false

                Result.success(
                    AppUpdateInfo(
                        versionCode = versionCode,
                        versionName = versionName,
                        apkUrl = apkUrl,
                        releaseNotes = releaseNotes,
                        forceUpdate = forceUpdate,
                        hasUpdate = false
                    )
                )
            } else {
                Result.failure(Exception("app_update node not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching app_update info from RTDB: ${e.message}")
            Result.failure(e)
        }
    }
}
