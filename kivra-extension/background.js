// Service worker for Kivra Receipt Downloader
//
// Responsibilities:
// 1. Intercept outgoing requests to app.api.kivra.com to capture the
//    Authorization token automatically (no manual copy-paste needed).
// 2. Handle download requests from the popup, fetching each PDF with
//    the captured token and saving it via chrome.downloads.

// ---------------------------------------------------------------------------
// Token capture via webRequest
// ---------------------------------------------------------------------------

chrome.webRequest.onBeforeSendHeaders.addListener(
  (details) => {
    const authHeader = details.requestHeaders?.find(
      (h) => h.name.toLowerCase() === 'authorization'
    );
    if (authHeader?.value) {
      chrome.storage.session.set({ authToken: authHeader.value });
    }
  },
  { urls: ['https://app.api.kivra.com/*'] },
  // 'extraHeaders' is required to observe security-sensitive headers such as
  // Authorization that Chrome hides from webRequest listeners by default.
  ['requestHeaders', 'extraHeaders']
);

// ---------------------------------------------------------------------------
// Message handler
// ---------------------------------------------------------------------------

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type === 'GET_TOKEN') {
    chrome.storage.session.get(['authToken'], (result) => {
      sendResponse({ token: result.authToken ?? null });
    });
    return true; // keep channel open for async response
  }

  if (message.type === 'DOWNLOAD_RECEIPTS') {
    const { receipts, token } = message;
    downloadAll(receipts, token)
      .then(() => sendResponse({ success: true }))
      .catch((err) => sendResponse({ success: false, error: err.message }));
    return true;
  }
});

// ---------------------------------------------------------------------------
// Download logic
// ---------------------------------------------------------------------------

async function downloadAll(receipts, token) {
  for (let i = 0; i < receipts.length; i++) {
    const { id, filename } = receipts[i];
    await downloadReceipt(id, filename, token);

    // Notify the popup about progress (it may already be closed – that's fine)
    chrome.runtime.sendMessage({
      type: 'DOWNLOAD_PROGRESS',
      current: i + 1,
      total: receipts.length,
    }).catch((err) => console.debug('[Kivra] Progress message failed:', err.message));

    // Small pause between requests to avoid rate-limiting
    if (i < receipts.length - 1) {
      await delay(300);
    }
  }
}

async function downloadReceipt(receiptId, filename, token) {
  const url = `https://app.api.kivra.com/v1/receipts/${receiptId}`;

  const response = await fetch(url, {
    headers: {
      Authorization: token,
      Accept: 'application/pdf',
      Origin: 'https://inbox.kivra.com',
    },
  });

  if (!response.ok) {
    console.error(
      `[Kivra] Receipt ${receiptId} fetch failed: HTTP ${response.status}`
    );
    return;
  }

  const arrayBuffer = await response.arrayBuffer();
  const base64 = arrayBufferToBase64(arrayBuffer);
  const dataUrl = `data:application/pdf;base64,${base64}`;

  await new Promise((resolve, reject) => {
    chrome.downloads.download(
      { url: dataUrl, filename, saveAs: false, conflictAction: 'uniquify' },
      (downloadId) => {
        if (chrome.runtime.lastError) {
          reject(new Error(chrome.runtime.lastError.message));
        } else {
          resolve(downloadId);
        }
      }
    );
  });
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function arrayBufferToBase64(buffer) {
  const bytes = new Uint8Array(buffer);
  const chars = [];
  for (let i = 0; i < bytes.byteLength; i++) {
    chars.push(String.fromCharCode(bytes[i]));
  }
  return btoa(chars.join(''));
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
