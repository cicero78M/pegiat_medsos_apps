package com.cicero.repostapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Configuration rule describing texts to search and action to execute. */
data class AutoPostRule(
    val packageName: String,
    val requiresAll: List<String>,
    val clickTargetText: String,
    val maxDepth: Int = Int.MAX_VALUE,
    val cooldownMs: Long = 1500L
)

/** Loader for autopost rules stored as JSON in SharedPreferences. */
object AutoPostRuleLoader {
    private const val PREF_NAME = "autopost_rules"
    private const val KEY_RULES = "rules_json"

    /** Default rule list bundled with the app. */
    private val defaultRulesJson = """
        [
          {
            "packageName": "com.twitter.android",
            "requiresAll": ["posting"],
            "clickTargetText": "posting",
            "maxDepth": 5,
            "cooldownMs": 1500
          },
          {
            "packageName": "com.twitter.android",
            "requiresAll": ["tweet"],
            "clickTargetText": "tweet",
            "maxDepth": 5,
            "cooldownMs": 1500
          }
        ]
    """.trimIndent()

    fun load(context: Context): List<AutoPostRule> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RULES, defaultRulesJson) ?: defaultRulesJson
        return runCatching {
            val type = object : TypeToken<List<AutoPostRule>>() {}.type
            Gson().fromJson<List<AutoPostRule>>(json, type)
        }.getOrElse { emptyList() }
    }
}
