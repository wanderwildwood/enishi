package com.wanderwildwood.enishi.data

import android.content.Context

/** What a reader can set, and what they have said no to. Kept on the phone, never anywhere else. */
class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("enishi", Context.MODE_PRIVATE)

    var sortByLast: Boolean
        get() = prefs.getBoolean("sort_by_last", false)
        set(v) = prefs.edit().putBoolean("sort_by_last", v).apply()

    var lastNameFirst: Boolean
        get() = prefs.getBoolean("last_name_first", false)
        set(v) = prefs.edit().putBoolean("last_name_first", v).apply()

    /** Where a new contact goes. Unset means "wherever most of them already are". */
    var saveTo: Account?
        get() = if (!prefs.contains("save_to_set")) null
        else Account(prefs.getString("save_to_name", null), prefs.getString("save_to_type", null))
        set(v) = prefs.edit().apply {
            if (v == null) {
                remove("save_to_set"); remove("save_to_name"); remove("save_to_type")
            } else {
                putBoolean("save_to_set", true); putString("save_to_name", v.name); putString("save_to_type", v.type)
            }
        }.apply()
}
