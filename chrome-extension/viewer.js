document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
  const host = params.get('ip') || '127.0.0.1';
  const pin = params.get('pin') || '';

  const stage = document.getElementById('stage') || document.querySelector('.stage');
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

  function updateLayout() {
    const stageW = (stage ? stage.clientWidth : window.innerWidth) - 24;
    const stageH = (stage ? stage.clientHeight : (window.innerHeight - 130)) - 24;
    if (stageW <= 0 || stageH <= 0) return;

    // Determine intrinsic aspect ratio (default 720x1560 portrait ~0.4615)
    const natW = streamImg.naturalWidth > 0 ? streamImg.naturalWidth : 720;
    const natH = streamImg.naturalHeight > 0 ? streamImg.naturalHeight : 1560;
    const portraitAspect = natW / natH;

    const isSideways = (rotationAngle === 90 || rotationAngle === 270);

    if (!isSideways) {
      // Portrait container (0° or 180°)
      const frameH = Math.min(stageH, stageW / portraitAspect);
      const frameW = frameH * portraitAspect;
      frame.style.width = Math.round(frameW) + 'px';
      frame.style.height = Math.round(frameH) + 'px';
      streamImg.style.width = Math.round(frameW) + 'px';
      streamImg.style.height = Math.round(frameH) + 'px';
    } else {
      // Landscape container (90° or 270°)
      const landscapeAspect = 1 / portraitAspect;
      const frameW = Math.min(stageW, stageH * landscapeAspect);
      const frameH = frameW / landscapeAspect;
      frame.style.width = Math.round(frameW) + 'px';
      frame.style.height = Math.round(frameH) + 'px';
      // When rotated 90/270deg, streamImg dimensions are inverted to match the container
      streamImg.style.width = Math.round(frameH) + 'px';
      streamImg.style.height = Math.round(frameW) + 'px';
    }

    streamImg.style.transform = `rotate(${rotationAngle}deg)`;
  }

  streamImg.addEventListener('load', updateLayout);
  window.addEventListener('resize', updateLayout);
  setTimeout(updateLayout, 150);

  function setRotation(deg) {
    rotationAngle = (deg % 360 + 360) % 360;
    btnRotate.textContent = `↻ Rotar (${rotationAngle}°)`;
    updateLayout();
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
  let startClientX = 0, startClientY = 0;
  let downTime = 0;

  frame.addEventListener('mousedown', (e) => {
    if (e.button !== 0) return;
    e.preventDefault();
    isMouseDown = true;
    downTime = Date.now();
    startClientX = e.clientX;
    startClientY = e.clientY;

    const frameRect = frame.getBoundingClientRect();
    createRipple(e.clientX - frameRect.left, e.clientY - frameRect.top);
  });

  window.addEventListener('mouseup', (e) => {
    if (!isMouseDown) return;
    isMouseDown = false;
    const frameRect = frame.getBoundingClientRect();
    const startRelX = Math.max(0, Math.min(1, (startClientX - frameRect.left) / frameRect.width));
    const startRelY = Math.max(0, Math.min(1, (startClientY - frameRect.top) / frameRect.height));
    const endRelX = Math.max(0, Math.min(1, (e.clientX - frameRect.left) / frameRect.width));
    const endRelY = Math.max(0, Math.min(1, (e.clientY - frameRect.top) / frameRect.height));

    const startP = mapToPhone(startRelX, startRelY);
    const endP = mapToPhone(endRelX, endRelY);

    const dist = Math.hypot(endP.x - startP.x, endP.y - startP.y);
    const duration = Math.min(500, Math.max(100, Date.now() - downTime));

    if (dist < 0.02) {
      sendTouch(1, endP.x, endP.y);
    } else {
      sendTouch(2, endP.x, endP.y, startP.x, startP.y, duration);
    }
  });

  // Mouse wheel scroll support with gesture throttling
  let isWheelScrolling = false;
  let wheelAccumulator = 0;

  frame.addEventListener('wheel', (e) => {
    e.preventDefault();
    wheelAccumulator += e.deltaY;

    if (isWheelScrolling) return;
    if (Math.abs(wheelAccumulator) < 20) return;

    const scrollDown = wheelAccumulator > 0;
    wheelAccumulator = 0;
    isWheelScrolling = true;

    const frameRect = frame.getBoundingClientRect();
    const cursorRelX = Math.max(0.2, Math.min(0.8, (e.clientX - frameRect.left) / frameRect.width));
    const cursorRelY = Math.max(0.25, Math.min(0.75, (e.clientY - frameRect.top) / frameRect.height));

    // Scrolling down on page = swiping finger upwards on touch screen
    const travel = 0.35;
    let startRelY, endRelY;
    if (scrollDown) {
      startRelY = Math.min(0.85, cursorRelY + travel / 2);
      endRelY = Math.max(0.15, startRelY - travel);
    } else {
      startRelY = Math.max(0.15, cursorRelY - travel / 2);
      endRelY = Math.min(0.85, startRelY + travel);
    }

    const startP = mapToPhone(cursorRelX, startRelY);
    const endP = mapToPhone(cursorRelX, endRelY);

    if (statusLabel) {
      statusLabel.textContent = scrollDown ? 'Desplazando hacia abajo...' : 'Desplazando hacia arriba...';
    }

    sendTouch(2, endP.x, endP.y, startP.x, startP.y, 220);

    setTimeout(() => {
      isWheelScrolling = false;
    }, 280);
  }, { passive: false });

  // Keyboard navigation shortcuts (Arrow keys for scrolling, Escape for Back)
  window.addEventListener('keydown', (e) => {
    if (e.target.tagName === 'INPUT') return;

    if (e.key === 'ArrowDown' || e.key === 'PageDown') {
      e.preventDefault();
      const startP = mapToPhone(0.5, 0.7);
      const endP = mapToPhone(0.5, 0.3);
      sendTouch(2, endP.x, endP.y, startP.x, startP.y, 220);
    } else if (e.key === 'ArrowUp' || e.key === 'PageUp') {
      e.preventDefault();
      const startP = mapToPhone(0.5, 0.3);
      const endP = mapToPhone(0.5, 0.7);
      sendTouch(2, endP.x, endP.y, startP.x, startP.y, 220);
    } else if (e.key === 'Escape') {
      sendKey(1);
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
