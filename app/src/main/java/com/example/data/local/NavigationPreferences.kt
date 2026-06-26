package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.navigationDataStore: DataStore<Preferences> by preferencesDataStore(name = "navigation_prefs")

class NavigationPreferences(private val context: Context) {
    companion object {
        private val KEY_TAB_ORDER = stringPreferencesKey("tab_order")
        private val KEY_DEFAULT_START = stringPreferencesKey("default_start")
        
        const val DEFAULT_ORDER = "HABAYEB,LEDGER,MAKHZAN"
        const val DEFAULT_START = "HABAYEB"
    }

    val tabOrderFlow: Flow<String> = context.navigationDataStore.data
        .map { preferences ->
            preferences[KEY_TAB_ORDER] ?: DEFAULT_ORDER
        }

    val defaultStartFlow: Flow<String> = context.navigationDataStore.data
        .map { preferences ->
            preferences[KEY_DEFAULT_START] ?: DEFAULT_START
        }

    suspend fun saveTabOrder(order: String) {
        context.navigationDataStore.edit { preferences ->
            preferences[KEY_TAB_ORDER] = order
        }
    }

    suspend fun saveDefaultStart(start: String) {
        context.navigationDataStore.edit { preferences ->
            preferences[KEY_DEFAULT_START] = start
        }
    }
}
