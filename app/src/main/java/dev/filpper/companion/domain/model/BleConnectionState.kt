package dev.filpper.companion.domain.model

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data class Pairing(val device: BleDevice) : BleConnectionState
    data class Connecting(val device: BleDevice) : BleConnectionState
    data class Connected(val device: BleDevice) : BleConnectionState // Connected == GATT services discovered, NOT Flipper RPC is ready
    data class Failed(val failure: BleFailure) : BleConnectionState
}

sealed interface BleFailure {
    data object BluetoothUnsupported : BleFailure
    data object BluetoothDisabled : BleFailure
    data object PermissionMissing : BleFailure
    data object LocationServicesDisabled : BleFailure
    data object PairingFailed : BleFailure
    data object TimedOut : BleFailure
    data class ScanFailed(val code: Int) : BleFailure
    data class ConnectionFailed(val code: Int) : BleFailure
    data class ServiceDiscoveryFailed(val code: Int) : BleFailure
    data object InvalidGattProfile : BleFailure
    data object EnableCCCDProblem : BleFailure
    data object RPCError : BleFailure
    data class RPCReadError(val message: String) : BleFailure
    data object RPCSessionInactive : BleFailure
    data class Unknown(val message: String? = null) : BleFailure
}
