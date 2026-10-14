package com.example.gpayexpensetracker.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object CategoryStore {
    private const val PREFS_NAME = "GPayTrackerPrefs"
    private const val CATEGORIES_KEY = "expense_categories"
    private const val SUGGESTION_ALIASES_KEY = "category_suggestion_aliases"

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(CATEGORIES_KEY, null)
            ?: return ExpenseCategories.all

        return runCatching {
            val json = JSONArray(raw)
            buildList<String> {
                for (index in 0 until json.length()) {
                    val name = cleanName(json.optString(index))
                    if (name.isNotEmpty() && none { it.equals(name, ignoreCase = true) }) add(name)
                }
                if (none { it == ExpenseCategories.OTHER }) add(ExpenseCategories.OTHER)
            }.ifEmpty { ExpenseCategories.all }
        }.getOrElse { ExpenseCategories.all }
    }

    fun save(context: Context, categories: List<String>) {
        val json = JSONArray()
        categories.forEach(json::put)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(CATEGORIES_KEY, json.toString())
            .apply()
    }

    fun recordRename(context: Context, oldName: String, newName: String) {
        val aliases = loadAliases(context)
        ExpenseCategories.all.forEach { builtIn ->
            if (aliases.optString(builtIn, builtIn).equals(oldName, ignoreCase = true)) {
                aliases.put(builtIn, newName)
            }
        }
        saveAliases(context, aliases)
    }

    fun recordDelete(context: Context, deletedName: String) {
        val aliases = loadAliases(context)
        ExpenseCategories.all.forEach { builtIn ->
            if (aliases.optString(builtIn, builtIn).equals(deletedName, ignoreCase = true)) {
                aliases.put(builtIn, ExpenseCategories.OTHER)
            }
        }
        saveAliases(context, aliases)
    }

    fun recordAdd(context: Context, addedName: String) {
        val builtIn = ExpenseCategories.all.firstOrNull { it.equals(addedName, ignoreCase = true) }
            ?: return
        val aliases = loadAliases(context)
        aliases.put(builtIn, addedName)
        saveAliases(context, aliases)
    }

    fun resolveSuggested(context: Context, builtInCategory: String): String {
        val resolved = loadAliases(context).optString(builtInCategory, builtInCategory)
        return load(context).firstOrNull { it.equals(resolved, ignoreCase = true) }
            ?: ExpenseCategories.OTHER
    }

    fun cleanName(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    private fun loadAliases(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(SUGGESTION_ALIASES_KEY, null)
            ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun saveAliases(context: Context, aliases: JSONObject) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SUGGESTION_ALIASES_KEY, aliases.toString())
            .apply()
    }
}
