package dev.filpper.companion.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.filpper.companion.data.Utils.toUInt
import dev.filpper.companion.data.bluetooth.FlipperGattUuid
import dev.filpper.companion.data.bluetooth.FlipperGattUuid.getCharacteristicByUuid
import dev.filpper.companion.data.bluetooth.GattManager
import dev.filpper.companion.data.bluetooth.RpcStatus
import dev.filpper.companion.domain.model.BleConnectionState
import dev.filpper.companion.domain.model.BleDevice
import dev.filpper.companion.domain.model.BleFailure
import dev.filpper.companion.domain.repository.BleConnectionRepository
import dev.filpper.companion.domain.store.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Native BLE transport starter: real scanning, system bonding, GATT connection, service discovery.
 * NOT an RPC client. This singleton owns callbacks so navigating away doesn't leak a ViewModel.
 * Requires foreground permission handling in the presentation layer and defensive checks here.
 */
class BleConnectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleConnectionRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _isScanning = MutableStateFlow(false)
    override val isScanning = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val discoveredDevices = _discoveredDevices.asStateFlow()

    private val lastDeviceAddress = stringPreferencesKey(SettingsStore.Key.LAST_DEVICE_ADDRESS)
    private val _connection = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connection = _connection.asStateFlow()

    private val _rpcStatus = MutableStateFlow(RpcStatus.NOT_ACTIVE)
    override val rpcStatus = _rpcStatus.asStateFlow()
    // TODO(rcp) if status=0 forbid new rpc-commands, to unready state; 1 - make session active

    private var maxAvailableBytesToSend: UInt = 0u
    private var availableBytesToSend: UInt = 0u


    private var activeScanner: BluetoothLeScanner? = null
    private var scanTimeout: Job? = null
    private var bondTimeout: Job? = null
    private var connectTimeout: Job? = null
    private var pendingDevice: BleDevice? = null
    private var receiverRegistered = false

    private val gattLock = Any()
    private var currentGatt: BluetoothGatt? = null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasConnectPermission()) return
            try {
                // Do not filter by name prefix: user-customized Flipper names can differ.
                val name = result.scanRecord?.deviceName ?: result.device.name
                if (name.isNullOrBlank()) return
                val device = BleDevice(name = name, address = result.device.address, rssi = result.rssi)
                _discoveredDevices.update { existing ->
                    (existing.filterNot { it.address == device.address } + device)
                        .sortedWith(compareByDescending<BleDevice> { it.rssi }.thenBy { it.name })
                }
            } catch (_: SecurityException) {
                // User may revoke Nearby Devices while scan is running
                saveConnectionState(BleConnectionState.Failed(BleFailure.PermissionMissing))
            }
        }

        override fun onScanFailed(errorCode: Int) {
            stopScanInternal()
            saveConnectionState(BleConnectionState.Failed(BleFailure.ScanFailed(errorCode)))
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            if (!hasConnectPermission()) {
                fail(BleFailure.PermissionMissing)
                return
            }
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val target = pendingDevice ?: return
            if (device.address != target.address) return

            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                BluetoothDevice.BOND_BONDED -> {
                    // Verify real adapter state: the dynamically registered receiver is exported.
                    if (device.bondState != BluetoothDevice.BOND_BONDED) return
                    clearBondWaiting()
                    startGattConnection(device, target)
                }
                BluetoothDevice.BOND_NONE -> fail(BleFailure.PairingFailed)
            }
        }
    }

    override suspend fun startScan() {
        stopScanInternal()
        if (!checkBluetoothReady(scan = true)) return
        _discoveredDevices.value = emptyList()
        saveConnectionState(BleConnectionState.Disconnected)
        try {
            val scanner = adapter?.bluetoothLeScanner
            if (scanner == null) {
                fail(BleFailure.BluetoothDisabled)
                return
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            activeScanner = scanner
            _isScanning.value = true
            scanner.startScan(emptyList(), settings, scanCallback)
            scanTimeout = scope.launch {
                delay(SCAN_DURATION_MS.milliseconds)
                stopScanInternal()
            }
        } catch (_: SecurityException) {
            stopScanInternal()
            fail(BleFailure.PermissionMissing)
        } catch (e: IllegalStateException) {
            stopScanInternal()
            fail(BleFailure.Unknown())
        }
    }

    override suspend fun stopScan() = stopScanInternal()

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: BleDevice) {
        stopScanInternal()
        if (!checkBluetoothReady(scan = false)) return
        clearBondWaiting()
        closeCurrentGatt()

        try {
            val remote = adapter?.getRemoteDevice(device.address) ?: run {
                fail(BleFailure.BluetoothUnsupported)
                return
            }
            if (remote.bondState == BluetoothDevice.BOND_BONDED) {
                startGattConnection(remote, device)
                return
            }

            pendingDevice = device
            saveConnectionState(BleConnectionState.Pairing(device))
            ContextCompat.registerReceiver(
                context,
                bondReceiver,
                IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
            if (remote.bondState != BluetoothDevice.BOND_BONDING && !remote.createBond()) {
                fail(BleFailure.PairingFailed)
                return
            }
            bondTimeout = scope.launch {
                delay(PAIRING_TIMEOUT_MS.milliseconds)
                if (_connection.value is BleConnectionState.Pairing) fail(BleFailure.TimedOut)
            }
        } catch (e: SecurityException) {
            fail(BleFailure.PermissionMissing, e)
        } catch (e: IllegalArgumentException) {
            fail(BleFailure.Unknown(e.message), e)
        }
    }

    override suspend fun disconnect() {
        stopScanInternal()
        clearBondWaiting()
        closeCurrentGatt()
        saveConnectionState(BleConnectionState.Disconnected)
    }

    fun updateAvailableBytesBound(value: ByteArray) {
        maxAvailableBytesToSend = value.toUInt(ByteOrder.BIG_ENDIAN) ?: 0u
    }

    fun saveRpcState(value: RpcStatus) {
        Log.v("OPOPO", "BleConnectionRepo -> saveRpcState $value")
        _rpcStatus.value = value
    }

    override fun saveConnectionState(value: BleConnectionState) {
        Log.v("OPOPO", "BleConnectionRepo -> saveConnectionState $value")
        _connection.value = value
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkBluetoothReady(scan: Boolean): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) || adapter == null) {
            fail(BleFailure.BluetoothUnsupported)
            return false
        }
        if (!hasConnectPermission() || (scan && !hasScanPermission())) {
            fail(BleFailure.PermissionMissing)
            return false
        }
        try {
            if (adapter?.isEnabled != true) {
                fail(BleFailure.BluetoothDisabled)
                return false
            }
        } catch (_: SecurityException) {
            fail(BleFailure.PermissionMissing)
            return false
        }
        if (scan && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            val location = context.getSystemService(LocationManager::class.java)
            if (location?.isLocationEnabled != true) {
                fail(BleFailure.LocationServicesDisabled)
                return false
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        scanTimeout?.cancel()
        scanTimeout = null
        val scanner = activeScanner
        activeScanner = null
        _isScanning.value = false
        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Log.e("OPOPO", "BleConnectionRepo -> stopScanInternal. Permission revoked during scanning; the OS stops the scan. ${e.message} ${e.stackTraceToString()}")
            } catch (e: IllegalStateException) {
                Log.e("OPOPO", "BleConnectionRepo -> stopScanInternal. Bluetooth was disabled during scanning. ${e.message} ${e.stackTraceToString()}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGattConnection(remote: BluetoothDevice, device: BleDevice) {
        if (!checkBluetoothReady(scan = false)) return
        saveConnectionState(BleConnectionState.Connecting(device))
        try {
            val callback = object : BluetoothGattCallback() {

                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (!isCurrentGatt(gatt)) return
                    Log.v("OPOPO", "BleConnectionRepo -> onConnectionStateChange status=$status newState=$newState")
                    when {
                        status != BluetoothGatt.GATT_SUCCESS -> failGatt(
                            gatt,
                            BleFailure.ConnectionFailed(status),
                        )
                        newState == BluetoothProfile.STATE_CONNECTED -> {
                            try {
                                if (!gatt.discoverServices()) {
                                    failGatt(gatt, BleFailure.ServiceDiscoveryFailed(-1))
                                }
                            } catch (_: SecurityException) {
                                failGatt(gatt, BleFailure.PermissionMissing)
                            }
                        }
                        newState == BluetoothProfile.STATE_DISCONNECTED -> {
                            closeCurrentGatt()
                            saveConnectionState(BleConnectionState.Disconnected)
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (!isCurrentGatt(gatt)) return
                    Log.v("OPOPO", "BleConnectionRepo -> onServicesDiscovered status=$status")
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (!GattManager.validateGattConnection(gatt)) {
                            failGatt(gatt, BleFailure.InvalidGattProfile)
                            return
                        }

                        // enable CCCD notifications: start here and continue in onDescriptorWrite
                        GattManager.initSubscribingQueue(gatt)
                        GattManager.processNextSubscription(gatt)?.let { error ->
                            // if we have error as result - handle it
                            failGatt(gatt, error)
                        }

                        // save connected state only after all cccd are set in onDescriptorWrite
                    } else {
                        failGatt(gatt, BleFailure.ServiceDiscoveryFailed(status))
                    }
                }

                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
                    handleCharacteristicRead(gatt, characteristic, characteristic.value, status)

                override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
                    handleCharacteristicRead(gatt, characteristic, value, status)


                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) =
                    handleCharacteristicChanged(gatt, characteristic, characteristic.value)

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) =
                    handleCharacteristicChanged(gatt, characteristic, value)

                override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor?, status: Int) {
                    if (!isCurrentGatt(gatt)) return
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        failGatt(gatt, BleFailure.EnableCCCDProblem)
                        return
                    }

                    Log.v("OPOPO", "BleConnectionRepo -> onDescriptorWrite descriptor ${descriptor?.uuid} char=${descriptor?.characteristic?.uuid?.getCharacteristicByUuid()}")

                    if (descriptor?.uuid == FlipperGattUuid.BtSigConst.CCCD) {
                        when(descriptor.characteristic?.uuid) {
                            FlipperGattUuid.Characteristic.TX.uuid -> {
                                // start enabling cccd for the next characteristic
                                GattManager.processNextSubscription(gatt)?.let { error ->
                                    // if we have error as result - handle it
                                    failGatt(gatt, error)
                                }
                            }
                            FlipperGattUuid.Characteristic.FLOW_CONTROL.uuid -> {
                                // start enabling cccd for the next characteristic
                                GattManager.processNextSubscription(gatt)?.let { error ->
                                    // if we have error as result - handle it
                                    failGatt(gatt, error)
                                }
                            }
                            FlipperGattUuid.Characteristic.RPC_STATUS.uuid -> {
                                onEnableAllCCCD(gatt, device)
                            }
                        }
                    }
                }

            }

            val gatt = remote.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                fail(BleFailure.ConnectionFailed(-1))
                return
            }
            synchronized(gattLock) { currentGatt = gatt }
            connectTimeout = scope.launch {
                delay(CONNECTION_TIMEOUT_MS.milliseconds)
                if (_connection.value is BleConnectionState.Connecting) {
                    failGatt(gatt, BleFailure.TimedOut)
                }
            }
        } catch (_: SecurityException) {
            fail(BleFailure.PermissionMissing)
        } catch (_: IllegalStateException) {
            fail(BleFailure.ConnectionFailed(-1))
        }
    }

    private fun handleCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        if (!isCurrentGatt(gatt)) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failGatt(gatt, BleFailure.RPCError)
            return
        }
        Log.v("OPOPO", "BleConnectionRepo -> onCharacteristicRead characteristic ${characteristic.uuid} value=${value.contentToString()}")

        when(characteristic.uuid) {
            FlipperGattUuid.Characteristic.RPC_STATUS.uuid -> RpcStatus.fromBytes(value)?.let {
                saveRpcState(it)
                if (it == RpcStatus.ACTIVE) {
                    GattManager.readFlowControl(gatt).let{ result ->
                        if (!result) failGatt(gatt, BleFailure.RPCReadError("FlowControl"))
                    }
                }
            } ?: Log.e("OPOPO", "Cannot get RPC status from ${value.contentToString()}")
            FlipperGattUuid.Characteristic.FLOW_CONTROL.uuid -> updateAvailableBytesBound(value)
        }
    }

    private fun handleCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (!isCurrentGatt(gatt)) return
        Log.v("OPOPO", "BleConnectionRepo -> onCharacteristicChanged characteristic ${characteristic.uuid} value=${value.contentToString()}")

        when(characteristic.uuid) {
            FlipperGattUuid.Characteristic.TX.uuid -> handleIncoming(value)
            FlipperGattUuid.Characteristic.RPC_STATUS.uuid -> RpcStatus.fromBytes(value)?.let {
                saveRpcState(it)
                if (it == RpcStatus.ACTIVE) {
                    GattManager.readFlowControl(gatt).let{ result ->
                        if (!result) failGatt(gatt, BleFailure.RPCReadError("FlowControl"))
                    }
                }
            } ?: Log.e("OPOPO", "Cannot get RPC status from ${value.contentToString()}")
            FlipperGattUuid.Characteristic.FLOW_CONTROL.uuid -> updateAvailableBytesBound(value)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun onEnableAllCCCD(gatt: BluetoothGatt, device: BleDevice) {
        Log.v("OPOPO", "BleConnectionRepo -> onEnableAllCCCD. all cccd are set - now we are fully connected")
        connectTimeout?.cancel()
        connectTimeout = null
        saveConnectionState(BleConnectionState.Connected(device))

        // extra read RPC_STATUS characteristic, see result in onCharacteristicRead()
        GattManager.readRpcStatus(gatt).let { result ->
            if (!result) failGatt(gatt, BleFailure.RPCReadError("RpcStatus"))
        }
        // read initial FLOW_CONTROL is after successful readRpcStatus - see onCharacteristicRead()
    }

    private fun handleIncoming(value: ByteArray) {
        // TODO(rpc) handle incoming bytes from TX
    }

    private fun isCurrentGatt(gatt: BluetoothGatt): Boolean = synchronized(gattLock) {
        currentGatt === gatt
    }

    @SuppressLint("MissingPermission")
    private fun closeCurrentGatt() {
        connectTimeout?.cancel()
        connectTimeout = null

        saveRpcState(RpcStatus.NOT_ACTIVE)
        maxAvailableBytesToSend = 0u

        val gatt = synchronized(gattLock) {
            currentGatt.also { currentGatt = null }
        }
        try {
            gatt?.disconnect()
        } catch (e: SecurityException) {
            Log.e("OPOPO", "BleConnectionRepo -> stopScanInternal. ${e.message} ${e.stackTraceToString()}")
        } finally {
            gatt?.close()
        }
    }

    private fun clearBondWaiting() {
        bondTimeout?.cancel()
        bondTimeout = null
        pendingDevice = null
        if (receiverRegistered) {
            receiverRegistered = false
            runCatching { context.unregisterReceiver(bondReceiver) }
        }
    }


    private fun failGatt(gatt: BluetoothGatt, reason: BleFailure) {
        if (!isCurrentGatt(gatt)) return
        Log.v("OPOPO", "BleConnectionRepo -> failGatt reason=$reason")
        closeCurrentGatt()
        saveConnectionState(BleConnectionState.Failed(reason))
    }

    private fun fail(reason: BleFailure, exception: Exception? = null) {
        Log.v("OPOPO", "BleConnectionRepo -> fail reason=$reason")
        exception?.let { e -> Log.e("OPOPO", "BleConnectionRepo -> connect error: ${e.message} ${e.stackTraceToString()}") }
        clearBondWaiting()
        saveConnectionState(BleConnectionState.Failed(reason))
    }

    private companion object {
        const val SCAN_DURATION_MS = 12_000L
        const val PAIRING_TIMEOUT_MS = 45_000L
        const val CONNECTION_TIMEOUT_MS = 25_000L
    }
}
