package dev.filpper.companion.data.bluetooth

enum class RpcStatus(val code: UInt) {
    NOT_ACTIVE(0u),
    ACTIVE(1u),
    UNKNOWN(UInt.MAX_VALUE);

    companion object {
        fun fromBytes(bytes: ByteArray): RpcStatus? {
            val firstByte = bytes.firstOrNull() ?: return null
            val code = firstByte.toUByte().toUInt()
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }

        fun fromInt(value: Int): RpcStatus = when(value) {
            0 -> NOT_ACTIVE
            1 -> ACTIVE
            else -> UNKNOWN
        }
    }
}