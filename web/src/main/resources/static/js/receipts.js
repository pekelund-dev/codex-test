(function () {
    const sortState = { column: 'updated', direction: 'desc' };
    // Track collapsed group state by store name. Defaults to null (not yet interacted/set).
    const groupStates = new Map();
    const poller = setupDashboardPolling(sortState, groupStates);
    setupClearReceiptsControl(poller);
    setupTableSorting(sortState, groupStates);
})();

function setupTableSorting(sortState, groupStates) {
    const table = document.querySelector('[data-parsed-table] table');
    if (!table) return;

    const headers = table.querySelectorAll('th.sortable');

    headers.forEach(header => {
        header.addEventListener('click', () => {
            const column = header.dataset.sort;
            if (sortState.column === column) {
                sortState.direction = sortState.direction === 'asc' ? 'desc' : 'asc';
            } else {
                sortState.column = column;
                sortState.direction = 'asc'; // Default to ascending for new columns
                if (column === 'date' || column === 'updated' || column === 'amount') {
                    sortState.direction = 'desc'; // Default to descending for dates and amounts
                }
            }
            sortAndRenderTable(table, sortState, groupStates);
            updateSortIcons(headers, sortState);
        });
    });
    
    // Initial sort icon update
    updateSortIcons(headers, sortState);
}

function sortAndRenderTable(table, sort, groupStates) {
    const tbody = table.querySelector('tbody');
    const rows = Array.from(tbody.querySelectorAll('tr[data-receipt-id]'));

    renderRows(tbody, rows, sort, groupStates);
}

function renderRows(tbody, rows, sort, groupStates) {
    rows.sort((a, b) => {
        let valA = getCellValue(a, sort.column);
        let valB = getCellValue(b, sort.column);
        return compareValues(valA, valB, sort.column, sort.direction);
    });

    tbody.innerHTML = '';

    if (sort.column === 'name') {
        renderGroupedByStore(tbody, rows, groupStates);
    } else {
        rows.forEach(row => {
            row.classList.remove('d-none');
            // Remove any indentation style if previously grouped
            const firstCell = row.querySelector('td:first-child');
            if (firstCell) firstCell.style.paddingLeft = '';
            tbody.appendChild(row);
        });
    }
}

function renderGroupedByStore(tbody, rows, groupStates) {
    const groups = new Map();
    rows.forEach(row => {
        const storeName = getCellValue(row, 'name');
        if (!groups.has(storeName)) {
            groups.set(storeName, []);
        }
        groups.get(storeName).push(row);
    });

    groups.forEach((groupRows, storeName) => {
        const count = groupRows.length;
        // Check persisted state, otherwise default to collapsed if >= 2 items
        let isCollapsed;
        if (groupStates && groupStates.has(storeName)) {
            isCollapsed = groupStates.get(storeName);
        } else {
            isCollapsed = count >= 2;
        }
        
        const headerRow = document.createElement('tr');
        headerRow.className = 'table-light group-header';
        headerRow.style.cursor = 'pointer';
        headerRow.setAttribute('data-collapsed', isCollapsed);
        
        const cell = document.createElement('td');
        // Determine colspan based on the number of columns in the first row, or default to 5
        const colCount = groupRows[0] ? groupRows[0].children.length : 5;
        cell.colSpan = colCount;
        
        const iconClass = isCollapsed ? 'bi-chevron-right' : 'bi-chevron-down';
        // Use a safe display name
        const displayStore = storeName || 'Övrigt';

        cell.innerHTML = `
            <div class="d-flex align-items-center gap-2">
                <i class="bi ${iconClass} text-muted"></i>
                <span class="fw-semibold">${displayStore}</span>
                <span class="badge bg-secondary-subtle text-secondary rounded-pill border">${count}</span>
            </div>
        `;
        headerRow.appendChild(cell);
        
        headerRow.addEventListener('click', () => {
            const currentlyCollapsed = headerRow.getAttribute('data-collapsed') === 'true';
            const newCollapsed = !currentlyCollapsed;
            headerRow.setAttribute('data-collapsed', newCollapsed);
            
            // Persist state
            if (groupStates) {
                groupStates.set(storeName, newCollapsed);
            }
            
            const icon = headerRow.querySelector('i');
            if (icon) {
                 icon.className = `bi ${newCollapsed ? 'bi-chevron-right' : 'bi-chevron-down'} text-muted`;
            }
            
            groupRows.forEach(r => {
                if (newCollapsed) r.classList.add('d-none');
                else r.classList.remove('d-none');
            });
        });
        
        tbody.appendChild(headerRow);

        groupRows.forEach(row => {
            // Add indentation to the first cell to indicate hierarchy
            const firstCell = row.querySelector('td:first-child');
            if (firstCell) {
                firstCell.style.paddingLeft = '2.5rem';
            }

            if (isCollapsed) row.classList.add('d-none');
            else row.classList.remove('d-none');
            tbody.appendChild(row);
        });
    });
}


