package dev.filpper.companion.domain.repository

import dev.filpper.companion.domain.model.FlipperFile

interface FlipperFileRepository {
    suspend fun loadFiles(): List<FlipperFile>
}
