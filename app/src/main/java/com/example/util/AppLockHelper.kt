package com.example.util

import android.content.Context
import android.content.SharedPreferences

object AppLockHelper {
    private const val PREFS_NAME = "app_lock_preferences"
    private const val KEY_ENABLED = "app_lock_enabled"
    private const val KEY_TYPE = "app_lock_type" // "pin" or "password"
    private const val KEY_CODE = "app_lock_code"
    private const val KEY_BIOMETRICS_ENABLED = "app_lock_biometrics_enabled"
    
    private const val KEY_Q1 = "security_q1"
    private const val KEY_Q2 = "security_q2"
    private const val KEY_Q3 = "security_q3"
    private const val KEY_A1 = "security_a1"
    private const val KEY_A2 = "security_a2"
    private const val KEY_A3 = "security_a3"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAppLockEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.getBoolean(KEY_ENABLED, false) && !getLockCode(context).isNullOrEmpty()
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getLockType(context: Context): String {
        return getPrefs(context).getString(KEY_TYPE, "pin") ?: "pin"
    }

    fun setLockType(context: Context, type: String) {
        getPrefs(context).edit().putString(KEY_TYPE, type).apply()
    }

    fun getLockCode(context: Context): String? {
        return getPrefs(context).getString(KEY_CODE, null)
    }

    fun setLockCode(context: Context, code: String?) {
        getPrefs(context).edit().putString(KEY_CODE, code).apply()
    }

    fun isBiometricsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BIOMETRICS_ENABLED, false)
    }

    fun setBiometricsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRICS_ENABLED, enabled).apply()
    }

    fun saveSecurityQuestions(
        context: Context,
        q1: String, a1: String,
        q2: String, a2: String,
        q3: String, a3: String
    ) {
        getPrefs(context).edit()
            .putString(KEY_Q1, q1)
            .putString(KEY_A1, a1.trim().lowercase())
            .putString(KEY_Q2, q2)
            .putString(KEY_A2, a2.trim().lowercase())
            .putString(KEY_Q3, q3)
            .putString(KEY_A3, a3.trim().lowercase())
            .apply()
    }

    fun getSecurityQuestions(context: Context): List<Pair<String, String>> {
        val prefs = getPrefs(context)
        val q1 = prefs.getString(KEY_Q1, "What was your childhood pet's name?") ?: "What was your childhood pet's name?"
        val a1 = prefs.getString(KEY_A1, "") ?: ""
        val q2 = prefs.getString(KEY_Q2, "What was the name of your elementary school?") ?: "What was the name of your elementary school?"
        val a2 = prefs.getString(KEY_A2, "") ?: ""
        val q3 = prefs.getString(KEY_Q3, "What city were you born in?") ?: "What city were you born in?"
        val a3 = prefs.getString(KEY_A3, "") ?: ""
        
        return listOf(Pair(q1, a1), Pair(q2, a2), Pair(q3, a3))
    }

    fun setSecuritySetupComplete(context: Context, complete: Boolean) {
        getPrefs(context).edit().putBoolean("security_setup_complete", complete).apply()
    }

    fun isSecuritySetupComplete(context: Context): Boolean {
        return getPrefs(context).getBoolean("security_setup_complete", false)
    }

    // Helper to generate default options for setup
    val DEFAULT_QUESTIONS_1 = listOf(
        "What was your childhood pet's name?",
        "What was the name of your first teacher?",
        "What was your favorite childhood toy?"
    )

    val DEFAULT_QUESTIONS_2 = listOf(
        "What was the name of your elementary school?",
        "What street did you grow up on?",
        "What was your first car's make/model?"
    )

    val DEFAULT_QUESTIONS_3 = listOf(
        "What city were you born in?",
        "What is your mother's maiden name?",
        "What is the name of your favorite movie?"
    )
}
