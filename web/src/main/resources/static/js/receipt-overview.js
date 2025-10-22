(function () {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initReceiptOverview);
    } else {
        initReceiptOverview();
    }
})();

function initReceiptOverview() {
    const root = document.querySelector('[data-overview-root]');
    if (!root) {
        return;
    }

    const controller = new ReceiptOverviewController(root);
    controller.init();
}

class ReceiptOverviewController {
    constructor(root) {
        this.root = root;
        this.overviewUrl = root.getAttribute('data-overview-url');
        this.scope = root.getAttribute('data-scope') || '';

        this.periodTypeSelect = root.querySelector('[data-overview-period-type]');
        this.primaryWeekInput = root.querySelector('[data-overview-primary-week]');
        this.primaryMonthInput = root.querySelector('[data-overview-primary-month]');
        this.compareToggle = root.querySelector('[data-overview-compare-toggle]');
        this.compareWeekInput = root.querySelector('[data-overview-compare-week]');
        this.compareMonthInput = root.querySelector('[data-overview-compare-month]');

        this.weekPicker = root.querySelector('[data-week-picker]');
        this.monthPicker = root.querySelector('[data-month-picker]');
        this.compareWeekPicker = root.querySelector('[data-compare-week]');
        this.compareMonthPicker = root.querySelector('[data-compare-month]');

        this.weekIndicators = root.querySelector('[data-week-indicators]');
        this.monthIndicators = root.querySelector('[data-month-indicators]');

        this.warningAlert = root.querySelector('[data-overview-warning]');
        this.errorAlert = root.querySelector('[data-overview-error]');
        this.loadingIndicator = root.querySelector('[data-overview-loading]');

        this.panels = {};
        root.querySelectorAll('[data-period-panel]').forEach((panelElement) => {
            const key = panelElement.getAttribute('data-period-panel');
            if (!key) {
                return;
            }
            this.panels[key] = new PeriodPanel(panelElement);
        });

        this.pendingRequest = null;
        this.receiptDateSet = new Set();
        this.weekIndicatorItems = [];
        this.monthIndicatorItems = [];
    }

    init() {
        if (this.periodTypeSelect) {
            const defaultType = this.root.getAttribute('data-default-period-type');
            if (defaultType) {
                this.periodTypeSelect.value = defaultType;
            }
        }
        if (this.primaryWeekInput) {
            const defaultWeek = this.root.getAttribute('data-default-primary-week');
            if (defaultWeek) {
                this.primaryWeekInput.value = defaultWeek;
            }
        }
        if (this.primaryMonthInput) {
            const defaultMonth = this.root.getAttribute('data-default-primary-month');
            if (defaultMonth) {
                this.primaryMonthInput.value = defaultMonth;
            }
        }
        if (this.compareWeekInput) {
            const defaultCompareWeek = this.root.getAttribute('data-default-compare-week');
            if (defaultCompareWeek) {
                this.compareWeekInput.value = defaultCompareWeek;
            }
        }
        if (this.compareMonthInput) {
            const defaultCompareMonth = this.root.getAttribute('data-default-compare-month');
            if (defaultCompareMonth) {
                this.compareMonthInput.value = defaultCompareMonth;
            }
        }
        if (this.compareToggle) {
            const hasDefaultCompareWeek = Boolean(this.compareWeekInput && this.compareWeekInput.value);
            const hasDefaultCompareMonth = Boolean(this.compareMonthInput && this.compareMonthInput.value);
            this.compareToggle.checked = hasDefaultCompareWeek || hasDefaultCompareMonth;
        }

        this.buildIndicatorElements();
        this.updatePickerVisibility();
        this.updateIndicators();
        this.registerEventListeners();
        this.refreshData();
    }

    registerEventListeners() {
        if (this.periodTypeSelect) {
            this.periodTypeSelect.addEventListener('change', () => {
                this.updatePickerVisibility();
                this.updateIndicators();
                this.refreshData();
            });
        }
        if (this.primaryWeekInput) {
            this.primaryWeekInput.addEventListener('change', () => this.refreshData());
        }
        if (this.primaryMonthInput) {
            this.primaryMonthInput.addEventListener('change', () => this.refreshData());
        }
        if (this.compareToggle) {
            this.compareToggle.addEventListener('change', () => {
                this.updatePickerVisibility();
                this.refreshData();
            });
        }
        if (this.compareWeekInput) {
            this.compareWeekInput.addEventListener('change', () => this.refreshData());
        }
        if (this.compareMonthInput) {
            this.compareMonthInput.addEventListener('change', () => this.refreshData());
        }
    }

