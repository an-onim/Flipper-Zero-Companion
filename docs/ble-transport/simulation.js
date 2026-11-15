/*
 * Pure, dependency-free model of Android -> Flipper RX transport.
 * Mirrors the documented RpcSender.send() algorithm; it does not use Bluetooth.
 * Available to the browser as window.BleTransportSimulation and to Node tests.
 */
(function (root, factory) {
  const api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (root) root.BleTransportSimulation = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  const RX_MAX = 486;
  const ATT_HEADER = 3;
  const MTUS = Object.freeze([23, 100, 247, 517]);

  const asInt = (value, min, max, label) => {
    const n = Number(value);
    if (!Number.isSafeInteger(n) || n < min || n > max) {
      throw new RangeError(`${label}: допустимое целое число от ${min} до ${max}`);
    }
    return n;
  };

  class Simulation {
    constructor() {
      this.reset();
    }

    reset() {
      this.phase = 'idle';
      this.payloadSize = 600;
      this.mtu = 247;
      this.initialCredit = 486;
      this.refillCredit = 486;
      this.credit = 0;
      this.active = null;
      this.queue = [];
      this.completed = [];
      this.pending = null;
      this.writeCount = 0;
      this.log = [];
      this.counter = 0;
      this.sessionId = 0;
      return this;
    }

    configure({ payloadSize, mtu, initialCredit, refillCredit }) {
      if (this.phase !== 'idle') throw new Error('Параметры изменяются до запуска');
      this.payloadSize = asInt(payloadSize, 1, 4096, 'Размер сообщения');
      const verifiedMtu = asInt(mtu, 23, 517, 'MTU');
      if (!MTUS.includes(verifiedMtu)) throw new Error('Неподдерживаемый MTU в симуляции');
      this.mtu = verifiedMtu;
      this.initialCredit = asInt(initialCredit, 0, 1024, 'FLOW_CONTROL');
      this.refillCredit = asInt(refillCredit, 0, 1024, 'Новое окно');
      return this;
    }

    get mtuPayloadLimit() {
      return Math.min(this.mtu - ATT_HEADER, RX_MAX);
    }

    get nextChunkSize() {
      if (this.phase !== 'ready' || !this.active) return 0;
      return Math.min(
        this.mtu - ATT_HEADER,
        RX_MAX,
        this.credit,
        this.active.total - this.active.offset,
      );
    }

    append(text, kind = 'info') {
      this.counter++;
      this.log.push({ n: this.counter, text, kind });
      if (this.log.length > 400) this.log.shift();
    }

    makeMessage(total) {
      const id = this.completed.length + this.queue.length + (this.active ? 1 : 0) + 1;
      return { id, total, offset: 0, writes: 0 };
    }

    start() {
      if (this.phase !== 'idle') return false;
      this.sessionId++;
      this.credit = this.initialCredit;
      this.active = { id: 1, total: this.payloadSize, offset: 0, writes: 0 };
      this.phase = this.credit > 0 ? 'ready' : 'waiting';
      this.append(`BleConnectionRepository → RPC_STATUS = ACTIVE; GATT-сессия #${this.sessionId}`);
      this.append(`onFlowControlUpdate(gatt, ${this.credit})`, 'flow');
      this.append(`RpcSender.send(ByteArray(${this.payloadSize})) → writeMutex.withLock`);
      this.append(this.credit > 0
        ? `awaitAvailableBytes() → ${this.credit}`
        : 'awaitAvailableBytes() → ожидание FLOW_CONTROL', 'wait');
      return true;
    }

    enqueue(total) {
      if (!['ready','pending','waiting'].includes(this.phase)) return false;
      total = asInt(total, 1, 4096, 'Размер очередного сообщения');
      if (this.queue.length >= 5) return false;
      const msg = this.makeMessage(total);
      this.queue.push(msg);
      this.append(`Параллельный send(ByteArray(${total})) → ожидает writeMutex (сообщение #${msg.id})`, 'queue');
      return true;
    }

    write() {
      if (this.phase !== 'ready') return false;
      const size = this.nextChunkSize;
      if (size <= 0) return false;
      const before = this.credit;
      // The reservation happens BEFORE initiating the asynchronous GATT write.
      this.credit -= size;
      this.pending = { size, offsetBefore: this.active.offset, messageId: this.active.id };
      this.active.writes++;
      this.writeCount++;
      this.phase = 'pending';
      this.append(`chunkSize = minOf(${this.mtu - 3}, ${RX_MAX}, ${before}, ${this.active.total - this.active.offset}) = ${size}`, 'write');
      this.append(`availableBytesToSend: ${before} → ${this.credit} (резервирование)`, 'flow');
      this.append(`writeRxChunkLocked(${size}) → writeCharacteristic(RX) → pendingWrite`, 'write');
      return true;
    }

    confirm() {
      if (this.phase !== 'pending' || !this.pending) return false;
      const p = this.pending;
      this.pending = null;
      this.active.offset += p.size;
      this.append(`onCharacteristicWrite(GATT_SUCCESS) → CompletableDeferred.complete(Success)`, 'success');
      this.append(`offset: ${p.offsetBefore} → ${this.active.offset} (только после callback)`);
      if (this.active.offset === this.active.total) {
        this.completed.push(this.active);
        this.append(`send(#${this.active.id}) → null; writeMutex освобождён`, 'success');
        if (this.queue.length > 0) {
          this.active = this.queue.shift();
          this.phase = this.credit > 0 ? 'ready' : 'waiting';
          this.append(`Mutex → следующий send(#${this.active.id}) захватил блокировку`, 'queue');
          if (this.phase === 'waiting') this.append('awaitAvailableBytes() → ожидание FLOW_CONTROL', 'wait');
        } else {
          this.active = null;
          this.phase = 'done';
        }
      } else {
        this.phase = this.credit > 0 ? 'ready' : 'waiting';
        if (this.phase === 'waiting') this.append('awaitAvailableBytes() → ожидание FLOW_CONTROL', 'wait');
      }
      return true;
    }

    notifyFlow(newCredit = this.refillCredit) {
      if (!['ready','pending','waiting'].includes(this.phase)) return false;
      newCredit = asInt(newCredit, 0, 1024, 'FLOW_CONTROL');
      const was = this.credit;
      // Notification is an absolute new credit window, not an increment.
      this.credit = newCredit;
      this.append(`FLOW_CONTROL notification: ${newCredit}`, 'flow');
      this.append(`onFlowControlUpdate(): availableBytesToSend ${was} → ${newCredit}`, 'flow');
      if (this.phase === 'waiting' && newCredit > 0) {
        this.phase = 'ready';
        this.append(`awaitAvailableBytes() → ${newCredit}; отправка возобновлена`, 'success');
      }
      return true;
    }

    interrupt(kind = 'error') {
      if (!['ready','pending','waiting'].includes(this.phase)) return false;
      const descriptions = {
        error: 'onCharacteristicWrite(ERROR) → onWriteAborted(gatt)',
        timeout: 'withTimeoutOrNull() → null; onWriteAborted(gatt)',
        disconnect: 'onGattClosed(gatt) → pendingWrite.complete(Disconnected)',
        inactive: 'onRpcInactive(gatt) → SessionInactive',
      };
      if (!descriptions[kind]) return false;
      this.append(descriptions[kind], 'error');
      this.append('Передача прервана; текущая GATT-сессия закрыта или непригодна для записи', 'error');
      this.phase = 'error';
      this.credit = 0;
      this.pending = null;
      this.queue = [];
      return true;
    }

    runUntilPause() {
      let iterations = 0;
      while (iterations++ < 10000) {
        if (this.phase === 'pending') {
          this.confirm();
        } else if (this.phase === 'ready') {
          this.write();
        } else {
          break;
        }
      }
      return iterations;
    }
  }

  return Object.freeze({ Simulation, RX_MAX, ATT_HEADER, MTUS });
});
