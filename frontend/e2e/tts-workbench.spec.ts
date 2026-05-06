import { test, expect, BrowserContext, Page } from '@playwright/test';

const e2eBaseUrl = process.env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';

async function authenticate(context: BrowserContext, page: Page): Promise<void> {
  await context.addCookies([
    { name: 'XSRF-TOKEN', value: 'test-token', url: e2eBaseUrl }
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
}

test('tts workbench displays speaker voice analysis results for authenticated users', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/projects/tts-workbench/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        speakers: [
          { speakerName: 'Alice', roleDescription: 'Detected dialogue speaker', voiceSuggestion: 'Warm neutral voice' }
        ]
      })
    });
  });

  await page.goto('/tts-workbench');

  await page.getByRole('textbox', { name: 'Raw dialogue' }).fill('Alice: Hello');
  await page.getByRole('button', { name: 'Analyze Speakers' }).click();

  await expect(page.getByRole('textbox', { name: 'Speaker name' })).toHaveValue('Alice');
  await expect(page.getByRole('textbox', { name: 'Voice suggestion' })).toHaveValue('Warm neutral voice');
});

test('tts workbench route shows sign-in UI for unauthenticated users', async ({ page }) => {
  await page.route('**/api/me', async (route) => {
    await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
  });

  await page.goto('/tts-workbench');

  await expect(page.getByRole('heading', { name: 'Sign in to use TTS Lab' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign in with Google' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'TTS Workbench' })).toHaveCount(0);
});
