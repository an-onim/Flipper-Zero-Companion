package dev.filpper.companion.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.filpper.companion.domain.model.ThemeMode
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

    override val theme: Flow<ThemeMode> = context.settingsStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[themeKey] } ?: ThemeMode.SYSTEM
    }

    override suspend fun setTheme(value: ThemeMode) {
        context.settingsStore.edit { preferences -> preferences[themeKey] = value.name }
    }
}
