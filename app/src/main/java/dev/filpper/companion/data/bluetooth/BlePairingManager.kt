package dev.filpper.companion.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.filpper.companion.domain.model.BleDevice
import dev.filpper.companion.domain.model.BleFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class BlePairingManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val adapterProvider: () -> BluetoothAdapter?,
    private val hasConnectPermission: () -> Boolean,
    private val onPairingStarted: (BleDevice) -> Unit,
    private val onSuccessBonding: (BluetoothDevice, BleDevice) -> Unit,
    private val onFailure: (BleFailure) -> Unit,
) {
    private var pendingDevice: BleDevice? = null
    private var pendingRemote: BluetoothDevice? = null
    private var bondTimeout: Job? = null
    private var receiverRegistered = false

    private val bondReceiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(receiverContext: Context,intent: Intent) {

            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

            if (!hasConnectPermission()) {
                finishWithFailure(BleFailure.PermissionMissing)
                return
            }

            val remote = pendingRemote ?: return
            val target = pendingDevice ?: return

            val receivedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java,)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return

            // Ignore broadcasts for another device.
            if (receivedDevice.address != target.address) return

            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            try {
                when (state) {
                    BluetoothDevice.BOND_BONDED -> {
                        // Do not trust the broadcast payload alone.
                        if (remote.bondState != BluetoothDevice.BOND_BONDED) return
                        finishSuccessfully(remote, target)
                    }

                    BluetoothDevice.BOND_NONE -> {
                        val previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,BluetoothDevice.ERROR)

                        if (previousState == BluetoothDevice.BOND_BONDING
                            && remote.bondState == BluetoothDevice.BOND_NONE) {
                            finishWithFailure(BleFailure.PairingFailed)
                        }
                    }
                }
            } catch (_: SecurityException) {
                finishWithFailure(BleFailure.PermissionMissing)
            }
        }
    }

    /**
     * Starts pairing if necessary.
     * Already bonded devices are returned immediately.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        clearBondWaiting()

        if (!hasConnectPermission()) {
            onFailure(BleFailure.PermissionMissing)
            return
        }

        try {
            val remote = adapterProvider()
                ?.getRemoteDevice(device.address)
                ?: run {
                    onFailure(BleFailure.BluetoothUnsupported)
                    return
                }

            if (remote.bondState == BluetoothDevice.BOND_BONDED) {
                onSuccessBonding(remote, device)
                return
            }

            pendingDevice = device
            pendingRemote = remote

            ContextCompat.registerReceiver(
                context,
                bondReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true

            // Bonding may have completed during registration.
            val state = remote.bondState

            if (state == BluetoothDevice.BOND_BONDED) {
                finishSuccessfully(remote, device)
                return
            }

            onPairingStarted(device)

            if (state != BluetoothDevice.BOND_BONDING && !remote.createBond()) {
                finishWithFailure(BleFailure.PairingFailed)
                return
            }

            bondTimeout = scope.launch {
                delay(PAIRING_TIMEOUT_MS.milliseconds)
                if (pendingDevice?.address == device.address) {
                    finishWithFailure(BleFailure.TimedOut)
                }
            }
        } catch (_: SecurityException) {
            finishWithFailure(BleFailure.PermissionMissing)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid Bluetooth device", e)
            finishWithFailure(BleFailure.Unknown(e.message))
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot start pairing", e)
            finishWithFailure(BleFailure.PairingFailed)
        }
    }

    private fun finishSuccessfully(
        remote: BluetoothDevice,
        device: BleDevice,
    ) {
        clearBondWaiting()
        onSuccessBonding(remote, device)
    }

    private fun finishWithFailure(reason: BleFailure) {
        clearBondWaiting()
        onFailure(reason)
    }

    /**
     * Cancels application-side waiting.
     *
     * This does not necessarily cancel the OS bonding procedure.
     */
    fun clearBondWaiting() {
        bondTimeout?.cancel()
        bondTimeout = null

        pendingDevice = null
        pendingRemote = null

        if (receiverRegistered) {
            receiverRegistered = false

            runCatching {
                context.unregisterReceiver(bondReceiver)
            }.onFailure {
                Log.w(TAG, "Receiver unregister failed", it)
            }
        }
    }

    private companion object {
        const val TAG = "BlePairingManager"
        const val PAIRING_TIMEOUT_MS = 45_000L
    }
}
