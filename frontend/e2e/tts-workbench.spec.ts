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
            renderIndex: 1,
            originalTurnIndexes: [0],
            speakerName: 'Narrator',
            voiceId: 'Schedar',
            text: '[calm] The rain hit the windows.',
            languageCode: 'en-US',
            modelName: '{{google-model}}',
            audioEncoding: 'MP3'
          },
          {
            renderIndex: 2,
            originalTurnIndexes: [1],
            speakerName: 'Mara',
            voiceId: 'Kore',
            text: '[sarcastic] So this is your surprise?',
            languageCode: 'en-US',
            modelName: '{{google-model}}',
            audioEncoding: 'MP3'
          },
          {
            renderIndex: 3,
            originalTurnIndexes: [2],
            speakerName: 'Jonas',
            voiceId: 'Iapetus',
            text: '[serious] I thought you would be pleased.',
            languageCode: 'en-US',
            modelName: '{{google-model}}',
            audioEncoding: 'MP3'
          }
        ]
      })
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
  await expect(page.getByText('Speaker: Narrator')).toBeVisible();
  await expect(page.getByText('Voice ID: Schedar')).toBeVisible();
  await expect(page.getByText('Original turn indexes: 0')).toBeVisible();
  await expect(page.locator('article.request-chunk').nth(0)).toContainText('"speakerName": "Narrator"');
  await expect(page.locator('article.request-chunk').nth(0)).toContainText('"voiceId": "Schedar"');
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
