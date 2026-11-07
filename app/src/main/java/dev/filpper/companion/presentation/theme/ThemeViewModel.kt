package dev.filpper.companion.presentation.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.filpper.companion.domain.model.ThemePreference
import dev.filpper.companion.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val repository: ThemeRepository,
) : ViewModel() {
    val theme = repository.theme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ThemePreference.SYSTEM,
    )

    fun toggle(currentlyDark: Boolean) {
        viewModelScope.launch {
            repository.setTheme(if (currentlyDark) ThemePreference.LIGHT else ThemePreference.DARK)
        }
    }
}
