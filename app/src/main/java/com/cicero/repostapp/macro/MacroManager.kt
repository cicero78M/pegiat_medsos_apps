package com.cicero.repostapp.macro

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory

/** Persist and load macros using SharedPreferences. */
object MacroManager {
    private const val PREF_NAME = "macros"
    private const val KEY_MACRO = "macro"

    private val gson: Gson by lazy {
        val rta = RuntimeTypeAdapterFactory
            .of(MacroAction::class.java, "type")
            .registerSubtype(MacroAction.Click::class.java, "click")
            .registerSubtype(MacroAction.SetText::class.java, "setText")
            .registerSubtype(MacroAction.Swipe::class.java, "swipe")
            .registerSubtype(MacroAction.Repost::class.java, "repost")
        GsonBuilder()
            .registerTypeAdapterFactory(rta)
            .create()
    }

    fun save(context: Context, macro: Macro) {
        val json = gson.toJson(macro)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MACRO, json)
            .apply()
    }

    fun load(context: Context): Macro? {
        val json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MACRO, null) ?: return null
        return gson.fromJson(json, object : TypeToken<Macro>() {}.type)
    }
}
