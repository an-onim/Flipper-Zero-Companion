package dev.filpper.companion.presentation.pages.files

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.filpper.companion.R
import dev.filpper.companion.domain.model.FlipperFile
import dev.filpper.companion.domain.model.FlipperFileCategory
import dev.filpper.companion.presentation.components.SectionTitle

@Composable
fun FilesRoute(viewModel: FilesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FilesScreen(state)
}

@Composable
private fun FilesScreen(state: FilesUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.files_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = stringResource(R.string.files_mock_notice),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.isLoading) {
            item { Text(stringResource(R.string.files_loading), modifier = Modifier.padding(horizontal = 20.dp)) }
        } else if (state.hasError) {
            item { Text(stringResource(R.string.bluetooth_unknown_error), modifier = Modifier.padding(horizontal = 20.dp)) }
        } else {
            items(FlipperFileCategory.entries, key = { it.name }) { category ->
                val files = state.files.filter { it.category == category }
                FileCategoryRow(category = category, files = files)
            }
        }
    }
}

@Composable
private fun FileCategoryRow(category: FlipperFileCategory, files: List<FlipperFile>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SectionTitle(stringResource(category.titleResource()), markerColor = category.accent())
        }
        if (files.isEmpty()) {
            Text(
                stringResource(R.string.files_empty),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(files, key = { it.path }) { file ->
                    FlipperFileCard(file, category)
                }
            }
        }
    }
}

@Composable
private fun FlipperFileCard(file: FlipperFile, category: FlipperFileCategory) {
    val accent = category.accent()
    Card(
        modifier = Modifier.width(194.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.10f),
        ),
        // TODO(files): add onClick only after defining a real file-detail interaction.
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                file.filename,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.file_size_bytes, file.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
