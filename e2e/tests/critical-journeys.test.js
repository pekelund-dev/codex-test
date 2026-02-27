import { test, expect } from '@playwright/test';

/**
 * E2E tests for critical user journeys.
 * Requires the app to be running at E2E_BASE_URL (default: http://localhost:8080).
 */

test.describe('Login page', () => {
    test('login page renders with signin form', async ({ page }) => {
        await page.goto('/login');
        await expect(page).toHaveTitle(/pklnd/i);
        await expect(page.locator('form')).toBeVisible();
        await expect(page.locator('input[name="username"], input[type="email"]')).toBeVisible();
        await expect(page.locator('input[type="password"]')).toBeVisible();
    });

    test('login page shows error on invalid credentials', async ({ page }) => {
        await page.goto('/login');
        await page.fill('input[name="username"], input[type="email"]', 'invalid@example.com');
        await page.fill('input[type="password"]', 'wrongpassword');
        await page.click('button[type="submit"]');
        // Should stay on login page or show error
        await expect(page).toHaveURL(/login/);
    });
});

test.describe('Navigation', () => {
    test('unauthenticated users are redirected to login', async ({ page }) => {
        await page.goto('/dashboard');
        await expect(page).toHaveURL(/login/);
    });

    test('unauthenticated users are redirected from receipts', async ({ page }) => {
        await page.goto('/receipts');
        await expect(page).toHaveURL(/login/);
    });

    test('unauthenticated users are redirected from uploads', async ({ page }) => {
        await page.goto('/receipts/uploads');
        await expect(page).toHaveURL(/login/);
    });
});

test.describe('Register page', () => {
    test('register page renders with registration form', async ({ page }) => {
        await page.goto('/register');
        await expect(page.locator('form')).toBeVisible();
        await expect(page.locator('input[name="displayName"], input[placeholder*="namn" i]')).toBeVisible();
    });
});
