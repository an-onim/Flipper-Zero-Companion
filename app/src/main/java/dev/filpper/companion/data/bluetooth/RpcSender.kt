package dev.filpper.companion.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import androidx.annotation.RequiresPermission
import dev.filpper.companion.domain.model.BleConnectionState
import dev.filpper.companion.domain.model.BleFailure
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

internal class RpcSender(
    private val getGatt: () -> BluetoothGatt?,
    private val isBtConnected: StateFlow<BleConnectionState>,
    private val rpcSessionStatus: StateFlow<RpcStatus>,
) {

    private val RPC_INIT_TIMEOUT_MS = 5_000L      // TODO(refactor) move

    /**
     * Waiting for activation of RPC-session
     */
    private suspend fun awaitRpcActive(): BluetoothGatt? {
        val result =
            withTimeoutOrNull(RPC_INIT_TIMEOUT_MS.milliseconds) {
                // to avoid case when connection is interrupted during timer work
                combine(isBtConnected, rpcSessionStatus) { connection, status ->
                    val connected = connection is BleConnectionState.Connected
                    val active = status == RpcStatus.ACTIVE
                    connected to active
                }.first { (connected, active) -> !connected || active }
            } ?: return null

        val (connected, active) = result
        if (!connected || !active) return null

        return getGatt()
    }

    /**
     * Executes one GATT-record and waiting for onCharacteristicWrite()
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeRxChunk(gatt: BluetoothGatt, bytes: ByteArray): BleFailure? {
        val rxChar = gatt.getService(FlipperGattUuid.SERVICE).getCharacteristic(FlipperGattUuid.Characteristic.RX.uuid)

        val operationSuccessInit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(rxChar, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            rxChar.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(rxChar)
        }

        return if (operationSuccessInit) null else BleFailure.RPCError
    }

    /**
     * Send full RPC message
     */
    suspend fun sendRpcCommand(command: ByteArray): BleFailure? {
        // 1. check connection
        // 2. waiting for RpcStatus.ACTIVE
        val gatt = awaitRpcActive() ?: return BleFailure.RPCError

        // 3. framing → фрагментация → Flow Control
        // TODO(rpc): implement framing, fragmentation, Flow Control

        // 4. write RX with writeRxChunk

        // 5. check result - see onCharacteristicWrite()

        return null
    }

}