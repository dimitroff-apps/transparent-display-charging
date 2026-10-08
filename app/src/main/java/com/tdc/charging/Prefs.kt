package com.tdc.charging

import android.content.Context

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("tdc", Context.MODE_PRIVATE)
    fun auto(ctx: Context) = sp(ctx).getBoolean("auto", false)
    fun setAuto(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("auto", on).apply()

    /** null = automatic (by phone model) */
    fun theme(ctx: Context): String? = sp(ctx).getString("theme", null)
    fun setTheme(ctx: Context, id: String?) = sp(ctx).edit().putString("theme", id).apply()

    /** Colour slots the user can change; null = the default look */
    enum class Slot(val key: String, val param: String) { CHARGE("color_charge", "cc"), DRAIN("color_drain", "dc"), TEXT("color_text", "tc") }

    fun color(ctx: Context, slot: Slot): Int? =
        if (sp(ctx).contains(slot.key)) sp(ctx).getInt(slot.key, 0) else null

    fun setColor(ctx: Context, slot: Slot, color: Int?) = sp(ctx).edit().apply {
        if (color == null) remove(slot.key) else putInt(slot.key, color)
    }.apply()
}
