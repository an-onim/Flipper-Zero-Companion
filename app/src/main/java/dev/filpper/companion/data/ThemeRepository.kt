package dev.filpper.companion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.filpper.companion.domain.model.ThemePreference
import dev.filpper.companion.domain.repository.ThemeRepository
import dev.filpper.companion.domain.store.SettingsStore
import dev.filpper.companion.domain.store.SettingsStore.settingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ThemeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThemeRepository {
    private val themeKey = stringPreferencesKey(SettingsStore.Key.THEME_MODE)

    override val theme: Flow<ThemePreference> = context.settingsStore.data.map { prefs ->
        ThemePreference.entries.firstOrNull { it.name == prefs[themeKey] } ?: ThemePreference.SYSTEM
    }

    override suspend fun setTheme(value: ThemePreference) {
        context.settingsStore.edit { preferences -> preferences[themeKey] = value.name }
    }
}