    updatePickerVisibility() {
        const periodType = this.getPeriodType();
        const showingWeek = periodType === 'week';

        if (this.weekPicker) {
            this.weekPicker.classList.toggle('d-none', !showingWeek);
        }
        if (this.monthPicker) {
            this.monthPicker.classList.toggle('d-none', showingWeek);
        }
        if (this.primaryWeekInput) {
            this.primaryWeekInput.disabled = !showingWeek;
        }
        if (this.primaryMonthInput) {
            this.primaryMonthInput.disabled = showingWeek;
        }

        const showCompare = this.compareToggle && this.compareToggle.checked;
        const showCompareWeek = showCompare && showingWeek;
        const showCompareMonth = showCompare && !showingWeek;

        if (this.compareWeekPicker) {
            this.compareWeekPicker.classList.toggle('d-none', !showCompareWeek);
        }
        if (this.compareMonthPicker) {
            this.compareMonthPicker.classList.toggle('d-none', !showCompareMonth);
        }
        if (this.compareWeekInput) {
            this.compareWeekInput.disabled = !showCompareWeek;
        }
        if (this.compareMonthInput) {
            this.compareMonthInput.disabled = !showCompareMonth;
        }
    }

    getPeriodType() {
        if (!this.periodTypeSelect) {
            return 'week';
        }
        const value = this.periodTypeSelect.value ? this.periodTypeSelect.value.toLowerCase() : '';
        return value === 'month' ? 'month' : 'week';
    }

    refreshData() {
        if (!this.overviewUrl) {
            return;
        }

        const periodType = this.getPeriodType();
        const primaryValue = periodType === 'week'
            ? (this.primaryWeekInput ? this.primaryWeekInput.value : '')
            : (this.primaryMonthInput ? this.primaryMonthInput.value : '');

        if (!primaryValue) {
            const message = 'Kontrollera vald period och försök igen.';
            this.setReceiptDates(null);
            this.updateSelectionIndicators(periodType, null);
            this.showError(message);
            this.setLoadingState(false);
            this.updatePanels(null, null, message);
            return;
        }

        this.updateSelectionIndicators(periodType, primaryValue);

        let compareValue = '';
        if (this.compareToggle && this.compareToggle.checked) {
            compareValue = periodType === 'week'
                ? (this.compareWeekInput ? this.compareWeekInput.value : '')
                : (this.compareMonthInput ? this.compareMonthInput.value : '');
        }

        const url = this.buildOverviewUrl({
            periodType,
            primary: primaryValue,
            compare: compareValue,
        });

        this.clearError();
        this.setLoadingState(true);
        this.updatePanelsLoading(true);

        if (this.pendingRequest) {
            this.pendingRequest.abort();
            this.pendingRequest = null;
        }

        const controller = new AbortController();
        this.pendingRequest = controller;

        fetch(url, {
            headers: { Accept: 'application/json' },
            credentials: 'same-origin',
            signal: controller.signal,
        })
            .then(async (response) => {
                let payload = null;
                try {
                    payload = await response.json();
                } catch (error) {
                    // Ignore JSON parsing errors and fall back to generic handling.
                }
                if (!response.ok) {
                    const message = payload && payload.errorMessage
                        ? payload.errorMessage
                        : 'Det gick inte att läsa in översikten just nu.';
                    throw new Error(message);
                }
                return payload;
            })
            .then((data) => {
                this.pendingRequest = null;
                this.handleData(data);
            })
            .catch((error) => {
                if (controller.signal.aborted) {
                    return;
                }
                this.pendingRequest = null;
                const message = error && error.message
                    ? error.message
                    : 'Det gick inte att läsa in översikten just nu.';
                this.showError(message);
                this.setReceiptDates(null);
                this.updateIndicators();
                this.updatePanels(null, null, message);
            })
            .finally(() => {
                if (this.pendingRequest === controller) {
                    this.pendingRequest = null;
                }
                this.setLoadingState(false);
                this.updatePanelsLoading(false);
            });
    }

