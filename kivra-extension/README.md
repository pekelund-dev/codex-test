# Kivra Receipt Downloader – Chrome Extension

A Chrome extension that bulk-downloads all your receipts from
[Kivra](https://inbox.kivra.com) as PDF files.

## How it works

The extension intercepts outgoing HTTPS requests from the Kivra web app to
`app.api.kivra.com` and captures the `Authorization` token automatically –
no manual copy-paste is needed.  Once the token is known and receipts are
visible on the page, a single button click downloads them all in sequence.

## Installation

1. Open Chrome and navigate to `chrome://extensions`.
2. Enable **Developer mode** (toggle in the top-right corner).
3. Click **Load unpacked** and select the `kivra-extension` folder from this
   repository.
4. The extension icon appears in your toolbar.

## Usage

1. Log in to [inbox.kivra.com](https://inbox.kivra.com) and navigate to the
   **Receipts** section.
2. Interact with the page (e.g. open or hover over a receipt) so that the
   browser sends at least one request to `app.api.kivra.com`.  The extension
   captures the authorization token from that request automatically.
3. *(Optional)* Click **Visa fler** ("Show more") one or more times to load
   additional receipts.  The extension only downloads receipts that are
   currently rendered in the DOM.
4. Click the extension icon in the toolbar to open the popup.
5. The popup shows how many receipts were found and whether the token was
   captured.  Click **Ladda ner alla kvitton** to start downloading.
6. PDFs are saved to a `kvitton/` subfolder inside your default Downloads
   directory.  Filenames are derived from the store name and date (e.g.
   `kvitton/ICA_Kvantum_Malmborgs_Caroli_Feb_19.pdf`).

### Token not detected?

If the popup reports that no token was found, you can supply it manually:

1. Open Chrome DevTools (F12) → **Network** tab.
2. In the filter bar type `app.api.kivra.com`.
3. Reload the page and click any receipt to trigger an API call.
4. Select the request, open the **Headers** tab and copy the value of the
   `Authorization` request header (starts with `token …`).
5. Paste it into the input field at the bottom of the popup and click
   **Spara token**.

## File structure

```
kivra-extension/
├── manifest.json   Chrome Extension Manifest V3
├── background.js   Service worker: token capture + PDF download
├── content.js      Content script: receipt link parser
├── popup.html      Extension popup UI
└── popup.js        Popup logic
```

## Permissions used

| Permission | Reason |
|---|---|
| `webRequest` | Intercept requests to `app.api.kivra.com` to capture the auth token |
| `downloads` | Save fetched PDFs to the filesystem |
| `storage` | Persist the captured token across popup opens (session storage) |
| `tabs` | Query the active tab so the popup can communicate with the content script |
| `https://inbox.kivra.com/*` | Read receipt links from the Kivra web app |
| `https://app.api.kivra.com/*` | Fetch PDF files and observe request headers |
