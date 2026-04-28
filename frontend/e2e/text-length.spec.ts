import { test, expect } from '@playwright/test';

test('text length flow works end-to-end', async ({ page }) => {
  await page.goto('/');

  const input = page.getByLabel('Text eingeben');
  await input.fill('abc');

  await page.getByRole('button', { name: 'Submit' }).click();

  await expect(page.getByText('Länge: 3')).toBeVisible();
  await expect(page.getByText('Backend request failed.')).toHaveCount(0);
});
