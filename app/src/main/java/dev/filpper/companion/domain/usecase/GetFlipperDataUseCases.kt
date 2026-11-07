package dev.filpper.companion.domain.usecase

import dev.filpper.companion.domain.repository.FlipperDeviceInfoRepository
import dev.filpper.companion.domain.repository.FlipperFileRepository
import javax.inject.Inject

class GetFlipperInfoUseCase @Inject constructor(
    private val repository: FlipperDeviceInfoRepository,
) {
    suspend operator fun invoke() = repository.loadDeviceInfo()
}

class GetFlipperFilesUseCase @Inject constructor(
    private val repository: FlipperFileRepository,
) {
    suspend operator fun invoke() = repository.loadFiles()
}
