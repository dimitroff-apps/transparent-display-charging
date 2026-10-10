package com.tdc.charging

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebView
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.max

/** Full-screen X-ray charging view. Works over the lock screen. */
class ChargingActivity : Activity() {

    private lateinit var web: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var auto = false
    private var lastTap = 0L
    @Volatile private var cutout = ""
    private var loadedUrl = ""
    private var restarts = 0

    private val closeLater = Runnable { finish() }

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!auto) return
            when (i.action) {
                // Opened by the charger: show the discharge flow for a few seconds, then close
                Intent.ACTION_POWER_DISCONNECTED -> handler.postDelayed(closeLater, 6000)
                Intent.ACTION_POWER_CONNECTED -> handler.removeCallbacks(closeLater)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auto = intent.getBooleanExtra(EXTRA_AUTO, false)

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        createScene()
        hideBars()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, power, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun createScene() {
        val bridge = SceneBridge(BatteryReader(this), ::onTap, { cutout }, ::setDim)
        web = Scene.create(this, intent.getStringExtra(EXTRA_DEMO), bridge) { dead ->
            // The renderer died: drop that WebView and start a fresh one (a few times at most)
            handler.post {
                if (isFinishing || ++restarts > 3) { finish(); return@post }
                (dead.parent as? android.view.ViewGroup)?.removeView(dead)
                dead.destroy()
                createScene()
            }
        }
        loadedUrl = Scene.url(this, intent.getStringExtra(EXTRA_DEMO))
        setContentView(web)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        auto = intent.getBooleanExtra(EXTRA_AUTO, false)
        if (auto) handler.removeCallbacks(closeLater)
        reloadIfChanged()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        reloadIfChanged()
        handler.post(fullCheck)
    }

    /** Not visible (screen off, another app on top): stop drawing completely */
    override fun onPause() {
        handler.removeCallbacks(fullCheck)
        web.onPause()
        super.onPause()
    }

    /** Night mode from the page: a dim screen while it shows only the percentage */
    private fun setDim(on: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (on) 0.02f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    /** Fully charged on the real battery: let the screen time out instead of keeping it on all night */
    private val battery by lazy { BatteryReader(this) }
    private val fullCheck = object : Runnable {
        override fun run() {
            val full = intent.getStringExtra(EXTRA_DEMO) == null && battery.isFull()
            if (full) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            handler.postDelayed(this, 30_000)
        }
    }

    /** The screen may still be open from before: show the theme/demo that is selected now. */
    private fun reloadIfChanged() {
        val url = Scene.url(this, intent.getStringExtra(EXTRA_DEMO))
        if (url != loadedUrl) {
            loadedUrl = url
            web.loadUrl(url)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideBars()
            readCutout()
        }
    }

    private fun readCutout() {
        if (Build.VERSION.SDK_INT < 28) return
        val cut = window.decorView.rootWindowInsets?.displayCutout ?: return
        val w = window.decorView.width
        val rect = cut.boundingRects.firstOrNull { it.top < 300 && it.centerX() in (w / 4)..(w * 3 / 4) }
            ?: cut.boundingRects.firstOrNull() ?: return
        val d = resources.displayMetrics.density
        cutout = String.format(
            Locale.US, "{\"x\":%.1f,\"y\":%.1f,\"r\":%.1f}",
            rect.exactCenterX() / d, rect.exactCenterY() / d, max(rect.width(), rect.height()) / 2f / d
        )
    }

    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    /**
     * A double tap closes the view, however it was opened. A single tap only wakes it from
     * night mode (the page handles that), so a brush of the finger does not close it.
     */
    private fun onTap() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTap < 400) finish()
        lastTap = now
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(power) } catch (_: Exception) {}
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTO = "auto"
        const val EXTRA_DEMO = "demo"

        fun intent(ctx: Context, auto: Boolean, demo: String? = null): Intent =
            Intent(ctx, ChargingActivity::class.java).apply {
                putExtra(EXTRA_AUTO, auto)
                if (demo != null) putExtra(EXTRA_DEMO, demo)
            }
    }
}
