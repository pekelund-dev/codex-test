// Content script – runs on https://inbox.kivra.com/*
//
// Scans the current page for receipt links and returns structured data
// to the popup on request.

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message.type === 'GET_RECEIPTS') {
    sendResponse({ receipts: collectReceipts() });
  }
});

function collectReceipts() {
  const receipts = [];
  const seen = new Set();

  // Receipt links follow the pattern /user/{userId}/receipts/{receiptId}
  document.querySelectorAll('a[href*="/receipts/"]').forEach((link) => {
    const href = link.getAttribute('href');
    const match = href && href.match(/\/receipts\/([a-f0-9]+)$/);
    if (!match) return;

    const id = match[1];
    if (seen.has(id)) return;
    seen.add(id);

    receipts.push({ id, filename: buildFilename(link, id) });
  });

  return receipts;
}

function buildFilename(link, id) {
  const text = link.textContent.replace(/\s+/g, ' ').trim();

  // Extract Swedish short date, e.g. "19 Feb" or "30 Jan"
  const dateMatch = text.match(
    /(\d{1,2})\s+(Jan|Feb|Mar|Apr|Maj|Jun|Jul|Aug|Sep|Okt|Nov|Dec)/i
  );
  const datePart = dateMatch
    ? `${dateMatch[2]}_${String(dateMatch[1]).padStart(2, '0')}`
    : '';

  // Extract store name – text before the first Swedish price pattern
  // (e.g. "1 087,79 kr", "37,80 kr").  The specific price format avoids
  // stopping prematurely at digits embedded in store names like "7-Eleven".
  const priceMatch = text.match(/^(.*?)\d{1,3}(?:[\s\xa0]\d{3})*,\d{2}\s*kr/i);
  const storePart = priceMatch
    ? priceMatch[1].trim()
    : text.substring(0, 40).trim();

  const name = [storePart, datePart]
    .filter(Boolean)
    .join('_')
    .replace(/[^\wåäöÅÄÖ._-]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/, '')
    .substring(0, 80);

  return `kvitton/${name || id.substring(0, 12)}.pdf`;
}
