package com.tdc.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the charger watcher after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.auto(context)) {
            try { ChargeWatchService.start(context) } catch (_: Exception) {}
        }
    }
}
