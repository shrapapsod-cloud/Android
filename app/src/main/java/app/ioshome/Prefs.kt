package app.ioshome

import android.content.Context

/** Настройки, общие для рабочего стола и окна настроек. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var hideLabels: Boolean
        get() = sp.getBoolean("hideLabels", false)
        set(v) = sp.edit().putBoolean("hideLabels", v).apply()
    /** 0 — выкл, 1 — среднее, 2 — сильное. */
    var blur: Int
        get() = sp.getInt("blur", 1).coerceIn(0, 2)
        set(v) = sp.edit().putInt("blur", v).apply()
    /** Размер значков, % (100% = 60dp, как в iOS). */
    var iconScale: Int
        get() = sp.getInt("iconScale", 100).coerceIn(MIN_SCALE, MAX_SCALE)
        set(v) = sp.edit().putInt("iconScale", v.coerceIn(MIN_SCALE, MAX_SCALE)).apply()
    /** Пакет набора значков, "" — стандартные. */
    var iconPack: String
        get() = sp.getString("iconPack", "") ?: ""
        set(v) = sp.edit().putString("iconPack", v).apply()

    /** «Сбросить раскладку» из настроек — выполняется при возврате на рабочий стол. */
    fun requestReset() = sp.edit().putBoolean("reset", true).apply()
    fun consumeReset(): Boolean = sp.getBoolean("reset", false).also { if (it) sp.edit().remove("reset").apply() }

    companion object {
        const val MIN_SCALE = 80
        const val MAX_SCALE = 140
    }
}
