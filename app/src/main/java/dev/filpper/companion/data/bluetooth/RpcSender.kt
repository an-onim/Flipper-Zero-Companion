package dev.filpper.companion.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import androidx.annotation.RequiresPermission
import dev.filpper.companion.data.repository.BleConnectionRepositoryImpl.Companion.ATT_WRITE_OVERHEAD
import dev.filpper.companion.data.repository.BleConnectionRepositoryImpl.Companion.FLIPPER_RX_MAX_SIZE
import dev.filpper.companion.domain.model.BleConnectionState
import dev.filpper.companion.domain.model.BleFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val getMtu: () -> Int,
    private val isBtConnected: StateFlow<BleConnectionState>,
    private val rpcSessionStatus: StateFlow<RpcStatus>,
    private val onWriteFail: (BluetoothGatt) -> Unit
) {

    private val RPC_INIT_TIMEOUT_MS = 5_000L      // TODO(refactor) move
    private val WRITE_TIMEOUT_MS = 10_000L        // TODO(refactor) move

    /**
     * Sends a full message (byte sequence) through RX
     *
     * * serializes concurrent sends with `Mutex`
     * * splits data according to the current ATT MTU
     * * waits for `onCharacteristicWrite()` after every chunk
     *
     * @return `null` if all GATT writes completed successfully
     */
    suspend fun sendRxMessage(message: ByteArray): BleFailure? {
        if (message.isEmpty()) return BleFailure.RPCError
        val buffer = message.copyOf()

        // TODO(transport): implement framing, fragmentation, Flow Control

        return writeRxMutex.withLock {
            val gatt = awaitRpcActive() ?: return@withLock BleFailure.RPCError
            var offset = 0

            try {
                while (offset < buffer.size) {
                    currentCoroutineContext().ensureActive()

                    val chunkSize = minOf(
                        getMtu() - ATT_WRITE_OVERHEAD,
                        FLIPPER_RX_MAX_SIZE,
                        buffer.size - offset
                    )
                    val chunk = buffer.copyOfRange(offset, offset + chunkSize)

                    val result = writeRxChunk(gatt, chunk)
                    if (result != null) {
                        if (offset > 0) onWriteFail(gatt)
                        return@withLock result
                    }

                    offset += chunkSize
                }
                null
            } catch (e: CancellationException) {
                if (offset > 0) onWriteFail(gatt)
                throw e
            }
        }
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


    /**
     * Executes one GATT-record and waiting for onCharacteristicWrite()
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun writeRxChunk(gatt: BluetoothGatt, bytes: ByteArray): BleFailure? {
        if (!isConnectionValid(gatt)) return BleFailure.RPCError

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

            // failed initiation may indicate an unavailable or busy GATT session
            if (!writeStarted) return BleFailure.RPCError

            val result = withTimeoutOrNull(WRITE_TIMEOUT_MS.milliseconds) {
                pending.completion.await()
            }
            return when(result) {
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

}