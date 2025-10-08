(function () {
    setupUploadControls();
    setupDashboardPolling();
})();

function setupUploadControls() {
    const dropzone = document.getElementById('receipt-dropzone');
    const fileInput = document.getElementById('receipt-files');
    const triggerButton = document.getElementById('trigger-file-select');
    const selectedList = document.getElementById('selected-files');
    const uploadButton = document.getElementById('upload-button');
    const hint = document.getElementById('file-limit-hint');

    if (!dropzone || !fileInput || !selectedList || !uploadButton) {
        return;
    }

    const form = dropzone.closest('form');
    const MAX_FILES = 10;
    const DEFAULT_HINT = `You can add up to ${MAX_FILES} files per upload.`;
    const FALLBACK_UPLOAD_BUTTON_TEXT = 'Upload receipt files';
    const originalButtonText = uploadButton.textContent;
    let selectedFiles = [];

    function formatBytes(bytes) {
        if (!bytes || bytes <= 0) {
            return '0 B';
        }
        const units = ['B', 'KB', 'MB', 'GB'];
        let size = bytes;
        let unit = 0;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit += 1;
        }
        return `${size.toFixed(1)} ${units[unit]}`;
    }

    function buildTransfer() {
        if (typeof DataTransfer !== 'undefined') {
            try {
                return new DataTransfer();
            } catch (error) {
                // Some browsers (for example Safari < 16.4) expose DataTransfer but do not allow constructing it.
            }
        }
        return null;
    }

    const supportsFileAssignment = buildTransfer() !== null;

    function syncFileInput() {
        if (!supportsFileAssignment) {
            return false;
        }

        const transfer = buildTransfer();
        if (!transfer) {
            return false;
        }

        selectedFiles.forEach((file) => transfer.items.add(file));
        fileInput.files = transfer.files;
        return true;
    }

    function updateHint(message) {
        if (hint) {
            hint.textContent = message;
        }
    }

    function refreshSelectedFiles() {
        selectedList.innerHTML = '';

        if (selectedFiles.length === 0) {
            uploadButton.disabled = true;
            updateHint(DEFAULT_HINT);
            return;
        }

        selectedFiles.forEach((file, index) => {
            const item = document.createElement('li');
            item.className = 'list-group-item d-flex justify-content-between align-items-center';

            const details = document.createElement('div');
            details.className = 'text-start';
            details.innerHTML = `<strong>${file.name}</strong><br><span class="text-muted small">${formatBytes(file.size)}</span>`;

            const removeButton = document.createElement('button');
            removeButton.type = 'button';
            removeButton.className = 'btn btn-link text-danger p-0 small';
            removeButton.textContent = 'Remove';
            removeButton.addEventListener('click', () => {
                selectedFiles = selectedFiles.filter((_, itemIndex) => itemIndex !== index);
                syncFileInput();
                refreshSelectedFiles();
            });

            item.append(details, removeButton);
            selectedList.appendChild(item);
        });

        uploadButton.disabled = false;
        updateHint(`${selectedFiles.length} file${selectedFiles.length === 1 ? '' : 's'} ready for upload.`);
    }

    function handleFiles(newFiles) {
        if (!newFiles || newFiles.length === 0) {
            return;
        }

        const incomingFiles = Array.from(newFiles).filter(
            (file) => file && Number.isFinite(file.size)
        );
        if (incomingFiles.length === 0) {
            return;
        }

        let combined = selectedFiles.concat(incomingFiles);
        if (combined.length > MAX_FILES) {
            combined = combined.slice(0, MAX_FILES);
            updateHint(`Only the first ${MAX_FILES} files were added.`);
        }

        selectedFiles = combined;
        syncFileInput();
        refreshSelectedFiles();
    }

    if (triggerButton) {
        triggerButton.addEventListener('click', () => fileInput.click());
    }

    fileInput.addEventListener('change', (event) => {
        handleFiles(event.target.files);

        if (!supportsFileAssignment) {
            event.target.value = '';
        }
    });

    ['dragenter', 'dragover'].forEach((type) => {
        dropzone.addEventListener(type, (event) => {
            event.preventDefault();
            dropzone.classList.add('is-dragover');
        });
    });

    ['dragleave', 'drop'].forEach((type) => {
        dropzone.addEventListener(type, (event) => {
            event.preventDefault();
            dropzone.classList.remove('is-dragover');
        });
    });

    dropzone.addEventListener('drop', (event) => {
        if (event.dataTransfer && event.dataTransfer.files) {
            handleFiles(event.dataTransfer.files);
        }
    });

    if (form && !supportsFileAssignment) {
        form.addEventListener('submit', (event) => {
            if (selectedFiles.length === 0) {
                updateHint('Please choose at least one file to upload.');
                event.preventDefault();
                return;
            }

            event.preventDefault();

            const formData = new FormData(form);
            formData.delete('files');
            selectedFiles.forEach((file) => formData.append('files', file));

            uploadButton.disabled = true;
            uploadButton.textContent = 'Uploading…';

            fetch(form.action, {
                method: form.method || 'POST',
                body: formData,
                credentials: 'same-origin',
            }).then((response) => {
                if (!response.ok) {
                    throw new Error('Upload failed');
                }

                const redirectUrl = response.url || response.headers.get('Location');
                if (redirectUrl) {
                    window.location.href = redirectUrl;
                } else {
                    uploadButton.disabled = false;
                    uploadButton.textContent = originalButtonText || FALLBACK_UPLOAD_BUTTON_TEXT;
                }
            }).catch(() => {
                uploadButton.disabled = false;
                uploadButton.textContent = originalButtonText || FALLBACK_UPLOAD_BUTTON_TEXT;
                updateHint('Upload failed. Please try again.');
            });
        });
    }

    refreshSelectedFiles();
}

