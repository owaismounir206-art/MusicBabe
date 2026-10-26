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
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the Wrapped poster says, already localized by the caller. */
data class WrappedShareContent(
    val eyebrow: String,
    val title: String,
    /** Label and value pairs, top to bottom. */
    val rows: List<Pair<String, String>>,
    val coverUri: String?,
    val shareText: String,
    val chooserTitle: String
)

/** Colours of the poster, taken from the Scena roles of the screen that shares it. */
data class WrappedShareColors(
    val background: Int,
    val ink: Int,
    val ink2: Int,
    val accent: Int,
    val onAccent: Int
)

/**
 * The Wrapped summary as a story-sized image (1080 × 1920), sent through the system share sheet.
 * Same approach as [LyricShareCard]: drawn on a plain Canvas, so it needs no screenshot of the UI.
 */
object WrappedShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val MARGIN = 96f
    private const val COVER = 560

    suspend fun share(context: Context, content: WrappedShareContent, colors: WrappedShareColors) {
        val bitmap = render(context, content, colors)
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "wrapped.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, content.shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, content.chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun render(
        context: Context,
        content: WrappedShareContent,
        colors: WrappedShareColors
    ): Bitmap = withContext(Dispatchers.Default) {
        val cover = content.coverUri?.let { loadCover(context, it) }
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colors.background)
        val textWidth = WIDTH - 2 * MARGIN

        val eyebrowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.ink2
            typeface = Typeface.create(Typeface.DEFAULT, 700, false)
            textSize = 40f
            letterSpacing = 0.08f
        }
        canvas.drawText(ellipsize(content.eyebrow.uppercase(), eyebrowPaint, textWidth), MARGIN, MARGIN + 40f, eyebrowPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.ink
            typeface = Typeface.create(Typeface.DEFAULT, 800, false)
            textSize = if (content.title.length < 22) 104f else 84f
        }
        val titleLayout = StaticLayout.Builder.obtain(content.title, 0, content.title.length, titlePaint, textWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setMaxLines(3)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        val titleTop = MARGIN + 80f
        canvas.save()
        canvas.translate(MARGIN, titleTop)
        titleLayout.draw(canvas)
        canvas.restore()

        val coverTop = titleTop + titleLayout.height + 72f
        val coverRect = RectF(MARGIN, coverTop, MARGIN + COVER, coverTop + COVER)
        val clip = Path().apply { addRoundRect(coverRect, 72f, 72f, Path.Direction.CW) }
        if (cover != null) {
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(cover, null, coverRect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            canvas.drawPath(clip, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.accent })
        }

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.ink2
            typeface = Typeface.create(Typeface.DEFAULT, 600, false)
            textSize = 36f
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.ink
            typeface = Typeface.create(Typeface.DEFAULT, 800, false)
            textSize = 60f
        }
        var rowTop = coverRect.bottom + 88f
        val rowHeight = 132f
        content.rows.forEach { (label, value) ->
            if (rowTop + rowHeight > HEIGHT - MARGIN - 40f) return@forEach
            canvas.drawText(ellipsize(label, labelPaint, textWidth), MARGIN, rowTop + 36f, labelPaint)
            canvas.drawText(ellipsize(value, valuePaint, textWidth), MARGIN, rowTop + 104f, valuePaint)
            rowTop += rowHeight
        }

        val accentBar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.accent }
        canvas.drawRoundRect(RectF(MARGIN, HEIGHT - MARGIN - 16f, MARGIN + 160f, HEIGHT - MARGIN), 8f, 8f, accentBar)
        bitmap
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()

    private suspend fun loadCover(context: Context, uri: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(COVER * 2)
            .allowHardware(false)
            .build()
        (ImageLoader(context).execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }.getOrNull()
}
