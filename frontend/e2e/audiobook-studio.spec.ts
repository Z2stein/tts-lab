import { test, expect, BrowserContext, Page } from '@playwright/test';
import { loadTestContractJson } from '../src/app/shared/test-contracts';

const e2eBaseUrl = process.env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
const testProjectId = 'project-1';

async function authenticate(context: BrowserContext, page: Page): Promise<void> {
  await context.addCookies([
    { name: 'XSRF-TOKEN', value: 'test-token', url: e2eBaseUrl }
  ]);

  const currentUser = await loadTestContractJson('auth/me/default/response.json');
  await page.route('**/api/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(currentUser)
    });
  });
}

test('audiobook studio fills the story textarea with sample content', async ({ context, page }) => {
  await authenticate(context, page);

  await page.goto('/audiobook-studio');
  await page.getByRole('button', { name: 'Use sample story' }).click();

  const storyText = page.getByRole('textbox', { name: 'Story text' });
  await expect(storyText).toHaveValue(/Mara/);
  await expect(storyText).toHaveValue(/Jonas/);
  await expect(storyText).toHaveValue(/Station Keeper/);
});

test('audiobook studio shows cast cards after story analysis succeeds', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/projects/tts-workbench/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('tts-workbench/speaker-voice-analysis/cast-analysis/response.json'))
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: We go now.\nJonas: Together.');
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();

  await expect(page.getByText('Detected character')).toHaveCount(2);
  await expect(page.getByRole('heading', { name: 'Mara' })).toBeVisible();
  await expect(page.getByText('Warm alto voice')).toBeVisible();
});

test('audiobook studio shows script preview turns after cast analysis continues', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/projects/tts-workbench/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('tts-workbench/speaker-voice-analysis/cast-analysis/response.json'))
    });
  });
  await page.route('**/api/projects/tts-workbench/speaker-split-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('tts-workbench/speaker-split-analysis/script-preview/response.json'))
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: We go now.\nJonas: Together.');
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();
  await expect(page.getByText('Detected character')).toHaveCount(2);
  await page.locator('#cast-section').getByRole('button', { name: 'Review script' }).click();

  await expect(page.locator('h2', { hasText: 'Review script' })).toBeVisible();
  await expect(page.getByText('We go now.')).toBeVisible();
  await expect(page.getByText('Together.')).toBeVisible();
});

test('audiobook studio edits a script preview turn without freezing the app', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/projects/tts-workbench/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('tts-workbench/speaker-voice-analysis/cast-analysis/response.json'))
    });
  });
  await page.route('**/api/projects/tts-workbench/speaker-split-analysis', async (route) => {
    const body = route.request().postDataJSON() as { projectId?: string };
    expect(body.projectId).toBe(testProjectId);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('tts-workbench/speaker-split-analysis/script-preview/response.json'))
    });
  });
  await page.route('**/api/projects/tts-workbench/script-preview-save', async (route) => {
    const body = route.request().postDataJSON() as { projectId?: string; turns?: Array<{ speaker?: string; text?: string }> };
    expect(body.projectId).toBe(testProjectId);
    expect(body.turns).toEqual([
      { speaker: 'Mara', text: 'The last train had already left, and the station clock was wrong.' },
      { speaker: 'Jonas', text: 'Together.' }
    ]);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        turns: body.turns
      })
    });
  });
  await page.route('**/api/projects/tts-workbench/emotion-annotation-analysis', async (route) => {
    const body = route.request().postDataJSON() as { projectId?: string; turns?: unknown };
    expect(body.projectId).toBe(testProjectId);
    expect(body.turns).toBeUndefined();
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('tts-workbench/emotion-annotation-analysis/script-preview/response.json'))
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('button', { name: 'Use sample story' }).click();
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();
  await expect(page.getByText('Detected character')).toHaveCount(2);
  await page.locator('#cast-section').getByRole('button', { name: 'Review script' }).click();

  await page.getByTestId('script-turn-edit-0').click();
  await expect(page.getByLabel('Speaker')).toBeVisible();
  await expect(page.locator('#script-text-0')).toBeVisible();

  await page.locator('#script-text-0').fill('The last train had already left, and the station clock was wrong.');
  await page.getByRole('button', { name: 'Save' }).click();

  await expect(page.getByText('The last train had already left, and the station clock was wrong.')).toBeVisible();
  await page.locator('#script-section').getByRole('button', { name: 'Approve script & continue' }).click();
  await page.locator('#script-section').getByRole('button', { name: 'Add emotion & pacing' }).click();

  await expect(page.getByText('[hushed]')).toBeVisible();
});

test('audiobook studio shows structured backend errors without internal details', async ({ context, page }) => {
  await authenticate(context, page);
  const providerUnavailable = await loadTestContractJson('tts-workbench/speaker-voice-analysis/provider-unavailable/response.json');
  await page.route('**/api/projects/tts-workbench/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: providerUnavailable.status ?? 502,
      contentType: 'application/json',
      body: JSON.stringify(providerUnavailable)
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: Hello');
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();

  await expect(page.getByRole('alert')).toContainText('The speaker voice analysis provider is currently unavailable. Please try again later.');
  await expect(page.getByText('TTS_WORKBENCH_PROVIDER_FAILED')).toHaveCount(0);
  await expect(page.getByText('request-1')).toHaveCount(0);
});
