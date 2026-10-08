package com.tdc.charging

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings screen: start the view by hand, turn on the charger trigger, permissions. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var autoSwitch: Switch

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }

        col.addView(text(getString(R.string.app_name), 24f, bold = true))
        col.addView(text("Рентгенов изглед на зареждането през дисплея.", 15f, muted = true))

        col.addView(header("Ръчно"))
        col.addView(button("Покажи анимацията") { startActivity(ChargingActivity.intent(this, auto = false)) })
        col.addView(button("Демо: зареждане") { startActivity(ChargingActivity.intent(this, false, "charge")) })
        col.addView(button("Демо: разреждане") { startActivity(ChargingActivity.intent(this, false, "drain")) })
        col.addView(text("Двойно докосване затваря анимацията.", 13f, muted = true))

        col.addView(header("Автоматично при включване на кабел"))
        autoSwitch = Switch(this).apply {
            text = "Анимация при включване на зарядното"
            textSize = 16f
            setPadding(0, dp(8), 0, dp(8))
            isChecked = Prefs.auto(this@MainActivity)
            setOnCheckedChangeListener { _, on ->
                Prefs.setAuto(this@MainActivity, on)
                if (on) {
                    askPermissions()
                    ChargeWatchService.start(this@MainActivity)
                } else {
                    ChargeWatchService.stop(this@MainActivity)
                }
                refresh()
            }
        }
        col.addView(autoSwitch)
        col.addView(button("1. Разреши „Показване върху други приложения“") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(button("2. Xiaomi: Автостарт") { openAutostart() })
        col.addView(button("3. Настройки на приложението") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        col.addView(text(
            "В настройките на приложението на Xiaomi включи:\n" +
                "• Други разрешения → „Показване на изскачащи прозорци при работа на заден план“ и „Показване на заключен екран“\n" +
                "• Пестене на батерия → „Без ограничения“",
            13f, muted = true
        ))

        col.addView(header("Винаги включен екран"))
        col.addView(text(
            "Xiaomi не позволява чужди приложения да заменят Always-on дисплея. Вместо това можеш да:\n" +
                "• избереш приложението за скрийнсейвър при зареждане (ако телефонът има тази настройка);\n" +
                "• пуснеш анимацията ръчно — екранът остава включен, докато не я затвориш.",
            13f, muted = true
        ))
        col.addView(button("Настройки на скрийнсейвъра") { openDreamSettings() })

        status = text("", 14f)
        status.setPadding(0, dp(20), 0, 0)
        col.addView(status)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(5, 7, 12))
            addView(col)
        })
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.auto(this)) {
            try { ChargeWatchService.start(this) } catch (_: Exception) {}
        }
        refresh()
    }

    private fun refresh() {
        val overlay = Settings.canDrawOverlays(this)
        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        status.text = buildString {
            append(if (Prefs.auto(this@MainActivity)) "✅ Автоматичният режим е включен\n" else "⚪ Автоматичният режим е изключен\n")
            append(if (overlay) "✅ Показване върху други приложения\n" else "❌ Показване върху други приложения (нужно за автоматичния режим)\n")
            append(if (notif) "✅ Известия" else "❌ Известия")
        }
    }

    private fun askPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разреши „Показване върху други приложения“", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun openAutostart() {
        val miui = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        )
        try {
            startActivity(miui)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun openDreamSettings() {
        try {
            startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Този телефон няма настройка за скрийнсейвър", Toast.LENGTH_LONG).show()
        }
    }

    private fun text(s: String, size: Float, bold: Boolean = false, muted: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(if (muted) Color.argb(170, 233, 241, 255) else Color.rgb(233, 241, 255))
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun header(s: String) = text(s, 18f, bold = true).apply {
        setTextColor(Color.rgb(125, 255, 165))
        setPadding(0, dp(24), 0, dp(6))
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) }
    }
}