function compareValues(valA, valB, column, direction) {
    if (column === 'amount') {
        valA = parseAmount(valA);
        valB = parseAmount(valB);
        return direction === 'asc' ? valA - valB : valB - valA;
    } else if (column === 'date' || column === 'updated') {
         // Handle empty dates (sort them last)
         if (!valA || valA === '—') return 1;
         if (!valB || valB === '—') return -1;
         return direction === 'asc' ? valA.localeCompare(valB) : valB.localeCompare(valA);
    } else {
        return direction === 'asc' ? valA.localeCompare(valB) : valB.localeCompare(valA);
    }
}

function getCellValue(row, column) {
    const cells = row.querySelectorAll('td');
    switch (column) {
        case 'name': return cells[0].textContent.trim();
        case 'date': return cells[1].textContent.trim();
        case 'status': return cells[2].textContent.trim();
        case 'amount': return cells[3].textContent.trim();
        case 'updated': return cells[4].textContent.trim();
        default: return '';
    }
}

function parseAmount(amountStr) {
    if (!amountStr || amountStr === '—') return -Infinity;
    // Remove currency symbols and non-breaking spaces, replace comma with dot
    const clean = amountStr.replace(/[^\d.,-]/g, '').replace(',', '.');
    return parseFloat(clean) || 0;
}

function updateSortIcons(headers, currentSort) {
    headers.forEach(header => {
        const icon = header.querySelector('i');
        if (!icon) return;

        icon.className = 'bi ms-1 small';
        if (header.dataset.sort === currentSort.column) {
            icon.classList.add(currentSort.direction === 'asc' ? 'bi-arrow-up' : 'bi-arrow-down');
            icon.classList.add('text-primary');
            icon.classList.remove('text-muted');
        } else {
            icon.classList.add('bi-arrow-down-up');
            icon.classList.add('text-muted');
            icon.classList.remove('text-primary');
        }
    });
}

function setupDashboardPolling(sortState, groupStates) {
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
            renderParsedSection(data, { parsedCountBadge, parsedEmpty, parsedTable, parsedBody, parsedError }, sortState, groupStates);
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

function renderParsedSection(data, refs, sortState, groupStates) {
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

    // If no receipts, clear body and return
    if (!hasReceipts) {
        refs.parsedBody.innerHTML = '';
        return;
    }

    // Build DOM rows
    const rows = receipts.map(buildParsedRow);

    // Use shared rendering logic
    renderRows(refs.parsedBody, rows, sortState, groupStates);
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
    const storeName = receipt && receipt.storeName ? receipt.storeName : null;
    const displayName = storeName
        ? storeName
        : (receipt && receipt.displayName
            ? receipt.displayName
            : (receipt && receipt.objectPath ? receipt.objectPath : 'Receipt'));
    link.textContent = displayName;
    receiptCell.appendChild(link);

    row.appendChild(receiptCell);

    const dateCell = document.createElement('td');
    dateCell.textContent = receipt && receipt.receiptDate ? receipt.receiptDate : '—';
    row.appendChild(dateCell);

    const statusCell = document.createElement('td');
    const reconciliationStatus = receipt && receipt.reconciliationStatus ? receipt.reconciliationStatus : null;
    if (reconciliationStatus === 'COMPLETE') {
        statusCell.appendChild(createStatusBadge('Avstämd', 'bg-success-subtle text-success'));
    } else if (reconciliationStatus === 'PARTIAL') {
        statusCell.appendChild(createStatusBadge('Delavstämd', 'bg-warning-subtle text-warning-emphasis'));
    } else if (reconciliationStatus === 'FAIL') {
        statusCell.appendChild(createStatusBadge('Fel', 'bg-danger-subtle text-danger'));
    } else {
        const emptyLabel = document.createElement('span');
        emptyLabel.className = 'text-muted small';
        emptyLabel.textContent = '—';
        statusCell.appendChild(emptyLabel);
    }
    row.appendChild(statusCell);

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

function createStatusBadge(label, className) {
    const badge = document.createElement('span');
    badge.className = `badge ${className}`;
    badge.textContent = label;
    return badge;
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
