package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.AllowedFamilyMembers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val releaseNotes: String = "Performance updates and enhancements",
    val forceUpdate: Boolean = false,
    val hasUpdate: Boolean = false
)

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    
    // Default RTDB endpoint for the project
    const val DEFAULT_RTDB_UPDATE_URL = "https://gen-lang-client-0143157303-default-rtdb.firebaseio.com/app_update.json"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getCurrentVersionCode(context: Context): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    suspend fun checkForUpdate(
        context: Context,
        customRtdbUrl: String? = null
    ): AppUpdateInfo = withContext(Dispatchers.IO) {
        val currentCode = getCurrentVersionCode(context)
        val url = if (!customRtdbUrl.isNullOrBlank()) customRtdbUrl else DEFAULT_RTDB_UPDATE_URL

        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "RTDB update check failed HTTP ${response.code}")
                    return@withContext AppUpdateInfo()
                }

                val bodyStr = response.body?.string() ?: return@withContext AppUpdateInfo()
                if (bodyStr.trim() == "null" || bodyStr.isBlank()) {
                    return@withContext AppUpdateInfo()
                }

                val json = JSONObject(bodyStr)
                val remoteCode = json.optInt("versionCode", json.optInt("version_code", 0))
                val remoteName = json.optString("versionName", json.optString("version_name", "1.0.1"))
                val apkUrl = json.optString("apkUrl", json.optString("apk_url", ""))
                val notes = json.optString("releaseNotes", json.optString("notes", "New APK release detected from GitHub / RTDB."))
                val force = json.optBoolean("forceUpdate", false)

                val isNewer = remoteCode > currentCode

                AppUpdateInfo(
                    versionCode = remoteCode,
                    versionName = remoteName,
                    apkUrl = apkUrl,
                    releaseNotes = notes,
                    forceUpdate = force,
                    hasUpdate = isNewer
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking update from RTDB: ${e.message}", e)
            AppUpdateInfo()
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (apkUrl.isBlank()) {
            withContext(Dispatchers.Main) { onError("Invalid APK URL") }
            return@withContext
        }

        try {
            val request = Request.Builder()
                .url(apkUrl)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) { onError("Failed to download APK: HTTP ${response.code}") }
                    return@withContext
                }

                val body = response.body ?: run {
                    withContext(Dispatchers.Main) { onError("Empty response body from APK URL") }
                    return@withContext
                }

                val totalBytes = body.contentLength()
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val apkFile = File(cacheDir, "update_family_vault.apk")

                body.byteStream().use { inputStream ->
                    FileOutputStream(apkFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalBytes > 0) {
                                val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    onProgress(1.0f)
                    installApk(context, apkFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading/installing APK: ${e.message}", e)
            withContext(Dispatchers.Main) { onError("Download error: ${e.localizedMessage ?: "Unknown error"}") }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            // Check for Unknown App Install permission on API 26+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Install intent failed: ${e.message}", e)
        }
    }
}
