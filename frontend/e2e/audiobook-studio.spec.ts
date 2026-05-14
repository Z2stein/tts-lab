import { test, expect, BrowserContext, Page } from '@playwright/test';
import { loadTestContractJson } from '../src/app/shared/test-contracts';

const e2eBaseUrl = process.env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
const testProjectId = 'project-1';
const defaultProductionSettings = {
  prompt: 'An immersive audiobook performance with a clear narrator and distinct character voices.',
  languageCode: 'en-US',
  modelName: 'gemini-3.1-flash-tts-preview',
  audioEncoding: 'MP3'
};

async function authenticate(context: BrowserContext, page: Page): Promise<void> {
  await context.addCookies([
    { name: 'XSRF-TOKEN', value: 'test-token', url: e2eBaseUrl }
  ]);

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

async function mockWorkflowSnapshot(page: Page, overrides: Partial<Record<string, unknown>> = {}): Promise<void> {
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildWorkflowSnapshot({
        workflowStage: 'CAST_REVIEW',
        scriptTurns: [],
        annotatedTurns: [],
        audioAssets: [],
        audioAssetsCurrent: false,
        performanceNotesStale: false,
        ...overrides
      }))
    });
  });
}

function buildWorkflowSnapshot(overrides: Partial<Record<string, unknown>> = {}): Record<string, unknown> {
  return {
    projectId: testProjectId,
    title: 'The Hidden Signal',
    storyText: 'Mara: We go now.\nJonas: Together.',
    workflowStage: 'CAST_REVIEW',
    speakers: [
      { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' },
      { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
    ],
    scriptTurns: [],
    annotatedTurns: [],
    productionSettings: defaultProductionSettings,
    audioAssets: [],
    audioAssetsCurrent: false,
    performanceNotesStale: false,
    ...overrides
  };
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
  await page.route('**/api/audiobooks/workflow/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/speaker-voice-analysis/cast-analysis/response.json'))
    });
  });
  await mockWorkflowSnapshot(page);

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: We go now.\nJonas: Together.');
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();

  await page.waitForURL(`**/audiobook-studio/${testProjectId}`);
  await expect(page.getByTestId('studio-hero')).toHaveCount(0);
  await expect(page.getByText('Detected character')).toHaveCount(2);
  await expect(page.getByRole('heading', { name: 'Mara' })).toBeVisible();
  await expect(page.getByText('Warm alto voice')).toBeVisible();
  await expect(page.getByText('The Hidden Signal')).toBeVisible();
});

test('audiobook studio lets the user edit and persist the AI project title', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/audiobooks/workflow/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/speaker-voice-analysis/cast-analysis/response.json'))
    });
  });
  await mockWorkflowSnapshot(page);
  await page.route('**/api/audiobooks/project-1', async (route) => {
    const body = route.request().postDataJSON() as { title?: string };
    expect(body.title).toBe('Updated Signal');
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'project-1',
        title: body.title,
        status: 'NEEDS_REVIEW',
        speechSegmentCount: 0,
        speakerCount: null,
        totalDurationSeconds: null,
        createdAt: '2026-05-12T10:00:00Z',
        updatedAt: '2026-05-12T10:01:00Z',
        speechSegments: [],
        audioAssets: []
      })
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: We go now.\nJonas: Together.');
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();
  await page.waitForURL(`**/audiobook-studio/${testProjectId}`);

  await expect(page.getByText('The Hidden Signal')).toBeVisible();
  await page.getByTestId('edit-project-title').click();
  await page.getByTestId('project-title-input').fill('Updated Signal');
  await page.getByTestId('save-project-title').click();

  await expect(page.getByTestId('project-title-display')).toHaveText('Updated Signal');
});

