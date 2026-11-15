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

- [BLE Connection & RPC Initialization]() - блок-схема подключения и инициализации RPC.
- [BLE RX Transport Simulator]() - интерактивная модель отправки данных с учётом MTU и размера окна передачи.
