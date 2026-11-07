package dev.filpper.companion.presentation.pages.files

import dev.filpper.companion.domain.model.FlipperFile

data class FilesUiState(
    val isLoading: Boolean = true,
    val files: List<FlipperFile> = emptyList(),
    val hasError: Boolean = false,
)
