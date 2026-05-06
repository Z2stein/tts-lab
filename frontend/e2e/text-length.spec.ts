import { test, expect } from '@playwright/test';

test.beforeEach(async ({ context, page }) => {
  await context.addCookies([
    { name: 'XSRF-TOKEN', value: 'test-token', url: 'http://127.0.0.1:4200' }
  ]);

  await page.route('**/api/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: '1',
        email: 'learner@example.dev',
        name: 'Learner',
        roles: ['USER'],
        authMode: 'mock'
      })
    });
  });
});

test('text length success path works end-to-end', async ({ page }) => {
  await page.route('**/api/projects/text-length/calculate', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ length: 3 })
    });
  });

  await page.goto('/text-length');

  const input = page.getByRole('textbox', { name: 'Text' });
  await input.fill('abc');

  await page.getByRole('button', { name: 'Check length' }).click();

  await expect(page.getByText('Length: 3')).toBeVisible();
  await expect(page.getByText('Backend request failed')).toHaveCount(0);
});

test('shows UI error when backend responds with an error', async ({ page }) => {
  await page.route('**/api/projects/text-length/calculate', async (route) => {
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'simulated backend error' })
    });
  });

  await page.goto('/text-length');

  const input = page.getByRole('textbox', { name: 'Text' });
  await input.fill('abc');

  await page.getByRole('button', { name: 'Check length' }).click();

  await expect(page.getByText('Backend request failed (HTTP 500).')).toBeVisible();
  await expect(page.getByText('Length: 3')).toHaveCount(0);
});
