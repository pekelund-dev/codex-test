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
        this.defaultPeriodType = root.getAttribute('data-default-period-type') || '';
        this.defaultPrimaryWeek = root.getAttribute('data-default-primary-week') || '';
        this.defaultPrimaryMonth = root.getAttribute('data-default-primary-month') || '';
        this.defaultCompareWeek = root.getAttribute('data-default-compare-week') || '';
        this.defaultCompareMonth = root.getAttribute('data-default-compare-month') || '';
        this.supportsAbortController =
            typeof window !== 'undefined' && typeof window.AbortController === 'function';

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

        this.activeRequest = null;
        this.receiptDateSet = new Set();
        this.weekIndicatorItems = [];
        this.monthIndicatorItems = [];
    }

    init() {
        if (this.periodTypeSelect) {
            if (this.defaultPeriodType) {
                this.periodTypeSelect.value = this.defaultPeriodType;
            }
        }
        if (this.primaryWeekInput) {
            if (this.defaultPrimaryWeek) {
                this.primaryWeekInput.value = this.defaultPrimaryWeek;
            }
        }
        if (this.primaryMonthInput) {
            if (this.defaultPrimaryMonth) {
                this.primaryMonthInput.value = this.defaultPrimaryMonth;
            }
        }
        if (this.compareWeekInput) {
            if (this.defaultCompareWeek) {
                this.compareWeekInput.value = this.defaultCompareWeek;
            }
        }
        if (this.compareMonthInput) {
            if (this.defaultCompareMonth) {
                this.compareMonthInput.value = this.defaultCompareMonth;
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

        if (showingWeek && this.primaryWeekInput && !this.primaryWeekInput.value && this.defaultPrimaryWeek) {
            this.primaryWeekInput.value = this.defaultPrimaryWeek;
        }
        if (!showingWeek && this.primaryMonthInput && !this.primaryMonthInput.value && this.defaultPrimaryMonth) {
            this.primaryMonthInput.value = this.defaultPrimaryMonth;
        }

        if (this.weekPicker) {
            if (showingWeek) {
                this.weekPicker.classList.remove('d-none');
            } else {
                this.weekPicker.classList.add('d-none');
            }
        }
        if (this.monthPicker) {
            if (showingWeek) {
                this.monthPicker.classList.add('d-none');
            } else {
                this.monthPicker.classList.remove('d-none');
            }
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

        if (showCompareWeek && this.compareWeekInput && !this.compareWeekInput.value && this.defaultCompareWeek) {
            this.compareWeekInput.value = this.defaultCompareWeek;
        }
        if (showCompareMonth && this.compareMonthInput && !this.compareMonthInput.value && this.defaultCompareMonth) {
            this.compareMonthInput.value = this.defaultCompareMonth;
        }

        if (this.compareWeekPicker) {
            if (showCompareWeek) {
                this.compareWeekPicker.classList.remove('d-none');
            } else {
                this.compareWeekPicker.classList.add('d-none');
            }
        }
        if (this.compareMonthPicker) {
            if (showCompareMonth) {
                this.compareMonthPicker.classList.remove('d-none');
            } else {
                this.compareMonthPicker.classList.add('d-none');
            }
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
            this.updatePanelsLoading(false);
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

        if (this.activeRequest && this.activeRequest.controller
            && typeof this.activeRequest.controller.abort === 'function') {
            this.activeRequest.controller.abort();
        }

        const requestToken = {
            controller: this.supportsAbortController ? new AbortController() : null,
        };
        this.activeRequest = requestToken;

        const fetchOptions = {
            headers: { Accept: 'application/json' },
            credentials: 'same-origin',
        };
        if (requestToken.controller) {
            fetchOptions.signal = requestToken.controller.signal;
        }

        let settled = false;

        fetch(url, fetchOptions)
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
                if (this.activeRequest !== requestToken) {
                    return;
                }
                this.activeRequest = null;
                this.handleData(data);
                settled = true;
            })
            .catch((error) => {
                const controller = requestToken.controller;
                if (controller && controller.signal && controller.signal.aborted) {
                    return;
                }
                if (this.activeRequest !== requestToken) {
                    return;
                }
                this.activeRequest = null;
                const message = error && error.message
                    ? error.message
                    : 'Det gick inte att läsa in översikten just nu.';
                this.showError(message);
                this.setReceiptDates(null);
                this.updateIndicators();
                this.updatePanels(null, null, message);
                settled = true;
            })
            .finally(() => {
                if (this.activeRequest === requestToken) {
                    this.activeRequest = null;
                }
                if (settled || this.activeRequest === null) {
                    this.setLoadingState(false);
                    this.updatePanelsLoading(false);
                }
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
            if (isLoading) {
                this.loadingIndicator.classList.remove('d-none');
            } else {
                this.loadingIndicator.classList.add('d-none');
            }
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
            if (hasReceipt) {
                item.classList.add('has-receipt');
            } else {
                item.classList.remove('has-receipt');
            }
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
            if (hasReceipt) {
                item.classList.add('has-receipt');
            } else {
                item.classList.remove('has-receipt');
            }
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
        let path = base;
        let queryString = '';
        const questionIndex = base.indexOf('?');
        if (questionIndex >= 0) {
            path = base.slice(0, questionIndex);
            queryString = base.slice(questionIndex + 1);
        }

        const params = {};
        if (queryString) {
            queryString.split('&').forEach((pair) => {
                if (!pair) {
                    return;
                }
                const [rawKey, rawValue] = pair.split('=', 2);
                const normalizedKey = rawKey ? rawKey.replace(/\+/g, ' ') : '';
                const key = normalizedKey ? decodeURIComponent(normalizedKey) : '';
                if (!key) {
                    return;
                }
                const normalizedValue = rawValue !== undefined ? rawValue.replace(/\+/g, ' ') : undefined;
                const value = normalizedValue !== undefined ? decodeURIComponent(normalizedValue) : '';
                params[key] = value;
            });
        }

        if (config && config.periodType) {
            params.periodType = config.periodType;
        } else {
            delete params.periodType;
        }
        if (config && config.primary) {
            params.primary = config.primary;
        } else {
            delete params.primary;
        }
        if (config && config.compare && config.compare.trim().length > 0) {
            params.compare = config.compare;
        } else {
            delete params.compare;
        }
        if (this.scope) {
            params.scope = this.scope;
        } else {
            delete params.scope;
        }

        const entries = Object.keys(params)
            .filter((key) => params[key] !== undefined && params[key] !== null && String(params[key]).length > 0)
            .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(String(params[key]))}`);

        if (entries.length === 0) {
            return path;
        }
        return `${path}?${entries.join('&')}`;
    }
}

class PeriodPanel {
    constructor(element) {
        this.element = element;
        this.titleElement = element.querySelector('[data-period-title]');
        this.rangeElement = element.querySelector('[data-period-range]');
        this.summaryElement = element.querySelector('[data-period-summary]');
        this.summaryValueElement = element.querySelector('[data-period-summary-value]');
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
                    const defaultDescending =
                        key === 'date' || key === 'totalPrice' || key === 'unitPrice' || key === 'price';
                    this.sortDirection = defaultDescending ? 'desc' : 'asc';
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
        if (isVisible) {
            this.element.classList.remove('d-none');
        } else {
            this.element.classList.add('d-none');
        }
    }

    setLoading(isLoading) {
        if (this.loadingIndicator) {
            if (isLoading) {
                this.loadingIndicator.classList.remove('d-none');
            } else {
                this.loadingIndicator.classList.add('d-none');
            }
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
        if (this.summaryElement && this.summaryValueElement) {
            if (data) {
                const summaryText = buildPeriodSummaryText(data);
                this.summaryValueElement.textContent = summaryText || '—';
                this.summaryElement.classList.remove('d-none');
            } else {
                this.summaryValueElement.textContent = '—';
                this.summaryElement.classList.add('d-none');
            }
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
            if (isActive) {
                button.classList.add('text-primary');
            } else {
                button.classList.remove('text-primary');
            }
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

    const meta = document.createElement('div');
    meta.className = 'table-meta d-md-none';
    addMetaItem(meta, 'Enhetspris', formatCurrency(item ? item.unitPriceValue : null));
    addMetaItem(meta, 'Totalt', formatCurrency(item ? item.totalPriceValue : null));
    addMetaItem(meta, 'Antal', item && item.quantityLabel ? item.quantityLabel : '—');
    addMetaItem(meta, 'Butik', item && item.store ? item.store : '—');
    addMetaItem(meta, 'Datum', formatDateLabel(item ? item.dateIso : null, item ? item.dateLabel : null));
    nameCell.appendChild(meta);

    row.appendChild(nameCell);

    const unitPriceCell = document.createElement('td');
    unitPriceCell.className = 'text-end d-none d-md-table-cell';
    unitPriceCell.textContent = formatCurrency(item ? item.unitPriceValue : null);
    row.appendChild(unitPriceCell);

    const totalPriceCell = document.createElement('td');
    totalPriceCell.className = 'text-end d-none d-md-table-cell';
    totalPriceCell.textContent = formatCurrency(item ? item.totalPriceValue : null);
    row.appendChild(totalPriceCell);

    const quantityCell = document.createElement('td');
    quantityCell.className = 'text-end d-none d-md-table-cell';
    quantityCell.textContent = item && item.quantityLabel ? item.quantityLabel : '—';
    row.appendChild(quantityCell);

    const storeCell = document.createElement('td');
    storeCell.className = 'd-none d-md-table-cell';
    storeCell.textContent = item && item.store ? item.store : '—';
    row.appendChild(storeCell);

    const dateCell = document.createElement('td');
    dateCell.className = 'd-none d-md-table-cell';
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

    const meta = document.createElement('div');
    meta.className = 'table-meta d-md-none';
    addMetaItem(meta, 'Enhetspris', formatGroupUnitPriceRange(summary));
    addMetaItem(meta, 'Totalt', formatGroupTotalPriceRange(summary));
    addMetaItem(meta, 'Antal', formatGroupQuantity(summary, itemCount));
    addMetaItem(meta, 'Butiker', formatGroupStoreCount(summary));
    addMetaItem(meta, 'Datum', formatDateRange(summary ? summary.earliestDateIso : null, summary ? summary.latestDateIso : null));
    nameCell.appendChild(meta);

    row.appendChild(nameCell);

    const unitPriceCell = document.createElement('td');
    unitPriceCell.className = 'text-end d-none d-md-table-cell';
    unitPriceCell.textContent = formatGroupUnitPriceRange(summary);
    row.appendChild(unitPriceCell);

    const totalPriceCell = document.createElement('td');
    totalPriceCell.className = 'text-end d-none d-md-table-cell';
    totalPriceCell.textContent = formatGroupTotalPriceRange(summary);
    row.appendChild(totalPriceCell);

    const quantityCell = document.createElement('td');
    quantityCell.className = 'text-end d-none d-md-table-cell';
    quantityCell.textContent = formatGroupQuantity(summary, itemCount);
    row.appendChild(quantityCell);

    const storeCell = document.createElement('td');
    storeCell.className = 'd-none d-md-table-cell';
    storeCell.textContent = formatGroupStoreCount(summary);
    row.appendChild(storeCell);

    const dateCell = document.createElement('td');
    dateCell.className = 'd-none d-md-table-cell';
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
        case 'unitPrice':
            return Number.isFinite(item.unitPriceValue) ? item.unitPriceValue : -Infinity;
        case 'totalPrice':
        case 'price':
            return Number.isFinite(item.totalPriceValue) ? item.totalPriceValue : -Infinity;
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

function addMetaItem(container, label, value) {
    if (!container) {
        return;
    }
    const span = document.createElement('span');
    span.textContent = `${label}: ${value}`;
    container.appendChild(span);
}

function formatGroupStoreCount(summary) {
    const storeCount = summary && Number.isFinite(summary.storeCount) ? summary.storeCount : 0;
    return storeCount > 0 ? `${storeCount} ${storeCount === 1 ? 'butik' : 'butiker'}` : '—';
}

function formatGroupUnitPriceRange(summary) {
    if (!summary) {
        return '—';
    }
    return formatPriceRange(summary.minUnitPriceValue, summary.maxUnitPriceValue);
}

function formatGroupTotalPriceRange(summary) {
    if (!summary) {
        return '—';
    }
    return formatPriceRange(summary.minTotalPriceValue, summary.maxTotalPriceValue);
}

function formatPriceRange(minValue, maxValue) {
    const min = Number.isFinite(minValue) ? minValue : null;
    const max = Number.isFinite(maxValue) ? maxValue : null;
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

function buildPeriodSummaryText(data) {
    if (!data || !data.summary) {
        return null;
    }
    const summary = data.summary;
    const parts = [];

    const totalPrice = Number.isFinite(summary.totalPriceValue) ? summary.totalPriceValue : null;
    if (totalPrice !== null) {
        parts.push(`${formatCurrency(totalPrice)} totalt belopp`);
    }

    if (Number.isFinite(summary.receiptCount)) {
        const receipts = summary.receiptCount;
        parts.push(`${receipts} ${receipts === 1 ? 'kvitto' : 'kvitton'}`);
    }

    if (Number.isFinite(summary.storeCount)) {
        const stores = summary.storeCount;
        parts.push(`${stores} ${stores === 1 ? 'butik' : 'butiker'}`);
    }

    if (Number.isFinite(summary.totalQuantityValue) && summary.totalQuantityValue > 0) {
        parts.push(`${numberFormatter.format(summary.totalQuantityValue)} totalt antal`);
    }

    if (parts.length === 0) {
        return '—';
    }
    return parts.join(' · ');
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

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initReceiptOverview);
} else {
    initReceiptOverview();
}