    handleData(data) {
        if (!data) {
            this.setReceiptDates(null);
            this.updatePanels(null, null, 'Det gick inte att läsa in översikten just nu.');
            this.updateIndicators();
            return;
        }

        if (!data.parsedReceiptsEnabled) {
            this.setReceiptDates(null);
            this.showWarning();
            this.updatePanels(null, null, data.errorMessage || null);
            this.updateIndicators();
            return;
        }

        this.hideWarning();
        this.clearError();

        this.setReceiptDates(data.receiptDates || null);
        const primary = data.primary || null;
        const comparison = data.comparison || null;

        this.updatePanels(primary, comparison, data.errorMessage || null);
        this.updateIndicators();
    }

    updatePanels(primary, comparison, errorMessage) {
        const primaryPanel = this.panels.primary;
        if (primaryPanel) {
            primaryPanel.setError(null);
            primaryPanel.setData(primary);
            primaryPanel.setVisible(Boolean(primary));
        }

        const comparisonPanel = this.panels.comparison;
        if (comparisonPanel) {
            comparisonPanel.setError(null);
            comparisonPanel.setData(comparison);
            comparisonPanel.setVisible(Boolean(comparison));
        }

        if (errorMessage) {
            if (primaryPanel) {
                primaryPanel.setError(errorMessage);
            }
            if (comparisonPanel && comparison) {
                comparisonPanel.setError(errorMessage);
            }
        }
    }

    updatePanelsLoading(isLoading) {
        Object.values(this.panels).forEach((panel) => panel.setLoading(isLoading));
    }

    setLoadingState(isLoading) {
        if (this.loadingIndicator) {
            this.loadingIndicator.classList.toggle('d-none', !isLoading);
        }
    }

    showWarning() {
        if (this.warningAlert) {
            this.warningAlert.classList.remove('d-none');
        }
    }

    hideWarning() {
        if (this.warningAlert) {
            this.warningAlert.classList.add('d-none');
        }
    }

    showError(message) {
        if (this.errorAlert) {
            this.errorAlert.textContent = message || 'Det gick inte att läsa in översikten just nu.';
            this.errorAlert.classList.remove('d-none');
        }
    }

    clearError() {
        if (this.errorAlert) {
            this.errorAlert.classList.add('d-none');
            this.errorAlert.textContent = '';
        }
    }

    buildIndicatorElements() {
        this.buildWeekIndicatorElements();
        this.buildMonthIndicatorElements();
    }

    buildWeekIndicatorElements() {
        if (!this.weekIndicators || this.weekIndicatorItems.length > 0) {
            return;
        }

        const days = [
            { index: 1, short: 'Må', full: 'Måndag' },
            { index: 2, short: 'Ti', full: 'Tisdag' },
            { index: 3, short: 'On', full: 'Onsdag' },
            { index: 4, short: 'To', full: 'Torsdag' },
            { index: 5, short: 'Fr', full: 'Fredag' },
            { index: 6, short: 'Lö', full: 'Lördag' },
            { index: 7, short: 'Sö', full: 'Söndag' },
        ];

        const fragment = document.createDocumentFragment();
        days.forEach((day) => {
            const item = document.createElement('div');
            item.className = 'overview-week-indicator';
            item.setAttribute('data-day-index', String(day.index));
            item.setAttribute('data-day-name', day.full);

            const label = document.createElement('span');
            label.className = 'overview-week-indicator-label';
            label.textContent = day.short;

            const dot = document.createElement('span');
            dot.className = 'overview-week-indicator-dot';

            item.append(label, dot);
            fragment.appendChild(item);
            this.weekIndicatorItems.push(item);
        });

        this.weekIndicators.appendChild(fragment);
    }

    buildMonthIndicatorElements() {
        if (!this.monthIndicators || this.monthIndicatorItems.length > 0) {
            return;
        }

        const fragment = document.createDocumentFragment();
        for (let day = 1; day <= 31; day += 1) {
            const item = document.createElement('div');
            item.className = 'overview-month-indicator outside-range';
            item.setAttribute('data-day-number', String(day));

            const label = document.createElement('span');
            label.className = 'overview-month-indicator-label';
            label.textContent = String(day);

            const dot = document.createElement('span');
            dot.className = 'overview-month-indicator-dot';

            item.append(label, dot);
            fragment.appendChild(item);
            this.monthIndicatorItems.push(item);
        }

        this.monthIndicators.appendChild(fragment);
    }

