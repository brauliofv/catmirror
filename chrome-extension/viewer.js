document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
  const host = params.get('ip') || '127.0.0.1';
  const pin = params.get('pin') || '';

  const streamImg = document.getElementById('screen-stream');
  const frame = document.getElementById('phone-frame');
  const hostLabel = document.getElementById('host-label');
  const statusLabel = document.getElementById('status-label');
  const btnBack = document.getElementById('btn-back');
  const btnHome = document.getElementById('btn-home');
  const btnRecents = document.getElementById('btn-recents');
  const btnRotate = document.getElementById('btn-rotate');
  const btnDim = document.getElementById('btn-dim');

  const baseUrl = `http://${host}:8888`;
  hostLabel.textContent = `${host}:8888`;

  // Start MJPEG stream
  streamImg.src = `${baseUrl}/stream?pin=${encodeURIComponent(pin)}&t=${Date.now()}`;

  let rotationAngle = 0;

  function setRotation(deg) {
    rotationAngle = (deg % 360 + 360) % 360;
    btnRotate.textContent = `↻ Rotar (${rotationAngle}°)`;
    streamImg.style.transform = `rotate(${rotationAngle}deg)`;
    if (rotationAngle === 90 || rotationAngle === 270) {
      streamImg.style.maxHeight = '85vw';
      streamImg.style.maxWidth = '75vh';
    } else {
      streamImg.style.maxHeight = '85vh';
      streamImg.style.maxWidth = '90vw';
    }
  }

  btnRotate.onclick = () => {
    setRotation(rotationAngle + 90);
  };

  function mapToPhone(relX, relY) {
    let px = relX;
    let py = relY;
    if (rotationAngle === 90) {
      px = relY;
      py = 1 - relX;
    } else if (rotationAngle === 180) {
      px = 1 - relX;
      py = 1 - relY;
    } else if (rotationAngle === 270) {
      px = 1 - relY;
      py = relX;
    }
    return {
      x: Math.max(0, Math.min(1, px)),
      y: Math.max(0, Math.min(1, py))
    };
  }

  let isMouseDown = false;
  let startPhoneX = 0, startPhoneY = 0;
  let downTime = 0;

  streamImg.addEventListener('mousedown', (e) => {
    if (e.button !== 0) return;
    isMouseDown = true;
    downTime = Date.now();
    const rect = streamImg.getBoundingClientRect();
    const relX = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    const relY = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));
    const p = mapToPhone(relX, relY);
    startPhoneX = p.x;
    startPhoneY = p.y;

    const frameRect = frame.getBoundingClientRect();
    createRipple(e.clientX - frameRect.left, e.clientY - frameRect.top);
  });

  window.addEventListener('mouseup', (e) => {
    if (!isMouseDown) return;
    isMouseDown = false;
    const rect = streamImg.getBoundingClientRect();
    const relX = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    const relY = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));
    const p = mapToPhone(relX, relY);

    const dist = Math.hypot(p.x - startPhoneX, p.y - startPhoneY);
    const duration = Math.min(600, Math.max(100, Date.now() - downTime));

    if (dist < 0.02) {
      sendTouch(1, p.x, p.y);
    } else {
      sendTouch(2, p.x, p.y, startPhoneX, startPhoneY, duration);
    }
  });

  function sendTouch(action, x, y, sx = x, sy = y, dur = 150) {
    if (statusLabel) {
      statusLabel.textContent = (action === 2 ? 'Deslizando...' : `Tap en ${(x * 100).toFixed(0)}%, ${(y * 100).toFixed(0)}%`);
    }
    const url = `${baseUrl}/touch?action=${action}&x=${x.toFixed(4)}&y=${y.toFixed(4)}&sx=${sx.toFixed(4)}&sy=${sy.toFixed(4)}&dur=${dur}&pin=${encodeURIComponent(pin)}`;
    fetch(url, { mode: 'no-cors' }).catch(() => {});
  }

  function sendKey(code) {
    const names = { 1: 'Atrás', 2: 'Inicio', 3: 'Recientes' };
    if (statusLabel) {
      statusLabel.textContent = `Botón: ${names[code] || code}`;
    }
    const url = `${baseUrl}/key?code=${code}&pin=${encodeURIComponent(pin)}`;
    fetch(url, { mode: 'no-cors' }).catch(() => {});
  }

  function createRipple(x, y) {
    const r = document.createElement('div');
    r.className = 'ripple';
    r.style.left = `${x}px`;
    r.style.top = `${y}px`;
    frame.appendChild(r);
    setTimeout(() => r.remove(), 400);
  }

  btnBack.onclick = () => sendKey(1);
  btnHome.onclick = () => sendKey(2);
  btnRecents.onclick = () => sendKey(3);

  let isDimmed = false;
  if (btnDim) {
    btnDim.onclick = () => {
      isDimmed = !isDimmed;
      btnDim.textContent = isDimmed ? '☀️ Restaurar Brillo' : '🌙 Modo Ahorro';
      if (statusLabel) {
        statusLabel.textContent = isDimmed ? 'Pantalla del teléfono atenuada al mínimo (Modo Ahorro)' : 'Brillo normal restaurado';
      }
      const url = `${baseUrl}/dim?state=${isDimmed ? '1' : '0'}&pin=${encodeURIComponent(pin)}`;
      fetch(url, { mode: 'no-cors' }).catch(() => {});
    };
  }
});
