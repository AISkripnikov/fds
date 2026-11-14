'use strict';

/** Калькуляторы: конвертер, маржа, Келли, экспресс, симулятор банка. */
const Calc = (() => {
  const kv = (pairs) => pairs.map(([k, v]) => `<div class="k">${k}</div><div class="v">${v}</div>`).join('');

  // ---------- Конвертер ----------
  function parseOdds(f, s) {
    s = String(s).trim().replace(',', '.');
    let d = NaN;
    if (f === 'dec') d = parseFloat(s);
    else if (f === 'frac') {
      const m = s.match(/^(\d+(?:\.\d+)?)\s*[/:]\s*(\d+(?:\.\d+)?)$/);
      if (m && +m[2] > 0) d = 1 + +m[1] / +m[2];
    } else if (f === 'us') {
      const v = parseFloat(s);
      if (v >= 100) d = 1 + v / 100;
      else if (v <= -100) d = 1 + 100 / -v;
    } else if (f === 'prob') {
      const p = parseFloat(s) / 100;
      if (p > 0 && p < 1) d = 1 / p;
    }
    return d > 1 ? d : NaN;
  }
  function toFrac(d) {
    const x = d - 1;
    let best = [Math.round(x), 1], err = Math.abs(x - Math.round(x));
    for (let den = 2; den <= 100 && err > 1e-9; den++) {
      const n = Math.round(x * den), e = Math.abs(x - n / den);
      if (e < err - 1e-12) { best = [n, den]; err = e; }
    }
    return `${best[0]}/${best[1]}`;
  }
  const toUS = (d) => (d >= 2 ? '+' + Math.round((d - 1) * 100) : '−' + Math.round(100 / (d - 1)));

  function converter() {
    const d = parseOdds($('#cv-fmt').value, $('#cv-val').value);
    $('#cv-out').innerHTML = Number.isFinite(d)
      ? kv([['Десятичный', fmt(d, 3)], ['Дробный', toFrac(d)], ['Американский', toUS(d)],
        ['Гонконгский', fmt(d - 1, 3)], ['Вероятность', pct(1 / d, 2)]])
      : '<div class="k">Некорректное значение</div><div></div>';
  }

  // ---------- Маржа ----------
  function margin() {
    const odds = ['#mg-1', '#mg-2', '#mg-3'].map(num).filter((o) => o > 1);
    if (odds.length < 2) { $('#mg-out').innerHTML = '<div class="k">Нужно минимум 2 коэффициента &gt; 1</div><div></div>'; return; }
    const sum = odds.reduce((s, o) => s + 1 / o, 0);
    const rows = [['Маржа', pct(sum - 1, 2)], ['Возврат игрокам', pct(1 / sum, 2)]];
    odds.forEach((o, i) => rows.push([`Исход ${i + 1}: вер-ть / честный кф`, `${pct(1 / o / sum)} / ${fmt(o * sum)}`]));
    $('#mg-out').innerHTML = kv(rows);
  }

  // ---------- Келли ----------
  function kelly() {
    const p = num('#k-p') / 100, o = num('#k-o'), b = num('#k-b'), f = num('#k-f');
    if (!(p > 0 && p < 1 && o > 1)) { $('#k-out').innerHTML = '<div class="k">Проверьте вероятность и коэффициент</div><div></div>'; return; }
    const edge = p * o - 1;
    const full = Math.max(0, edge / (o - 1));
    const stake = full * (f > 0 ? f : 0) * (b > 0 ? b : 0);
    $('#k-out').innerHTML = kv([
      ['Матожидание на 1 ед.', `<span class="${cls(edge)}">${signed(edge * 100, 2)}%</span>`],
      ['Полный Келли', pct(full, 2) + ' банка'],
      ['Рекомендуемая ставка', money(Math.round(stake * 100) / 100)],
      ['Безубыточная вер-ть', pct(1 / o, 2)],
    ]) + (edge <= 0 ? '<div class="k neg" style="grid-column:1/-1">Перевеса нет — по Келли ставить не нужно.</div>' : '');
  }

  // ---------- Экспресс ----------
  function addLeg(odds = '', prob = '') {
    const div = document.createElement('div');
    div.className = 'leg';
    div.innerHTML = `<label>Кф<input type="number" step="0.01" class="lg-o" value="${odds}"></label>` +
      `<label>Вер-ть, %<input type="number" step="0.1" class="lg-p" value="${prob}" placeholder="—"></label>` +
      '<button class="lg-del" title="Убрать">✕</button>';
    $('#px-legs').appendChild(div);
  }
  function parlay() {
    const legs = $$('#px-legs .leg').map((l) => ({ o: num($('.lg-o', l)), p: num($('.lg-p', l)) / 100 }));
    const valid = legs.filter((l) => l.o > 1);
    if (!valid.length) { $('#px-out').innerHTML = ''; return; }
    const O = valid.reduce((s, l) => s * l.o, 1);
    const stake = num('#px-stake');
    const rows = [['Событий', valid.length], ['Итоговый кф', fmt(O)], ['Выплата', stake > 0 ? money(stake * O) : '—'],
      ['Вер-ть по кф БК', pct(1 / O, 2)]];
    if (valid.every((l) => l.p > 0 && l.p <= 1)) {
      const P = valid.reduce((s, l) => s * l.p, 1);
      const ev = P * O - 1;
      rows.push(['Ваша вер-ть', pct(P, 2)], ['Матожидание', `<span class="${cls(ev)}">${signed(ev * 100, 1)}%</span>`]);
    }
    $('#px-out').innerHTML = kv(rows);
  }

  // ---------- Симулятор ----------
  function stakeLabel() {
    const m = $('#s-mode').value;
    $('#s-stake-lbl').textContent = m === 'flat' ? 'Сумма ставки' : m === 'pct' ? '% от банка' : 'Доля Келли (0–1)';
  }
  function simulate() {
    const p = num('#s-p') / 100, o = num('#s-o'), start = num('#s-bank'), mode = $('#s-mode').value, st = num('#s-stake');
    const n = Math.min(5000, Math.max(1, Math.round(num('#s-n')))), runs = Math.min(10000, Math.max(10, Math.round(num('#s-runs'))));
    if (!(p > 0 && p < 1 && o > 1 && start > 0 && st > 0)) { $('#s-out').innerHTML = '<p class="hint">Проверьте параметры.</p>'; return; }
    const kFull = Math.max(0, (p * o - 1) / (o - 1));
    const finals = new Float64Array(runs);
    const paths = [];
    let ruined = 0, halved = 0;
    for (let r = 0; r < runs; r++) {
      let b = start, minB = start;
      const path = r < 25 ? [b] : null;
      for (let i = 0; i < n; i++) {
        let s = mode === 'flat' ? st : mode === 'pct' ? (b * st) / 100 : b * kFull * st;
        s = Math.min(s, b);
        if (s <= 0 || b < start * 0.01) { if (path) path.push(b); continue; }
        b += rand() < p ? s * (o - 1) : -s;
        if (b < minB) minB = b;
        if (path) path.push(b);
      }
      finals[r] = b;
      if (b < start * 0.01) ruined++;
      if (minB <= start / 2) halved++;
      if (path) paths.push(path);
    }
    const sortedF = Array.from(finals).sort((a, b) => a - b);
    const q = (x) => sortedF[Math.min(runs - 1, Math.floor(x * runs))];
    const mean = sortedF.reduce((s, v) => s + v, 0) / runs;
    const profitable = sortedF.filter((v) => v > start).length / runs;
    const ev = p * o - 1;
    const tile = (k, v, c = '') => `<div class="stat"><div class="k">${k}</div><div class="v ${c}">${v}</div></div>`;
    $('#s-out').innerHTML =
      tile('Матожидание ставки', signed(ev * 100, 2) + '%', cls(ev)) +
      tile('Медиана банка', money(Math.round(q(0.5)))) +
      tile('Средний банк', money(Math.round(mean))) +
      tile('5% худших ниже', money(Math.round(q(0.05)))) +
      tile('5% лучших выше', money(Math.round(q(0.95)))) +
      tile('В плюсе', pct(profitable)) +
      tile('Хоть раз −50%', pct(halved / runs)) +
      tile('Банкротство', pct(ruined / runs), ruined ? 'neg' : '');
    lineChart($('#s-chart'), paths, { base: start, cls: 'ch-thin', height: 260 });
  }

  function init() {
    $('#cv-fmt').addEventListener('change', converter);
    $('#cv-val').addEventListener('input', converter);
    ['#mg-1', '#mg-2', '#mg-3'].forEach((s) => $(s).addEventListener('input', margin));
    ['#k-p', '#k-o', '#k-b', '#k-f'].forEach((s) => $(s).addEventListener('input', kelly));

    addLeg('1.80', ''); addLeg('2.10', ''); addLeg('1.65', '');
    $('#px-add').addEventListener('click', () => { addLeg(); parlay(); });
    $('#px-legs').addEventListener('input', parlay);
    $('#px-stake').addEventListener('input', parlay);
    $('#px-legs').addEventListener('click', (e) => {
      if (e.target.closest('.lg-del')) { e.target.closest('.leg').remove(); parlay(); }
    });

    $('#s-mode').addEventListener('change', () => {
      stakeLabel();
      const m = $('#s-mode').value;
      $('#s-stake').value = m === 'flat' ? 20 : m === 'pct' ? 2 : 0.25;
    });
    $('#s-run').addEventListener('click', simulate);

    converter(); margin(); kelly(); parlay(); stakeLabel();
    lineChart($('#s-chart'), [], {});
  }

  return { init };
})();
