package dev.filpper.companion.domain.repository

import dev.filpper.companion.domain.model.FlipperDeviceInfo
import dev.filpper.companion.domain.model.FlipperFile

interface FlipperDeviceInfoRepository {
    suspend fun loadDeviceInfo(): FlipperDeviceInfo
}

interface FlipperFileRepository {
    suspend fun loadFiles(): List<FlipperFile>
}
