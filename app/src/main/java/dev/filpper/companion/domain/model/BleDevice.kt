package dev.filpper.companion.domain.model

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)
