package dev.filpper.companion.domain.usecase

import android.util.Log
import com.flipperdevices.protobuf.FlipperProto
import com.flipperdevices.protobuf.system.SystemProto
import dev.filpper.companion.domain.model.FlipperDeviceInfo
import dev.filpper.companion.domain.repository.FlipperFileRepository
import dev.filpper.companion.domain.repository.FlipperRepository
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import javax.inject.Inject

class PingUseCase @Inject constructor(private val repository: FlipperRepository) {
    suspend operator fun invoke() {
        val commandId = repository.nextCommandId.getAndIncrement()
        val ping = FlipperProto.Main.newBuilder()
            .setCommandId(commandId)
            .setSystemPingRequest(SystemProto.PingRequest.getDefaultInstance())
            .build()
        val failure = repository.sendMessage(ping)
        if (failure != null) {
            Log.e("PingUseCase", "RPC Ping send failed: $failure")
        }
    }
}


class GetFlipperInfoUseCase @Inject constructor(private val repository: FlipperRepository) {
    suspend operator fun invoke(): FlipperDeviceInfo {
        val commandId = repository.nextCommandId.getAndIncrement()
        val command = FlipperProto.Main.newBuilder()
            .setCommandId(commandId)
            .setSystemDeviceInfoRequest(SystemProto.DeviceInfoRequest.getDefaultInstance())
            .build()

        val values = linkedMapOf<String, String>()
        // subscribing and sending message
        repository.messages
            .onSubscription {
                val failure = repository.sendMessage(command)
                if(failure == null) {
                    Log.e("GetFlipperInfoUseCase", "DeviceInfo request failed: $failure" )
                }
            }
            .filter { it.commandId == commandId }
            .onEach { response ->
                check(response.commandStatus == FlipperProto.CommandStatus.OK) { "RPC error: ${response.commandStatus}" }
                check(response.hasSystemDeviceInfoResponse()) { "Unexpected DeviceInfo response" }

                val data = response.systemDeviceInfoResponse
                values[data.key] = data.value
            }
            .first {!it.hasNext}
        Log.v("GetFlipperInfoUseCase", "DeviceInfo result: ${values.entries.joinToString(prefix = "{", postfix = "}") { "${it.key} - ${it.value}, " }}")

        val failure = repository.sendMessage(command)
        if (failure != null) {
            Log.e("GetFlipperInfoUseCase", "RPC DeviceInfo send failed: $failure")
        }

        // TODO(transport): parsing.
        return FlipperDeviceInfo(
            firmwareVersion = "1.2.0 (demo)",
            hardwareRevision = "Flipper Zero (demo)",
            serialNumber = "DEMO-00001",
            batteryPercentage = 85,
        )
    }
}

class GetFlipperFilesUseCase @Inject constructor(private val repository: FlipperFileRepository) {
    suspend operator fun invoke() = repository.loadFiles()
}
