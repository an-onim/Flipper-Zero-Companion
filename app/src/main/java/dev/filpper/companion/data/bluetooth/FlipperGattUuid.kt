package dev.filpper.companion.data.bluetooth

import java.util.UUID

/**
 * From flipperzero-firmware/targets/f7/ble_glue/services/serial_service_uuid.inc
 * (https://github.com/flipperdevices/flipperzero-firmware/blob/dev)
 */
object FlipperGattUuid {

    val SERVICE = UUID.fromString("8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000")

    enum class Characteristic(val uuid: UUID) {
        RX(UUID.fromString("19ed82ae-ed21-4c9d-4145-228e62fe0000")),
        TX(UUID.fromString("19ed82ae-ed21-4c9d-4145-228e61fe0000")),
        FLOW_CONTROL(UUID.fromString("19ed82ae-ed21-4c9d-4145-228e63fe0000")),
        RPC_STATUS(UUID.fromString("19ed82ae-ed21-4c9d-4145-228e64fe0000"));
    }
    fun UUID.getCharacteristicByUuid() = Characteristic.entries.find { it.uuid == this }

    object BtSigConst {
        val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

}