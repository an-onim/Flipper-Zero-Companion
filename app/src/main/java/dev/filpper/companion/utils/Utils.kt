package dev.filpper.companion.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Utils {

    fun ByteArray.toInt(byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN) = when (this.size) {
        // in case the firmware compiler has compressed the enum to 1 byte (-fshort-enums)
        1 -> this[0].toUByte().toInt()
        // in case with 2 bytes
        2 -> ByteBuffer.wrap(this).order(byteOrder).short.toInt()
        // classic 4 bytes for 32-bit unsigned long (UL)
        4 -> ByteBuffer.wrap(this).order(byteOrder).int
        else -> null
    }

}