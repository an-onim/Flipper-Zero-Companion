package dev.filpper.companion.utils

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.filpper.companion.domain.model.BleFailure
import javax.inject.Inject

class PermissionChecker @Inject constructor(
    private val context: Context
) {

    fun hasConnectPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || checkPermission(Manifest.permission.BLUETOOTH_CONNECT)

    fun hasScanPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return checkPermission(permission)
    }

    fun checkBluetoothReady(scan: Boolean, onFail: (BleFailure) -> Unit): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) || adapter == null) {
            onFail(BleFailure.BluetoothUnsupported)
            return false
        }
        if (!hasConnectPermission() || (scan && !hasScanPermission())) {
            onFail(BleFailure.PermissionMissing)
            return false
        }
        try {
            if (adapter?.isEnabled != true) {
                onFail(BleFailure.BluetoothDisabled)
                return false
            }
        } catch (_: SecurityException) {
            onFail(BleFailure.PermissionMissing)
            return false
        }
        if (scan && Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            val location = context.getSystemService(LocationManager::class.java)
            // TODO (refactor) handle versions
            if (location?.isLocationEnabled != true) {
                onFail(BleFailure.LocationServicesDisabled)
                return false
            }
        }
        return true
    }


    private fun checkPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED


    companion object {

        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    }
}