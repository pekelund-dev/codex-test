/**
 * Tests for receipt overview polling/URL building (receipt-overview.js)
 * Uses jsdom to instantiate ReceiptOverviewController and test URL building.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(resolve(__dirname, '../../main/frontend/receipt-overview.js'), 'utf8');

function buildController(overviewUrl) {
    // Create a minimal root element
    const root = document.createElement('div');
    root.setAttribute('data-overview-root', '');
    root.setAttribute('data-overview-url', overviewUrl);
    root.setAttribute('data-scope', 'my');
    document.body.appendChild(root);

    // Evaluate the module so classes are available on globalThis
    // eslint-disable-next-line no-new-func
    new Function(source + '; globalThis.ReceiptOverviewController = ReceiptOverviewController;')();
    return new globalThis.ReceiptOverviewController(root);
}

describe('receipt-overview: ReceiptOverviewController.buildOverviewUrl', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        delete globalThis.ReceiptOverviewController;
    });

    it('appends periodType and primary params', () => {
        const ctrl = buildController('/receipts/overview/data');
        const url = ctrl.buildOverviewUrl({ periodType: 'week', primary: '2025-W01' });
        expect(url).toContain('periodType=week');
        expect(url).toContain('primary=2025-W01');
    });

    it('omits compare param when empty', () => {
        const ctrl = buildController('/receipts/overview/data');
        const url = ctrl.buildOverviewUrl({ periodType: 'week', primary: '2025-W01', compare: '' });
        expect(url).not.toContain('compare=');
    });

    it('includes compare param when provided', () => {
        const ctrl = buildController('/receipts/overview/data');
        const url = ctrl.buildOverviewUrl({ periodType: 'week', primary: '2025-W01', compare: '2025-W02' });
        expect(url).toContain('compare=2025-W02');
    });

    it('preserves existing query params from base URL', () => {
        const ctrl = buildController('/receipts/overview/data?foo=bar');
        const url = ctrl.buildOverviewUrl({ periodType: 'month', primary: '2025-01' });
        expect(url).toContain('foo=bar');
        expect(url).toContain('periodType=month');
    });
});
