import { test, expect } from '@playwright/test';

test('text length success path works end-to-end', async ({ page }) => {
  await page.route('**/api/text-length', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ length: 3 })
    });
  });

  await page.goto('/');

  const input = page.getByLabel('Text eingeben');
  await input.fill('abc');

  await page.getByRole('button', { name: 'Submit' }).click();

  await expect(page.getByText('Länge: 3')).toBeVisible();
  await expect(page.getByText('Backend request failed.')).toHaveCount(0);
});


test('shows UI error when backend responds with an error', async ({ page }) => {

  await page.route('**/api/text-length', async (route) => {
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'simulated backend error' })

    });
  });

  await page.goto('/');

  const input = page.getByLabel('Text eingeben');
  await input.fill('abc');

  await page.getByRole('button', { name: 'Submit' }).click();

  await expect(page.getByText('Backend request failed.')).toBeVisible();
  await expect(page.getByText('Länge: 3')).toHaveCount(0);
});
