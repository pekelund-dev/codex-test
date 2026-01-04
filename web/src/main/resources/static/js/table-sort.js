/**
 * Table sorting functionality for sortable tables
 */
document.addEventListener('DOMContentLoaded', function() {
    const sortableTables = document.querySelectorAll('.sortable-table');
    
    sortableTables.forEach(table => {
        const headers = table.querySelectorAll('th.sortable');
        const tbody = table.querySelector('tbody');
        
        // Apply initial sort if specified
        const initialColumn = parseInt(table.dataset.sortColumn || '0');
        const initialDirection = table.dataset.sortDirection || 'asc';
        
        headers.forEach((header, index) => {
            header.style.cursor = 'pointer';
            header.style.userSelect = 'none';
            
            // Add sort indicator
            const sortIndicator = document.createElement('span');
            sortIndicator.className = 'sort-indicator ms-1';
            sortIndicator.innerHTML = '<i class="bi bi-arrow-down-up"></i>';
            header.appendChild(sortIndicator);
            
            header.addEventListener('click', () => {
                sortTable(table, index, header);
            });
        });
        
        // Apply initial sort
        if (headers[initialColumn]) {
            const direction = initialDirection === 'desc' ? 'asc' : 'desc'; // Will be toggled
            sortTable(table, initialColumn, headers[initialColumn]);
        }
    });
    
    function sortTable(table, columnIndex, header) {
        const tbody = table.querySelector('tbody');
        const rows = Array.from(tbody.querySelectorAll('tr'));
        const sortType = header.dataset.sortType || 'text';
        const currentDirection = header.dataset.sortDirection || 'asc';
        const newDirection = currentDirection === 'asc' ? 'desc' : 'asc';
        
        // Update all headers to remove active state
        table.querySelectorAll('th.sortable').forEach(h => {
            h.dataset.sortDirection = '';
            const indicator = h.querySelector('.sort-indicator i');
            if (indicator) {
                indicator.className = 'bi bi-arrow-down-up';
            }
        });
        
        // Set active state on clicked header
        header.dataset.sortDirection = newDirection;
        const indicator = header.querySelector('.sort-indicator i');
        if (indicator) {
            indicator.className = newDirection === 'asc' ? 'bi bi-arrow-up' : 'bi bi-arrow-down';
        }
        
        // Sort rows
        rows.sort((a, b) => {
            let aVal, bVal;
            
            // Find the correct cell index accounting for conditional columns
            let cellIndex = 0;
            let headerIndex = 0;
            const headerCells = table.querySelectorAll('thead th');
            
            for (let i = 0; i < headerCells.length; i++) {
                if (i === columnIndex) {
                    cellIndex = i;
                    break;
                }
            }
            
            const aCell = a.children[cellIndex];
            const bCell = b.children[cellIndex];
            
            if (!aCell || !bCell) return 0;
            
            // Get sort values from data attribute if available
            aVal = aCell.dataset.sortValue || aCell.textContent.trim();
            bVal = bCell.dataset.sortValue || bCell.textContent.trim();
            
            // Handle different sort types
            if (sortType === 'number') {
                aVal = parseFloat(aVal) || 0;
                bVal = parseFloat(bVal) || 0;
            } else if (sortType === 'date') {
                aVal = new Date(aVal || '1970-01-01');
                bVal = new Date(bVal || '1970-01-01');
            }
            
            // Compare values
            let comparison = 0;
            if (aVal > bVal) comparison = 1;
            else if (aVal < bVal) comparison = -1;
            
            return newDirection === 'asc' ? comparison : -comparison;
        });
        
        // Reorder rows in DOM
        rows.forEach(row => tbody.appendChild(row));
    }
});
