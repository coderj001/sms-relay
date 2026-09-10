package com.smsrelay.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore by preferencesDataStore("settings")

object AppSettings {
    const val AUTO_SIM = -1
    val MASTER_AUTOMATION = booleanPreferencesKey("master_automation")
    val DEFAULT_SIM_SUBSCRIPTION_ID = intPreferencesKey("default_sim_subscription_id")
}
