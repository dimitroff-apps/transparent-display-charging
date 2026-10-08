package com.tdc.charging

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
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
    private lateinit var themeText: TextView
    private val colorButtons = mutableMapOf<Prefs.Slot, Button>()
    private lateinit var styleButton: Button

    private val styles = listOf("xray" to "Рентген: телефонът през дисплея", "photo" to "Фото: вътре в батерията (макро)")

    private fun chooseStyle() {
        val checked = styles.indexOfFirst { it.first == Prefs.style(this) }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Стил")
            .setSingleChoiceItems(styles.map { it.second }.toTypedArray(), checked) { d, which ->
                Prefs.setStyle(this, styles[which].first)
                d.dismiss()
                refresh()
                startActivity(ChargingActivity.intent(this, false, "charge"))
            }
            .setNegativeButton("Отказ", null)
            .show()
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }

        col.addView(text(getString(R.string.app_name), 24f, bold = true))
        col.addView(text("Рентгенов изглед на зареждането през дисплея.", 15f, muted = true))

        col.addView(header("Тема"))
        themeText = text("", 15f)
        col.addView(themeText)
        col.addView(button("Избери тема") { chooseTheme() })

        col.addView(header("Стил"))
        styleButton = button("") { chooseStyle() }
        col.addView(styleButton)

        col.addView(header("Цветове"))
        for (slot in Prefs.Slot.values()) {
            val b = button("") { pickColor(slot) }
            colorButtons[slot] = b
            col.addView(b)
        }
        col.addView(text("Плъзни пръст по цветовия микс, за да избереш цвят.", 13f, muted = true))

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

    private fun chooseTheme() {
        val detected = ThemeCatalog.detect(this)
        val autoLabel = "Автоматично (" + (detected?.name ?: ThemeCatalog.universalFor(this).name) + ")"
        val items = listOf(autoLabel) + ThemeCatalog.all.map { "${it.brand}: ${it.name}" }
        val saved = Prefs.theme(this)
        val checked = if (saved == null) 0 else ThemeCatalog.all.indexOfFirst { it.id == saved } + 1
        AlertDialog.Builder(this)
            .setTitle("Тема")
            .setSingleChoiceItems(items.toTypedArray(), checked) { d, which ->
                Prefs.setTheme(this, if (which == 0) null else ThemeCatalog.all[which - 1].id)
                d.dismiss()
                refresh()
                // Show the chosen theme straight away
                startActivity(ChargingActivity.intent(this, false, "charge"))
            }
            .setNegativeButton("Отказ", null)
            .show()
    }

    private fun slotName(slot: Prefs.Slot) = when (slot) {
        Prefs.Slot.CHARGE -> "Зареждане"
        Prefs.Slot.DRAIN -> "Разреждане"
        Prefs.Slot.TEXT -> "Проценти"
    }

    /** What the scene uses when nothing is picked (the percentage then follows the state colour) */
    private fun defaultColor(slot: Prefs.Slot) = when (slot) {
        Prefs.Slot.CHARGE -> Color.rgb(110, 255, 140)
        Prefs.Slot.DRAIN -> Color.rgb(255, 160, 40)
        Prefs.Slot.TEXT -> Color.rgb(125, 255, 165)
    }

    private fun pickColor(slot: Prefs.Slot) {
        val cur = Prefs.color(this, slot) ?: defaultColor(slot)
        ColorPicker.show(this, "Цвят: ${slotName(slot)}", cur) { c ->
            Prefs.setColor(this, slot, c)
            refresh()
            // Show the result straight away; on battery colours are seen in the discharge demo
            startActivity(ChargingActivity.intent(this, false, if (slot == Prefs.Slot.DRAIN) "drain" else "charge"))
        }
    }

    private fun refreshColors() {
        for ((slot, b) in colorButtons) {
            val c = Prefs.color(this, slot)
            val label = SpannableString("●  ${slotName(slot)}" + if (c == null) " (по подразбиране)" else "")
            label.setSpan(ForegroundColorSpan(c ?: defaultColor(slot)), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            label.setSpan(RelativeSizeSpan(1.4f), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.text = label
        }
    }

    private fun refresh() {
        refreshColors()
        styleButton.text = styles.firstOrNull { it.first == Prefs.style(this) }?.second ?: styles[0].second
        val detected = ThemeCatalog.detect(this)
        val cur = ThemeCatalog.current(this)
        themeText.text = buildString {
            append("Телефон: ${ThemeCatalog.modelLabel(this@MainActivity)}\n")
            append(if (detected != null) "✅ Разпознат: ${detected.name}\n" else "⚪ Моделът не е в списъка, ползва се универсална тема\n")
            append("Тема: ${cur.name}" + if (Prefs.theme(this@MainActivity) == null) " (автоматично)" else "")
        }
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
