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
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.filpper.companion.data.Utils.toUInt
import dev.filpper.companion.data.bluetooth.BlePairingManager
import dev.filpper.companion.data.bluetooth.BleScanner
import dev.filpper.companion.data.bluetooth.FlipperGattUuid
import dev.filpper.companion.data.bluetooth.GattManager
import dev.filpper.companion.data.bluetooth.RpcStatus
import dev.filpper.companion.domain.model.BleConnectionState
import dev.filpper.companion.domain.model.BleDevice
import dev.filpper.companion.domain.model.BleFailure
import dev.filpper.companion.domain.repository.BleConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Coordinates BLE discovery, pairing and GATT connection.
 *
 * BLE scanning and pairing are delegated to dedicated components.
 * This repository owns the current GATT connection and exposes
 * its states to the domain layer.
 *
 * All internal Bluetooth lifecycle operations are serialized
 * on the main thread.
 *
 * TODO(transport): add RX/TX byte transport.
 * TODO(rpc): add protobuf framing and RPC client.
 */
class BleConnectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BleConnectionRepository {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    // ---------- Connection state ---------- //

    private val _connection = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    override val connection = _connection.asStateFlow()

    private val _rpcStatus = MutableStateFlow(RpcStatus.NOT_ACTIVE)
    override val rpcStatus = _rpcStatus.asStateFlow()

    // TODO(transport): move credit accounting to FlipperBleTransport.
    private var maxAvailableBytesToSend: UInt = 0u
    private var availableBytesToSend: UInt = 0u

    // ---------- BLE components ---------- //

    private val bleScanner = BleScanner(
        scope = scope,
        adapterProvider = { adapter },
        hasConnectPermission = ::hasConnectPermission,
        onFailure = { reason -> fail(reason) }
    )

    private val blePairingManager = BlePairingManager(
        context = context,
        scope = scope,
        adapterProvider = { adapter },
        hasConnectPermission = ::hasConnectPermission,
        onPairingStarted = { device -> saveConnectionState(BleConnectionState.Pairing(device)) },
        onSuccessBonding = ::startGattConnection,
        onFailure = { reason -> fail(reason) },
    )

    override val isScanning = bleScanner.isScanning
    override val discoveredDevices = bleScanner.discoveredDevices

    // ---------- GATT session ---------- //

    @Volatile
    private var currentGatt: BluetoothGatt? = null

    private var connectTimeout: Job? = null


    // ---------- Public API ---------- //

    override suspend fun startScan() {
        withContext(Dispatchers.Main) {
            bleScanner.stop()
            blePairingManager.clearBondWaiting()
            closeCurrentGatt()

            if (!checkBluetoothReady(scan = true)) {
                return@withContext
            }

            saveConnectionState(BleConnectionState.Disconnected)

            bleScanner.start()?.let { reason ->
                fail(reason)
            }
        }
    }

    override suspend fun stopScan() {
        withContext(Dispatchers.Main) {
            bleScanner.stop()
        }
    }

    override suspend fun connect(device: BleDevice) {
        withContext(Dispatchers.Main) {
            bleScanner.stop()
            blePairingManager.clearBondWaiting()
            closeCurrentGatt()

            if (!checkBluetoothReady(scan = false)) {
                return@withContext
            }

            blePairingManager.connect(device)
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.Main) {
            bleScanner.stop()
            blePairingManager.clearBondWaiting()
            closeCurrentGatt()
            saveConnectionState(BleConnectionState.Disconnected)
        }
    }

    override fun saveConnectionState(value: BleConnectionState) {
        Log.d(TAG, "Connection: $value")
        _connection.update { value }
    }

    private fun saveRpcState(value: RpcStatus) {
        Log.d(TAG, "RPC status: $value")
        _rpcStatus.update { value }

        if (value != RpcStatus.ACTIVE) {
            maxAvailableBytesToSend = 0u
        }
    }

    // ---------- Environment checks ---------- //

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

    // ---------- GATT connection ---------- //

