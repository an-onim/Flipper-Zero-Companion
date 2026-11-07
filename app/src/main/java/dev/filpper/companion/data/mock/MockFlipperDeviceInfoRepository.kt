package dev.filpper.companion.data.mock

import com.flipperdevices.protobuf.system.SystemProto
import dev.filpper.companion.domain.model.FlipperDeviceInfo
import dev.filpper.companion.domain.repository.FlipperDeviceInfoRepository
import javax.inject.Inject

/** Protobuf-shaped SAMPLE data; no RPC is performed. */
class MockFlipperDeviceInfoRepository @Inject constructor() : FlipperDeviceInfoRepository {
    override suspend fun loadDeviceInfo(): FlipperDeviceInfo {
        val fields = listOf(
            SystemProto.DeviceInfoResponse.newBuilder()
                .setKey("demo.firmware.version").setValue("1.2.0 (demo)").build(),
            SystemProto.DeviceInfoResponse.newBuilder()
                .setKey("demo.hardware.revision").setValue("Flipper Zero (demo)").build(),
            SystemProto.DeviceInfoResponse.newBuilder()
                .setKey("demo.serial").setValue("DEMO-00001").build(),
        ).associate { it.key to it.value }

        val power = SystemProto.PowerInfoResponse.newBuilder()
            .setKey("demo.battery.percent").setValue("85").build()

        return FlipperDeviceInfo(
            firmwareVersion = fields.getValue("demo.firmware.version"),
            hardwareRevision = fields.getValue("demo.hardware.revision"),
            serialNumber = fields.getValue("demo.serial"),
            batteryPercentage = power.value.toInt(),
        )
    }
}
