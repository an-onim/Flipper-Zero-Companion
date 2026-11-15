# Flipper Zero Companion

Android-приложение для подключения к Flipper Zero по BLE и работы с его RPC API. 

> Проект находится в разработке!

## Возможности

- Поиск BLE-устройств, системное сопряжение и GATT-подключение.
- Подписка на `TX`, `RPC_STATUS` и `FLOW_CONTROL` в Flipper Serial Service.
- Разработка двунаправленного BLE-транспорта

## Используемый стек

* Kotlin - 2.2.20
* Jetpack Compose
* Coroutines/Flow
* Hilt
* Android Bluetooth LE

## Документация

Интерактивные материалы и схемы алгоритмов размещаются в [`docs/`](docs/).

- [BLE Connection & RPC Initialization](https://an-onim.github.io/Flipper-Zero-Companion/ble-connection/index.html) - блок-схема подключения и инициализации RPC.
- [BLE RX Transport Simulator](https://an-onim.github.io/Flipper-Zero-Companion/ble-transport/simulator.html) - интерактивная модель отправки данных с учётом MTU и размера окна передачи.
