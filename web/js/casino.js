'use strict';

/** Казино на виртуальных фишках: слоты, рулетка, блэкджек. */
const Casino = (() => {
  const START = 1000;
  const freshStats = () => ({ slots: { w: 0, r: 0, n: 0 }, roulette: { w: 0, r: 0, n: 0 }, bj: { w: 0, r: 0, n: 0 } });
  let st = store.get('bl.casino', null);
  if (!st || !st.stats) st = { chips: START, stats: freshStats() };
  const save = () => store.set('bl.casino', st);

  function wager(game, amt, newRound = true) {
    if (!(amt > 0) || amt > st.chips) return false;
    st.chips -= amt;
    st.stats[game].w += amt;
    if (newRound) st.stats[game].n++;
    save(); renderBalance();
    return true;
  }
  function payout(game, amt) {
    if (amt <= 0) return;
    st.chips += amt;
    st.stats[game].r += amt;
    save(); renderBalance();
  }

  const THEORY = {}; // теоретический возврат, заполняется ниже
  function renderBalance() {
    $('#c-bal').textContent = money(st.chips);
    for (const [g, el] of [['slots', '#st-slots'], ['roulette', '#st-roulette'], ['bj', '#st-bj']]) {
      const s = st.stats[g];
      const net = s.r - s.w;
      $(el).innerHTML = `Раундов: ${s.n} · Поставлено: ${money(s.w)} · Итог: <span class="${cls(net)}">${signed(net, 0)}</span><br>` +
        `Возврат: факт ${s.w ? pct(s.r / s.w) : '—'}, теория ${THEORY[g]}`;
    }
  }

  // ================= СЛОТЫ =================
  const SYMS = [
    { s: '🍒', w: 5, pay: 10 },
    { s: '🍋', w: 5, pay: 12 },
    { s: '🔔', w: 4, pay: 20 },
    { s: '⭐', w: 3, pay: 50 },
    { s: '💎', w: 2, pay: 100 },
    { s: '7️⃣', w: 1, pay: 300 },
  ];
  const TWO_CHERRY = 1; // ровно две вишни — возврат ставки
  const TOTAL_W = SYMS.reduce((s, x) => s + x.w, 0);

  function pickSym() {
    let r = randInt(TOTAL_W);
    for (let i = 0; i < SYMS.length; i++) { if (r < SYMS[i].w) return i; r -= SYMS[i].w; }
    return SYMS.length - 1;
  }
  function slotMult(a, b, c) {
    if (a === b && b === c) return SYMS[a].pay;
    const cherries = [a, b, c].filter((x) => x === 0).length;
    return cherries === 2 ? TWO_CHERRY : 0;
  }
  // Точный теоретический возврат перебором всех комбинаций
  function slotRTP() {
    let rtp = 0;
    for (let a = 0; a < SYMS.length; a++) for (let b = 0; b < SYMS.length; b++) for (let c = 0; c < SYMS.length; c++)
      rtp += (SYMS[a].w * SYMS[b].w * SYMS[c].w) / TOTAL_W ** 3 * slotMult(a, b, c);
    return rtp;
  }

  let spinning = false;
  function spinSlots() {
    if (spinning) return;
    const bet = +$('#sl-bet').value;
    if (!wager('slots', bet)) { $('#sl-msg').textContent = 'Не хватает фишек.'; return; }
    spinning = true;
    $('#sl-spin').disabled = true;
    $('#sl-msg').textContent = '';
    const reels = $$('.reel');
    reels.forEach((r) => r.classList.remove('win'));
    const result = [pickSym(), pickSym(), pickSym()];
    const stopped = [false, false, false];
    const iv = setInterval(() => reels.forEach((r, i) => { if (!stopped[i]) r.textContent = SYMS[randInt(SYMS.length)].s; }), 70);
    [500, 800, 1100].forEach((t, i) => setTimeout(() => {
      stopped[i] = true;
      reels[i].textContent = SYMS[result[i]].s;
      if (i === 2) {
        clearInterval(iv);
        const mult = slotMult(...result);
        const win = bet * mult;
        payout('slots', win);
        if (mult > 0) {
          reels.forEach((r, k) => { if (mult > 1 || result[k] === 0) r.classList.add('win'); });
          $('#sl-msg').innerHTML = `<span class="pos">Выигрыш ${money(win)} (×${mult})</span>`;
        } else {
          $('#sl-msg').textContent = 'Мимо.';
        }
        spinning = false;
        $('#sl-spin').disabled = false;
      }
    }, t));
  }

  function slotPaytable() {
    let html = '<table class="tbl"><tr><th>Комбинация</th><th class="num">Выплата</th><th class="num">Шанс</th></tr>';
    for (const x of SYMS) {
      const p = (x.w / TOTAL_W) ** 3;
      html += `<tr><td>${x.s}${x.s}${x.s}</td><td class="num">×${x.pay}</td><td class="num">1 к ${Math.round(1 / p)}</td></tr>`;
    }
    const q = SYMS[0].w / TOTAL_W;
    html += `<tr><td>Ровно две 🍒</td><td class="num">×${TWO_CHERRY}</td><td class="num">1 к ${fmt(1 / (3 * q * q * (1 - q)), 1)}</td></tr></table>`;
    $('#sl-pay').innerHTML = html;
  }

  // ================= РУЛЕТКА =================
  const REDS = new Set([1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36]);
  const colorOf = (n) => (n === 0 ? 'green' : REDS.has(n) ? 'red' : 'black');
  const range = (a, b) => Array.from({ length: b - a + 1 }, (_, i) => a + i);
  const ALL = range(1, 36);
  const BETS = {
    red: { label: 'Красное', nums: ALL.filter((n) => REDS.has(n)), mult: 2, css: 'b-red' },
    black: { label: 'Чёрное', nums: ALL.filter((n) => !REDS.has(n)), mult: 2, css: 'b-black' },
    even: { label: 'Чёт', nums: ALL.filter((n) => n % 2 === 0), mult: 2 },
    odd: { label: 'Нечет', nums: ALL.filter((n) => n % 2 === 1), mult: 2 },
    low: { label: '1–18', nums: range(1, 18), mult: 2 },
    high: { label: '19–36', nums: range(19, 36), mult: 2 },
    d1: { label: '1–12', nums: range(1, 12), mult: 3 },
    d2: { label: '13–24', nums: range(13, 24), mult: 3 },
    d3: { label: '25–36', nums: range(25, 36), mult: 3 },
    c1: { label: 'Колонка 1', nums: ALL.filter((n) => n % 3 === 1), mult: 3 },
    c2: { label: 'Колонка 2', nums: ALL.filter((n) => n % 3 === 2), mult: 3 },
    c3: { label: 'Колонка 3', nums: ALL.filter((n) => n % 3 === 0), mult: 3 },
  };
  let slip = []; // { label, nums, mult, amt }
  let history = [];
  let rlSpinning = false;

  function buildBoard() {
    let html = '';
    for (const [k, b] of Object.entries(BETS)) html += `<button data-bet="${k}" class="${b.css || ''}">${b.label}</button>`;
    html += '<button data-bet="num" class="wide">На число (×36)</button>';
    $('#rl-board').innerHTML = html;
  }
  function renderSlip() {
    const total = slip.reduce((s, b) => s + b.amt, 0);
    $('#rl-slip').innerHTML = slip.length
      ? slip.map((b) => `${esc(b.label)}: ${b.amt}`).join(' · ') + ` — всего <b>${total}</b>`
      : 'Нажмите на поле, чтобы поставить фишку.';
  }
  function addBet(key) {
    const amt = +$('#rl-amt').value;
    let bet;
    if (key === 'num') {
      const n = Math.round(num('#rl-n'));
      if (!(n >= 0 && n <= 36)) { $('#rl-msg').textContent = 'Число от 0 до 36.'; return; }
      bet = { label: `Число ${n}`, nums: [n], mult: 36 };
    } else bet = { ...BETS[key] };
    const existing = slip.find((b) => b.label === bet.label);
    if (existing) existing.amt += amt; else slip.push({ label: bet.label, nums: bet.nums, mult: bet.mult, amt });
    $('#rl-msg').textContent = '';
    renderSlip();
  }
  function spinRoulette() {
    if (rlSpinning) return;
    const total = slip.reduce((s, b) => s + b.amt, 0);
    if (!total) { $('#rl-msg').textContent = 'Сначала сделайте ставку.'; return; }
    if (!wager('roulette', total)) { $('#rl-msg').textContent = 'Не хватает фишек.'; return; }
    rlSpinning = true;
    $('#rl-spin').disabled = true;
    const result = randInt(37);
    const numEl = $('#rl-num');
    const iv = setInterval(() => { const n = randInt(37); numEl.textContent = n; numEl.className = 'rl-num ' + colorOf(n); }, 60);
    setTimeout(() => {
      clearInterval(iv);
      numEl.textContent = result;
      numEl.className = 'rl-num ' + colorOf(result);
      const win = slip.reduce((s, b) => s + (b.nums.includes(result) ? b.amt * b.mult : 0), 0);
      payout('roulette', win);
      const net = win - total;
      $('#rl-msg').innerHTML = `Выпало ${result}. ` + (win
        ? `<span class="${cls(net)}">Выплата ${money(win)} (${signed(net, 0)})</span>`
        : `<span class="neg">Проигрыш ${money(total)}</span>`);
      history.unshift(result);
      history = history.slice(0, 15);
      $('#rl-hist').innerHTML = history.map((n) => `<span class="chip ${colorOf(n)}">${n}</span>`).join('');
      rlSpinning = false;
      $('#rl-spin').disabled = false;
    }, 1200);
  }

  // ================= БЛЭКДЖЕК =================
  const SUITS = ['♠', '♥', '♦', '♣'];
  const RANKS = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
  let shoe = [];
  let bj = { phase: 'idle', p: [], d: [], bet: 0 };

  function newShoe() {
    shoe = [];
    for (let k = 0; k < 6; k++) for (const s of SUITS) for (const r of RANKS) shoe.push({ r, s });
    for (let i = shoe.length - 1; i > 0; i--) { const j = randInt(i + 1); [shoe[i], shoe[j]] = [shoe[j], shoe[i]]; }
  }
  function draw() {
    if (shoe.length < 78) newShoe();
    return shoe.pop();
  }
  function handVal(cards) {
    let t = 0, aces = 0;
    for (const c of cards) {
      if (c.r === 'A') { t += 11; aces++; } else if ('JQK'.includes(c.r) || c.r === '10') t += 10; else t += +c.r;
    }
    while (t > 21 && aces) { t -= 10; aces--; }
    return { t, soft: aces > 0 };
  }
  const isBJ = (cards) => cards.length === 2 && handVal(cards).t === 21;
  const cardHtml = (c, hidden) => hidden
    ? '<div class="card-pc back"></div>'
    : `<div class="card-pc ${c.s === '♥' || c.s === '♦' ? 'red' : ''}">${c.r}${c.s}</div>`;

  function renderBJ(msg) {
    const hide = bj.phase === 'play';
    $('#bj-d').innerHTML = bj.d.map((c, i) => cardHtml(c, hide && i === 1)).join('');
    $('#bj-p').innerHTML = bj.p.map((c) => cardHtml(c)).join('');
    $('#bj-dv').textContent = bj.d.length ? (hide ? `(${handVal([bj.d[0]]).t} + ?)` : `(${handVal(bj.d).t})`) : '';
    $('#bj-pv').textContent = bj.p.length ? `(${handVal(bj.p).t})` : '';
    const play = bj.phase === 'play';
    $('#bj-deal').disabled = play;
    $('#bj-bet').disabled = play;
    $('#bj-hit').disabled = !play;
    $('#bj-stand').disabled = !play;
    $('#bj-double').disabled = !(play && bj.p.length === 2 && st.chips >= bj.bet);
    if (msg !== undefined) $('#bj-msg').innerHTML = msg;
  }
  function deal() {
    const amt = +$('#bj-bet').value;
    if (!wager('bj', amt)) { renderBJ('Не хватает фишек.'); return; }
    bj = { phase: 'play', p: [draw(), draw()], d: [draw(), draw()], bet: amt };
    const pBJ = isBJ(bj.p), dBJ = isBJ(bj.d);
    if (pBJ || dBJ) {
      bj.phase = 'done';
      if (pBJ && dBJ) { payout('bj', amt); renderBJ('Оба блэкджека — ничья.'); }
      else if (pBJ) { payout('bj', amt * 2.5); renderBJ(`<span class="pos">Блэкджек! +${money(amt * 1.5)}</span>`); }
      else renderBJ(`<span class="neg">У дилера блэкджек. −${money(amt)}</span>`);
      return;
    }
    renderBJ('');
  }
  function hit() {
    bj.p.push(draw());
    const v = handVal(bj.p).t;
    if (v > 21) { bj.phase = 'done'; renderBJ(`<span class="neg">Перебор. −${money(bj.bet)}</span>`); }
    else if (v === 21) stand();
    else renderBJ();
  }
  function stand() {
    while (handVal(bj.d).t < 17) bj.d.push(draw());
    bj.phase = 'done';
    const p = handVal(bj.p).t, d = handVal(bj.d).t;
    if (d > 21 || p > d) { payout('bj', bj.bet * 2); renderBJ(`<span class="pos">Победа! +${money(bj.bet)}</span>`); }
    else if (p === d) { payout('bj', bj.bet); renderBJ('Ничья, ставка возвращена.'); }
    else renderBJ(`<span class="neg">Дилер выиграл. −${money(bj.bet)}</span>`);
  }
  function double() {
    if (!wager('bj', bj.bet, false)) return;
    bj.bet *= 2;
    bj.p.push(draw());
    if (handVal(bj.p).t > 21) { bj.phase = 'done'; renderBJ(`<span class="neg">Перебор. −${money(bj.bet)}</span>`); }
    else stand();
  }

  function init() {
    const rtp = slotRTP();
    THEORY.slots = pct(rtp);
    THEORY.roulette = pct(36 / 37);
    THEORY.bj = '≈99.5% (при базовой стратегии)';
    slotPaytable();
    $('#sl-pay').insertAdjacentHTML('beforeend', `<p class="hint">Теоретический возврат: ${pct(rtp, 2)} — казино в среднем забирает ${pct(1 - rtp, 2)} каждой ставки.</p>`);
    $('#sl-spin').addEventListener('click', spinSlots);

    buildBoard();
    renderSlip();
    $('#rl-board').addEventListener('click', (e) => { const b = e.target.closest('[data-bet]'); if (b) addBet(b.dataset.bet); });
    $('#rl-spin').addEventListener('click', spinRoulette);
    $('#rl-clear').addEventListener('click', () => { slip = []; renderSlip(); });

    newShoe();
    $('#bj-deal').addEventListener('click', deal);
    $('#bj-hit').addEventListener('click', hit);
    $('#bj-stand').addEventListener('click', stand);
    $('#bj-double').addEventListener('click', double);
    renderBJ('');

    $('#c-reset').addEventListener('click', () => {
      if (!confirm('Сбросить баланс и статистику казино?')) return;
      st = { chips: START, stats: freshStats() };
      save();
      renderBalance();
      renderBJ();
    });
    renderBalance();
  }

  return { init };
})();
