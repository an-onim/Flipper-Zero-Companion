package dev.filpper.companion.domain.repository

import dev.filpper.companion.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    val theme: Flow<ThemePreference>
    suspend fun setTheme(value: ThemePreference)
}
