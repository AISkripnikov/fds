'use strict';

/** Учёт ставок: история, ROI, график банка. Данные — в localStorage. */
const Tracker = (() => {
  const STATUS = { pending: 'В игре', won: 'Выигрыш', lost: 'Проигрыш', void: 'Возврат' };
  let bets = store.get('bl.bets', []);
  let settings = store.get('bl.settings', { bankroll: 1000 });

  const save = () => store.set('bl.bets', bets);
  const saveSettings = () => store.set('bl.settings', settings);
  const profitOf = (b) => (b.status === 'won' ? b.stake * (b.odds - 1) : b.status === 'lost' ? -b.stake : 0);
  const sorted = (list) => [...list].sort((a, b) => (a.date || '').localeCompare(b.date || '') || a.id - b.id);

  function currentBankroll() {
    return settings.bankroll + bets.reduce((s, b) => s + profitOf(b), 0);
  }

  function init() {
    $('#t-date').value = today();
    $('#t-bank').value = settings.bankroll;

    $('#t-form').addEventListener('submit', (e) => {
      e.preventDefault();
      const odds = num('#t-odds'), stake = num('#t-stake'), prob = num('#t-prob');
      if (!(odds > 1) || !(stake > 0)) { alert('Коэффициент должен быть больше 1, сумма — больше 0.'); return; }
      bets.push({
        id: Date.now(),
        date: $('#t-date').value || today(),
        sport: $('#t-sport').value.trim(),
        event: $('#t-event').value.trim(),
        market: $('#t-market').value.trim(),
        odds,
        stake,
        status: $('#t-status').value,
        prob: prob > 0 && prob <= 100 ? prob / 100 : null,
      });
      save();
      ['#t-event', '#t-market', '#t-odds', '#t-stake', '#t-prob'].forEach((s) => ($(s).value = ''));
      $('#t-status').value = 'pending';
      render();
    });

    $('#t-bank').addEventListener('input', () => {
      const v = num('#t-bank');
      if (v >= 0) { settings.bankroll = v; saveSettings(); render(); }
    });

    $('#t-table').addEventListener('change', (e) => {
      const sel = e.target.closest('select[data-id]');
      if (!sel) return;
      const b = bets.find((x) => x.id === +sel.dataset.id);
      if (b) { b.status = sel.value; save(); render(); }
    });
    $('#t-table').addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-del]');
      if (!btn) return;
      if (!confirm('Удалить ставку?')) return;
      bets = bets.filter((x) => x.id !== +btn.dataset.del);
      save();
      render();
    });
    $('#t-filter').addEventListener('change', render);

    $('#t-export').addEventListener('click', () =>
      download(`bets-${today()}.json`, JSON.stringify({ settings, bets }, null, 2), 'application/json'));
    $('#t-csv').addEventListener('click', exportCSV);
    $('#t-import').addEventListener('change', importJSON);

    render();
  }

  function render() {
    // Список видов спорта для фильтра и подсказок
    const sports = [...new Set(bets.map((b) => b.sport).filter(Boolean))].sort();
    const filterEl = $('#t-filter');
    const cur = filterEl.value;
    filterEl.innerHTML = '<option value="">Все</option>' + sports.map((s) => `<option>${esc(s)}</option>`).join('');
    filterEl.value = sports.includes(cur) ? cur : '';
    $('#t-sports').innerHTML = [...new Set(['Футбол', 'Хоккей', 'Теннис', 'Баскетбол', 'Киберспорт', ...sports])]
      .map((s) => `<option value="${esc(s)}">`).join('');

    const filter = filterEl.value;
    const list = sorted(bets).filter((b) => !filter || b.sport === filter);
    renderStats(list);
    renderTable(list);
  }

  function renderStats(list) {
    const settled = list.filter((b) => b.status === 'won' || b.status === 'lost');
    const won = settled.filter((b) => b.status === 'won').length;
    const turnover = settled.reduce((s, b) => s + b.stake, 0);
    const profit = settled.reduce((s, b) => s + profitOf(b), 0);
    const pending = list.filter((b) => b.status === 'pending');
    const avgOdds = settled.length ? settled.reduce((s, b) => s + b.odds, 0) / settled.length : NaN;
    const withProb = settled.filter((b) => b.prob);
    const expProfit = withProb.reduce((s, b) => s + b.stake * (b.prob * b.odds - 1), 0);
    const expWins = withProb.reduce((s, b) => s + b.prob, 0);
    const actWins = withProb.filter((b) => b.status === 'won').length;

    // Кривая банка и максимальная просадка
    let bank = settings.bankroll, peak = bank, maxDD = 0;
    const curve = [bank];
    for (const b of list) {
      if (b.status === 'pending') continue;
      bank += profitOf(b);
      curve.push(bank);
      peak = Math.max(peak, bank);
      if (peak > 0) maxDD = Math.max(maxDD, (peak - bank) / peak);
    }

    const tile = (k, v, c = '') => `<div class="stat"><div class="k">${k}</div><div class="v ${c}">${v}</div></div>`;
    let html =
      tile('Ставок', `${list.length}`) +
      tile('Рассчитано', `${settled.length}`) +
      tile('Проходимость', settled.length ? pct(won / settled.length) : '—') +
      tile('Средний кф', fmt(avgOdds)) +
      tile('Оборот', money(turnover)) +
      tile('Прибыль', signed(profit), cls(profit)) +
      tile('ROI', turnover ? signed((profit / turnover) * 100, 1) + '%' : '—', cls(profit)) +
      tile('Макс. просадка', pct(maxDD)) +
      tile('В игре', `${pending.length} / ${money(pending.reduce((s, b) => s + b.stake, 0))}`) +
      tile(filter() ? 'Банк (фильтр)' : 'Текущий банк', money(bank));
    if (withProb.length) {
      html += tile('Ожид. прибыль*', signed(expProfit), cls(expProfit)) +
        tile('Побед: ожид./факт*', `${fmt(expWins, 1)} / ${actWins}`);
    }
    $('#t-stats').innerHTML = html + (withProb.length
      ? '<p class="hint" style="grid-column:1/-1">* По ставкам, где вы указали свою вероятность. Если факт стабильно ниже ожидания — ваши оценки завышены.</p>'
      : '');
    lineChart($('#t-chart'), [curve], { base: settings.bankroll });
  }

  const filter = () => $('#t-filter').value;

  function renderTable(list) {
    if (!list.length) {
      $('#t-table').innerHTML = '<tbody><tr><td class="hint">Ставок пока нет.</td></tr></tbody>';
      return;
    }
    let html = '<thead><tr><th>Дата</th><th>Спорт</th><th>Событие</th><th>Рынок</th><th class="num">Кф</th>' +
      '<th class="num">Сумма</th><th class="num">Вер-ть</th><th>Статус</th><th class="num">Результат</th><th></th></tr></thead><tbody>';
    for (const b of [...list].reverse()) {
      const p = profitOf(b);
      const opts = Object.entries(STATUS).map(([k, v]) => `<option value="${k}"${k === b.status ? ' selected' : ''}>${v}</option>`).join('');
      html += `<tr><td>${esc(b.date)}</td><td>${esc(b.sport)}</td><td>${esc(b.event)}</td><td>${esc(b.market)}</td>` +
        `<td class="num">${fmt(b.odds)}</td><td class="num">${money(b.stake)}</td>` +
        `<td class="num">${b.prob ? pct(b.prob) : ''}</td>` +
        `<td><select data-id="${b.id}" aria-label="Статус">${opts}</select></td>` +
        `<td class="num ${cls(p)}">${b.status === 'pending' ? '' : signed(p)}</td>` +
        `<td><button data-del="${b.id}" title="Удалить">✕</button></td></tr>`;
    }
    $('#t-table').innerHTML = html + '</tbody>';
  }

  function prefill(o) {
    $('#t-date').value = today();
    if (o.sport != null) $('#t-sport').value = o.sport;
    $('#t-event').value = o.event ?? '';
    $('#t-market').value = o.market ?? '';
    $('#t-odds').value = o.odds ?? '';
    $('#t-stake').value = o.stake ?? '';
    $('#t-prob').value = o.prob ?? '';
    $('#t-status').value = 'pending';
    setTimeout(() => $('#t-stake').focus(), 0);
  }

  function exportCSV() {
    const q = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const head = ['Дата', 'Спорт', 'Событие', 'Рынок', 'Коэфф', 'Сумма', 'Вероятность', 'Статус', 'Результат'];
    const rows = sorted(bets).map((b) => [b.date, b.sport, b.event, b.market, b.odds, b.stake, b.prob ?? '',
      STATUS[b.status], b.status === 'pending' ? '' : profitOf(b).toFixed(2)].map(q).join(';'));
    download(`bets-${today()}.csv`, '﻿' + [head.map(q).join(';'), ...rows].join('\n'), 'text/csv');
  }

  function importJSON(e) {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const data = JSON.parse(reader.result);
        const arr = Array.isArray(data) ? data : data.bets;
        if (!Array.isArray(arr)) throw new Error('нет массива ставок');
        const clean = arr.filter((b) => b && b.odds > 1 && b.stake > 0 && STATUS[b.status]).map((b, i) => ({
          id: Number(b.id) || Date.now() + i,
          date: String(b.date || today()), sport: String(b.sport || ''), event: String(b.event || ''),
          market: String(b.market || ''), odds: +b.odds, stake: +b.stake, status: b.status,
          prob: b.prob > 0 && b.prob <= 1 ? +b.prob : null,
        }));
        if (!confirm(`Заменить текущие ставки (${bets.length}) на импортированные (${clean.length})?`)) return;
        bets = clean;
        if (data.settings && data.settings.bankroll >= 0) { settings.bankroll = +data.settings.bankroll; saveSettings(); $('#t-bank').value = settings.bankroll; }
        save();
        render();
      } catch (err) {
        alert('Не удалось прочитать файл: ' + err.message);
      }
    };
    reader.readAsText(file);
  }

  return { init, render, prefill, currentBankroll };
})();
