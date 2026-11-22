package dev.filpper.companion.data.flipper.rpc

import com.flipperdevices.protobuf.FlipperProto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream
import java.io.EOFException

internal class RpcMessageFramer {

    /**
     * Encodes [FlipperProto.Main] message with varint prefix
     * @return array of bytes with format: [varint (message size)][serialized message]
     */
    fun encode(message: FlipperProto.Main): ByteArray =
        ByteArrayOutputStream(MAX_VARINT_SIZE + message.serializedSize)
            .apply { message.writeDelimitedTo(this) }
            .toByteArray()

    /**
     * Converts TX chunks into complete message.
     *
     * One chunk may contain:
     * * incomplete varint
     * * part of protobuf payload
     * * one or several complete messages
     */
    fun decode(chunks: Flow<ByteArray>): Flow<FlipperProto.Main> = flow {
        var frameLength = 0L
        var varintBytes = 0

        var payload: ByteArray? = null
        var received = 0

        chunks.collect { chunk ->
            var offset = 0

            while (offset < chunk.size) {
                val currentPayload = payload

                if (currentPayload == null) {
                    // read the next byte of the length varint
                    val byte = chunk[offset++].toInt() and 0xFF

                    // uint32 varint cannot use more than 32 bits
                    require(varintBytes != 4 || (byte and 0xF0) == 0) { "Invalid RPC frame length varint" }

                    frameLength = frameLength or ((byte and 0x7F).toLong() shl (varintBytes * 7))
                    varintBytes++

                    if ((byte and 0x80) != 0) {
                        require(varintBytes < MAX_VARINT_SIZE) { "RPC frame length Varint is too long" }
                        continue
                    }

                    // full varint received
                    require(frameLength in 1L..MAX_FRAME_SIZE.toLong()) { "Invalid RPC frame size: $frameLength" }

                    payload = ByteArray(frameLength.toInt())
                    frameLength = 0L
                    varintBytes = 0
                    received = 0
                } else {
                    // read as much of the payload as this chunk holds
                    val count = minOf(currentPayload.size - received, chunk.size - offset)

                    chunk.copyInto(
                        destination = currentPayload,
                        destinationOffset = received,
                        startIndex = offset,
                        endIndex = offset + count
                    )

                    received += count
                    offset += count

                    if (received == currentPayload.size) {
                        // complete protobuf message is available
                        val message = FlipperProto.Main.parseFrom(currentPayload)

                        payload = null
                        received = 0

                        emit(message)
                    }
                }
            }
        }

        // the upstream finished normally with an incomplete frame
        if (payload != null || varintBytes != 0) {
            throw EOFException("Incomplete RPC frame")
        }

    }


    private companion object {
        const val MAX_VARINT_SIZE = 5
        const val MAX_FRAME_SIZE = 1024 * 1024 // app safety limit, not a BLE characteristic limit
    }

}