    setReceiptDates(values) {
        if (!Array.isArray(values) || values.length === 0) {
            this.receiptDateSet = new Set();
            return;
        }
        const filtered = values
            .map((value) => (typeof value === 'string' ? value.trim() : ''))
            .filter((value) => value.length > 0);
        this.receiptDateSet = new Set(filtered);
    }

    updateIndicators() {
        const periodType = this.getPeriodType();
        const primaryValue = periodType === 'week'
            ? (this.primaryWeekInput ? this.primaryWeekInput.value : '')
            : (this.primaryMonthInput ? this.primaryMonthInput.value : '');
        this.updateSelectionIndicators(periodType, primaryValue);
    }

    updateSelectionIndicators(periodType, primaryValue) {
        if (periodType === 'month') {
            this.updateWeekIndicators(null);
            this.updateMonthIndicators(primaryValue);
        } else {
            this.updateMonthIndicators(null);
            this.updateWeekIndicators(primaryValue);
        }
    }

    updateWeekIndicators(weekValue) {
        if (!this.weekIndicators) {
            return;
        }
        this.buildWeekIndicatorElements();
        const container = this.weekIndicators;
        const items = this.weekIndicatorItems;
        if (!items || items.length === 0) {
            container.classList.add('d-none');
            return;
        }

        items.forEach((item) => {
            item.classList.remove('has-receipt');
            item.removeAttribute('data-date');
            item.removeAttribute('aria-label');
            item.removeAttribute('title');
        });

        if (!weekValue) {
            container.classList.add('d-none');
            return;
        }

        const isoDates = this.getIsoDatesForWeek(weekValue);
        if (!isoDates || isoDates.length !== items.length) {
            container.classList.add('d-none');
            return;
        }

        container.classList.remove('d-none');
        isoDates.forEach((iso, index) => {
            const item = items[index];
            if (!item) {
                return;
            }
            const hasReceipt = this.receiptDateSet.has(iso);
            item.classList.toggle('has-receipt', hasReceipt);
            item.setAttribute('data-date', iso);
            const dayName = item.getAttribute('data-day-name') || '';
            const message = `${dayName} (${iso}) ${hasReceipt ? 'har kvitto' : 'inget kvitto'}`;
            item.setAttribute('aria-label', message);
            item.setAttribute('title', message);
        });
    }

    updateMonthIndicators(monthValue) {
        if (!this.monthIndicators) {
            return;
        }
        this.buildMonthIndicatorElements();
        const container = this.monthIndicators;
        const items = this.monthIndicatorItems;
        if (!items || items.length === 0) {
            container.classList.add('d-none');
            return;
        }

        items.forEach((item) => {
            item.classList.remove('has-receipt');
            item.classList.add('outside-range');
            item.removeAttribute('data-date');
            item.removeAttribute('aria-label');
            item.removeAttribute('title');
        });

        if (!monthValue) {
            container.classList.add('d-none');
            return;
        }

        const match = /^\d{4}-\d{2}$/.exec(monthValue);
        if (!match) {
            container.classList.add('d-none');
            return;
        }

        const year = Number(monthValue.slice(0, 4));
        const month = Number(monthValue.slice(5));
        if (!Number.isFinite(year) || !Number.isFinite(month)) {
            container.classList.add('d-none');
            return;
        }

        const daysInMonth = this.getDaysInMonth(year, month);
        if (!daysInMonth) {
            container.classList.add('d-none');
            return;
        }

        container.classList.remove('d-none');
        for (let day = 1; day <= items.length; day += 1) {
            const item = items[day - 1];
            if (!item) {
                continue;
            }
            if (day > daysInMonth) {
                item.classList.add('outside-range');
                continue;
            }
            const iso = `${year}-${this.padNumber(month)}-${this.padNumber(day)}`;
            const hasReceipt = this.receiptDateSet.has(iso);
            item.classList.remove('outside-range');
            item.classList.toggle('has-receipt', hasReceipt);
            item.setAttribute('data-date', iso);
            const message = `Dag ${day} (${iso}) ${hasReceipt ? 'har kvitto' : 'inget kvitto'}`;
            item.setAttribute('aria-label', message);
            item.setAttribute('title', message);
        }
    }

