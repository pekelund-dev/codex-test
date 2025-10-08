(function () {
    const poller = setupDashboardPolling();
    setupClearReceiptsControl(poller);
})();

function setupDashboardPolling() {
    const container = document.querySelector('[data-dashboard-url]');
    if (!container) {
        return null;
    }

    const endpoint = container.getAttribute('data-dashboard-url');
    if (!endpoint) {
        return null;
    }

    const parsedCountBadge = container.querySelector('[data-parsed-count]');
    const parsedEmpty = container.querySelector('[data-parsed-empty]');
    const parsedTable = container.querySelector('[data-parsed-table]');
    const parsedBody = container.querySelector('[data-parsed-body]');
    const parsedError = container.querySelector('[data-parsed-error]');

    const POLL_INTERVAL = 2000;
    let pollTimeoutId = null;
    let isFetching = false;

    async function fetchAndRender() {
        if (pollTimeoutId !== null) {
            window.clearTimeout(pollTimeoutId);
            pollTimeoutId = null;
        }

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
            renderParsedSection(data, { parsedCountBadge, parsedEmpty, parsedTable, parsedBody, parsedError });
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

    return {
        refreshNow: fetchAndRender,
    };
}

function renderParsedSection(data, refs) {
    if (!refs || !refs.parsedBody) {
        return;
    }

    const enabled = Boolean(data.parsedReceiptsEnabled);
    const receipts = Array.isArray(data.parsedReceipts) ? data.parsedReceipts : [];
    const hasReceipts = enabled && receipts.length > 0;
    const listingError = data.parsedListingError ? String(data.parsedListingError).trim() : '';

    updateCountBadge(refs.parsedCountBadge, receipts.length, 'receipt', hasReceipts);
    toggleVisibility(refs.parsedTable, hasReceipts);
    toggleVisibility(refs.parsedEmpty, enabled && !hasReceipts);
    updateErrorMessage(refs.parsedError, listingError);

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

function setupClearReceiptsControl(poller) {
    const form = document.querySelector('form[action$="/receipts/clear"]');
    if (!form) {
        return;
    }

    const submitButton = form.querySelector('button[type="submit"]');
    const feedbackContainer = form.parentElement
        ? form.parentElement.querySelector('[data-clear-feedback]')
        : null;

    function renderFeedback(message, type) {
        if (!feedbackContainer) {
            return;
        }

        if (!message) {
            feedbackContainer.innerHTML = '';
            feedbackContainer.classList.add('d-none');
            return;
        }

        const alert = document.createElement('div');
        alert.className = `alert alert-${type} mb-0`;
        alert.setAttribute('role', 'alert');
        alert.textContent = message;
        feedbackContainer.innerHTML = '';
        feedbackContainer.appendChild(alert);
        feedbackContainer.classList.remove('d-none');
    }

    form.addEventListener('submit', (event) => {
        event.preventDefault();

        if (submitButton) {
            submitButton.disabled = true;
        }
        renderFeedback(null, 'info');

        const formData = new FormData(form);

        fetch(form.action, {
            method: form.method || 'POST',
            body: formData,
            headers: { Accept: 'application/json' },
            credentials: 'same-origin',
        }).then(async (response) => {
            let payload = null;
            try {
                payload = await response.json();
            } catch (error) {
                // Ignore parsing errors and fall back to a generic message.
            }

            if (!response.ok) {
                const message = payload && payload.errorMessage
                    ? payload.errorMessage
                    : 'Failed to clear receipt data. Please try again.';
                throw new Error(message);
            }

            const successMessage = payload && payload.successMessage
                ? payload.successMessage
                : 'Receipt data cleared.';
            renderFeedback(successMessage, 'success');

            if (poller && typeof poller.refreshNow === 'function') {
                poller.refreshNow();
            }
        }).catch((error) => {
            const message = error && error.message
                ? error.message
                : 'Failed to clear receipt data. Please try again.';
            renderFeedback(message, 'danger');
        }).finally(() => {
            if (submitButton) {
                submitButton.disabled = false;
            }
        });
    });
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

function updateErrorMessage(element, message) {
    if (!element) {
        return;
    }

    if (!message) {
        element.classList.add('d-none');
        element.textContent = '';
        return;
    }

    element.textContent = message;
    element.classList.remove('d-none');
}