function setupDashboardPolling() {
    const container = document.querySelector('[data-dashboard-url]');
    if (!container) {
        return;
    }

    const endpoint = container.getAttribute('data-dashboard-url');
    if (!endpoint) {
        return;
    }

    const filesCountBadge = container.querySelector('[data-files-count]');
    const filesEmpty = container.querySelector('[data-files-empty]');
    const filesTable = container.querySelector('[data-files-table]');
    const filesBody = container.querySelector('[data-files-body]');
    const parsedCountBadge = container.querySelector('[data-parsed-count]');
    const parsedEmpty = container.querySelector('[data-parsed-empty]');
    const parsedTable = container.querySelector('[data-parsed-table]');
    const parsedBody = container.querySelector('[data-parsed-body]');

    const POLL_INTERVAL = 5000;
    let pollTimeoutId = null;
    let isFetching = false;

    async function fetchAndRender() {
        if (isFetching) {
            scheduleNext();
            return;
        }

        if (document.hidden) {
            scheduleNext();
            return;
        }

        isFetching = true;
        try {
            const response = await fetch(endpoint, {
                headers: { 'Accept': 'application/json' },
                credentials: 'same-origin',
            });
            if (!response.ok) {
                throw new Error(`Unexpected status ${response.status}`);
            }
            const data = await response.json();
            renderFilesSection(data, { filesCountBadge, filesEmpty, filesTable, filesBody });
            renderParsedSection(data, { parsedCountBadge, parsedEmpty, parsedTable, parsedBody });
        } catch (error) {
            // eslint-disable-next-line no-console
            console.error('Failed to refresh receipt data', error);
        } finally {
            isFetching = false;
            scheduleNext();
        }
    }

    function scheduleNext() {
        if (pollTimeoutId !== null) {
            window.clearTimeout(pollTimeoutId);
        }
        pollTimeoutId = window.setTimeout(fetchAndRender, POLL_INTERVAL);
    }

    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) {
            fetchAndRender();
        }
    });

    fetchAndRender();
}

function renderFilesSection(data, refs) {
    if (!refs || !refs.filesBody) {
        return;
    }

    const files = Array.isArray(data.files) ? data.files : [];
    const hasFiles = files.length > 0;

    updateCountBadge(refs.filesCountBadge, files.length, 'file', hasFiles);
    toggleVisibility(refs.filesTable, hasFiles);
    toggleVisibility(refs.filesEmpty, !hasFiles);

    refs.filesBody.innerHTML = '';
    if (!hasFiles) {
        return;
    }

    files.forEach((file) => {
        refs.filesBody.appendChild(buildFileRow(file));
    });
}

function renderParsedSection(data, refs) {
    if (!refs || !refs.parsedBody) {
        return;
    }

    const enabled = Boolean(data.parsedReceiptsEnabled);
    const receipts = Array.isArray(data.parsedReceipts) ? data.parsedReceipts : [];
    const hasReceipts = enabled && receipts.length > 0;

    updateCountBadge(refs.parsedCountBadge, receipts.length, 'receipt', hasReceipts);
    toggleVisibility(refs.parsedTable, hasReceipts);
    toggleVisibility(refs.parsedEmpty, enabled && !hasReceipts);

    refs.parsedBody.innerHTML = '';
    if (!hasReceipts) {
        return;
    }

    receipts.forEach((receipt) => {
        refs.parsedBody.appendChild(buildParsedRow(receipt));
    });
}