    getIsoDatesForWeek(weekValue) {
        if (typeof weekValue !== 'string' || weekValue.length === 0) {
            return null;
        }
        const match = /^(\d{4})-W(\d{2})$/.exec(weekValue);
        if (!match) {
            return null;
        }
        const year = Number(match[1]);
        const week = Number(match[2]);
        if (!Number.isFinite(year) || !Number.isFinite(week)) {
            return null;
        }

        const base = new Date(Date.UTC(year, 0, 4));
        const dayOfWeek = base.getUTCDay() || 7;
        base.setUTCDate(base.getUTCDate() - dayOfWeek + 1 + (week - 1) * 7);

        const dates = [];
        for (let offset = 0; offset < 7; offset += 1) {
            const current = new Date(base);
            current.setUTCDate(base.getUTCDate() + offset);
            dates.push(current.toISOString().slice(0, 10));
        }
        return dates;
    }

    getDaysInMonth(year, month) {
        if (!Number.isFinite(year) || !Number.isFinite(month) || month < 1 || month > 12) {
            return null;
        }
        const date = new Date(Date.UTC(year, month, 0));
        return date.getUTCDate();
    }

    padNumber(value) {
        return String(value).padStart(2, '0');
    }

    buildOverviewUrl(config) {
        const base = this.overviewUrl || '';
        const url = new URL(base, window.location.origin);
        if (config && config.periodType) {
            url.searchParams.set('periodType', config.periodType);
        } else {
            url.searchParams.delete('periodType');
        }
        if (config && config.primary) {
            url.searchParams.set('primary', config.primary);
        } else {
            url.searchParams.delete('primary');
        }
        if (config && config.compare && config.compare.trim().length > 0) {
            url.searchParams.set('compare', config.compare);
        } else {
            url.searchParams.delete('compare');
        }
        if (this.scope) {
            url.searchParams.set('scope', this.scope);
        }
        return url.toString();
    }
}

class PeriodPanel {
    constructor(element) {
        this.element = element;
        this.titleElement = element.querySelector('[data-period-title]');
        this.rangeElement = element.querySelector('[data-period-range]');
        this.countBadge = element.querySelector('[data-period-count]');
        this.groupToggle = element.querySelector('[data-period-group-toggle]');
        this.emptyAlert = element.querySelector('[data-period-empty]');
        this.errorAlert = element.querySelector('[data-period-panel-error]');
        this.loadingIndicator = element.querySelector('[data-period-loading]');
        this.tableContainer = element.querySelector('[data-period-table-container]');
        this.tableBody = element.querySelector('[data-period-table-body]');
        this.sortButtons = Array.from(element.querySelectorAll('button[data-sort-key]'));

        this.currentData = null;
        this.sortKey = 'date';
        this.sortDirection = 'desc';
        this.grouped = false;
        this.expandedGroups = new Set();

        this.attachEventListeners();
    }

    attachEventListeners() {
        if (this.groupToggle) {
            this.groupToggle.addEventListener('change', () => {
                this.grouped = Boolean(this.groupToggle.checked);
                this.expandedGroups.clear();
                this.render();
            });
        }
        this.sortButtons.forEach((button) => {
            button.addEventListener('click', () => {
                const key = button.getAttribute('data-sort-key');
                if (!key) {
                    return;
                }
                if (this.sortKey === key) {
                    this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
                } else {
                    this.sortKey = key;
                    this.sortDirection = key === 'date' || key === 'price' ? 'desc' : 'asc';
                }
                this.render();
            });
        });
        if (this.tableBody) {
            this.tableBody.addEventListener('click', (event) => {
                const toggle = event.target.closest('button[data-group-toggle]');
                if (!toggle) {
                    return;
                }
                const groupId = toggle.getAttribute('data-group-toggle');
                if (!groupId) {
                    return;
                }
                if (this.expandedGroups.has(groupId)) {
                    this.expandedGroups.delete(groupId);
                } else {
                    this.expandedGroups.add(groupId);
                }
                this.render();
            });
        }
    }

    setData(data) {
        this.currentData = data;
        this.expandedGroups.clear();
        if (this.groupToggle) {
            this.groupToggle.checked = this.grouped;
        }
        this.render();
    }

    setVisible(isVisible) {
        this.element.classList.toggle('d-none', !isVisible);
    }

    setLoading(isLoading) {
        if (this.loadingIndicator) {
            this.loadingIndicator.classList.toggle('d-none', !isLoading);
        }
    }

