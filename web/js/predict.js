'use strict';

/** Прогноз футбольного матча по модели Пуассона + поиск value-ставок. */
const Predict = (() => {
  const MAXG = 10;

  function pmf(k, l) {
    let p = Math.exp(-l);
    for (let i = 1; i <= k; i++) p *= l / i;
    return p;
  }

  function matrix(lh, la) {
    const ph = [], pa = [];
    for (let i = 0; i <= MAXG; i++) { ph.push(pmf(i, lh)); pa.push(pmf(i, la)); }
    return ph.map((a) => pa.map((b) => a * b));
  }

  function markets(m) {
    let h = 0, d = 0, a = 0, btts = 0;
    const tot = new Array(2 * MAXG + 1).fill(0);
    m.forEach((row, i) => row.forEach((p, j) => {
      if (i > j) h += p; else if (i === j) d += p; else a += p;
      if (i > 0 && j > 0) btts += p;
      tot[i + j] += p;
    }));
    const s = h + d + a; // нормировка на обрезанный хвост
    const under = (t) => tot.slice(0, Math.floor(t) + 1).reduce((x, y) => x + y, 0) / s;
    return { h: h / s, d: d / s, a: a / s, btts: btts / s, under, s };
  }

  // Строки таблицы рынков. group — для расчёта маржи.
  const ROWS = [
    { key: '1', name: () => 'П1', group: '1X2', p: (r) => r.h },
    { key: 'x', name: () => 'Ничья', group: '1X2', p: (r) => r.d },
    { key: '2', name: () => 'П2', group: '1X2', p: (r) => r.a },
    { key: '1x', name: () => '1X', group: null, p: (r) => r.h + r.d },
    { key: 'x2', name: () => 'X2', group: null, p: (r) => r.d + r.a },
    { key: 'o15', name: () => 'ТБ 1.5', group: 'T15', p: (r) => 1 - r.under(1.5) },
    { key: 'u15', name: () => 'ТМ 1.5', group: 'T15', p: (r) => r.under(1.5) },
    { key: 'o25', name: () => 'ТБ 2.5', group: 'T25', p: (r) => 1 - r.under(2.5) },
    { key: 'u25', name: () => 'ТМ 2.5', group: 'T25', p: (r) => r.under(2.5) },
    { key: 'o35', name: () => 'ТБ 3.5', group: 'T35', p: (r) => 1 - r.under(3.5) },
    { key: 'u35', name: () => 'ТМ 3.5', group: 'T35', p: (r) => r.under(3.5) },
    { key: 'by', name: () => 'Обе забьют — да', group: 'BTTS', p: (r) => r.btts },
    { key: 'bn', name: () => 'Обе забьют — нет', group: 'BTTS', p: (r) => 1 - r.btts },
  ];
  const GROUP_NAMES = { '1X2': '1X2', T15: 'Тотал 1.5', T25: 'Тотал 2.5', T35: 'Тотал 3.5', BTTS: 'Обе забьют' };

  let last = null; // последние вычисленные вероятности

  function buildTable() {
    let html = '<thead><tr><th>Исход</th><th class="num">Вер-ть</th><th class="num">Честный кф</th>' +
      '<th class="num">Кф БК</th><th class="num">Перевес</th><th class="num">Ставка</th><th></th></tr></thead><tbody>';
    for (const r of ROWS) {
      html += `<tr data-key="${r.key}"><td>${r.name()}</td><td class="num c-p"></td><td class="num c-fair"></td>` +
        `<td class="num"><input type="number" step="0.01" min="1.01" class="c-odds" aria-label="Коэффициент ${r.name()}"></td>` +
        `<td class="num c-edge"></td><td class="num c-stake"></td><td><button class="c-add" title="Добавить в трекер" disabled>+</button></td></tr>`;
    }
    $('#p-markets').innerHTML = html + '</tbody>';
  }

  function lambdas() {
    if ($('#p-manual').checked) return [num('#p-xh'), num('#p-xa')];
    const lgh = num('#p-lgh'), lga = num('#p-lga');
    return [(num('#p-hs') * num('#p-ac')) / lgh, (num('#p-as') * num('#p-hc')) / lga];
  }

  function render() {
    const [lh, la] = lambdas();
    const home = $('#p-home').value.trim() || 'Хозяева';
    const away = $('#p-away').value.trim() || 'Гости';
    if (!(lh > 0 && la > 0 && lh <= 8 && la <= 8)) {
      $('#p-lambda').textContent = 'Проверьте данные: ожидаемые голы должны быть больше 0 и не больше 8.';
      $('#p-bar').innerHTML = '';
      $('#p-scores').innerHTML = '';
      last = null;
      $$('#p-markets tbody tr').forEach((tr) => { $$('.c-p,.c-fair,.c-edge,.c-stake', tr).forEach((td) => (td.textContent = '')); });
      return;
    }
    const m = matrix(lh, la);
    const r = markets(m);
    last = { r, home, away };

    $('#p-lambda').innerHTML = `Ожидаемые голы: <b>${esc(home)} ${fmt(lh)}</b> — <b>${esc(away)} ${fmt(la)}</b>`;
    const seg = (p, color, label) => `<div style="width:${(p * 100).toFixed(2)}%;background:var(${color})" title="${label}">${p > 0.08 ? label : ''}</div>`;
    $('#p-bar').innerHTML = seg(r.h, '--p1', 'П1 ' + pct(r.h, 0)) + seg(r.d, '--px', 'X ' + pct(r.d, 0)) + seg(r.a, '--p2', 'П2 ' + pct(r.a, 0));

    const bank = num('#p-bank'), kf = num('#p-kf'), th = num('#p-th') / 100;
    const groupOdds = {};
    for (const row of ROWS) {
      const tr = $(`#p-markets tr[data-key="${row.key}"]`);
      const p = row.p(r);
      const odds = num($('.c-odds', tr));
      $('.c-p', tr).textContent = pct(p);
      $('.c-fair', tr).textContent = fmt(1 / p);
      let edgeTxt = '', stakeTxt = '', isValue = false;
      if (odds > 1) {
        const edge = p * odds - 1;
        const kelly = Math.max(0, edge / (odds - 1));
        edgeTxt = `<span class="${cls(edge)}">${signed(edge * 100, 1)}%</span>`;
        const stake = (bank > 0 ? bank : 0) * (kf > 0 ? kf : 0) * kelly;
        stakeTxt = stake > 0 ? money(Math.round(stake * 100) / 100) : '0';
        isValue = edge >= (Number.isFinite(th) ? th : 0) && edge > 0;
        if (row.group) (groupOdds[row.group] ||= []).push(odds);
      }
      $('.c-edge', tr).innerHTML = edgeTxt;
      $('.c-stake', tr).textContent = stakeTxt;
      $('.c-add', tr).disabled = !(odds > 1);
      tr.classList.toggle('value', isValue);
    }

    // Маржа по группам, где заполнены все исходы
    const margins = [];
    for (const g of Object.keys(GROUP_NAMES)) {
      const need = ROWS.filter((x) => x.group === g).length;
      const arr = groupOdds[g] || [];
      if (arr.length === need) margins.push(`${GROUP_NAMES[g]}: ${pct(arr.reduce((s, o) => s + 1 / o, 0) - 1)}`);
    }
    $('#p-margins').textContent = margins.length
      ? 'Маржа букмекера — ' + margins.join(', ')
      : 'Введите коэффициенты букмекера в колонку «Кф БК», чтобы найти value-ставки (подсвечиваются зелёным).';

    // Тепловая карта счёта
    const N = 5;
    let maxP = 0;
    for (let i = 0; i <= N; i++) for (let j = 0; j <= N; j++) maxP = Math.max(maxP, m[i][j]);
    let html = `<table class="heat"><tr><th title="${esc(home)} \\ ${esc(away)}">${esc(home.slice(0, 3))}\\${esc(away.slice(0, 3))}</th>`;
    for (let j = 0; j <= N; j++) html += `<th>${j}</th>`;
    html += '</tr>';
    for (let i = 0; i <= N; i++) {
      html += `<tr><th>${i}</th>`;
      for (let j = 0; j <= N; j++) {
        const p = m[i][j] / r.s;
        const a = (0.08 + 0.75 * (m[i][j] / maxP)).toFixed(2);
        const color = i > j ? '37,99,235' : i === j ? '120,120,130' : '234,88,12';
        html += `<td style="background:rgba(${color},${a})" title="${i}:${j} — ${pct(p)}">${(p * 100).toFixed(1)}</td>`;
      }
      html += '</tr>';
    }
    $('#p-scores').innerHTML = html + '</table><p class="hint">Строки — голы хозяев, столбцы — голы гостей, значения в %.</p>';
  }

  function onAdd(e) {
    const btn = e.target.closest('.c-add');
    if (!btn || !last) return;
    const tr = btn.closest('tr');
    const row = ROWS.find((x) => x.key === tr.dataset.key);
    const odds = num($('.c-odds', tr));
    const p = row.p(last.r);
    const kelly = Math.max(0, (p * odds - 1) / (odds - 1));
    const stake = Math.round(num('#p-bank') * num('#p-kf') * kelly * 100) / 100;
    Tracker.prefill({
      sport: 'Футбол',
      event: `${last.home} — ${last.away}`,
      market: row.name(),
      odds,
      stake: stake > 0 ? stake : '',
      prob: Math.round(p * 1000) / 10,
    });
    showTab('tracker');
  }

  function init() {
    buildTable();
    const saved = store.get('bl.predict', null);
    if (saved) {
      for (const [id, v] of Object.entries(saved.fields || {})) {
        const el = document.getElementById(id);
        if (el) el.value = v;
      }
      $('#p-manual').checked = !!saved.manual;
    }
    syncMode();
    $('#tab-predict').addEventListener('input', (e) => {
      if (e.target.id === 'p-manual') syncMode();
      render();
      persist();
    });
    $('#p-markets').addEventListener('click', onAdd);
    render();
  }

  function persist() {
    const fields = {};
    $$('#tab-predict input:not([type=checkbox]):not(.c-odds)').forEach((el) => (fields[el.id] = el.value));
    store.set('bl.predict', { fields, manual: $('#p-manual').checked });
  }

  function syncMode() {
    const manual = $('#p-manual').checked;
    $('#p-stats').hidden = manual;
    $('#p-xg').hidden = !manual;
  }

  return { init, render };
})();