function updateCountBadge(element, count, noun, visible) {
    if (!element) {
        return;
    }

    if (!visible) {
        element.classList.add('d-none');
        return;
    }

    const safeCount = Number.isFinite(count) ? count : 0;
    const label = `${safeCount} ${noun}${safeCount === 1 ? '' : 's'}`;
    element.textContent = label;
    element.classList.remove('d-none');
}

function toggleVisibility(element, shouldShow) {
    if (!element) {
        return;
    }
    element.classList.toggle('d-none', !shouldShow);
}

function buildFileRow(file) {
    const row = document.createElement('tr');
    if (file && file.objectName) {
        row.dataset.objectName = String(file.objectName);
    }

    const nameCell = document.createElement('td');
    const nameSpan = document.createElement('span');
    nameSpan.className = 'fw-semibold';
    nameSpan.textContent = file && file.name ? file.name : '—';
    nameCell.appendChild(nameSpan);
    row.appendChild(nameCell);

    const sizeCell = document.createElement('td');
    sizeCell.className = 'text-end';
    sizeCell.textContent = file && file.formattedSize ? file.formattedSize : '—';
    row.appendChild(sizeCell);

    const ownerCell = document.createElement('td');
    ownerCell.textContent = file && file.ownerDisplayName ? file.ownerDisplayName : '—';
    row.appendChild(ownerCell);

    const updatedCell = document.createElement('td');
    updatedCell.textContent = file && file.updated ? file.updated : '—';
    row.appendChild(updatedCell);

    const statusCell = document.createElement('td');
    const statusContainer = document.createElement('div');
    statusContainer.className = 'd-flex flex-column gap-1';
    const badge = document.createElement('span');
    const badgeClass = file && file.statusBadgeClass ? file.statusBadgeClass : 'bg-secondary-subtle text-secondary';
    badge.className = `badge text-uppercase fw-semibold file-status-badge ${badgeClass}`.trim();
    const statusText = file && file.status && String(file.status).trim() ? String(file.status).trim() : 'PENDING';
    badge.textContent = statusText;
    statusContainer.appendChild(badge);

    const message = document.createElement('span');
    message.className = 'text-muted small file-status-message';
    if (file && file.statusMessage) {
        message.textContent = file.statusMessage;
    } else {
        message.classList.add('d-none');
    }
    statusContainer.appendChild(message);
    statusCell.appendChild(statusContainer);
    row.appendChild(statusCell);

    const typeCell = document.createElement('td');
    typeCell.textContent = file && file.contentType ? file.contentType : '—';
    row.appendChild(typeCell);

    return row;
}

function buildParsedRow(receipt) {
    const row = document.createElement('tr');
    if (receipt && receipt.id) {
        row.dataset.receiptId = String(receipt.id);
    }

    const receiptCell = document.createElement('td');
    const link = document.createElement('a');
    link.className = 'fw-semibold text-decoration-none';
    const href = receipt && receipt.detailsUrl ? receipt.detailsUrl : (receipt && receipt.id ? `/receipts/${encodeURIComponent(receipt.id)}` : '#');
    link.href = href;
    const displayName = receipt && receipt.displayName ? receipt.displayName : (receipt && receipt.objectPath ? receipt.objectPath : 'Receipt');
    link.textContent = displayName;
    receiptCell.appendChild(link);

    if (receipt && receipt.storeName) {
        const store = document.createElement('div');
        store.className = 'text-muted small';
        store.textContent = receipt.storeName;
        receiptCell.appendChild(store);
    }

    row.appendChild(receiptCell);

    const dateCell = document.createElement('td');
    dateCell.textContent = receipt && receipt.receiptDate ? receipt.receiptDate : '—';
    row.appendChild(dateCell);

    const totalCell = document.createElement('td');
    const totalText = receipt && receipt.formattedTotalAmount
        ? receipt.formattedTotalAmount
        : (receipt && receipt.totalAmount ? receipt.totalAmount : '—');
    totalCell.textContent = totalText;
    row.appendChild(totalCell);

    const updatedCell = document.createElement('td');
    updatedCell.className = 'text-end';
    updatedCell.textContent = receipt && receipt.updatedAt ? receipt.updatedAt : '—';
    row.appendChild(updatedCell);

    return row;
}
