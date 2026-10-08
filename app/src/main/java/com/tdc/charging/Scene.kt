package com.tdc.charging

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView

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
    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, demo: String?, bridge: SceneBridge): WebView =
        WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(bridge, "AndroidBattery")
            val query = if (demo != null) "?demo=$demo" else ""
            loadUrl("file:///android_asset/scene.html$query")
        }
}
