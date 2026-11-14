'use strict';

const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));

/** Читает число из input (поддерживает запятую). */
function num(el) {
  if (typeof el === 'string') el = $(el);
  return parseFloat(String(el.value).replace(',', '.'));
}
const fmt = (x, d = 2) => (Number.isFinite(x) ? x.toFixed(d) : '—');
const pct = (x, d = 1) => (Number.isFinite(x) ? (x * 100).toFixed(d) + '%' : '—');
const money = (x) => (Number.isFinite(x) ? x.toLocaleString('ru-RU', { maximumFractionDigits: 2 }) : '—');
const signed = (x, d = 2) => (Number.isFinite(x) ? (x > 0 ? '+' : '') + x.toFixed(d) : '—');
const cls = (x) => (x > 0 ? 'pos' : x < 0 ? 'neg' : '');

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const store = {
  get(k, def) {
    try { const v = localStorage.getItem(k); return v == null ? def : JSON.parse(v); } catch (e) { return def; }
  },
  set(k, v) {
    try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* хранилище недоступно */ }
  },
};

/** Криптографически стойкое случайное число в [0, 1). */
function rand() {
  const a = new Uint32Array(1);
  crypto.getRandomValues(a);
  return a[0] / 4294967296;
}
const randInt = (n) => Math.floor(rand() * n);

function download(name, text, type) {
  const blob = new Blob([text], { type });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

function shortNum(v) {
  const a = Math.abs(v);
  if (a >= 1e6) return (v / 1e6).toFixed(1) + 'M';
  if (a >= 1e4) return (v / 1e3).toFixed(0) + 'k';
  if (a >= 100) return v.toFixed(0);
  return v.toFixed(1);
}

/**
 * Простой линейный график в SVG.
 * series — массив массивов значений (x = индекс).
 * opts: { base, classes: [cls per series], cls, height }
 */
function lineChart(svg, series, opts = {}) {
  const W = 600, H = opts.height || 220, pl = 46, pr = 10, pt = 10, pb = 22;
  svg.setAttribute('viewBox', `0 0 ${W} ${H}`);
  const all = series.flat().filter(Number.isFinite);
  if (!all.length) {
    svg.innerHTML = `<text x="${W / 2}" y="${H / 2}" text-anchor="middle" class="ch-empty">Нет данных</text>`;
    return;
  }
  let min = Math.min(...all), max = Math.max(...all);
  if (opts.base != null) { min = Math.min(min, opts.base); max = Math.max(max, opts.base); }
  if (min === max) { min -= 1; max += 1; }
  const n = Math.max(...series.map((s) => s.length));
  const x = (i) => pl + (n <= 1 ? 0 : (i * (W - pl - pr)) / (n - 1));
  const y = (v) => pt + ((max - v) * (H - pt - pb)) / (max - min);
  let out = '';
  for (let k = 0; k <= 4; k++) {
    const v = min + ((max - min) * k) / 4, yy = y(v).toFixed(1);
    out += `<line x1="${pl}" x2="${W - pr}" y1="${yy}" y2="${yy}" class="ch-grid"/>`;
    out += `<text x="${pl - 6}" y="${+yy + 4}" text-anchor="end" class="ch-label">${shortNum(v)}</text>`;
  }
  if (opts.base != null) {
    const yb = y(opts.base).toFixed(1);
    out += `<line x1="${pl}" x2="${W - pr}" y1="${yb}" y2="${yb}" class="ch-base"/>`;
  }
  series.forEach((s, idx) => {
    if (!s.length) return;
    const d = s.map((v, i) => (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1)).join('');
    const c = (opts.classes && opts.classes[idx]) || opts.cls || 'ch-line';
    out += `<path d="${d}" class="${c}"/>`;
  });
  out += `<text x="${pl}" y="${H - 5}" class="ch-label">0</text>`;
  out += `<text x="${W - pr}" y="${H - 5}" text-anchor="end" class="ch-label">${n - 1}</text>`;
  svg.innerHTML = out;
}

const today = () => {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
};
