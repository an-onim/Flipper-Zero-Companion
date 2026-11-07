package dev.filpper.companion.domain.usecase

import dev.filpper.companion.domain.model.BleDevice
import dev.filpper.companion.domain.repository.BleConnectionRepository
import javax.inject.Inject

class StartBleScanUseCase @Inject constructor(private val repository: BleConnectionRepository) {
    suspend operator fun invoke() = repository.startScan()
}

class StopBleScanUseCase @Inject constructor(private val repository: BleConnectionRepository) {
    suspend operator fun invoke() = repository.stopScan()
}

class ConnectFlipperUseCase @Inject constructor(private val repository: BleConnectionRepository) {
    suspend operator fun invoke(device: BleDevice) = repository.connect(device)
}

class DisconnectFlipperUseCase @Inject constructor(private val repository: BleConnectionRepository) {
    suspend operator fun invoke() = repository.disconnect()
}
