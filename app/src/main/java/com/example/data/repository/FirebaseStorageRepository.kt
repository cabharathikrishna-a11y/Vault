package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class FirebaseStorageRepository(
    storageUrl: String = "gs://familyvault2007.firebasestorage.app"
) {
    private val storage = Firebase.storage(storageUrl)
    private val TAG = "FirebaseStorageRepo"

    companion object {
        const val MAX_SINGLE_FILE_SIZE_BYTES: Long = 25 * 1024 * 1024 // 25 MB max per upload
        const val MAX_FREE_TIER_TOTAL_STORAGE_BYTES: Long = 5L * 1024L * 1024L * 1024L // 5 GB limit
    }

    suspend fun uploadFileBytes(
        storagePath: String,
        data: ByteArray,
        mimeType: String = "application/octet-stream"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (data.size > MAX_SINGLE_FILE_SIZE_BYTES) {
                return@withContext Result.failure(
                    IllegalArgumentException("File size (${data.size / (1024 * 1024)}MB) exceeds maximum single file limit of 25MB for free storage quota.")
                )
            }
            val ref = storage.reference.child(storagePath)
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(mimeType)
                .build()

            ref.putBytes(data, metadata).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Log.d(TAG, "File uploaded successfully to $storagePath. Download URL: $downloadUrl")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed uploading file bytes to Firebase Storage: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadFileUri(
        context: Context,
        storagePath: String,
        fileUri: Uri,
        mimeType: String = "application/octet-stream"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val fileSize = contentResolver.openAssetFileDescriptor(fileUri, "r")?.use { it.length } ?: -1L
            if (fileSize > MAX_SINGLE_FILE_SIZE_BYTES) {
                return@withContext Result.failure(
                    IllegalArgumentException("File size exceeds maximum allowed single file upload limit (25MB) to preserve free tier quota.")
                )
            }

            val ref = storage.reference.child(storagePath)
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType(mimeType)
                .build()

            ref.putFile(fileUri, metadata).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Log.d(TAG, "File uploaded successfully from URI. Download URL: $downloadUrl")
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed uploading file URI to Firebase Storage: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun downloadFileBytes(storageUrlOrPath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val ref = if (storageUrlOrPath.startsWith("http://") || storageUrlOrPath.startsWith("https://") || storageUrlOrPath.startsWith("gs://")) {
                storage.getReferenceFromUrl(storageUrlOrPath)
            } else {
                storage.reference.child(storageUrlOrPath)
            }

            // Max download 15MB in memory
            val maxBytes: Long = 15 * 1024 * 1024
            val bytes = ref.getBytes(maxBytes).await()
            Result.success(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading file from Firebase Storage: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteFile(storageUrlOrPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ref = if (storageUrlOrPath.startsWith("http://") || storageUrlOrPath.startsWith("https://") || storageUrlOrPath.startsWith("gs://")) {
                storage.getReferenceFromUrl(storageUrlOrPath)
            } else {
                storage.reference.child(storageUrlOrPath)
            }
            ref.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting file from Firebase Storage: ${e.message}", e)
            Result.failure(e)
        }
    }
}
