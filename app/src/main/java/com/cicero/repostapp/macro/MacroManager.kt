package com.cicero.repostapp.macro

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Persist and load macros using SharedPreferences. */
object MacroManager {
    private const val PREF_NAME = "macros"
    private const val KEY_MACRO = "macro"

    fun save(context: Context, macro: Macro) {
        val json = Gson().toJson(macro)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MACRO, json)
            .apply()
    }

    fun load(context: Context): Macro? {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MACRO, null) ?: return null
        return Gson().fromJson(json, object : TypeToken<Macro>() {}.type)
    }
}
