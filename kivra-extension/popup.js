// popup.js – logic for the Kivra Receipt Downloader popup

let authToken = null;
let receipts = [];

// ---------------------------------------------------------------------------
// Initialisation
// ---------------------------------------------------------------------------

async function init() {
  // 1. Ask the background for the captured token
  const tokenResponse = await sendToBackground({ type: 'GET_TOKEN' });
  if (tokenResponse?.token) {
    authToken = tokenResponse.token;
    setStatus('token-status', 'Token hittad automatiskt', 'ok');
  } else {
    setStatus(
      'token-status',
      'Token ej hittad – klicka på ett kvitto i Kivra och öppna popup:en igen',
      'warn'
    );
    document.getElementById('token-section').style.display = 'block';
  }

  // 2. Ask the content script on the active tab for the receipt list
  let tab;
  try {
    [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  } catch {
    setStatus('receipt-status', 'Kunde inte hämta aktuell flik', 'error');
    return;
  }

  if (!tab?.id) {
    setStatus('receipt-status', 'Ingen aktiv flik hittades', 'error');
    return;
  }

  try {
    const receiptResponse = await chrome.tabs.sendMessage(tab.id, {
      type: 'GET_RECEIPTS',
    });
    receipts = receiptResponse?.receipts ?? [];

    if (receipts.length > 0) {
      setStatus(
        'receipt-status',
        `${receipts.length} kvitto${receipts.length !== 1 ? 'n' : ''} hittade på sidan`,
        'ok'
      );
    } else {
      setStatus(
        'receipt-status',
        'Inga kvitton hittades – navigera till kvittosidan i Kivra',
        'warn'
      );
    }
  } catch {
    setStatus(
      'receipt-status',
      'Navigera till kvittosidan i Kivra och öppna popup:en igen',
      'warn'
    );
  }

  updateDownloadButton();
}

// ---------------------------------------------------------------------------
// Event listeners
// ---------------------------------------------------------------------------

document.getElementById('download-btn').addEventListener('click', () => {
  if (!authToken || receipts.length === 0) return;
  startDownload();
});

document.getElementById('save-token-btn').addEventListener('click', () => {
  const input = document.getElementById('token-input').value.trim();
  if (!input) return;

  authToken = input;
  chrome.storage.session.set({ authToken: input });
  setStatus('token-status', 'Token sparad manuellt', 'ok');
  document.getElementById('token-section').style.display = 'none';
  updateDownloadButton();
});

// ---------------------------------------------------------------------------
// Download flow
// ---------------------------------------------------------------------------

async function startDownload() {
  const btn = document.getElementById('download-btn');
  btn.disabled = true;
  setProgress(`Laddar ner 0 / ${receipts.length}…`);

  // Listen for progress updates from the background service worker
  const progressListener = (msg) => {
    if (msg.type !== 'DOWNLOAD_PROGRESS') return;
    setProgress(`Laddar ner ${msg.current} / ${msg.total}…`);
    if (msg.current >= msg.total) {
      setProgress(`✓ Klart! ${msg.total} kvitton nedladdade.`);
      chrome.runtime.onMessage.removeListener(progressListener);
      btn.disabled = false;
    }
  };
  chrome.runtime.onMessage.addListener(progressListener);

  try {
    const result = await sendToBackground({
      type: 'DOWNLOAD_RECEIPTS',
      receipts,
      token: authToken,
    });
    if (result && !result.success) {
      setProgress(`Fel: ${result.error}`);
      btn.disabled = false;
      chrome.runtime.onMessage.removeListener(progressListener);
    }
  } catch (err) {
    setProgress(`Fel: ${err.message}`);
    btn.disabled = false;
    chrome.runtime.onMessage.removeListener(progressListener);
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function updateDownloadButton() {
  document.getElementById('download-btn').disabled =
    !authToken || receipts.length === 0;
}

function sendToBackground(msg) {
  return new Promise((resolve, reject) => {
    chrome.runtime.sendMessage(msg, (response) => {
      if (chrome.runtime.lastError) {
        reject(new Error(chrome.runtime.lastError.message));
      } else {
        resolve(response);
      }
    });
  });
}

function setStatus(id, text, type) {
  const el = document.getElementById(id);
  el.textContent = text;
  el.className = `status ${type ?? ''}`;
}

function setProgress(text) {
  document.getElementById('progress').textContent = text;
}

init();
