package dev.filpper.companion.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import dev.filpper.companion.data.bluetooth.FlipperGattUuid.getCharacteristicByUuid
import dev.filpper.companion.domain.model.BleFailure

object GattManager {

    private val notifySubscriptionQueue = ArrayDeque<BluetoothGattCharacteristic>()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun initSubscribingQueue(gatt: BluetoothGatt) {
        notifySubscriptionQueue.clear()

        // start from TX and after that FLOW_CONTROL and RPC_STATUS
        val service = gatt.getService(FlipperGattUuid.SERVICE)
        notifySubscriptionQueue.addAll(
            listOf(
                service.getCharacteristic(FlipperGattUuid.Characteristic.TX.uuid),
                service.getCharacteristic(FlipperGattUuid.Characteristic.FLOW_CONTROL.uuid),
                service.getCharacteristic(FlipperGattUuid.Characteristic.RPC_STATUS.uuid),
            )
        )
    }

    /** @return `null` if operation of enabling is initiated, [BleFailure] - otherwise */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun processNextSubscription(gatt: BluetoothGatt): BleFailure? {
        val characteristic = notifySubscriptionQueue.removeFirstOrNull() ?: return BleFailure.Unknown("No characteristics in queue to make subscriptions")
        Log.v("OPOPO", "BleSubscriptionManager -> processNextSubscription process char ${characteristic.uuid.getCharacteristicByUuid()}")

        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(FlipperGattUuid.BtSigConst.CCCD) ?: run {
            // if we don't have the descriptor - return
            return BleFailure.Unknown("No description CCCD for characteristics ${characteristic.uuid.getCharacteristicByUuid()}")
        }
        Log.v("OPOPO", "BleSubscriptionManager -> processNextSubscription process CCCD description was found")

        // make a decision: Notify (0x01, 0x00) or Indicate (0x02, 0x00)
        val isIndicate = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
        Log.v("OPOPO", "BleSubscriptionManager -> processNextSubscription set ${if (isIndicate) "INDICATION" else "NOTIFICATION"}")
        val payload = if (isIndicate) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        val operationSuccessInit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, payload) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = payload
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }

        return if (operationSuccessInit) null else BleFailure.EnableCCCDProblem
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    /** @return true, if the read operation was initiated successfully */
    fun readRpcStatus(gatt: BluetoothGatt): Boolean {
        val char = gatt.getService(FlipperGattUuid.SERVICE).getCharacteristic(FlipperGattUuid.Characteristic.RPC_STATUS.uuid)
        return gatt.readCharacteristic(char)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    /** @return true, if the read operation was initiated successfully */
    fun readFlowControl(gatt: BluetoothGatt): Boolean {
        val char = gatt.getService(FlipperGattUuid.SERVICE).getCharacteristic(FlipperGattUuid.Characteristic.FLOW_CONTROL.uuid)
        return gatt.readCharacteristic(char)
    }


    /** @return verdict about success of connection by checking necessary uuids */
    fun validateGattConnection(gatt: BluetoothGatt): Boolean {
        Log.v(
            "OPOPO", "BleConnectionRepo -> validateGattConnection services: ${
                gatt.services.joinToString("\n") {
                    "{ ${it.uuid} ${it.type} ${it.instanceId} chars = [${
                        it.characteristics.joinToString(", ") { c ->
                            "c{ uuid=${c.uuid} value=${c.value} permissions=${c.permissions} props=${c.properties} descriptors=${
                                c.descriptors.joinToString(", ") { d ->
                                    "d{ uuid=${d.uuid} value=${d.value} permissions=${d.permissions} }"
                                }
                            } }"
                        }
                    }] }"
                }
            }")

        return gatt.getService(FlipperGattUuid.SERVICE)
            ?.let { service ->
                val containsChars = FlipperGattUuid.Characteristic.entries.mapNotNull { service.getCharacteristic(it.uuid) }.size == FlipperGattUuid.Characteristic.entries.size
                val containsCCCD = service.getCharacteristic(FlipperGattUuid.Characteristic.TX.uuid)?.getDescriptor(FlipperGattUuid.BtSigConst.CCCD) != null
                        && service.getCharacteristic(FlipperGattUuid.Characteristic.FLOW_CONTROL.uuid)?.getDescriptor(FlipperGattUuid.BtSigConst.CCCD) != null
                        && service.getCharacteristic(FlipperGattUuid.Characteristic.RPC_STATUS.uuid)?.getDescriptor(FlipperGattUuid.BtSigConst.CCCD) != null
                containsChars && containsCCCD
            } ?: false
    }

}