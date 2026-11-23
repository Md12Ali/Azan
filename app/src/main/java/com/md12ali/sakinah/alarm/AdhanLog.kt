package com.md12ali.sakinah.alarm

import android.content.Context
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Tiny on-device log of the Adhan chain (scheduled → alarm fired → player started / failed).
 * Shown in Adhan tab → Health check, so you can see exactly what happened at the last prayer time.
 */
object AdhanLog {
    private const val PREFS = "adhan_log"
    private const val KEY = "lines"
    private val fmt = DateTimeFormatter.ofPattern("EEE HH:mm:ss")

    @Synchronized
    fun add(context: Context, line: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lines = (listOf("${LocalDateTime.now().format(fmt)}  $line") + p.getString(KEY, "").orEmpty().lines())
            .filter { it.isNotBlank() }.take(30)
        p.edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    fun lines(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty().lines().filter { it.isNotBlank() }

    fun setNext(context: Context, text: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("next", text).apply()

    fun next(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("next", null)
}
