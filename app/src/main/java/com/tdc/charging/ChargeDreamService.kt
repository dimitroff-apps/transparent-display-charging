package com.tdc.charging

import android.service.dreams.DreamService
import android.webkit.WebView

/** Screen saver ("Daydream") while charging: the closest thing to an always-on view that Android allows. */
class ChargeDreamService : DreamService() {
    private var web: WebView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val bridge = SceneBridge(BatteryReader(this), { finish() }) { "" }
        web = Scene.create(this, null, bridge) { finish() }.also { setContentView(it) }
    }

    override fun onDetachedFromWindow() {
        web?.destroy()
        web = null
        super.onDetachedFromWindow()
    }
}
