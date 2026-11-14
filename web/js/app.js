'use strict';

function showTab(t) {
  if (!document.getElementById('tab-' + t)) t = 'predict';
  $$('.tab').forEach((s) => (s.hidden = s.id !== 'tab-' + t));
  $$('.tabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === t));
  store.set('bl.tab', t);
  if (t === 'predict') {
    // Подставляем текущий банк из трекера
    $('#p-bank').value = Math.max(0, Math.round(Tracker.currentBankroll() * 100) / 100);
    Predict.render();
  }
}

Tracker.init();
Predict.init();
Calc.init();
Casino.init();
$$('.tabs button').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));
showTab(store.get('bl.tab', 'predict'));
