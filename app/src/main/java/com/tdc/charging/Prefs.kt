package com.tdc.charging

import android.content.Context

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("tdc", Context.MODE_PRIVATE)
    fun auto(ctx: Context) = sp(ctx).getBoolean("auto", false)
    fun setAuto(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("auto", on).apply()

    /** null = automatic (by phone model) */
    fun theme(ctx: Context): String? = sp(ctx).getString("theme", null)
    fun setTheme(ctx: Context, id: String?) = sp(ctx).edit().putString("theme", id).apply()
}
