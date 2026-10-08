package com.tdc.charging

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.hypot

/** Per-model themes. The id must match a key in LAYOUTS inside scene.html. */
object ThemeCatalog {
    data class Theme(val id: String, val name: String, val brand: String, val match: List<Regex> = emptyList())

    private fun r(vararg p: String) = p.map { Regex(it) }

    // Most specific first: "pro xl" before "pro", "ultra" before the base model
    val all: List<Theme> = listOf(
        Theme("s26u", "Galaxy S26 Ultra", "Samsung", r("^sm-s94[89]", "galaxy s26 ultra")),
        Theme("s26p", "Galaxy S26+", "Samsung", r("^sm-s94[4-7]", "galaxy s26\\s?(\\+|plus)")),
        Theme("s26", "Galaxy S26", "Samsung", r("^sm-s94[0-3]", "galaxy s26\\b")),
        Theme("s25u", "Galaxy S25 Ultra", "Samsung", r("^sm-s93[89]", "galaxy s25 ultra")),
        Theme("s25p", "Galaxy S25+", "Samsung", r("^sm-s93[4-7]", "galaxy s25\\s?(\\+|plus)")),
        Theme("s25", "Galaxy S25", "Samsung", r("^sm-s93[0-3]", "galaxy s25\\b")),
        Theme("s24u", "Galaxy S24 Ultra", "Samsung", r("^sm-s92[89]", "galaxy s24 ultra")),
        Theme("s24p", "Galaxy S24+", "Samsung", r("^sm-s92[4-7]", "galaxy s24\\s?(\\+|plus)")),
        Theme("s24", "Galaxy S24", "Samsung", r("^sm-s92[0-3]", "galaxy s24\\b")),

        Theme("x17u", "Xiaomi 17 Ultra", "Xiaomi", r("xiaomi 17 ultra")),
        Theme("x17pm", "Xiaomi 17 Pro Max", "Xiaomi", r("xiaomi 17 pro max")),
        Theme("x17p", "Xiaomi 17 Pro", "Xiaomi", r("xiaomi 17 pro\\b")),
        Theme("x17", "Xiaomi 17", "Xiaomi", r("xiaomi 17\\b")),
        Theme("x15u", "Xiaomi 15 Ultra", "Xiaomi", r("xiaomi 15 ultra", "^xuanyuan$")),
        Theme("x15p", "Xiaomi 15 Pro", "Xiaomi", r("xiaomi 15 pro\\b", "^haotian$")),
        Theme("x15", "Xiaomi 15", "Xiaomi", r("xiaomi 15\\b", "^dada$")),
        Theme("x14u", "Xiaomi 14 Ultra", "Xiaomi", r("xiaomi 14 ultra", "^aurora$")),
        Theme("x14p", "Xiaomi 14 Pro", "Xiaomi", r("xiaomi 14 pro\\b", "^shennong$")),
        Theme("x14", "Xiaomi 14", "Xiaomi", r("xiaomi 14\\b", "^houji$")),

        Theme("p11px", "Pixel 11 Pro XL", "Google", r("pixel 11 pro xl")),
        Theme("p11p", "Pixel 11 Pro", "Google", r("pixel 11 pro\\b")),
        Theme("p11", "Pixel 11", "Google", r("pixel 11\\b")),
        Theme("p10px", "Pixel 10 Pro XL", "Google", r("pixel 10 pro xl")),
        Theme("p10p", "Pixel 10 Pro", "Google", r("pixel 10 pro\\b")),
        Theme("p10", "Pixel 10", "Google", r("pixel 10\\b")),
        Theme("p9px", "Pixel 9 Pro XL", "Google", r("pixel 9 pro xl")),
        Theme("p9p", "Pixel 9 Pro", "Google", r("pixel 9 pro\\b")),
        Theme("p9", "Pixel 9", "Google", r("pixel 9\\b")),

        Theme("op15", "OnePlus 15", "OnePlus", r("oneplus 15\\b")),
        Theme("op13", "OnePlus 13", "OnePlus", r("oneplus 13\\b")),
        Theme("op12", "OnePlus 12", "OnePlus", r("oneplus 12\\b")),

        Theme("motosig", "Motorola Signature", "Motorola", r("motorola signature", "edge 70 ultra")),
        Theme("moto60p", "Motorola Edge 60 Pro", "Motorola", r("edge 60 pro")),
        Theme("moto50u", "Motorola Edge 50 Ultra", "Motorola", r("edge 50 ultra")),

        Theme("u61", "Универсална: компактен 6.1″", "Универсални"),
        Theme("u64", "Универсална: стандартен 6.4″", "Универсални"),
        Theme("u67", "Универсална: голям 6.7″", "Универсални"),
        Theme("u69", "Универсална: огромен 6.9″", "Универсални")
    )

    fun byId(id: String?) = all.firstOrNull { it.id == id }

    private fun sysProp(key: String): String = try {
        val c = Class.forName("android.os.SystemProperties")
        (c.getMethod("get", String::class.java).invoke(null, key) as? String).orEmpty()
    } catch (_: Throwable) { "" }

    /** Every name the phone reports about itself, lower case. */
    fun deviceNames(ctx: Context): List<String> = listOf(
        sysProp("ro.product.marketname"),
        sysProp("ro.product.vendor.marketname"),
        sysProp("ro.vendor.oplus.market.name"),
        sysProp("ro.vendor.oplus.market.enname"),
        Build.MODEL ?: "",
        Build.DEVICE ?: "",
        try { Settings.Global.getString(ctx.contentResolver, "device_name") ?: "" } catch (_: Exception) { "" }
    ).map { it.trim().lowercase().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }

    /** The phone's own model, or null when it is not in the list. */
    fun detect(ctx: Context): Theme? {
        val names = deviceNames(ctx)
        return all.firstOrNull { t -> t.match.any { re -> names.any { re.containsMatchIn(it) } } }
    }

    /** Universal theme chosen by the real screen diagonal. */
    fun universalFor(ctx: Context): Theme {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        val inches = hypot(dm.widthPixels / dm.xdpi.toDouble(), dm.heightPixels / dm.ydpi.toDouble())
        val id = when {
            inches < 6.25 -> "u61"
            inches < 6.55 -> "u64"
            inches < 6.85 -> "u67"
            else -> "u69"
        }
        return byId(id)!!
    }

    /** Theme to show: the saved choice, or the detected model, or a universal one. */
    fun current(ctx: Context): Theme = byId(Prefs.theme(ctx)) ?: detect(ctx) ?: universalFor(ctx)

    fun modelLabel(ctx: Context): String {
        val market = sysProp("ro.product.marketname").ifEmpty { sysProp("ro.vendor.oplus.market.name") }
        return market.ifEmpty { "${Build.MANUFACTURER} ${Build.MODEL}" }.trim()
    }
}
