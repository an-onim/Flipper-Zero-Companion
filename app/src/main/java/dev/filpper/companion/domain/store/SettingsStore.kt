package dev.filpper.companion.domain.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

object SettingsStore {

    val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "ui_settings")


    object Key {
        const val THEME_MODE = "theme_mode"
        const val IS_DEVICE_CONNECTED = "is_device_flipper_connected"
    }

}