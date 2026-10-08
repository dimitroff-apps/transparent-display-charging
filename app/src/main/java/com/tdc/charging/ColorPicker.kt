package com.tdc.charging

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Colour mix: hue left to right, pale at the top to full colour at the bottom. Drag a finger to pick. */
class SpectrumView(context: Context, initial: Int, private val onPick: (Int) -> Unit) : View(context) {
    private val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
    private var bitmap: Bitmap? = null
    private val clip = Path()
    private val dp = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * dp; color = Color.WHITE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    val color: Int get() = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        val px = IntArray(w * h)
        val c = FloatArray(3)
        for (y in 0 until h) {
            c[1] = y / (h - 1f)
            c[2] = 1f
            for (x in 0 until w) {
                c[0] = 360f * x / w
                px[y * w + x] = Color.HSVToColor(c)
            }
        }
        bitmap = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        clip.reset()
        clip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 14 * dp, 14 * dp, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(b, 0f, 0f, null)
        canvas.restore()
        val x = hsv[0] / 360f * width
        val y = hsv[1] * height
        dot.color = color
        canvas.drawCircle(x, y, 13 * dp, dot)
        canvas.drawCircle(x, y, 13 * dp, ring)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                hsv[0] = (e.x / width).coerceIn(0f, 0.999f) * 360f
                hsv[1] = (e.y / height).coerceIn(0f, 1f)
                invalidate()
                onPick(color)
            }
        }
        return true
    }
}

object ColorPicker {
    /** Picks a colour for one slot; onDone(null) puts the default look back. */
    fun show(activity: Activity, title: String, current: Int, onDone: (Int?) -> Unit) {
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).toInt()
        val preview = TextView(activity).apply {
            text = "54.123%"
            textSize = 40f
            gravity = Gravity.CENTER
            setTextColor(current)
            setShadowLayer(dp(12).toFloat(), 0f, 0f, current)
            setBackgroundColor(Color.BLACK)
            setPadding(0, dp(10), 0, dp(10))
        }
        val spectrum = SpectrumView(activity, current) { c ->
            preview.setTextColor(c)
            preview.setShadowLayer(dp(12).toFloat(), 0f, 0f, c)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200)).apply { topMargin = dp(14) }
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(preview)
            addView(spectrum)
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Готово") { _, _ -> onDone(spectrum.color) }
            .setNeutralButton("По подразбиране") { _, _ -> onDone(null) }
            .setNegativeButton("Отказ", null)
            .show()
    }
}
