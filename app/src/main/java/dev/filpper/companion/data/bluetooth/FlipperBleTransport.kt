package dev.filpper.companion.data.bluetooth

import kotlinx.coroutines.flow.Flow

/**
 * Sending and receiving of bytes
 */
class FlipperBleTransport() {

//    val incomingBytes: Flow<ByteArray>

    fun send(bytes: ByteArray) {
        /*
        Взять сообщение из очереди
        Проверить подключение/RPC_STATUS
        Дождаться доступного FLOW_CONTROL
        Вычислить размер следующего фрагмента
        writeCharacteristic(RX)
        Дождаться onCharacteristicWrite()
        Уменьшить доступный объём
        Есть ещё байты? - Да Повторить
                   │
                  Нет
        Завершить отправку
         */

        // Реализация должна завершать send() успешно только после подтверждения всех фрагментов. Иначе - ошибка
    }

}