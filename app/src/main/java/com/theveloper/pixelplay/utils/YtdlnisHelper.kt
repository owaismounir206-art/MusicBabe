package com.theveloper.pixelplay.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import timber.log.Timber

object YtdlnisHelper {
    const val YTDLNIS_PACKAGE_NAME = "com.deniscerri.ytdl"
    const val YTDLNIS_RELEASES_URL = "https://github.com/deniscerri/ytdlnis/releases"

    /**
     * Checks if YTDLnis is installed on the device.
     */
    fun isYtdlnisInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.getPackageInfo(YTDLNIS_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Timber.e(e, "Error checking YTDLnis installation status")
            false
        }
    }

    /**
     * Sends a song search / download request to YTDLnis via Intent.ACTION_SEND.
     * @param context Android context
     * @param artist Artist name
     * @param title Song title
     * @return true if YTDLnis intent was launched, false otherwise
     */
    fun downloadSong(context: Context, artist: String, title: String): Boolean {
        val searchQuery = "ytsearch1:$artist - $title".trim()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/*"
            setPackage(YTDLNIS_PACKAGE_NAME)
            putExtra(Intent.EXTRA_TEXT, searchQuery)
            putExtra("TYPE", "audio")
            putExtra("BACKGROUND", false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.w(e, "Failed to launch YTDLnis directly. Trying chooser fallback.")
            false
        }
    }

    /**
     * Opens GitHub releases page for downloading YTDLnis.
     */
    fun openGitHubReleases(context: Context) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(YTDLNIS_RELEASES_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open browser for YTDLnis URL")
        }
    }

    /**
     * Shares the download search query via standard Android share chooser.
     * Useful when other downloaders (Seal, NewPipe, etc.) are installed.
     */
    fun shareDownloadQuery(context: Context, artist: String, title: String) {
        try {
            val searchQuery = "ytsearch1:$artist - $title".trim()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, searchQuery)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Scarica con...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch share chooser for search query")
        }
    }
}