    setError(message) {
        if (!this.errorAlert) {
            return;
        }
        if (!message) {
            this.errorAlert.classList.add('d-none');
            this.errorAlert.textContent = '';
            return;
        }
        this.errorAlert.textContent = message;
        this.errorAlert.classList.remove('d-none');
    }

    render() {
        const data = this.currentData;
        if (!data) {
            this.updateHeader(null, null, 0);
            this.renderTable([]);
            return;
        }

        const totalItems = Array.isArray(data.items) ? data.items.length : 0;
        this.updateHeader(data, formatPeriodRange(data), totalItems);
        this.renderTable(Array.isArray(data.items) ? data.items : []);
    }

    updateHeader(data, rangeLabel, itemCount) {
        if (this.titleElement) {
            const label = buildPeriodLabel(data);
            this.titleElement.textContent = label || '—';
        }
        if (this.rangeElement) {
            this.rangeElement.textContent = rangeLabel || '—';
        }
        if (this.countBadge) {
            this.countBadge.textContent = formatItemCount(itemCount);
        }
    }

    renderTable(items) {
        if (!this.tableBody || !this.tableContainer) {
            return;
        }

        const sortedItems = sortItems(items, this.sortKey, this.sortDirection);
        const hasItems = sortedItems.length > 0;

        this.tableBody.innerHTML = '';

        if (!hasItems) {
            this.tableContainer.classList.add('d-none');
            if (this.emptyAlert) {
                this.emptyAlert.classList.remove('d-none');
            }
            return;
        }

        if (this.emptyAlert) {
            this.emptyAlert.classList.add('d-none');
        }
        this.tableContainer.classList.remove('d-none');

        if (this.grouped) {
            this.renderGroupedRows(sortedItems);
        } else {
            sortedItems.forEach((item) => {
                this.tableBody.appendChild(buildItemRow(item));
            });
        }

        this.updateSortIndicators();
    }

    renderGroupedRows(sortedItems) {
        const groupsByEan = new Map();
        const order = [];
        const summaries = new Map();
        const groupSummaries = Array.isArray(this.currentData.groups) ? this.currentData.groups : [];
        groupSummaries.forEach((summary) => {
            if (summary && summary.ean) {
                summaries.set(summary.ean, summary);
            }
        });

        const ungroupedItems = [];
        sortedItems.forEach((item) => {
            const ean = item && item.ean ? item.ean : null;
            if (!ean || !summaries.has(ean)) {
                ungroupedItems.push(item);
                return;
            }
            if (!groupsByEan.has(ean)) {
                groupsByEan.set(ean, []);
                order.push(ean);
            }
            groupsByEan.get(ean).push(item);
        });

        order.forEach((ean) => {
            const summary = summaries.get(ean);
            const groupItems = groupsByEan.get(ean) || [];
            const expanded = this.expandedGroups.has(ean);
            this.tableBody.appendChild(buildGroupSummaryRow(summary, ean, groupItems.length, expanded));
            if (expanded) {
                groupItems.forEach((item) => {
                    const row = buildItemRow(item, true);
                    this.tableBody.appendChild(row);
                });
            }
        });

        ungroupedItems.forEach((item) => {
            this.tableBody.appendChild(buildItemRow(item));
        });
    }

    updateSortIndicators() {
        this.sortButtons.forEach((button) => {
            const key = button.getAttribute('data-sort-key');
            if (!key) {
                return;
            }
            const isActive = key === this.sortKey;
            button.classList.toggle('text-primary', isActive);
            button.setAttribute('aria-pressed', isActive ? 'true' : 'false');
            button.setAttribute('data-sort-direction', isActive ? this.sortDirection : 'none');
        });
    }
}

