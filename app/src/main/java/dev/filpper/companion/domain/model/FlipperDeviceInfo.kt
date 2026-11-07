package dev.filpper.companion.domain.model

data class FlipperDeviceInfo(
    val firmwareVersion: String,
    val hardwareRevision: String,
    val serialNumber: String,
    val batteryPercentage: Int,
)
