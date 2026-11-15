package dev.filpper.companion.data.bluetooth

import com.flipperdevices.protobuf.FlipperProto
import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayOutputStream

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
     */
    fun decode(chunks: Flow<ByteArray>): Flow<FlipperProto.Main> {
        TODO()
    }


    private companion object {
        const val MAX_VARINT_SIZE = 5
    }

}