package com.theveloper.pixelplay.data.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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

sealed class YtdlnisInstallState {
    object Idle : YtdlnisInstallState()
    object Checking : YtdlnisInstallState()
    data class Downloading(val progress: Float) : YtdlnisInstallState()
    data class ReadyToInstall(val apkFile: File) : YtdlnisInstallState()
    data class Error(val message: String) : YtdlnisInstallState()
}

@Singleton
class InAppYtdlnisInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _installState = MutableStateFlow<YtdlnisInstallState>(YtdlnisInstallState.Idle)
    val installState: StateFlow<YtdlnisInstallState> = _installState.asStateFlow()

    fun resetState() {
        _installState.value = YtdlnisInstallState.Idle
    }

    /**
     * Downloads the latest YTDLnis APK directly within MusicBabe without opening any browser or GitHub.
     */
    fun startInAppDownloadAndInstall() {
        scope.launch {
            _installState.value = YtdlnisInstallState.Checking

            try {
                val apkUrl = fetchLatestYtdlnisApkUrl()
                if (apkUrl.isNullOrBlank()) {
                    _installState.value = YtdlnisInstallState.Error("Nessun APK compatibile trovato nella release di YTDLnis")
                    return@launch
                }

                _installState.value = YtdlnisInstallState.Downloading(0f)
                val downloadResult = downloadApk(apkUrl) { progress ->
                    _installState.value = YtdlnisInstallState.Downloading(progress)
                }

                if (downloadResult.isSuccess) {
                    val file = downloadResult.getOrThrow()
                    _installState.value = YtdlnisInstallState.ReadyToInstall(file)
                    installApk(file)
                } else {
                    _installState.value = YtdlnisInstallState.Error(
                        downloadResult.exceptionOrNull()?.message ?: "Errore durante il download dell'APK"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error downloading and installing YTDLnis in-app")
                _installState.value = YtdlnisInstallState.Error(e.message ?: "Download di YTDLnis non riuscito")
            }
        }
    }

    private suspend fun fetchLatestYtdlnisApkUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/deniscerri/ytdlnis/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "MusicBabe/1.3.0")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(response)
                val assets = root.optJSONArray("assets") ?: return@withContext null

                val is64Bit = Build.SUPPORTED_ABIS.any { it.contains("arm64", ignoreCase = true) }

                var fallbackApkUrl: String? = null
                var arm64ApkUrl: String? = null
                var arm32ApkUrl: String? = null

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")

                    if (name.endsWith(".apk", ignoreCase = true)) {
                        if (name.contains("arm64", ignoreCase = true)) {
                            arm64ApkUrl = downloadUrl
                        } else if (name.contains("armeabi", ignoreCase = true) || name.contains("arm-", ignoreCase = true)) {
                            arm32ApkUrl = downloadUrl
                        } else {
                            fallbackApkUrl = downloadUrl
                        }
                    }
                }

                connection.disconnect()
                return@withContext if (is64Bit && arm64ApkUrl != null) {
                    arm64ApkUrl
                } else if (!is64Bit && arm32ApkUrl != null) {
                    arm32ApkUrl
                } else {
                    fallbackApkUrl ?: arm64ApkUrl ?: arm32ApkUrl
                }
            }
            connection.disconnect()
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to query YTDLnis latest release API")
            null
        }
    }

    private suspend fun downloadApk(downloadUrl: String, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val targetFile = File(context.cacheDir, "ytdlnis_installer.apk")
                if (targetFile.exists()) targetFile.delete()

                val url = URL(downloadUrl)
                var connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("User-Agent", "MusicBabe/1.3.0")

                // Handle redirects
                var redirectCode = connection.responseCode
                var redirects = 0
                while ((redirectCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        redirectCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        redirectCode == 307 || redirectCode == 308) && redirects < 5) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    connection = URL(newUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.setRequestProperty("User-Agent", "MusicBabe/1.3.0")
                    redirectCode = connection.responseCode
                    redirects++
                }

                val totalLength = connection.contentLengthLong
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (totalLength > 0) {
                        val progress = (totalBytesRead.toFloat() / totalLength).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()

                Timber.d("YTDLnis APK downloaded successfully: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                Result.success(targetFile)
            } catch (e: Exception) {
                Timber.e(e, "Error downloading YTDLnis APK")
                Result.failure(e)
            }
        }

    /**
     * Triggers the Android system package installer for the downloaded APK.
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
            Timber.e(e, "Error launching package installer for YTDLnis")
            _installState.value = YtdlnisInstallState.Error("Impossibile avviare l'installazione: ${e.message}")
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
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to open unknown app sources settings")
            }
        }
    }
}
