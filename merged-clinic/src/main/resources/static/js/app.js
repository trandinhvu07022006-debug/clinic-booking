/* ════════════════════════════════════
   CLINIC BOOKING — Global JS
════════════════════════════════════ */

// ── TOAST ──────────────────────────
window.Toast = {
  _c() {
    let c = document.getElementById('toast-container');
    if (!c) { c = document.createElement('div'); c.id = 'toast-container'; document.body.appendChild(c); }
    return c;
  },
  show(msg, type='info', dur=3500) {
    const icons={success:'✓',error:'✕',warn:'⚠',info:'ℹ'};
    const t = document.createElement('div');
    t.className = `toast toast-${type}`;
    t.innerHTML = `<span class="toast-icon">${icons[type]}</span><span style="flex:1">${msg}</span><button class="toast-close" onclick="this.parentElement.remove()">×</button>`;
    this._c().appendChild(t);
    requestAnimationFrame(() => t.classList.add('show'));
    setTimeout(() => { t.classList.remove('show'); t.classList.add('hide'); setTimeout(() => t.remove(), 320); }, dur);
  },
  success:(m,d)=>Toast.show(m,'success',d),
  error:(m,d)=>Toast.show(m,'error',d),
  warn:(m,d)=>Toast.show(m,'warn',d),
  info:(m,d)=>Toast.show(m,'info',d),
};

document.addEventListener('DOMContentLoaded', () => {
  // Spinner on submit
  document.querySelectorAll('form').forEach(form => {
    form.addEventListener('submit', () => {
      const btn = form.querySelector('[type=submit]');
      if (btn) setTimeout(() => btn.classList.add('btn-loading'), 50);
    });
  });

  // Flash toast từ data attributes
  const fok  = document.querySelector('[data-flash-ok]');
  const ferr = document.querySelector('[data-flash-err]');
  if (fok)  Toast.success(fok.dataset.flashOk);
  if (ferr) Toast.error(ferr.dataset.flashErr);

  // Mobile hamburger
  const hb  = document.getElementById('hamburger');
  const sb  = document.querySelector('.dash-sidebar');
  const ov  = document.getElementById('sidebarOverlay');
  if (hb && sb) {
    hb.addEventListener('click', () => { sb.classList.toggle('open'); ov && ov.classList.toggle('show'); });
    ov && ov.addEventListener('click', () => { sb.classList.remove('open'); ov.classList.remove('show'); });
  }
});

// ── BAR CHART ──────────────────────
function renderBarChart(id, labels, values, todayLabel) {
  const w = document.getElementById(id); if (!w) return;
  const max = Math.max(...values, 1);
  w.innerHTML = '';
  labels.forEach((lbl, i) => {
    const pct = Math.round((values[i]/max)*100);
    const isToday = lbl === todayLabel;
    const col = document.createElement('div');
    col.className = 'bar-col';
    col.innerHTML = `<span class="bar-val">${values[i]||''}</span>
      <div class="bar${isToday?' today-bar':''}" style="height:${Math.max(pct, values[i]>0?8:3)}px" title="${lbl}: ${values[i]} lịch"></div>
      <span class="bar-label">${lbl}</span>`;
    w.appendChild(col);
  });
}

// ── DONUT CHART ─────────────────────
function renderDonut(svgId, data) {
  const svg = document.getElementById(svgId); if (!svg) return;
  const colors = {CONFIRMED:'#1a6b52',COMPLETED:'#94a3b8',CANCELLED:'#f87171',PENDING_OTP:'#f59e0b'};
  const total = Object.values(data).reduce((a,b)=>a+b,0);
  if (!total) { svg.innerHTML='<circle cx="60" cy="60" r="45" fill="#f1f5f9"/>'; return; }
  let offset = -90, paths = '';
  Object.entries(data).forEach(([k,v]) => {
    if (!v) return;
    const a = (v/total)*360, r=45, cx=60, cy=60;
    const s = pxy(cx,cy,r,offset), e = pxy(cx,cy,r,offset+a);
    paths += `<path d="M${cx},${cy} L${s.x},${s.y} A${r},${r} 0 ${a>180?1:0},1 ${e.x},${e.y} Z" fill="${colors[k]||'#ccc'}" opacity=".9"/>`;
    offset += a;
  });
  svg.innerHTML = paths + `<circle cx="60" cy="60" r="28" fill="white"/>
    <text x="60" y="57" text-anchor="middle" font-size="11" fill="#64748b" font-family="Tinos,serif">Tổng</text>
    <text x="60" y="71" text-anchor="middle" font-size="15" font-weight="700" fill="#1a3d2e" font-family="Tinos,serif">${total}</text>`;
}
function pxy(cx,cy,r,deg){const rd=(deg*Math.PI)/180;return{x:+(cx+r*Math.cos(rd)).toFixed(2),y:+(cy+r*Math.sin(rd)).toFixed(2)};}

// ── MINI CALENDAR ───────────────────
function MiniCal(opts) {
  const {containerId, selectedDate, onSelect} = opts;
  const el = document.getElementById(containerId); if (!el) return;
  let cur = selectedDate ? new Date(selectedDate) : new Date();
  const today = new Date(); today.setHours(0,0,0,0);
  const days = ['CN','T2','T3','T4','T5','T6','T7'];
  const months = ['Tháng 1','Tháng 2','Tháng 3','Tháng 4','Tháng 5','Tháng 6',
                  'Tháng 7','Tháng 8','Tháng 9','Tháng 10','Tháng 11','Tháng 12'];

  function fmt(d){ return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0'); }

  function render() {
    const yr = cur.getFullYear(), mo = cur.getMonth();
    const first = new Date(yr, mo, 1).getDay();
    const last  = new Date(yr, mo+1, 0).getDate();
    let html = `<div class="mini-cal">
      <div class="cal-header">
        <button class="cal-nav" onclick="calNav(-1)">‹</button>
        <span>${months[mo]} ${yr}</span>
        <button class="cal-nav" onclick="calNav(1)">›</button>
      </div>
      <div class="cal-grid">
        ${days.map(d=>`<div class="cal-dow">${d}</div>`).join('')}
        ${Array(first).fill('<div class="cal-day cal-empty"></div>').join('')}`;
    for (let d = 1; d <= last; d++) {
      const dt = new Date(yr, mo, d);
      const iso = fmt(dt);
      const isPast = dt < today;
      const isTod  = iso === fmt(today);
      const isSel  = iso === (opts.selectedDate||'');
      let cls = 'cal-day';
      if (isPast) cls += ' cal-past';
      if (isTod)  cls += ' cal-today';
      if (isSel)  cls += ' cal-selected';
      html += `<div class="${cls}" ${!isPast?`onclick="calPick('${iso}')"`:''}>${d}</div>`;
    }
    html += '</div></div>';
    el.innerHTML = html;
    window.calNav = (dir) => { cur = new Date(yr, mo+dir, 1); render(); };
    window.calPick = (iso) => { opts.selectedDate = iso; render(); if(onSelect) onSelect(iso); };
  }
  render();
}