test('audiobook studio shows script preview turns after cast analysis continues', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/audiobooks/workflow/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/speaker-voice-analysis/cast-analysis/response.json'))
    });
  });
  await mockWorkflowSnapshot(page);
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}/cast-approval`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        projectId: testProjectId,
        title: 'The Hidden Signal',
        storyText: 'Mara: We go now.\nJonas: Together.',
        workflowStage: 'CAST_APPROVED',
        speakers: [
          { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' },
          { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
        ],
        scriptTurns: [],
        annotatedTurns: [],
        productionSettings: defaultProductionSettings,
        audioAssets: [],
        audioAssetsCurrent: false,
        performanceNotesStale: false
      })
    });
  });
  await page.route('**/api/audiobooks/workflow/speaker-split-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/speaker-split-analysis/script-preview/response.json'))
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: We go now.\nJonas: Together.');
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();
  await page.waitForURL(`**/audiobook-studio/${testProjectId}`);
  await expect(page.getByText('Detected character')).toHaveCount(2);
  await page.locator('#cast-section').getByRole('button', { name: 'Approve voices & continue' }).click();

  await expect(page.locator('h2', { hasText: 'Review script' })).toBeVisible();
  await expect(page.getByText('We go now.')).toBeVisible();
  await expect(page.getByText('Together.')).toBeVisible();
});

test('audiobook studio generates the final preview after the workflow reaches audio production', async ({ context, page }) => {
  await authenticate(context, page);
  const scriptPreviewResponse = await loadTestContractJson<{ turns: Array<{ speaker: string; text: string }> }>(
    'audiobook-workflow/speaker-split-analysis/script-preview/response.json'
  );
  const annotatedTurnsResponse = await loadTestContractJson<{ turns: Array<{ speaker: string; text: string }> }>(
    'audiobook-workflow/emotion-annotation-analysis/script-preview/response.json'
  );
  let createAudioCalls = 0;
  await page.route('**/api/audiobooks/workflow/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/speaker-voice-analysis/cast-analysis/response.json'))
    });
  });
  await mockWorkflowSnapshot(page);
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}/cast-approval`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildWorkflowSnapshot({ workflowStage: 'CAST_APPROVED' }))
    });
  });
  await page.route('**/api/audiobooks/workflow/speaker-split-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/speaker-split-analysis/script-preview/response.json'))
    });
  });
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}/script-approval`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildWorkflowSnapshot({
        workflowStage: 'SCRIPT_APPROVED',
        scriptTurns: scriptPreviewResponse.turns,
        annotatedTurns: []
      }))
    });
  });
  await page.route('**/api/audiobooks/workflow/emotion-annotation-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(annotatedTurnsResponse)
    });
  });
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}/production-settings`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildWorkflowSnapshot({
        workflowStage: 'PERFORMANCE_READY',
        scriptTurns: scriptPreviewResponse.turns,
        annotatedTurns: annotatedTurnsResponse.turns
      }))
    });
  });
  await page.route('**/api/audiobooks/workflow/final-request-preview', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/final-request-preview/default/response.json'))
    });
  });
  await page.route('**/api/audiobooks/workflow/single-speaker-render-plan', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        renderRequests: [
          {
            input: { text: 'The rain hit the windows.\nThe café was nearly empty.' },
            voice: { languageCode: 'en-US', name: 'Kore', modelName: '{{google-model}}' },
            audioConfig: { audioEncoding: 'MP3' }
          },
          {
            input: { text: 'So this is your surprise?' },
            voice: { languageCode: 'en-US', name: 'Iapetus', modelName: '{{google-model}}' },
            audioConfig: { audioEncoding: 'MP3' }
          },
          {
            input: { text: 'I thought you would be pleased.' },
            voice: { languageCode: 'en-US', name: 'Rasalgethi', modelName: '{{google-model}}' },
            audioConfig: { audioEncoding: 'MP3' }
          }
        ]
      })
    });
  });
  await page.route(/\/api\/audiobooks\/workflow\/create-audio(\?.*)?$/, async (route) => {
    createAudioCalls += 1;
    await route.fulfill({
      status: 200,
      contentType: 'audio/mpeg',
      headers: {
        'Content-Disposition': 'attachment; filename="audiobook-preview-merged.mp3"',
        'X-Audiobook-Project-Id': testProjectId
      },
      body: 'ID3MOCKMP3'
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: We go now.\nJonas: Together.');
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();
  await page.waitForURL(`**/audiobook-studio/${testProjectId}`);
  await page.locator('#cast-section').getByRole('button', { name: 'Approve voices & continue' }).click();
  await Promise.all([
    page.waitForResponse((response) =>
      response.url().includes(`/api/audiobooks/workflow/projects/${testProjectId}/script-approval`) &&
      response.request().method() === 'POST'
    ),
    page.locator('#script-section').getByRole('button', { name: 'Approve script & continue' }).click()
  ]);
  await expect(page.locator('#script-section').getByRole('button', { name: 'Add emotion & pacing' })).toBeEnabled();
  await Promise.all([
    page.waitForResponse((response) =>
      response.url().includes('/api/audiobooks/workflow/emotion-annotation-analysis') &&
      response.request().method() === 'POST'
    ),
    page.locator('#script-section').getByRole('button', { name: 'Add emotion & pacing' }).click()
  ]);
  await expect(page.locator('#performance-section').getByRole('button', { name: 'Next: Prepare audiobook' })).toBeEnabled();
  await page.locator('#performance-section').getByRole('button', { name: 'Next: Prepare audiobook' }).click();

  await expect(page.getByRole('button', { name: 'Generate pending parts' })).toBeVisible();
  await page.getByRole('button', { name: 'Generate pending parts' }).click();

  await expect(page.getByText('Audiobook preview ready.')).toBeVisible();
  await expect(page.locator('.generated-audio-player')).toBeVisible();
  await expect(page.getByText('audiobook-preview-merged.mp3')).toBeVisible();
  await expect(page.getByRole('button', { name: 'All parts ready' })).toBeVisible();
  expect(createAudioCalls).toBe(3);
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('audiobook studio keeps performance notes stale after reloading an edited script', async ({ context, page }) => {
  await authenticate(context, page);
  await mockWorkflowSnapshot(page, {
    workflowStage: 'SCRIPT_APPROVED',
    scriptTurns: [
      { speaker: 'Mara', text: 'We go now.' }
    ],
    annotatedTurns: [
      { speaker: 'Mara', text: '[urgent] We go now.' }
    ],
    performanceNotesStale: true
  });

  await page.goto(`/audiobook-studio/${testProjectId}`);

  await expect(page.getByText('Notes stale')).toBeVisible();
  await expect(page.getByText('Update emotion & pacing, then continue.')).toBeVisible();
  await expect(page.locator('#performance-section').getByRole('button', { name: 'Next: Prepare audiobook' })).toBeDisabled();
});

test('audiobook studio resume route keeps a single studio shell and renders styled journey and workflow sections', async ({ context, page }) => {
  await authenticate(context, page);
  await mockWorkflowSnapshot(page);

  await page.goto(`/audiobook-studio/${testProjectId}`);

  await expect(page.locator('.studio')).toHaveCount(1);
  await expect(page.getByTestId('studio-hero')).toHaveCount(0);
  await expect(page.getByTestId('journey-grid')).toBeVisible();
  await expect(page.getByTestId('journey-card')).toHaveCount(5);
  await expect(page.getByTestId('workflow-progress')).toBeVisible();
  await expect(page.getByTestId('workflow-step')).toHaveCount(5);
  await expect(page.getByTestId('current-task')).toBeVisible();
  await expect(page.getByTestId('current-task')).toContainText('Choose your voices');
});

test('audiobook studio edits a script preview turn without freezing the app', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/audiobooks/workflow/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/speaker-voice-analysis/cast-analysis/response.json'))
    });
  });
  await mockWorkflowSnapshot(page);
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}/cast-approval`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        projectId: testProjectId,
        title: 'The Hidden Signal',
        storyText: 'Mara: We go now.\nJonas: Together.',
        workflowStage: 'CAST_APPROVED',
        speakers: [
          { speakerName: 'Mara', roleDescription: 'Bold traveler', voiceSuggestion: 'Warm alto voice' },
          { speakerName: 'Jonas', roleDescription: 'Careful friend', voiceSuggestion: 'Gentle tenor voice' }
        ],
        scriptTurns: [],
        annotatedTurns: [],
        productionSettings: defaultProductionSettings,
        audioAssets: [],
        audioAssetsCurrent: false,
        performanceNotesStale: false
      })
    });
  });
  await page.route('**/api/audiobooks/workflow/speaker-split-analysis', async (route) => {
    const body = route.request().postDataJSON() as { projectId?: string };
    expect(body.projectId).toBe(testProjectId);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/speaker-split-analysis/script-preview/response.json'))
    });
  });
  await page.route('**/api/audiobooks/workflow/script-preview-save', async (route) => {
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
  await page.route('**/api/audiobooks/workflow/emotion-annotation-analysis', async (route) => {
    const body = route.request().postDataJSON() as { projectId?: string; turns?: unknown };
    expect(body.projectId).toBe(testProjectId);
    expect(body.turns).toBeUndefined();
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/emotion-annotation-analysis/script-preview/response.json'))
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('button', { name: 'Use sample story' }).click();
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();
  await page.waitForURL(`**/audiobook-studio/${testProjectId}`);
  await expect(page.getByText('Detected character')).toHaveCount(2);
  await page.locator('#cast-section').getByRole('button', { name: 'Approve voices & continue' }).click();

  await page.getByTestId('script-turn-edit-0').click();
  await expect(page.getByLabel('Speaker')).toBeVisible();
  await expect(page.locator('#script-text-0')).toBeVisible();

  await page.locator('#script-text-0').fill('The last train had already left, and the station clock was wrong.');
  await Promise.all([
    page.waitForResponse((response) =>
      response.url().includes('/api/audiobooks/workflow/script-preview-save') && response.request().method() === 'POST'
    ),
    page.getByRole('button', { name: 'Save' }).click()
  ]);

  await expect(page.getByText('The last train had already left, and the station clock was wrong.')).toBeVisible();
  await Promise.all([
    page.waitForResponse((response) =>
      response.url().includes(`/api/audiobooks/workflow/projects/${testProjectId}/script-approval`) &&
      response.request().method() === 'POST'
    ),
    page.locator('#script-section').getByRole('button', { name: 'Approve script & continue' }).click()
  ]);
  await expect(page.getByText('The last train had already left, and the station clock was wrong.')).toBeVisible();
});

test('audiobook studio shows structured backend errors without internal details', async ({ context, page }) => {
  await authenticate(context, page);
  const providerUnavailable = await loadTestContractJson<{ status?: number }>('audiobook-workflow/speaker-voice-analysis/provider-unavailable/response.json');
  await page.route('**/api/audiobooks/workflow/speaker-voice-analysis', async (route) => {
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
  await expect(page.getByText('AUDIOBOOK_WORKFLOW_PROVIDER_FAILED')).toHaveCount(0);
  await expect(page.getByText('request-1')).toHaveCount(0);
});