function buildItemRow(item, isGroupedChild = false) {
    const row = document.createElement('tr');
    if (isGroupedChild) {
        row.classList.add('table-group-item');
    }

    const nameCell = document.createElement('td');
    if (isGroupedChild) {
        nameCell.classList.add('ps-4');
    }
    const nameText = item && item.name ? item.name : '—';
    const nameElement = document.createElement('span');
    nameElement.className = 'fw-semibold';
    nameElement.textContent = nameText;
    nameCell.appendChild(nameElement);

    if (item && item.ean) {
        const eanText = document.createElement('div');
        eanText.className = 'text-muted small';
        eanText.textContent = `EAN: ${item.ean}`;
        nameCell.appendChild(eanText);
    }

    if (item && item.receiptName && item.receiptUrl) {
        const receiptLink = document.createElement('a');
        receiptLink.className = 'text-decoration-none small';
        receiptLink.href = item.receiptUrl;
        receiptLink.textContent = `Kvitto: ${item.receiptName}`;
        nameCell.appendChild(receiptLink);
    }

    row.appendChild(nameCell);

    const priceCell = document.createElement('td');
    priceCell.className = 'text-end';
    priceCell.textContent = formatCurrency(item ? item.priceValue : null);
    row.appendChild(priceCell);

    const quantityCell = document.createElement('td');
    quantityCell.className = 'text-end';
    quantityCell.textContent = item && item.quantityLabel ? item.quantityLabel : '—';
    row.appendChild(quantityCell);

    const storeCell = document.createElement('td');
    storeCell.textContent = item && item.store ? item.store : '—';
    row.appendChild(storeCell);

    const dateCell = document.createElement('td');
    dateCell.textContent = formatDateLabel(item ? item.dateIso : null, item ? item.dateLabel : null);
    row.appendChild(dateCell);

    return row;
}

function buildGroupSummaryRow(summary, ean, itemCount, expanded) {
    const row = document.createElement('tr');
    row.className = 'table-group-summary';

    const nameCell = document.createElement('td');
    const toggleButton = document.createElement('button');
    toggleButton.type = 'button';
    toggleButton.className = 'btn btn-link btn-sm text-decoration-none d-inline-flex align-items-center gap-1';
    toggleButton.setAttribute('data-group-toggle', ean);
    toggleButton.setAttribute('aria-expanded', expanded ? 'true' : 'false');
    toggleButton.innerHTML = `<i class="bi ${expanded ? 'bi-dash-lg' : 'bi-plus-lg'}"></i>`;
    const labelSpan = document.createElement('span');
    const groupName = summary && summary.displayName ? summary.displayName : (ean ? `EAN ${ean}` : 'Grupp');
    labelSpan.textContent = `${groupName}`;
    toggleButton.appendChild(labelSpan);
    nameCell.appendChild(toggleButton);

    if (ean) {
        const eanText = document.createElement('div');
        eanText.className = 'text-muted small';
        eanText.textContent = `EAN: ${ean}`;
        nameCell.appendChild(eanText);
    }

    row.appendChild(nameCell);

    const priceCell = document.createElement('td');
    priceCell.className = 'text-end';
    priceCell.textContent = formatGroupPriceRange(summary);
    row.appendChild(priceCell);

    const quantityCell = document.createElement('td');
    quantityCell.className = 'text-end';
    quantityCell.textContent = formatGroupQuantity(summary, itemCount);
    row.appendChild(quantityCell);

    const storeCell = document.createElement('td');
    const storeCount = summary && Number.isFinite(summary.storeCount) ? summary.storeCount : 0;
    storeCell.textContent = storeCount > 0 ? `${storeCount} ${storeCount === 1 ? 'butik' : 'butiker'}` : '—';
    row.appendChild(storeCell);

    const dateCell = document.createElement('td');
    dateCell.textContent = formatDateRange(summary ? summary.earliestDateIso : null, summary ? summary.latestDateIso : null);
    row.appendChild(dateCell);

    return row;
}

function sortItems(items, sortKey, sortDirection) {
    const sorted = items.slice();
    const direction = sortDirection === 'asc' ? 1 : -1;

    sorted.sort((a, b) => {
        const valueA = getSortValue(a, sortKey);
        const valueB = getSortValue(b, sortKey);
        if (valueA < valueB) {
            return -1 * direction;
        }
        if (valueA > valueB) {
            return 1 * direction;
        }
        return 0;
    });

    return sorted;
}

function getSortValue(item, sortKey) {
    if (!item) {
        return 0;
    }
    switch (sortKey) {
        case 'name':
            return item.name ? item.name.toString().toLowerCase() : '';
        case 'price':
            return Number.isFinite(item.priceValue) ? item.priceValue : -Infinity;
        case 'quantity':
            return Number.isFinite(item.quantityValue) ? item.quantityValue : -Infinity;
        case 'store':
            return item.store ? item.store.toString().toLowerCase() : '';
        case 'date':
        default:
            return Number.isFinite(item.sortTimestamp) ? item.sortTimestamp : 0;
    }
}

