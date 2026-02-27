/**
 * Tests for table sorting logic (table-sort.js)
 * Uses jsdom to simulate the browser environment.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const tableSort = readFileSync(resolve(__dirname, '../../main/frontend/table-sort.js'), 'utf8');

function createSortableTable(rows) {
    document.body.innerHTML = `
        <table class="sortable-table" data-sort-column="0">
            <thead>
                <tr>
                    <th class="sortable" data-sort-type="text">Name</th>
                    <th class="sortable" data-sort-type="number">Amount</th>
                </tr>
            </thead>
            <tbody>
                ${rows.map(([name, amount]) =>
                    `<tr><td>${name}</td><td>${amount}</td></tr>`
                ).join('')}
            </tbody>
        </table>
    `;
}

describe('table-sort', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
        // Re-evaluate the module to register a fresh DOMContentLoaded listener
        // eslint-disable-next-line no-new-func
        new Function(tableSort)();
    });

    it('sorts text column descending on initial load', () => {
        createSortableTable([['Banana', 2], ['Apple', 1], ['Cherry', 3]]);
        document.dispatchEvent(new Event('DOMContentLoaded'));

        const rows = document.querySelectorAll('tbody tr');
        // Initial sort: currentDirection defaults to 'asc', newDirection becomes 'desc'
        expect(rows[0].children[0].textContent).toBe('Cherry');
        expect(rows[1].children[0].textContent).toBe('Banana');
        expect(rows[2].children[0].textContent).toBe('Apple');
    });

    it('reverses sort order when header is clicked a second time', () => {
        createSortableTable([['Banana', 2], ['Apple', 1], ['Cherry', 3]]);
        document.dispatchEvent(new Event('DOMContentLoaded'));

        const header = document.querySelector('th.sortable');
        // Second click: currentDirection = 'desc', newDirection = 'asc'
        header.click();

        const rows = document.querySelectorAll('tbody tr');
        expect(rows[0].children[0].textContent).toBe('Apple');
        expect(rows[2].children[0].textContent).toBe('Cherry');
    });

    it('sorts numeric column correctly', () => {
        createSortableTable([['B', 20], ['A', 5], ['C', 10]]);
        document.dispatchEvent(new Event('DOMContentLoaded'));

        const headers = document.querySelectorAll('th.sortable');
        // Click the number column (index 1): currentDirection = '', newDirection = 'desc'
        headers[1].click();

        const rows = document.querySelectorAll('tbody tr');
        expect(rows[0].children[1].textContent).toBe('20');
        expect(rows[1].children[1].textContent).toBe('10');
        expect(rows[2].children[1].textContent).toBe('5');
    });
});
