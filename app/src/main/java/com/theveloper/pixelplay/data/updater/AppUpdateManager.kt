package com.theveloper.pixelplay.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.theveloper.pixelplay.BuildConfig
import com.theveloper.pixelplay.data.network.ensureCleartextAllowed
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _latestRelease = MutableStateFlow<AppReleaseInfo?>(null)
    val latestRelease: StateFlow<AppReleaseInfo?> = _latestRelease.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun openUpdateDialog() {
        if (_latestRelease.value?.isUpdateAvailable == true) {
            _showUpdateDialog.value = true
        }
    }

    /**
     * Checks GitHub for the latest release of MusicBabe.
     */
    suspend fun checkForUpdates(promptIfAvailable: Boolean = false): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        _isChecking.value = true
        try {
            val url = URL("https://api.github.com/repos/owaismounir206-art/MusicBabe/releases/latest")
            val connection = url.ensureCleartextAllowed().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val code = connection.responseCode
            if (code == 200) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val tagName = root.optString("tag_name", "")
                val releaseName = root.optString("name", tagName)
                val body = root.optString("body", "")
                val htmlUrl = root.optString("html_url", "https://github.com/owaismounir206-art/MusicBabe/releases")
                val publishedAt = root.optString("published_at", "")

                val assetsArray = root.optJSONArray("assets")
                var apkUrl: String? = null
                var apkName: String? = null

                if (assetsArray != null) {
                    for (i in 0 until assetsArray.length()) {
                        val asset = assetsArray.getJSONObject(i)
                        val name = asset.optString("name", "")
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            // Prefer arm64-v8a if available, otherwise any apk
                            if (apkUrl == null || name.contains("arm64", ignoreCase = true)) {
                                apkUrl = downloadUrl
                                apkName = name
                            }
                        }
                    }
                }

                val currentVersion = BuildConfig.VERSION_NAME
                val isUpdateAvailable = isVersionNewer(tagName, currentVersion)

                val releaseInfo = AppReleaseInfo(
                    tagName = tagName,
                    versionName = tagName.removePrefix("v"),
                    releaseName = releaseName,
                    releaseNotes = body,
                    htmlUrl = htmlUrl,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName ?: "MusicBabe-update.apk",
                    isUpdateAvailable = isUpdateAvailable,
                    publishedAt = publishedAt
                )

                _latestRelease.value = releaseInfo
                if (isUpdateAvailable && promptIfAvailable) {
                    _showUpdateDialog.value = true
                }

                Timber.d("Update check complete: current=$currentVersion, remote=$tagName, available=$isUpdateAvailable")
                Result.success(releaseInfo)
            } else {
                val err = "GitHub returned status $code"
                Timber.w(err)
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates")
            Result.failure(e)
        } finally {
            _isChecking.value = false
        }
    }

    /**
     * Downloads the APK file and emits download progress.
     */
    fun startDownload(releaseInfo: AppReleaseInfo) {
        val downloadUrl = releaseInfo.apkDownloadUrl ?: return
        managerScope.launch {
            _downloadState.value = UpdateDownloadState.Downloading(0f)
            try {
                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val targetFile = File(updateDir, releaseInfo.apkFileName ?: "MusicBabe-update.apk")
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                val url = URL(downloadUrl)
                val connection = url.ensureCleartextAllowed().openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                // Follow GitHub redirects if needed
                var redirectConnection = connection
                var redirectCode = redirectConnection.responseCode
                var redirectCount = 0
                while ((redirectCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        redirectCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        redirectCode == 307 || redirectCode == 308) && redirectCount < 5) {
                    val newUrl = redirectConnection.getHeaderField("Location")
                    redirectConnection.disconnect()
                    redirectConnection = URL(newUrl).ensureCleartextAllowed().openConnection() as HttpURLConnection
                    redirectConnection.connectTimeout = 15000
                    redirectConnection.readTimeout = 30000
                    redirectCode = redirectConnection.responseCode
                    redirectCount++
                }

                val totalLength = redirectConnection.contentLengthLong
                val inputStream = redirectConnection.inputStream
                val outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalLength > 0) {
                        val progress = (totalBytesRead.toFloat() / totalLength).coerceIn(0f, 1f)
                        _downloadState.value = UpdateDownloadState.Downloading(progress)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                redirectConnection.disconnect()

                _downloadState.value = UpdateDownloadState.ReadyToInstall(targetFile)
                Timber.d("Update downloaded successfully: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            } catch (e: Exception) {
                Timber.e(e, "Error downloading APK update")
                _downloadState.value = UpdateDownloadState.Error(e.message ?: "Download fallito")
            }
        }
    }

    /**
     * Triggers package installer with the downloaded APK.
     */
    fun installApk(apkFile: File) {
        try {
            if (!canRequestPackageInstalls()) {
                openInstallPermissionSettings()
                return
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Timber.e(e, "Error launching package installer")
            _downloadState.value = UpdateDownloadState.Error("Impossibile avviare l'installazione: ${e.message}")
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun isVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val cleanRemote = remoteVersion.trim().removePrefix("v").substringBefore("-")
            val cleanCurrent = currentVersion.trim().removePrefix("v").substringBefore("-")

            val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        } catch (_: Exception) {
            return false
        }
    }
}
