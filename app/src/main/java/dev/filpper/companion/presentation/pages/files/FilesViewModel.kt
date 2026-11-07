package dev.filpper.companion.presentation.pages.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.filpper.companion.domain.usecase.GetFlipperFilesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val getFiles: GetFlipperFilesUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(FilesUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _state.value = FilesUiState(isLoading = false, files = getFiles())
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _state.value = FilesUiState(isLoading = false, hasError = true)
            }
        }
    }

    // TODO(rpc): expose RefreshFiles intent once Storage.List is available.
    // TODO(files): implement file detail action only when the required fields are defined.
}
