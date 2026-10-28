package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val releaseDate: String = "",
    val forceUpdate: Boolean = false,
    val minSupportedVersion: Int = 1
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class Available(val info: AppUpdateInfo) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

class AppUpdateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("litre_ledger_update_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_UPDATE_URL = "https://raw.githubusercontent.com/aistudio-build/litre-ledger-releases/main/version.json"
        const val PREF_UPDATE_URL = "update_manifest_url"
        const val PREF_AUTO_CHECK = "auto_check_updates"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _updateManifestUrl = MutableStateFlow(
        prefs.getString(PREF_UPDATE_URL, DEFAULT_UPDATE_URL) ?: DEFAULT_UPDATE_URL
    )
    val updateManifestUrl: StateFlow<String> = _updateManifestUrl.asStateFlow()

    private val _autoCheck = MutableStateFlow(
        prefs.getBoolean(PREF_AUTO_CHECK, true)
    )
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    val currentVersionCode: Int = BuildConfig.VERSION_CODE
    val currentVersionName: String = BuildConfig.VERSION_NAME

    fun setUpdateManifestUrl(url: String) {
        val trimmed = url.trim()
        _updateManifestUrl.value = trimmed
        prefs.edit().putString(PREF_UPDATE_URL, trimmed).apply()
    }

    fun setAutoCheck(enabled: Boolean) {
        _autoCheck.value = enabled
        prefs.edit().putBoolean(PREF_AUTO_CHECK, enabled).apply()
    }

    fun dismissUpdate() {
        _updateStatus.value = UpdateStatus.Idle
    }

    suspend fun checkForUpdates(customUrl: String? = null): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val targetUrl = customUrl?.trim()?.ifEmpty { null } ?: _updateManifestUrl.value
        _updateStatus.value = UpdateStatus.Checking

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", "LitreLedger-Android/${BuildConfig.VERSION_NAME}")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = "Server returned code ${response.code}: ${response.message}"
                _updateStatus.value = UpdateStatus.Error(errorMsg)
                return@withContext null
            }

            val bodyString = response.body?.string()
            if (bodyString.isNullOrBlank()) {
                _updateStatus.value = UpdateStatus.Error("Empty response from update server")
                return@withContext null
            }

            val json = JSONObject(bodyString)
            val remoteVersionCode = json.optInt("versionCode", 1)
            val remoteVersionName = json.optString("versionName", "1.0.0")
            val apkUrl = json.optString("apkUrl", "")
            val releaseNotes = json.optString("releaseNotes", "New improvements and bug fixes.")
            val releaseDate = json.optString("releaseDate", "")
            val forceUpdate = json.optBoolean("forceUpdate", false)
            val minSupportedVersion = json.optInt("minSupportedVersion", 1)

            val updateInfo = AppUpdateInfo(
                versionCode = remoteVersionCode,
                versionName = remoteVersionName,
                apkUrl = apkUrl,
                releaseNotes = releaseNotes,
                releaseDate = releaseDate,
                forceUpdate = forceUpdate,
                minSupportedVersion = minSupportedVersion
            )

            if (remoteVersionCode > currentVersionCode) {
                _updateStatus.value = UpdateStatus.Available(updateInfo)
                updateInfo
            } else {
                _updateStatus.value = UpdateStatus.UpToDate
                null
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Failed to connect to update server"
            _updateStatus.value = UpdateStatus.Error(errorMsg)
            null
        }
    }

    suspend fun downloadAndInstallApk(apkUrl: String) = withContext(Dispatchers.IO) {
        try {
            _updateStatus.value = UpdateStatus.Downloading(progressPercent = 0, bytesDownloaded = 0, totalBytes = 0)

            val request = Request.Builder()
                .url(apkUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _updateStatus.value = UpdateStatus.Error("Failed to download APK: HTTP ${response.code}")
                return@withContext
            }

            val body = response.body ?: throw IllegalStateException("Response body is empty")
            val contentLength = body.contentLength()

            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val destinationFile = File(downloadsDir, "litre_ledger_update.apk")
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                inputStream = body.byteStream()
                outputStream = FileOutputStream(destinationFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            _updateStatus.value = UpdateStatus.Downloading(
                                progressPercent = progress,
                                bytesDownloaded = totalBytesRead,
                                totalBytes = contentLength
                            )
                        }
                    }
                }
                outputStream.flush()
            } finally {
                inputStream?.close()
                outputStream?.close()
            }

            _updateStatus.value = UpdateStatus.ReadyToInstall(destinationFile)

            // Trigger install on Main thread
            withContext(Dispatchers.Main) {
                promptApkInstall(destinationFile)
            }
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error("Download failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    fun promptApkInstall(apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(manageIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error("Failed to launch installer: ${e.localizedMessage}")
        }
    }
}
