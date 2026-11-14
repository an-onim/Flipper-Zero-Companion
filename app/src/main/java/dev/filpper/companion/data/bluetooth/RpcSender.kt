package dev.filpper.companion.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import androidx.annotation.RequiresPermission
import dev.filpper.companion.domain.model.BleConnectionState
import dev.filpper.companion.domain.model.BleFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

internal class RpcSender(
    private val getGatt: () -> BluetoothGatt?,
    private val isBtConnected: StateFlow<BleConnectionState>,
    private val rpcSessionStatus: StateFlow<RpcStatus>,
    private val onWriteFail: (BluetoothGatt) -> Unit
) {

    private val RPC_INIT_TIMEOUT_MS = 5_000L      // TODO(refactor) move
    private val WRITE_TIMEOUT_MS = 10_000L        // TODO(refactor) move

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
     * Use to write fragments sequentially
     */
    private val writeRxMutex = Mutex()
    /**
     * Reference to one specific operation
     */
    private var pendingRxWrite: PendingWrite? = null

    private data class PendingWrite(
        val gatt: BluetoothGatt,
        val completion: CompletableDeferred<WriteResult>,
    )
    private sealed interface WriteResult {
        data object Success : WriteResult
        data object Disconnected : WriteResult
        data object SessionInactive : WriteResult
        data class Failure(val status: Int) : WriteResult
    }

    fun onGattClosed(gatt: BluetoothGatt) {
        val pending = pendingRxWrite ?: return
        if (pending.gatt != gatt) return

        pending.completion.complete(WriteResult.Disconnected)
    }

    fun onRpcInactive(gatt: BluetoothGatt) {
        val pending = pendingRxWrite ?: return
        if (pending.gatt != gatt) return

        pending.completion.complete(WriteResult.SessionInactive)
    }

    fun handleCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        if (characteristic.uuid != FlipperGattUuid.Characteristic.RX.uuid) return
        val pending = pendingRxWrite ?: return
        if (pending.gatt != gatt) return

        val result = if (status == BluetoothGatt.GATT_SUCCESS) {
            WriteResult.Success
        } else {
            WriteResult.Failure(status)
        }

        pending.completion.complete(result)
    }

    /**
     * Executes one GATT-record and waiting for onCharacteristicWrite()
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    internal suspend fun writeRxChunk(gatt: BluetoothGatt, bytes: ByteArray): BleFailure? = writeRxMutex.withLock {
        if (!isConnectionValid(gatt)) return@withLock BleFailure.RPCError

        // TODO(ble): change to real MTU
        // using ATT MTU = 23
        if (bytes.isEmpty() || bytes.size > 20) return@withLock BleFailure.RPCError

        val rxChar = gatt.getService(FlipperGattUuid.SERVICE).getCharacteristic(FlipperGattUuid.Characteristic.RX.uuid)

        val pending = PendingWrite(gatt = gatt, completion = CompletableDeferred())
        pendingRxWrite = pending
        var writeStarted = false

        try {
            writeStarted = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(rxChar, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                } else @Suppress("DEPRECATION") {
                    rxChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    rxChar.value = bytes.copyOf()
                    gatt.writeCharacteristic(rxChar)
                }
            } catch (_: Exception) {
                false
            }

            if (!writeStarted) return@withLock BleFailure.RPCError

            val result = withTimeoutOrNull(WRITE_TIMEOUT_MS.milliseconds) {
                pending.completion.await()
            }
            when(result) {
                WriteResult.Success -> if (isConnectionValid(gatt)) null else BleFailure.RPCError
                WriteResult.Disconnected -> BleFailure.RPCError
                is WriteResult.Failure,
                WriteResult.SessionInactive,
                null -> {
                    onWriteFail.invoke(gatt)
                    BleFailure.RPCError
                }
            }
        } catch (e: CancellationException) {
            if (writeStarted && !pending.completion.isCompleted) onWriteFail.invoke(gatt)
            throw e
        } finally {
            pendingRxWrite = null
        }


    }

    private fun isConnectionValid(gatt: BluetoothGatt) =
        getGatt() == gatt && isBtConnected.value is BleConnectionState.Connected && rpcSessionStatus.value == RpcStatus.ACTIVE


    /**
     * Send full RPC message
     */
    suspend fun sendRpcCommand(command: ByteArray): BleFailure? {
        // 1. check connection
        // 2. waiting for RpcStatus.ACTIVE
        val gatt = awaitRpcActive() ?: return BleFailure.RPCError

        // 3. framing → фрагментация → Flow Control
        // TODO(transport): implement framing, fragmentation, Flow Control

        // 4. write RX with writeRxChunk

        // 5. check result - see onCharacteristicWrite()

        return null
    }

}