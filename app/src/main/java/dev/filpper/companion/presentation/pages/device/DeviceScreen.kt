package dev.filpper.companion.presentation.pages.device

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.filpper.companion.R
import dev.filpper.companion.domain.model.BleConnectionState
import dev.filpper.companion.domain.model.BleDevice
import dev.filpper.companion.domain.model.BleFailure
import dev.filpper.companion.presentation.components.FlipperOutlinedButton
import dev.filpper.companion.presentation.components.FlipperPrimaryButton
import dev.filpper.companion.presentation.components.InfoPanel
import dev.filpper.companion.presentation.components.InfoRow
import dev.filpper.companion.presentation.components.SectionTitle

@Composable
fun DeviceRoute(viewModel: DeviceViewModel = hiltViewModel()) {

    // TODO(rpc): check connection state

    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var enableDenied by rememberSaveable { mutableStateOf(false) }

    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            enableDenied = false
            viewModel.onIntent(DeviceIntent.StartScan)
        } else {
            enableDenied = true
        }
    }

    // Runs only from UI events / permission results, never inside composition.
    val beginScan = {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter?.isEnabled == true) {
            viewModel.onIntent(DeviceIntent.StartScan)
        } else if (adapter != null) {
            enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            // Repository will expose BluetoothUnsupported as a typed failure.
            viewModel.onIntent(DeviceIntent.StartScan)
        }
    }
    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { outcome ->
        if (permissions.all { outcome[it] == true }) {
            permissionDenied = false
            beginScan()
        } else {
            permissionDenied = true
        }
    }

    DeviceScreen(
        state = uiState,
        permissionDenied = permissionDenied,
        enableDenied = enableDenied,
        onScan = {
            val granted = permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) beginScan() else requestPermissions.launch(permissions)
        },
        onStopScan = { viewModel.onIntent(DeviceIntent.StopScan) },
        onConnect = { viewModel.onIntent(DeviceIntent.SelectDevice(it)) },
        onDisconnect = { viewModel.onIntent(DeviceIntent.Disconnect) },
        onRefresh = { viewModel.onIntent(DeviceIntent.RefreshInfo) },
    )
}

@Composable
private fun DeviceScreen(
    state: DeviceUiState,
    permissionDenied: Boolean,
    enableDenied: Boolean,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.connection_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.connection_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val connection = state.connection) {
            is BleConnectionState.Connected -> {
                Text(
                    stringResource(R.string.connected_status),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
                SectionTitle(stringResource(R.string.device_info_title))
                InfoPanel {
                    InfoRow(stringResource(R.string.device_name_label), connection.device.name)
                    InfoRow(stringResource(R.string.device_address_label), connection.device.address)
                    when (val info = state.info) {
                        DeviceInfoState.None, DeviceInfoState.Loading -> {
                            Text(stringResource(R.string.loading_info))
                        }
                        is DeviceInfoState.Ready -> {
                            InfoRow(stringResource(R.string.firmware_label), info.data.firmwareVersion)
                            InfoRow(stringResource(R.string.hardware_label), info.data.hardwareRevision)
                            InfoRow(stringResource(R.string.serial_label), info.data.serialNumber)
                            InfoRow(
                                stringResource(R.string.battery_label),
                                stringResource(R.string.percentage, info.data.batteryPercentage),
                            )
                            Text(
                                stringResource(R.string.device_info_mock),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DeviceInfoState.Failed -> Text(stringResource(R.string.bluetooth_unknown_error))
                    }
                }
                FlipperPrimaryButton(stringResource(R.string.refresh_info), onRefresh)
                FlipperOutlinedButton(stringResource(R.string.disconnect_device), onDisconnect)
            }
            is BleConnectionState.Pairing -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.device_pairing, connection.device.name))
                FlipperOutlinedButton(stringResource(R.string.disconnect_device), onDisconnect)
            }
            is BleConnectionState.Connecting -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.device_connecting, connection.device.name))
                FlipperOutlinedButton(stringResource(R.string.disconnect_device), onDisconnect)
            }
            BleConnectionState.Disconnected, is BleConnectionState.Failed -> {
                if (connection is BleConnectionState.Failed) {
                    Text(
                        failureText(connection.failure),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (permissionDenied) {
                    Text(stringResource(R.string.permission_denied), color = MaterialTheme.colorScheme.error)
                }
                if (enableDenied) {
                    Text(stringResource(R.string.bluetooth_enable_denied), color = MaterialTheme.colorScheme.error)
                }
                if (state.isScanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.scanning))
                    FlipperOutlinedButton(stringResource(R.string.stop_scan), onStopScan)
                } else {
                    FlipperPrimaryButton(
                        label = stringResource(
                            if (state.devices.isEmpty()) R.string.connect_device else R.string.scan_again,
                        ),
                        onClick = onScan,
                    )
                }
                if (state.isScanning || state.devices.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.available_devices))
                    if (state.devices.isEmpty()) Text(stringResource(R.string.no_devices_yet))
                    state.devices.forEach { device ->
                        Card(onClick = { onConnect(device) }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.Bluetooth, contentDescription = null)
                                Column {
                                    Text(device.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun failureText(failure: BleFailure): String = when (failure) {
    BleFailure.BluetoothUnsupported -> stringResource(R.string.bluetooth_not_supported)
    BleFailure.BluetoothDisabled -> stringResource(R.string.bluetooth_disabled)
    BleFailure.PermissionMissing -> stringResource(R.string.bluetooth_permission_missing)
    BleFailure.LocationServicesDisabled -> stringResource(R.string.permission_location_off)
    BleFailure.PairingFailed -> stringResource(R.string.bluetooth_pairing_failed)
    BleFailure.TimedOut -> stringResource(R.string.bluetooth_timeout)
    is BleFailure.ScanFailed -> stringResource(R.string.bluetooth_scan_failed, failure.code)
    is BleFailure.ConnectionFailed -> stringResource(R.string.bluetooth_connect_failed, failure.code)
    is BleFailure.ServiceDiscoveryFailed -> stringResource(R.string.bluetooth_services_failed, failure.code)
    is BleFailure.Unknown -> stringResource(R.string.bluetooth_unknown_error)
}
