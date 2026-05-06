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

test('tts workbench previews provider-compatible request splitting after final JSON generation', async ({ context, page }) => {
  await authenticate(context, page);

  let providerPlanRequest: { input: { prompt: string } } | null = null;

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

  await page.route('**/api/projects/tts-workbench/provider-compatible-request-plan', async (route) => {
    providerPlanRequest = route.request().postDataJSON();
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        chunks: [
          {
            chunkNumber: 1,
            speakers: ['Narrator', 'Mara'],
            request: {
              ...finalRequest,
              input: {
                ...finalRequest.input,
                multiSpeakerMarkup: { turns: finalRequest.input.multiSpeakerMarkup.turns.slice(0, 2) }
              },
              voice: {
                ...finalRequest.voice,
                multiSpeakerVoiceConfig: {
                  speakerVoiceConfigs: finalRequest.voice.multiSpeakerVoiceConfig.speakerVoiceConfigs.slice(0, 2)
                }
              }
            }
          },
          {
            chunkNumber: 2,
            speakers: ['Jonas'],
            request: {
              ...finalRequest,
              input: {
                ...finalRequest.input,
                multiSpeakerMarkup: { turns: finalRequest.input.multiSpeakerMarkup.turns.slice(2) }
              },
              voice: {
                ...finalRequest.voice,
                multiSpeakerVoiceConfig: {
                  speakerVoiceConfigs: finalRequest.voice.multiSpeakerVoiceConfig.speakerVoiceConfigs.slice(2)
                }
              }
            }
          }
        ]
      })
    });
  });

  await page.goto('/tts-workbench');

  await expect(page.getByRole('heading', { name: '6. Provider-Compatible Request Splitting Preview' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Plan Provider-Compatible Requests' })).toBeDisabled();

  await page.getByRole('textbox', { name: 'Raw dialogue' }).fill('Narrator: The rain hit the windows.\nMara: So this is your surprise?\nJonas: I thought you would be pleased.');
  await page.getByRole('button', { name: 'Analyze Speakers' }).click();
  await page.getByRole('button', { name: 'Split Dialogue' }).click();
  await page.getByRole('button', { name: 'Annotate Emotions' }).click();
  await page.getByRole('button', { name: 'Generate Final JSON' }).click();

  await expect(page.getByRole('heading', { name: '5. Final Request JSON Preview' })).toBeVisible();
  await expect(page.locator('pre').filter({ hasText: 'A tense café conversation.' })).toBeVisible();

  await page.getByRole('button', { name: 'Plan Provider-Compatible Requests' }).click();

  expect(providerPlanRequest?.input.prompt).toBe('A tense café conversation.');
  await expect(page.getByText('Chunk count: 2')).toBeVisible();
  await expect(page.getByText('Included speakers: Narrator, Mara')).toBeVisible();
  await expect(page.getByText('Included speakers: Jonas')).toBeVisible();
  await expect(page.locator('article.request-chunk').nth(0)).toContainText('"speakerAlias": "Narrator"');
  await expect(page.locator('article.request-chunk').nth(0)).not.toContainText('"speakerAlias": "Jonas"');
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
