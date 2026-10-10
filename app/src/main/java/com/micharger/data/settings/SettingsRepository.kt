package com.micharger.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val targetSoc: Int = 80,
    val resumeSoc: Int = 75,
    val themeMode: String = "System",
    val sampleMinutes: Int = 5,
    val guardEnabled: Boolean = false,
    val bootStart: Boolean = false,
    val showNotification: Boolean = true,
    val tempStopEnabled: Boolean = false,
    val tempStopC: Int = 55,
    val tempResumeC: Int = 41,
)

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val target = (p[KEY_TARGET] ?: 80).coerceIn(50, 95)
        val resume = (p[KEY_RESUME] ?: 75).coerceIn(30, target - 5)
        val sample = (p[KEY_SAMPLE] ?: 5).takeIf { it in SAMPLE_MINUTES } ?: 5
        val theme = (p[KEY_THEME] ?: "System").takeIf { it in THEMES } ?: "System"
        val tempStop = (p[KEY_TEMP_STOP] ?: 55).coerceIn(40, 60)
        val tempResume = (p[KEY_TEMP_RESUME] ?: 41).coerceIn(25, tempStop - 5)
        AppSettings(
            targetSoc = target,
            resumeSoc = resume,
            themeMode = theme,
            sampleMinutes = sample,
            guardEnabled = p[KEY_GUARD] ?: false,
            bootStart = p[KEY_BOOT] ?: false,
            showNotification = p[KEY_NOTIFY] ?: true,
            tempStopEnabled = p[KEY_TEMP_ENABLED] ?: false,
            tempStopC = tempStop,
            tempResumeC = tempResume,
        )
    }

    suspend fun settingsOnce(): AppSettings = settingsFlow.first()

    suspend fun setTargetSoc(v: Int) = context.dataStore.edit { it[KEY_TARGET] = v }
    suspend fun setResumeSoc(v: Int) = context.dataStore.edit { it[KEY_RESUME] = v }
    suspend fun setThemeMode(v: String) = context.dataStore.edit { it[KEY_THEME] = v }
    suspend fun setSampleMinutes(v: Int) = context.dataStore.edit { it[KEY_SAMPLE] = v }
    suspend fun setGuardEnabled(v: Boolean) = context.dataStore.edit { it[KEY_GUARD] = v }
    suspend fun setBootStart(v: Boolean) = context.dataStore.edit { it[KEY_BOOT] = v }
    suspend fun setShowNotification(v: Boolean) = context.dataStore.edit { it[KEY_NOTIFY] = v }
    suspend fun setTempStopEnabled(v: Boolean) = context.dataStore.edit { it[KEY_TEMP_ENABLED] = v }
    suspend fun setTempStopC(v: Int) = context.dataStore.edit { it[KEY_TEMP_STOP] = v }
    suspend fun setTempResumeC(v: Int) = context.dataStore.edit { it[KEY_TEMP_RESUME] = v }

    private companion object {
        val SAMPLE_MINUTES = setOf(1, 5, 15, 30)
        val THEMES = setOf("System", "Light", "Dark")
        val KEY_TARGET = intPreferencesKey("target_soc")
        val KEY_RESUME = intPreferencesKey("resume_soc")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SAMPLE = intPreferencesKey("sample_minutes")
        val KEY_GUARD = booleanPreferencesKey("guard_enabled")
        val KEY_BOOT = booleanPreferencesKey("boot_start")
        val KEY_NOTIFY = booleanPreferencesKey("show_notification")
        val KEY_TEMP_ENABLED = booleanPreferencesKey("temp_stop_enabled")
        val KEY_TEMP_STOP = intPreferencesKey("temp_stop_c")
        val KEY_TEMP_RESUME = intPreferencesKey("temp_resume_c")
    }
}
