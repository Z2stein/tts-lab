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
      body: JSON.stringify({
        speakers: [
          { speakerName: 'Mara', roleDescription: 'Determined lead', voiceSuggestion: 'Warm alto voice' },
          { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
        ]
      })
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: We go now.\nJonas: Together.');
  await page.locator('#story-section').getByRole('button', { name: 'Find characters' }).click();

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
      body: JSON.stringify({
        speakers: [
          { speakerName: 'Mara', roleDescription: 'Determined lead', voiceSuggestion: 'Warm alto voice' },
          { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
        ]
      })
    });
  });
  await page.route('**/api/projects/tts-workbench/speaker-split-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        turns: [
          { speaker: 'Mara', text: 'We go now.' },
          { speaker: 'Jonas', text: 'Together.' }
        ]
      })
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: We go now.\nJonas: Together.');
  await page.locator('#story-section').getByRole('button', { name: 'Find characters' }).click();
  await page.locator('#cast-section').getByRole('button', { name: 'Review script' }).click();

  await expect(page.getByRole('heading', { name: 'Review script preview' })).toBeVisible();
  await expect(page.getByText('We go now.')).toBeVisible();
  await expect(page.getByText('Together.')).toBeVisible();
});

test('audiobook studio edits a script preview turn without freezing the app', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/projects/tts-workbench/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        speakers: [
          { speakerName: 'Narrator', roleDescription: 'Story voice', voiceSuggestion: 'Clear narrator' },
          { speakerName: 'Mara', roleDescription: 'Determined lead', voiceSuggestion: 'Warm alto voice' }
        ]
      })
    });
  });
  await page.route('**/api/projects/tts-workbench/speaker-split-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        turns: [
          { speaker: 'Narrator', text: 'The last train had already left when Mara found the brass key under the station clock.' },
          { speaker: 'Mara', text: 'Jonas, tell me you did not hide this here all winter.' }
        ]
      })
    });
  });
  await page.route('**/api/projects/tts-workbench/emotion-annotation-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        turns: [
          { speaker: 'Narrator', text: '[hushed] The last train had already left.' },
          { speaker: 'Mara', text: '[worried] Jonas, answer me.' }
        ]
      })
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('button', { name: 'Use sample story' }).click();
  await page.locator('#story-section').getByRole('button', { name: 'Find characters' }).click();
  await page.locator('#cast-section').getByRole('button', { name: 'Review script' }).click();

  await page.getByTestId('script-turn-edit-0').click();
  await expect(page.getByLabel('Speaker')).toBeVisible();
  await expect(page.locator('#script-text-0')).toBeVisible();

  await page.locator('#script-text-0').fill('The last train had already left, and the station clock was wrong.');
  await page.getByRole('button', { name: 'Save' }).click();

  await expect(page.getByText('The last train had already left, and the station clock was wrong.')).toBeVisible();
  await page.locator('#script-section').getByRole('button', { name: 'Approve script' }).click();
  await page.locator('#script-section').getByRole('button', { name: 'Add emotion & pacing' }).click();

  await expect(page.getByText('[hushed]')).toBeVisible();
});

test('audiobook studio shows structured backend errors without internal details', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/projects/tts-workbench/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 502,
      contentType: 'application/json',
      body: JSON.stringify({
        status: 502,
        code: 'TTS_WORKBENCH_PROVIDER_FAILED',
        message: 'The cast analysis provider is currently unavailable. Please try again later.',
        details: null,
        requestId: 'request-1'
      })
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: Hello');
  await page.locator('#story-section').getByRole('button', { name: 'Find characters' }).click();

  await expect(page.getByRole('alert')).toContainText('The cast analysis provider is currently unavailable. Please try again later.');
  await expect(page.getByText('TTS_WORKBENCH_PROVIDER_FAILED')).toHaveCount(0);
  await expect(page.getByText('request-1')).toHaveCount(0);
});
