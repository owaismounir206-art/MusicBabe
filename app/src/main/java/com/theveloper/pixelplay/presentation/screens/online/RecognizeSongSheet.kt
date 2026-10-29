package com.theveloper.pixelplay.presentation.screens.online

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.GeminiQuick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "What's playing?": records 10 seconds with the microphone and lets Gemini name the song (the
 * user's key), then searches it online.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecognizeSongSheet(gemini: GeminiQuick, onFound: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val listening = stringResource(R.string.recognize_listening)
    val thinking = stringResource(R.string.recognize_thinking)
    val failed = stringResource(R.string.recognize_failed)
    val noKey = stringResource(R.string.recognize_no_key)

    LaunchedEffect(granted) {
        if (!granted) { permission.launch(Manifest.permission.RECORD_AUDIO); return@LaunchedEffect }
        if (!gemini.hasKey()) { status = noKey; return@LaunchedEffect }
        status = listening
        val clip = recordClip(context) ?: run { status = failed; return@LaunchedEffect }
        status = thinking
        val answer = gemini.ask(
            "Identify the song playing in this recording. Answer only as: Artist - Title. If you cannot tell, answer: UNKNOWN",
            clip
        ).getOrNull()?.lines()?.firstOrNull()?.trim()
        if (answer.isNullOrBlank() || answer.contains("UNKNOWN", true)) status = failed else onFound(answer)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(stringResource(R.string.recognize_title), style = MaterialTheme.typography.titleLarge)
            if (status == listening || status == thinking) LoadingIndicator()
            status?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private suspend fun recordClip(context: Context): ByteArray? = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, "recognize.m4a")
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
    try {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioSamplingRate(44_100)
        recorder.setAudioEncodingBitRate(128_000)
        recorder.setOutputFile(file.absolutePath)
        recorder.prepare()
        recorder.start()
        delay(10_000)
        recorder.stop()
        file.readBytes()
    } catch (e: Exception) {
        null
    } finally {
        runCatching { recorder.release() }
        file.delete()
    }
}
