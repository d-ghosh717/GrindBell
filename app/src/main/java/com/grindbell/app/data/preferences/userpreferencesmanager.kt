package com.grindbell.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "grindbell_prefs")

class UserPreferencesManager(private val context: Context) {

    companion object {
        val KEY_HOLD_TO_COMPLETE = booleanPreferencesKey("hold_to_complete")
        val KEY_DEFAULT_REMINDER_MODE = stringPreferencesKey("default_reminder_mode")
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val holdToComplete: Flow<Boolean> = context.dataStore.data.map { it[KEY_HOLD_TO_COMPLETE] ?: false }

    suspend fun setHoldToComplete(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_HOLD_TO_COMPLETE] = enabled }
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { prefs -> prefs[KEY_ONBOARDING_COMPLETE] = true }
    }

    fun isOnboardingComplete(): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }
}
