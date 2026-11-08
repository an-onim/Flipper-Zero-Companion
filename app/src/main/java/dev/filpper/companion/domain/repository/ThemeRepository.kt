package dev.filpper.companion.domain.repository

import dev.filpper.companion.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    val theme: Flow<ThemeMode>
    suspend fun setTheme(value: ThemeMode)
}
