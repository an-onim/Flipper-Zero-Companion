
package dev.filpper.companion.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.filpper.companion.domain.model.BleDevice
import dev.filpper.companion.domain.model.BleFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class BleScanner(
    private val scope: CoroutineScope,
    private val adapterProvider: () -> BluetoothAdapter?,
    private val hasConnectPermission: () -> Boolean,
    private val onFailure: (BleFailure) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private var activeScanner: BluetoothLeScanner? = null
    private var activeCallback: ScanCallback? = null
    private var scanTimeout: Job? = null

    /**
     * Starts a new BLE scan.
     *
     * Preconditions and runtime permissions are checked by the caller.
     * Returns null if startScan() was initiated successfully.
     */
    @SuppressLint("MissingPermission")
    fun start(): BleFailure? {
        stop()
        _discoveredDevices.value = emptyList()

        val scanner = adapterProvider()?.bluetoothLeScanner ?: return BleFailure.BluetoothDisabled

        val callback = createScanCallback()

        return try {
            activeScanner = scanner
            activeCallback = callback
            _isScanning.value = true

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(emptyList(), settings, callback)

            scanTimeout = scope.launch {
                delay(SCAN_DURATION_MS.milliseconds)
                stop()
            }

            null
        } catch (_: SecurityException) {
            stop()
            BleFailure.PermissionMissing
        } catch (e: IllegalStateException) {
            stop()
            BleFailure.Unknown(e.message)
        }
    }

    private fun createScanCallback(): ScanCallback =
        object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                mainHandler.post {
                    if (activeCallback !== this) return@post

                    handleScanResult(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                mainHandler.post {
                    if (activeCallback !== this) return@post

                    stop()
                    onFailure(BleFailure.ScanFailed(errorCode))
                }
            }
        }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        if (!_isScanning.value) return

        if (!hasConnectPermission()) {
            stop()
            onFailure(BleFailure.PermissionMissing)
            return
        }

        try {
            val name = result.scanRecord?.deviceName ?: result.device.name
            if (name.isNullOrBlank()) return

            val device = BleDevice(
                name = name,
                address = result.device.address,
                rssi = result.rssi,
            )

            _discoveredDevices
                .update { devices ->
                    (devices
                        .filterNot { it.address == device.address } + device)
                        .sortedWith(
                        compareByDescending<BleDevice> { it.rssi }.thenBy { it.name }
                        )
            }
        } catch (_: SecurityException) {
            stop()
            onFailure(BleFailure.PermissionMissing)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        scanTimeout?.cancel()
        scanTimeout = null

        val scanner = activeScanner
        val callback = activeCallback

        activeScanner = null
        activeCallback = null
        _isScanning.value = false

        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback)
            } catch (e: SecurityException) {
                Log.w(TAG, "Scan permission revoked", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Bluetooth unavailable", e)
            }
        }
    }

    private companion object {
        const val TAG = "BleScanner"
        const val SCAN_DURATION_MS = 12_000L
    }
}
