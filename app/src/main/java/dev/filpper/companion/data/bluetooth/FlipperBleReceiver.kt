package dev.filpper.companion.data.bluetooth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

internal class FlipperBleReceiver {


    private var channel = createChannel()

    /**
     * Consumer stream of incoming TX fragments
     */
    val incomingChunks: Flow<ByteArray>
        get() = channel.consumeAsFlow()  // TODO(transport): use for Framer


    /**
     * Accepts bytes received from Flipper TX
     * @return `false` - if the queue is full or closed,
     *         `true` - otherwise
     */
    fun onReceive(bytes: ByteArray) =
        bytes.isEmpty() || channel.trySend(bytes.copyOf()).isSuccess


    private fun createChannel() = Channel<ByteArray>(capacity = BUFFER_CAPACITY)

    /**
     * Recreates channel
     */
    fun reset() {
        val prevChannel = channel

        channel = createChannel()
        prevChannel.cancel()
    }

    /**
     * Fully closes channel and removes all existing elements
     */
    fun close() {
        channel.cancel()
    }


    private companion object {
        const val BUFFER_CAPACITY = 64
    }

}