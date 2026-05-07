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

test('tts workbench previews single-speaker render requests after final JSON generation', async ({ context, page }) => {
  await authenticate(context, page);

  let renderPlanRequest: { input: { prompt: string } } | null = null;
  let createAudioRequest: { renderRequests: unknown[] } | null = null;
  let perRequestAudioRequest: { renderRequests: Array<{ input?: { text?: string } }> } | null = null;

  await page.route('**/api/projects/tts-workbench/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        speakers: [
          { speakerName: 'Narrator', roleDescription: 'Narrates the scene', voiceSuggestion: 'SCHEDAR' },
          { speakerName: 'Mara', roleDescription: 'Tense speaker', voiceSuggestion: 'KORE' },
          { speakerName: 'Jonas', roleDescription: 'Careful speaker', voiceSuggestion: 'IAPETUS' }
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
          { speaker: 'Narrator', text: 'The rain hit the windows.' },
          { speaker: 'Mara', text: 'So this is your surprise?' },
          { speaker: 'Jonas', text: 'I thought you would be pleased.' }
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
          { speaker: 'Narrator', text: '[calm] The rain hit the windows.' },
          { speaker: 'Mara', text: '[sarcastic] So this is your surprise?' },
          { speaker: 'Jonas', text: '[serious] I thought you would be pleased.' }
        ]
      })
    });
  });

  const finalRequest = {
    input: {
      prompt: 'A tense café conversation.',
      multiSpeakerMarkup: {
        turns: [
          { speaker: 'Narrator', text: '[calm] The rain hit the windows.' },
          { speaker: 'Mara', text: '[sarcastic] So this is your surprise?' },
          { speaker: 'Jonas', text: '[serious] I thought you would be pleased.' }
        ]
      }
    },
    voice: {
      languageCode: 'en-US',
      modelName: '{{google-model}}',
      multiSpeakerVoiceConfig: {
        speakerVoiceConfigs: [
          { speakerAlias: 'Narrator', speakerId: 'Schedar' },
          { speakerAlias: 'Mara', speakerId: 'Kore' },
          { speakerAlias: 'Jonas', speakerId: 'Iapetus' }
        ]
      }
    },
    audioConfig: { audioEncoding: 'MP3' }
  };

  await page.route('**/api/projects/tts-workbench/final-request-preview', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(finalRequest)
    });
  });

  await page.route('**/api/projects/tts-workbench/single-speaker-render-plan', async (route) => {
    renderPlanRequest = route.request().postDataJSON();
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        renderRequests: [
          {
            input: { text: '[calm] The rain hit the windows.' },
            voice: { languageCode: 'en-US', name: 'Schedar', modelName: '{{google-model}}' },
            audioConfig: { audioEncoding: 'MP3' }
          },
          {
            input: { text: '[sarcastic] So this is your surprise?' },
            voice: { languageCode: 'en-US', name: 'Kore', modelName: '{{google-model}}' },
            audioConfig: { audioEncoding: 'MP3' }
          },
          {
            input: { text: '[serious] I thought you would be pleased.' },
            voice: { languageCode: 'en-US', name: 'Iapetus', modelName: '{{google-model}}' },
            audioConfig: { audioEncoding: 'MP3' }
          }
        ]
      })
    });
  });

  await page.route('**/api/projects/tts-workbench/create-audio', async (route) => {
    const request = route.request().postDataJSON() as { renderRequests: unknown[] };
    createAudioRequest = request;
    const isSingleRequest = request.renderRequests.length === 1;
    await route.fulfill({
      status: 200,
      contentType: 'audio/mpeg',
      headers: { 'Content-Disposition': `attachment; filename="${isSingleRequest ? 'tts-render-request-2.mp3' : 'tts-render-plan.mp3'}"` },
      body: 'mock mp3 bytes'
    });
  });

  await page.goto('/tts-workbench');

  await expect(page.getByRole('heading', { name: '6. Single-Speaker Render Plan Preview' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Plan Single-Speaker Render Requests' })).toBeDisabled();

  await page.getByRole('textbox', { name: 'Raw dialogue' }).fill('Narrator: The rain hit the windows.\nMara: So this is your surprise?\nJonas: I thought you would be pleased.');
  await page.getByRole('button', { name: 'Analyze Speakers' }).click();
  await page.getByRole('button', { name: 'Split Dialogue' }).click();
  await page.getByRole('button', { name: 'Annotate Emotions' }).click();
  await page.getByRole('button', { name: 'Generate Final JSON' }).click();

  await expect(page.getByRole('heading', { name: '5. Final Request JSON Preview' })).toBeVisible();
  await expect(page.locator('pre').filter({ hasText: 'A tense café conversation.' })).toBeVisible();

  await page.getByRole('button', { name: 'Plan Single-Speaker Render Requests' }).click();

  expect(renderPlanRequest?.input.prompt).toBe('A tense café conversation.');
  await expect(page.getByText('Render request count: 3')).toBeVisible();
  await expect(page.locator('article.request-chunk').nth(0)).toContainText('\"input\"');
  await expect(page.locator('article.request-chunk').nth(0)).toContainText('\"text\": \"[calm] The rain hit the windows.\"');
  await expect(page.locator('article.request-chunk').nth(0)).toContainText('\"name\": \"Schedar\"');
  await expect(page.locator('article.request-chunk').nth(0)).toContainText('\"audioEncoding\": \"MP3\"');

  const perRequestDownloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Create audio for render request 2' }).click();
  const perRequestDownload = await perRequestDownloadPromise;
  perRequestAudioRequest = createAudioRequest as { renderRequests: Array<{ input?: { text?: string } }> };

  expect(perRequestAudioRequest?.renderRequests).toHaveLength(1);
  expect(perRequestAudioRequest?.renderRequests[0].input?.text).toBe('[sarcastic] So this is your surprise?');
  expect(perRequestDownload.suggestedFilename()).toBe('tts-render-request-2.mp3');

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Create full-plan audio' }).click();
  const download = await downloadPromise;

  expect(createAudioRequest?.renderRequests).toHaveLength(3);
  expect(download.suggestedFilename()).toBe('tts-render-plan.mp3');
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
