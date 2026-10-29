package com.theveloper.pixelplay.presentation.floating

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The sung line in a small bubble over other apps; drag it anywhere, double tap to close. */
class FloatingLyricsService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var view: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        val wm = getSystemService(WindowManager::class.java)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 200 }
        val text = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(36, 20, 36, 20)
            maxWidth = (resources.displayMetrics.widthPixels * 0.85f).toInt()
            background = GradientDrawable().apply { cornerRadius = 48f; setColor(0xCC000000.toInt()) }
            text = "♪"
        }
        var startY = 0; var touchY = 0f; var startX = 0; var touchX = 0f; var lastTap = 0L
        text.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (System.currentTimeMillis() - lastTap < 300) { stopSelf(); return@setOnTouchListener true }
                    lastTap = System.currentTimeMillis()
                    startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt(); params.y = startY + (e.rawY - touchY).toInt()
                    runCatching { wm.updateViewLayout(text, params) }
                }
            }
            true
        }
        runCatching { wm.addView(text, params) }.onSuccess { view = text }.onFailure { stopSelf() }
        running.value = true
        scope.launch { line.collect { view?.text = it.ifBlank { "♪" } } }
    }

    override fun onDestroy() {
        running.value = false
        scope.cancel()
        view?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        super.onDestroy()
    }

    companion object {
        val line = MutableStateFlow("")
        val running = MutableStateFlow(false)
    }
}
