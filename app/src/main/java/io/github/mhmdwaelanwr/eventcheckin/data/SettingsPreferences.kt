package io.github.mhmdwaelanwr.eventcheckin.data

import android.content.Context
import android.content.SharedPreferences
import io.github.mhmdwaelanwr.eventcheckin.viewmodel.DarkModeConfig
import io.github.mhmdwaelanwr.eventcheckin.viewmodel.SettingsState

class SettingsPreferences private constructor(
    private val sharedPreferences: SharedPreferences
) {
    fun loadSettings(): SettingsState {
        val storedMode = sharedPreferences.getString(KEY_DARK_MODE, DarkModeConfig.SYSTEM.name)
        val darkMode = runCatching { DarkModeConfig.valueOf(storedMode ?: DarkModeConfig.SYSTEM.name) }
            .getOrDefault(DarkModeConfig.SYSTEM)
        val hapticEnabled = sharedPreferences.getBoolean(KEY_HAPTIC_ENABLED, true)
        return SettingsState(darkMode = darkMode, hapticEnabled = hapticEnabled)
    }

    fun saveDarkMode(config: DarkModeConfig) {
        sharedPreferences.edit().putString(KEY_DARK_MODE, config.name).apply()
    }

    fun saveHapticEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "settings_prefs"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"

        fun from(context: Context): SettingsPreferences {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SettingsPreferences(prefs)
        }
    }
}

