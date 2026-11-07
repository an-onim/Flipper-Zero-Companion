package dev.filpper.companion.presentation.pages.device

import dev.filpper.companion.domain.model.BleConnectionState
import dev.filpper.companion.domain.model.BleDevice
import dev.filpper.companion.domain.model.FlipperDeviceInfo

sealed interface DeviceIntent {
    data object StartScan : DeviceIntent
    data object StopScan : DeviceIntent
    data class SelectDevice(val device: BleDevice) : DeviceIntent
    data object Disconnect : DeviceIntent
    data object RefreshInfo : DeviceIntent
}

sealed interface DeviceInfoState {
    data object None : DeviceInfoState
    data object Loading : DeviceInfoState
    data class Ready(val data: FlipperDeviceInfo) : DeviceInfoState
    data object Failed : DeviceInfoState
}

data class DeviceUiState(
    val connection: BleConnectionState = BleConnectionState.Disconnected,
    val devices: List<BleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val info: DeviceInfoState = DeviceInfoState.None,
)
