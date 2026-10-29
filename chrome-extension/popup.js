document.addEventListener('DOMContentLoaded', () => {
  const ipInput = document.getElementById('ip');
  const pinInput = document.getElementById('pin');
  const openBtn = document.getElementById('open-btn');
  const chipLocal = document.getElementById('chip-local');
  const chipUsb = document.getElementById('chip-usb');

  chrome.storage.local.get(['catmirror_ip', 'catmirror_pin'], (result) => {
    if (result.catmirror_ip) ipInput.value = result.catmirror_ip;
    if (result.catmirror_pin) pinInput.value = result.catmirror_pin;
  });

  chipLocal.onclick = () => { ipInput.value = '127.0.0.1'; };
  chipUsb.onclick = () => { ipInput.value = '192.168.42.129'; };

  openBtn.onclick = () => {
    const ip = ipInput.value.trim() || '127.0.0.1';
    const pin = pinInput.value.trim();

    chrome.storage.local.set({
      catmirror_ip: ip,
      catmirror_pin: pin
    });

    const viewerUrl = chrome.runtime.getURL(`viewer.html?ip=${encodeURIComponent(ip)}&pin=${encodeURIComponent(pin)}`);
    chrome.tabs.create({ url: viewerUrl });
  };
});
