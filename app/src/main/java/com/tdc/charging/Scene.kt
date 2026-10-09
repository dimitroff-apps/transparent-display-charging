package com.tdc.charging

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/** Bridge the page calls as window.AndroidBattery */
class SceneBridge(
    private val reader: BatteryReader,
    private val onTapAction: () -> Unit,
    private val cutoutJson: () -> String
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun read(): String = reader.snapshot()

    @JavascriptInterface
    fun onTap() {
        main.post { onTapAction() }
    }

    @JavascriptInterface
    fun cutout(): String = cutoutJson()
}

object Scene {
    /**
     * onGone runs when the page's renderer process dies (out of memory, GPU driver crash...).
     * Handling it keeps the app alive; without it Android closes the whole app.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, demo: String?, bridge: SceneBridge, onGone: (WebView) -> Unit = {}): WebView =
        WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(bridge, "AndroidBattery")
            webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Log.w("TDC", "Scene renderer gone (crash=${detail.didCrash()}), restarting it")
                    onGone(view)
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    Log.i("TDC", "scene: ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                    return true
                }
            }
            loadUrl(url(context, demo))
        }

    /** Page address with the theme and colours that are selected right now */
    fun url(context: Context, demo: String?): String {
        val theme = ThemeCatalog.current(context).id
        val demoPart = if (demo != null) "&demo=$demo" else ""
        val colors = Prefs.Slot.values().joinToString("") { slot ->
            Prefs.color(context, slot)?.let { "&${slot.param}=%06x".format(it and 0xFFFFFF) } ?: ""
        }
        val style = if (Prefs.style(context) == "photo") "&style=photo" else ""
        return "file:///android_asset/scene.html?theme=$theme$demoPart$colors$style"
    }
}
