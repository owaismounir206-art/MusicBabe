package com.theveloper.pixelplay.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A lyric line shared as an image (Scena style): cover, the line large, title and artist below.
 * Sized for stories (1080 × 1350) and sent through the system share sheet.
 */
object LyricShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val MARGIN = 96f
    private const val COVER = 320

    suspend fun share(
        context: Context,
        song: Song,
        lineText: String,
        background: Int,
        ink: Int,
        accent: Int
    ) {
        val bitmap = render(context, song, lineText, background, ink, accent)
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "verse.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "“$lineText” — ${song.title}, ${song.displayArtist}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.lyrics_share_line)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private suspend fun render(
        context: Context,
        song: Song,
        lineText: String,
        background: Int,
        ink: Int,
        accent: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val cover = song.albumArtUriString?.let { loadCover(context, it) }
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)

        val coverRect = RectF(MARGIN, MARGIN, MARGIN + COVER, MARGIN + COVER)
        val clip = Path().apply { addRoundRect(coverRect, 48f, 48f, Path.Direction.CW) }
        if (cover != null) {
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(cover, null, coverRect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            canvas.drawPath(clip, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        }

        val bold = Typeface.create(Typeface.DEFAULT, 800, false)
        val linePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = bold
            textSize = when {
                lineText.length < 40 -> 96f
                lineText.length < 90 -> 76f
                else -> 60f
            }
        }
        val textWidth = (WIDTH - 2 * MARGIN).toInt()
        val lineLayout = StaticLayout.Builder.obtain(lineText, 0, lineText.length, linePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setMaxLines(7)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        val lineTop = MARGIN + COVER + 96f
        canvas.save()
        canvas.translate(MARGIN, lineTop)
        lineLayout.draw(canvas)
        canvas.restore()

        val accentBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val footerTop = HEIGHT - MARGIN - 120f
        canvas.drawRoundRect(RectF(MARGIN, footerTop, MARGIN + 96f, footerTop + 12f), 6f, 6f, accentBar)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Typeface.create(Typeface.DEFAULT, 700, false)
            textSize = 44f
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            alpha = 170
            textSize = 38f
        }
        val maxWidth = WIDTH - 2 * MARGIN
        canvas.drawText(ellipsize(song.title, titlePaint, maxWidth), MARGIN, footerTop + 72f, titlePaint)
        canvas.drawText(ellipsize(song.displayArtist, artistPaint, maxWidth), MARGIN, footerTop + 120f, artistPaint)
        bitmap
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String =
        android.text.TextUtils.ellipsize(text, paint, width, android.text.TextUtils.TruncateAt.END).toString()

    private suspend fun loadCover(context: Context, uri: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(COVER * 2)
            .allowHardware(false)
            .build()
        (ImageLoader(context).execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }.getOrNull()
}