    @SuppressLint("MissingPermission")
    private fun startGattConnection(remote: BluetoothDevice, device: BleDevice) {
        if (!checkBluetoothReady(scan = false)) return

        saveConnectionState(BleConnectionState.Connecting(device))

        try {
            val callback = createGattCallback(device)
            val gatt = remote.connectGatt(
                context,
                false,
                callback,
                BluetoothDevice.TRANSPORT_LE,
                BluetoothDevice.PHY_LE_1M_MASK,
                mainHandler,
            )

            if (gatt == null) {
                fail(BleFailure.ConnectionFailed(-1))
                return
            }

            currentGatt = gatt

            connectTimeout = scope.launch {
                delay(CONNECTION_TIMEOUT_MS.milliseconds)
                if (isCurrentGatt(gatt) && _connection.value is BleConnectionState.Connecting) {
                    failGatt(gatt, BleFailure.TimedOut)
                }
            }

        } catch (_: SecurityException) {
            fail(BleFailure.PermissionMissing)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot connect GATT", e)
            fail(BleFailure.ConnectionFailed(-1))
        }
    }

    private fun createGattCallback(device: BleDevice): BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!isCurrentGatt(gatt)) return

            when {
                status != BluetoothGatt.GATT_SUCCESS ->
                    failGatt(gatt, BleFailure.ConnectionFailed(status))

                newState == BluetoothProfile.STATE_CONNECTED -> discoverServices(gatt)
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    closeCurrentGatt()
                    saveConnectionState(BleConnectionState.Disconnected)
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!isCurrentGatt(gatt)) return

            if (status != BluetoothGatt.GATT_SUCCESS) {
                failGatt(gatt, BleFailure.ServiceDiscoveryFailed(status))
                return
            }

            if (!GattManager.validateGattConnection(gatt)) {
                failGatt(gatt, BleFailure.InvalidGattProfile)
                return
            }

            // Start sequential CCCD subscriptions:
            // TX -> FLOW_CONTROL -> RPC_STATUS.
            GattManager.initSubscribingQueue(gatt)
            subscribeNext(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt,descriptor: BluetoothGattDescriptor, status: Int) {
            if (!isCurrentGatt(gatt)) return

            if (status != BluetoothGatt.GATT_SUCCESS) {
                failGatt(gatt, BleFailure.EnableCCCDProblem)
                return
            }

            if (descriptor.uuid != FlipperGattUuid.BtSigConst.CCCD) return

            when (descriptor.characteristic.uuid) {
                FlipperGattUuid.Characteristic.TX.uuid,
                FlipperGattUuid.Characteristic.FLOW_CONTROL.uuid -> {
                    subscribeNext(gatt)
                }
                FlipperGattUuid.Characteristic.RPC_STATUS.uuid -> {
                    onEnableAllCCCD(gatt, device)
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) =
            handleCharacteristicRead(gatt, characteristic, characteristic.value.copyOf(), status)

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            handleCharacteristicRead(gatt, characteristic, value, status)

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) =
            handleCharacteristicChanged(gatt, characteristic, characteristic.value.copyOf())

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) =
            handleCharacteristicChanged(gatt, characteristic, value)

        // TODO(transport): onCharacteristicWrite() will be
        // handled by the future BLE write queue.
    }

    // ---------- GATT initialization ---------- //

    @SuppressLint("MissingPermission")
    private fun discoverServices(gatt: BluetoothGatt) {
        try {
            if (!gatt.discoverServices()) {
                failGatt(gatt, BleFailure.ServiceDiscoveryFailed(-1))
            }
        } catch (_: SecurityException) {
            failGatt(gatt, BleFailure.PermissionMissing)
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeNext(gatt: BluetoothGatt) {
        try {
            GattManager
                .processNextSubscription(gatt)
                ?.let { error ->failGatt(gatt, error) }
        } catch (_: SecurityException) {
            failGatt(gatt, BleFailure.PermissionMissing)
        }
    }

    private fun handleCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        if (!isCurrentGatt(gatt)) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            failGatt(gatt, BleFailure.RPCError)
            return
        }

        when (characteristic.uuid) {
            FlipperGattUuid.Characteristic.RPC_STATUS.uuid -> RpcStatus.fromBytes(value)?.let {
                saveRpcState(it)
                if (it == RpcStatus.ACTIVE) {
                    if (!GattManager.readFlowControl(gatt)) {
                        failGatt(gatt, BleFailure.RPCReadError("FlowControl"))
                    }
                }
            } ?: Log.e(TAG, "Cannot get RPC status from ${value.contentToString()}")
            FlipperGattUuid.Characteristic.FLOW_CONTROL.uuid -> handleFlowControlValue(value)
        }
    }

    private fun handleCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (!isCurrentGatt(gatt)) return

        when (characteristic.uuid) {
            FlipperGattUuid.Characteristic.TX.uuid -> handleIncoming(value)
            FlipperGattUuid.Characteristic.RPC_STATUS.uuid -> RpcStatus.fromBytes(value)?.let {
                saveRpcState(it)
                if (it == RpcStatus.ACTIVE) {
                    GattManager.readFlowControl(gatt).let{ result ->
                        if (!result) failGatt(gatt, BleFailure.RPCReadError("FlowControl"))
                    }
                }
            } ?: Log.e(TAG, "Cannot get RPC status from ${value.contentToString()}")
            FlipperGattUuid.Characteristic.FLOW_CONTROL.uuid -> handleFlowControlValue(value)
        }
    }

    private fun onEnableAllCCCD(gatt: BluetoothGatt, device: BleDevice) {
        Log.d(TAG, "All CCCD subscriptions enabled")
        connectTimeout?.cancel()
        connectTimeout = null
        saveConnectionState(BleConnectionState.Connected(device))

        // extra read RPC_STATUS characteristic, see result in onCharacteristicRead()
        try {
            if (!GattManager.readRpcStatus(gatt)) {
                failGatt(gatt, BleFailure.RPCReadError("RpcStatus"))
            }
        } catch (_: SecurityException) {
            failGatt(gatt, BleFailure.PermissionMissing)
        }
    }

    private fun handleFlowControlValue(value: ByteArray) {
        if (_rpcStatus.value != RpcStatus.ACTIVE) return

        maxAvailableBytesToSend = value.toUInt(ByteOrder.BIG_ENDIAN) ?: 0u

        Log.d(TAG, "FLOW_CONTROL: $maxAvailableBytesToSend bytes")

        // TODO(transport): notify sender and update
        // the active transmission credit window.
    }

    private fun handleIncoming(value: ByteArray) {
        // TODO(transport): forward TX bytes to incoming Flow/Channel.
        // TODO(rpc): decode framed protobuf messages later.
    }

    // ---------- GATT cleanup ---------- //

    private fun isCurrentGatt(gatt: BluetoothGatt): Boolean = currentGatt === gatt

    @SuppressLint("MissingPermission")
    private fun closeCurrentGatt() {
        connectTimeout?.cancel()
        connectTimeout = null

        saveRpcState(RpcStatus.NOT_ACTIVE)
        maxAvailableBytesToSend = 0u

        GattManager.resetSubscriptions()

        val gatt = currentGatt
        currentGatt = null

        try {
            gatt?.disconnect()
        } catch (e: SecurityException) {
            Log.w(TAG, "Disconnect permission missing", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "GATT already unavailable", e)
        } finally {
            gatt?.close()
        }
    }

    private fun failGatt(gatt: BluetoothGatt, reason: BleFailure) {
        if (!isCurrentGatt(gatt)) return
        Log.w(TAG, "GATT failure: $reason")
        closeCurrentGatt()
        saveConnectionState(BleConnectionState.Failed(reason))
    }

    private fun fail(reason: BleFailure) {
        Log.w(TAG, "BLE failure: $reason")
        blePairingManager.clearBondWaiting()
        closeCurrentGatt()
        saveConnectionState(BleConnectionState.Failed(reason))
    }

    private companion object {
        const val TAG = "BleConnectionRepository"
        const val CONNECTION_TIMEOUT_MS = 25_000L
    }
}
