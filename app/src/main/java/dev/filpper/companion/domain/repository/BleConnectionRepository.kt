package dev.filpper.companion.domain.repository

import dev.filpper.companion.domain.model.BleConnectionState
import dev.filpper.companion.domain.model.BleDevice
import kotlinx.coroutines.flow.StateFlow

interface BleConnectionRepository {
    val connection: StateFlow<BleConnectionState>
    val discoveredDevices: StateFlow<List<BleDevice>>
    val isScanning: StateFlow<Boolean>

    fun saveConnectionState(value: BleConnectionState)
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(device: BleDevice)
    suspend fun disconnect()
}
