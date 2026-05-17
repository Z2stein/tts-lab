import { test, expect, BrowserContext, Page } from '@playwright/test';
import { loadTestContractJson } from '../src/app/shared/test-contracts';

const e2eBaseUrl = process.env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';

async function authenticate(context: BrowserContext, page: Page): Promise<void> {
  await context.addCookies([{ name: 'XSRF-TOKEN', value: 'test-token', url: e2eBaseUrl }]);
  const currentUser = await loadTestContractJson('auth/me/default/response.json');
  const requestLimits = await loadTestContractJson('limits/request-limits-me/default/response.json');
  await page.route('**/api/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(currentUser)
    });
  });
  await page.route('**/api/request-limits/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(requestLimits)
    });
  });
}

async function routeAudiobookList(page: Page, relativePath: string): Promise<void> {
  const response = await loadTestContractJson<{ items: Array<{ title: string }> }>(relativePath);
  await page.route('**/api/audiobooks', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(response) });
  });
}

test('authenticated user can open the empty audiobook library', async ({ context, page }) => {
  await authenticate(context, page);
  await routeAudiobookList(page, 'audiobooks/list/empty/response.json');

  await page.goto('/audiobook-library');

  await expect(page.getByRole('link', { name: 'My Audiobooks' })).toHaveClass(/active/);
  await expect(page.getByRole('heading', { name: 'Library' })).toBeVisible();
  await expect(page.getByTestId('empty-library-state')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Create audiobook' })).toHaveAttribute('href', '/audiobook-studio');
});

test('library cards render with ready preview actions', async ({ context, page }) => {
  await authenticate(context, page);
  await routeAudiobookList(page, 'audiobooks/list/amber-signal/response.json');

  await page.goto('/library');

  await expect(page.getByTestId('audiobook-card')).toHaveCount(1);
  await expect(page.getByRole('heading', { name: 'The Amber Signal' })).toBeVisible();
  await expect(page.getByTestId('continue-studio')).toBeVisible();
  await expect(page.getByTestId('play-preview')).toBeVisible();
  await expect(page.getByTestId('download-preview')).toBeVisible();
});

test('multiple audiobook projects appear as distinct cards', async ({ context, page }) => {
  await authenticate(context, page);
  await routeAudiobookList(page, 'audiobooks/list/amber-and-silver/response.json');

  await page.goto('/library');

  await expect(page.getByTestId('audiobook-card')).toHaveCount(2);
  await expect(page.getByRole('heading', { name: 'The Amber Signal' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'The Silver Key' })).toBeVisible();
});

test('play preview button opens waveform player modal instead of navigating', async ({ context, page }) => {
  await authenticate(context, page);
  await routeAudiobookList(page, 'audiobooks/list/amber-signal/response.json');

  await page.goto('/library');

  await page.getByTestId('play-preview').click();

  await expect(page.getByRole('button', { name: 'Play', exact: true })).toBeVisible();
  await expect(page.locator('app-audio-player-modal').getByRole('button', { name: 'Download', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Close', exact: true })).toBeVisible();
});

test('closing waveform player modal returns to library view', async ({ context, page }) => {
  await authenticate(context, page);
  await routeAudiobookList(page, 'audiobooks/list/amber-signal/response.json');

  await page.goto('/library');

  await page.getByTestId('play-preview').click();
  await expect(page.getByRole('button', { name: 'Close', exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Close', exact: true }).click();

  await expect(page.getByRole('heading', { name: 'Library' })).toBeVisible();
  await expect(page.getByTestId('audiobook-card')).toHaveCount(1);
});

test('audiobook studio remains reachable from authenticated navigation', async ({ context, page }) => {
  await authenticate(context, page);

  await page.goto('/audiobook-studio');

  await expect(page.getByRole('link', { name: 'My Audiobooks' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Give every character in your story a voice.' })).toBeVisible();
  await page.getByRole('link', { name: 'My Audiobooks' }).click();
  await expect(page).toHaveURL(/\/audiobook-library$/);
});

test('library card displays correct metadata for multi-segment audiobook with repeated speakers', async ({ context, page }) => {
  await authenticate(context, page);
  await routeAudiobookList(page, 'audiobooks/list/multi-segment/response.json');

  await page.goto('/audiobook-library');

  const card = page.locator('[data-testid="audiobook-card"]').first();
  await expect(card).toBeVisible();

  await expect(card.getByTestId('speech-segment-count')).toHaveText('3');

  await expect(card.getByTestId('speaker-count')).toHaveText('2');

  const duration = card.locator('dt:has-text("Duration")').locator('..').locator('dd');
  await expect(duration).toContainText('0:19');

  await expect(card.locator('h2')).toContainText('Generated audiobook 2026-05-09T20:50:36');

  const continueButton = card.locator('text=Continue');
  await expect(continueButton).toBeVisible();
  await expect(continueButton).toHaveAttribute('href', '/audiobook-studio/project-multi-speaker');
});

test('library card disables preview actions when no project preview exists', async ({ context, page }) => {
  await authenticate(context, page);
  await routeAudiobookList(page, 'audiobooks/list/multi-segment/response.json');

  await page.goto('/audiobook-library');

  const card = page.locator('[data-testid="audiobook-card"]').first();
  await expect(card.getByTestId('play-preview')).toBeDisabled();
  await expect(card.getByTestId('download-preview')).toBeDisabled();
});

test('audiobook library integration with components is functional', async ({ context, page }) => {
  await authenticate(context, page);
  await routeAudiobookList(page, 'audiobooks/list/amber-signal/response.json');

  await page.goto('/audiobook-library');
  await expect(page.getByRole('heading', { name: 'Library' })).toBeVisible();
});

test('authenticated header account menu shows user details and logout action', async ({ context, page }) => {
  await authenticate(context, page);
  await routeAudiobookList(page, 'audiobooks/list/amber-signal/response.json');

  await page.goto('/audiobook-library');

  await expect(page.getByTestId('account-menu-trigger')).toContainText('L');
  await page.getByTestId('account-menu-trigger').click();
  await expect(page.getByTestId('account-menu-panel')).toContainText('Learner');
  await expect(page.getByTestId('account-menu-panel')).toContainText('Signed in with Mock');
  await expect(page.getByTestId('account-menu-logout')).toBeVisible();
});
