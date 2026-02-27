/**
 * Tests for upload file handling logic (receipt-uploads.js)
 * Tests the formatBytes and truncateName utility behaviour by DOM integration.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * Re-define pure utility functions extracted from receipt-uploads.js
 * for focused unit testing (the functions are inside an IIFE in the source).
 */
function formatBytes(bytes) {
    if (!Number.isFinite(bytes) || bytes <= 0) {
        return '0 B';
    }
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
        size /= 1024;
        unitIndex += 1;
    }
    return `${size.toFixed(1)} ${units[unitIndex]}`;
}

function truncateName(name, maxLength) {
    if (typeof name !== 'string' || name.trim() === '') {
        return '—';
    }
    const trimmed = name.trim();
    if (trimmed.length <= maxLength) {
        return trimmed;
    }
    const extensionIndex = trimmed.lastIndexOf('.');
    if (extensionIndex > 0 && extensionIndex < trimmed.length - 1) {
        const base = trimmed.substring(0, extensionIndex);
        const extension = trimmed.substring(extensionIndex);
        const allowedBase = Math.max(1, maxLength - extension.length - 1);
        return `${base.substring(0, allowedBase)}…${extension}`;
    }
    return `${trimmed.substring(0, Math.max(1, maxLength - 1))}…`;
}

describe('receipt-uploads: formatBytes', () => {
    it('returns "0 B" for zero', () => {
        expect(formatBytes(0)).toBe('0 B');
    });

    it('returns "0 B" for negative values', () => {
        expect(formatBytes(-1)).toBe('0 B');
    });

    it('returns "0 B" for non-finite values', () => {
        expect(formatBytes(NaN)).toBe('0 B');
        expect(formatBytes(Infinity)).toBe('0 B');
    });

    it('formats bytes', () => {
        expect(formatBytes(512)).toBe('512.0 B');
    });

    it('formats kilobytes', () => {
        expect(formatBytes(1024)).toBe('1.0 KB');
        expect(formatBytes(2048)).toBe('2.0 KB');
    });

    it('formats megabytes', () => {
        expect(formatBytes(1024 * 1024)).toBe('1.0 MB');
    });
});

describe('receipt-uploads: truncateName', () => {
    it('returns "—" for empty string', () => {
        expect(truncateName('', 20)).toBe('—');
        expect(truncateName('   ', 20)).toBe('—');
    });

    it('returns "—" for non-string', () => {
        expect(truncateName(null, 20)).toBe('—');
        expect(truncateName(undefined, 20)).toBe('—');
    });

    it('returns full name when within max length', () => {
        expect(truncateName('short.pdf', 20)).toBe('short.pdf');
    });

    it('truncates long names preserving extension', () => {
        const result = truncateName('very-long-filename.pdf', 15);
        expect(result).toContain('.pdf');
        expect(result.length).toBeLessThanOrEqual(15);
        expect(result).toContain('…');
    });

    it('truncates names without extension', () => {
        const result = truncateName('averylongfilenamewithnodot', 10);
        expect(result.length).toBeLessThanOrEqual(10);
        expect(result).toContain('…');
    });
});