const currencyFormatter = new Intl.NumberFormat('sv-SE', { style: 'currency', currency: 'SEK' });
const numberFormatter = new Intl.NumberFormat('sv-SE', { maximumFractionDigits: 2 });
const dateFormatter = new Intl.DateTimeFormat('sv-SE', { dateStyle: 'medium', timeZone: 'UTC' });

function formatCurrency(value) {
    if (!Number.isFinite(value)) {
        return '—';
    }
    return currencyFormatter.format(value);
}

function formatGroupPriceRange(summary) {
    if (!summary) {
        return '—';
    }
    const min = Number.isFinite(summary.minPriceValue) ? summary.minPriceValue : null;
    const max = Number.isFinite(summary.maxPriceValue) ? summary.maxPriceValue : null;
    if (min === null && max === null) {
        return '—';
    }
    if (min !== null && max !== null) {
        if (Math.abs(min - max) < 0.001) {
            return formatCurrency(min);
        }
        return `${formatCurrency(min)} – ${formatCurrency(max)}`;
    }
    if (min !== null) {
        return formatCurrency(min);
    }
    return formatCurrency(max);
}

function formatGroupQuantity(summary, itemCount) {
    if (!summary) {
        return `Totalt ${itemCount} ${itemCount === 1 ? 'köp' : 'köp'}`;
    }
    const quantity = Number.isFinite(summary.totalQuantityValue) ? summary.totalQuantityValue : null;
    if (quantity === null) {
        return `Totalt ${itemCount} ${itemCount === 1 ? 'köp' : 'köp'}`;
    }
    return `Totalt ${numberFormatter.format(quantity)}`;
}

function formatDateLabel(dateIso, fallback) {
    if (dateIso) {
        const date = parseIsoDate(dateIso);
        if (date) {
            return dateFormatter.format(date);
        }
    }
    return fallback || '—';
}

function formatDateRange(startIso, endIso) {
    if (!startIso && !endIso) {
        return '—';
    }
    const startDate = parseIsoDate(startIso);
    const endDate = parseIsoDate(endIso);
    if (startDate && endDate) {
        const startText = dateFormatter.format(startDate);
        const endText = dateFormatter.format(endDate);
        if (startText === endText) {
            return startText;
        }
        return `${startText} – ${endText}`;
    }
    if (startDate) {
        return dateFormatter.format(startDate);
    }
    if (endDate) {
        return dateFormatter.format(endDate);
    }
    return '—';
}

function parseIsoDate(value) {
    if (!value || typeof value !== 'string') {
        return null;
    }
    const parts = value.split('-');
    if (parts.length !== 3) {
        return null;
    }
    const year = Number.parseInt(parts[0], 10);
    const month = Number.parseInt(parts[1], 10);
    const day = Number.parseInt(parts[2], 10);
    if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
        return null;
    }
    return new Date(Date.UTC(year, month - 1, day));
}

function buildPeriodLabel(data) {
    if (!data) {
        return null;
    }
    if (data.type === 'WEEK' || data.type === 'week') {
        if (Number.isFinite(data.weekNumber) && Number.isFinite(data.weekYear)) {
            return `Vecka ${data.weekNumber}, ${data.weekYear}`;
        }
    }
    if (data.type === 'MONTH' || data.type === 'month') {
        if (Number.isFinite(data.month) && Number.isFinite(data.year)) {
            return `${formatMonthName(data.month)} ${data.year}`;
        }
    }
    return null;
}

function formatMonthName(monthNumber) {
    const monthNames = [
        '',
        'januari',
        'februari',
        'mars',
        'april',
        'maj',
        'juni',
        'juli',
        'augusti',
        'september',
        'oktober',
        'november',
        'december',
    ];
    if (!Number.isFinite(monthNumber) || monthNumber < 1 || monthNumber > 12) {
        return '';
    }
    return monthNames[monthNumber];
}

function formatPeriodRange(data) {
    if (!data) {
        return null;
    }
    return formatDateRange(data.startDate, data.endDate);
}

function formatItemCount(count) {
    const total = Number.isFinite(count) ? count : 0;
    return total === 1 ? '1 artikel' : `${total} artiklar`;
}
