document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
  const host = params.get('ip') || '127.0.0.1';
  const pin = params.get('pin') || '';

  const streamImg = document.getElementById('screen-stream');
  const frame = document.getElementById('phone-frame');
  const hostLabel = document.getElementById('host-label');
  const btnBack = document.getElementById('btn-back');
  const btnHome = document.getElementById('btn-home');
  const btnRecents = document.getElementById('btn-recents');

  const baseUrl = `http://${host}:8888`;
  hostLabel.textContent = `${host}:8888`;

  // Start MJPEG stream
  streamImg.src = `${baseUrl}/stream?pin=${encodeURIComponent(pin)}&t=${Date.now()}`;

  let isMouseDown = false;
  let startX = 0;
  let startY = 0;

  streamImg.addEventListener('mousedown', (e) => {
    isMouseDown = true;
    const rect = streamImg.getBoundingClientRect();
    startX = (e.clientX - rect.left) / rect.width;
    startY = (e.clientY - rect.top) / rect.height;

    sendTouch(0, startX, startY);
    createRipple(e.clientX - rect.left, e.clientY - rect.top);
  });

  streamImg.addEventListener('mousemove', (e) => {
    if (!isMouseDown) return;
    const rect = streamImg.getBoundingClientRect();
    const curX = (e.clientX - rect.left) / rect.width;
    const curY = (e.clientY - rect.top) / rect.height;
    sendTouch(2, curX, curY, startX, startY);
  });

  window.addEventListener('mouseup', (e) => {
    if (!isMouseDown) return;
    isMouseDown = false;
    const rect = streamImg.getBoundingClientRect();
    const curX = (e.clientX - rect.left) / rect.width;
    const curY = (e.clientY - rect.top) / rect.height;
    sendTouch(1, curX, curY);
  });

  function sendTouch(action, x, y, sx = x, sy = y) {
    const clampedX = Math.max(0, Math.min(1, x));
    const clampedY = Math.max(0, Math.min(1, y));
    const clampedSx = Math.max(0, Math.min(1, sx));
    const clampedSy = Math.max(0, Math.min(1, sy));

    const url = `${baseUrl}/touch?action=${action}&x=${clampedX.toFixed(4)}&y=${clampedY.toFixed(4)}&sx=${clampedSx.toFixed(4)}&sy=${clampedSy.toFixed(4)}&pin=${encodeURIComponent(pin)}`;
    fetch(url, { mode: 'no-cors' }).catch(() => {});
  }

  function sendKey(code) {
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
});
