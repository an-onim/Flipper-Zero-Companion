package dev.filpper.companion.domain.repository

import com.flipperdevices.protobuf.FlipperProto
import dev.filpper.companion.data.flipper.rpc.RpcStatus
import dev.filpper.companion.domain.model.BleConnectionState
import dev.filpper.companion.domain.model.BleDevice
import dev.filpper.companion.domain.model.BleFailure
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

interface FlipperRepository {
    val rpcStatus: StateFlow<RpcStatus>
    val connection: StateFlow<BleConnectionState>
    val discoveredDevices: StateFlow<List<BleDevice>>
    val isScanning: StateFlow<Boolean>

    fun saveConnectionState(value: BleConnectionState)
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(device: BleDevice)
    suspend fun disconnect()

    val nextCommandId: AtomicInteger
    val messages: MutableSharedFlow<FlipperProto.Main>
    suspend fun sendMessage(message: FlipperProto.Main): BleFailure?
}
