/* Browser-only DOM adapter; the actual simulation lives in simulation.js. */
(function () {
  'use strict';
  const { Simulation } = window.BleTransportSimulation;
  const sim = new Simulation();
  const $ = id => document.getElementById(id);
  const refs = {
    payloadSize: $('payloadSize'), payloadValue: $('payloadValue'),
    mtu: $('mtu'), initialCredit: $('initialCredit'), creditValue: $('creditValue'),
    refillCredit: $('refillCredit'), refillValue: $('refillValue'), chunkLimit: $('chunkLimit'),
    startBtn: $('startBtn'), resetBtn: $('resetBtn'), statusIndicator: $('statusIndicator'),
    statusLabel: $('statusLabel'), activeMessage: $('activeMessage'),
    progressText: $('progressText'), progressConfirmed: $('progressConfirmed'),
    progressPending: $('progressPending'), offsetMetric: $('offsetMetric'),
    creditMetric: $('creditMetric'), pendingMetric: $('pendingMetric'),
    remainingMetric: $('remainingMetric'), actionTitle: $('actionTitle'),
    actionDescription: $('actionDescription'), writeBtn: $('writeBtn'),
    confirmBtn: $('confirmBtn'), autoBtn: $('autoBtn'), errorBtn: $('errorBtn'),
    notifyBtn: $('notifyBtn'), secondSize: $('secondSize'), enqueueBtn: $('enqueueBtn'),
    faultActions: $('faultActions'), disconnectBtn: $('disconnectBtn'),
    inactiveBtn: $('inactiveBtn'), timeoutBtn: $('timeoutBtn'),
    queueList: $('queueList'), eventLog: $('eventLog'), copyLogBtn: $('copyLogBtn'),
  };
  const states = {
    idle: ['Ожидание запуска', 'Запусти симуляцию', 'Параметры можно менять до начала отправки.'],
    ready: ['Готов к записи', 'Следующий RX-фрагмент', 'Доступного FLOW_CONTROL достаточно. Можно инициировать очередную GATT-запись.'],
    pending: ['Ожидание onCharacteristicWrite()', 'Операция GATT выполняется', 'writeCharacteristic() только инициировал запись. Корутина ждёт completion.await().'],
    waiting: ['Ожидание FLOW_CONTROL', 'Передача приостановлена', 'availableBytesToSend = 0. Новую запись начинать нельзя.'],
    done: ['Все записи подтверждены', 'Передача завершена', 'send() вернул null, Mutex освобождён. Это не означает выполнение RPC-команды.'],
    error: ['Передача прервана', 'Ошибка транспортной сессии', 'Нельзя безопасно продолжать передачу. Сбрось симуляцию для новой сессии.'],
  };

  const pct = (part, total) => `${Math.max(0, Math.min(100, (part / total) * 100))}%`;
  const element = (tag, text, className) => {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
  };

  function render() {
    const isIdle = sim.phase === 'idle';
    const isActive = ['ready','pending','waiting'].includes(sim.phase);
    refs.payloadSize.disabled = !isIdle;
    refs.mtu.disabled = !isIdle;
    refs.initialCredit.disabled = !isIdle;
    refs.startBtn.disabled = !isIdle;
    refs.notifyBtn.disabled = !isActive;
    refs.enqueueBtn.disabled = !isActive || sim.queue.length >= 5;
    refs.payloadValue.textContent = `${refs.payloadSize.value} Б`;
    refs.creditValue.textContent = `${refs.initialCredit.value} Б`;
    refs.refillValue.textContent = `${refs.refillCredit.value} Б`;
    refs.chunkLimit.textContent = `${Math.min(Number(refs.mtu.value) - 3, 486)} Б`;
    refs.statusIndicator.className = `status-indicator ${sim.phase}`;
    refs.statusLabel.textContent = states[sim.phase][0];

    const active = sim.active;
    const pending = sim.pending;
    const total = active ? active.total : Number(refs.payloadSize.value);
    const offset = active ? active.offset : sim.phase === 'done' && sim.completed.length ? sim.completed.at(-1).offset : 0;
    const pendingSize = pending ? pending.size : 0;
    const rem = active ? Math.max(0, active.total - offset - pendingSize) : sim.phase === 'done' ? 0 : total;
    refs.activeMessage.textContent = active
      ? `send(#${active.id}, ${total} Б) · GATT writes: ${active.writes}`
      : sim.phase === 'done'
        ? `${sim.completed.length} сообщ. завершено · ${sim.writeCount} GATT-записей`
        : 'send() ещё не запущен';
    refs.progressText.textContent = `${offset} / ${total} Б`;
    refs.progressConfirmed.style.width = pct(offset, total);
    refs.progressPending.style.width = pct(pendingSize, total);
    refs.offsetMetric.textContent = String(offset);
    refs.creditMetric.textContent = String(sim.credit);
    refs.pendingMetric.textContent = String(pendingSize);
    refs.remainingMetric.textContent = String(rem);

    refs.actionTitle.textContent = states[sim.phase][1];
    refs.actionDescription.textContent = states[sim.phase][2];
    if (sim.phase === 'ready') {
      refs.actionTitle.textContent = `writeRxChunkLocked(${sim.nextChunkSize} Б)`;
      refs.actionDescription.textContent = `minOf(${sim.mtu - 3}, 486, ${sim.credit}, ${total - offset}) = ${sim.nextChunkSize}. Счётчик уменьшится до ${sim.credit - sim.nextChunkSize} перед вызовом writeCharacteristic().`;
    }
    if (sim.phase === 'pending') {
      refs.actionTitle.textContent = `pendingWrite: ${pendingSize} байт`;
    }
    refs.writeBtn.hidden = sim.phase !== 'ready';
    refs.confirmBtn.hidden = sim.phase !== 'pending';
    refs.autoBtn.hidden = !['ready','pending'].includes(sim.phase);
    refs.errorBtn.hidden = sim.phase !== 'pending';
    refs.faultActions.hidden = !isActive;
    refs.timeoutBtn.disabled = !['pending','waiting'].includes(sim.phase);

    refs.queueList.replaceChildren();
    if (active) refs.queueList.append(element('span', `Активен #${active.id} · ${active.total} Б`, 'queue-pill active'));
    sim.queue.forEach(msg => refs.queueList.append(element('span', `Ожидает #${msg.id} · ${msg.total} Б`, 'queue-pill')));
    if (!active && !sim.queue.length) refs.queueList.append(element('span', 'Ожидающих сообщений нет', 'dim'));

    refs.eventLog.replaceChildren();
    if (sim.log.length === 0) {
      refs.eventLog.append(element('li', 'Журнал появится после запуска симуляции.', 'empty-log'));
    } else {
      sim.log.slice(-60).forEach(evt => {
        const line = element('li', undefined, evt.kind);
        line.append(element('span', String(evt.n).padStart(2, '0'), 'event-index'));
        line.append(element('span', evt.text));
        refs.eventLog.append(line);
      });
      refs.eventLog.scrollTop = refs.eventLog.scrollHeight;
    }
  }

  function applyConfigAndStart() {
    try {
      sim.configure({
        payloadSize: Number(refs.payloadSize.value),
        mtu: Number(refs.mtu.value),
        initialCredit: Number(refs.initialCredit.value),
        refillCredit: Number(refs.refillCredit.value),
      });
      sim.start();
      render();
    } catch (e) {
      window.alert(e.message);
    }
  }

  refs.startBtn.addEventListener('click', applyConfigAndStart);
  refs.resetBtn.addEventListener('click', () => { sim.reset(); render(); });
  refs.writeBtn.addEventListener('click', () => { sim.write(); render(); });
  refs.confirmBtn.addEventListener('click', () => { sim.confirm(); render(); });
  refs.autoBtn.addEventListener('click', () => { sim.runUntilPause(); render(); });
  refs.errorBtn.addEventListener('click', () => { sim.interrupt('error'); render(); });
  refs.disconnectBtn.addEventListener('click', () => { sim.interrupt('disconnect'); render(); });
  refs.inactiveBtn.addEventListener('click', () => { sim.interrupt('inactive'); render(); });
  refs.timeoutBtn.addEventListener('click', () => { sim.interrupt('timeout'); render(); });
  refs.notifyBtn.addEventListener('click', () => {
    sim.notifyFlow(Number(refs.refillCredit.value));
    render();
  });
  refs.enqueueBtn.addEventListener('click', () => {
    try {
      sim.enqueue(Number(refs.secondSize.value));
      render();
    } catch (e) { window.alert(e.message); }
  });
  refs.copyLogBtn.addEventListener('click', async () => {
    const log = sim.log.map(row => `[${String(row.n).padStart(3, '0')}] ${row.text}`).join('\n');
    if (!log) return;
    try {
      await navigator.clipboard.writeText(log);
      const old = refs.copyLogBtn.textContent;
      refs.copyLogBtn.textContent = 'Скопировано';
      window.setTimeout(() => { refs.copyLogBtn.textContent = old; }, 1100);
    } catch (e) {
      window.prompt('Скопируйте журнал:', log);
    }
  });
  [refs.payloadSize, refs.mtu, refs.initialCredit, refs.refillCredit].forEach(control => {
    control.addEventListener('input', render);
  });
  render();
})();
