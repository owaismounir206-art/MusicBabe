package com.theveloper.pixelplay.presentation.floating

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Lyrics on a TV or monitor connected as a second screen (HDMI, wireless display): the sung
 * line large in the middle, the next one below.
 */
class TvLyricsPresentation(context: Context, display: Display) : Presentation(context, display) {
    private lateinit var current: TextView
    private lateinit var next: TextView
    private lateinit var title: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun text(size: Float, color: Int) = TextView(context).apply {
            setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, size); gravity = Gravity.CENTER; setPadding(80, 24, 80, 24)
        }
        title = text(22f, 0x99FFFFFF.toInt())
        current = text(56f, Color.WHITE)
        next = text(34f, 0x66FFFFFF)
        setContentView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.BLACK)
            addView(title); addView(current); addView(next)
        })
    }

    fun show(song: String, line: String, upcoming: String) {
        if (!::current.isInitialized) return
        title.text = song; current.text = line.ifBlank { "♪" }; next.text = upcoming
    }

    companion object {
        /** The first presentation display (a connected TV), if any. */
        fun findDisplay(context: Context): Display? =
            context.getSystemService(DisplayManager::class.java)
                .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
    }
}
