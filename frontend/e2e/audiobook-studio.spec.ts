import { test, expect, BrowserContext, Page } from '@playwright/test';
import { loadTestContractJson } from '../src/app/shared/test-contracts';

const e2eBaseUrl = process.env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
const testProjectId = 'project-1';

type WorkflowSnapshotName =
  | 'cast-review'
  | 'cast-approved'
  | 'script-approved'
  | 'script-approved-stale'
  | 'performance-ready'
  | 'audio-generated';

async function loadWorkflowSnapshotFixture(name: WorkflowSnapshotName): Promise<Record<string, unknown>> {
  return loadTestContractJson(`audiobook-workflow/workflow-snapshot/${name}/response.json`);
}

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

async function mockWorkflowSnapshot(
  page: Page,
  snapshotName: WorkflowSnapshotName = 'cast-review',
  overrides: Partial<Record<string, unknown>> = {}
): Promise<void> {
  const snapshot = {
    ...(await loadWorkflowSnapshotFixture(snapshotName)),
    ...overrides
  };

  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(snapshot)
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
    await expect(page.locator('.cast-card')).toHaveCount(2);
    await expect(page.getByText('Detected dialogue speaker')).toHaveCount(2);
  await expect(page.getByRole('heading', { name: 'Mara' })).toBeVisible();
  await expect(page.getByText('KORE')).toBeVisible();
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
  await page.route(`**/api/audiobooks/${testProjectId}`, async (route) => {
    const body = route.request().postDataJSON() as { title?: string };
    expect(body.title).toBe('Updated Signal');
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobooks/detail/updated-title/response.json'))
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
  await mockWorkflowSnapshot(page, 'cast-approved');
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}/cast-approval`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadWorkflowSnapshotFixture('cast-approved'))
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
    await expect(page.locator('.cast-card')).toHaveCount(2);
    await expect(page.getByText('Detected dialogue speaker')).toHaveCount(2);
  await page.locator('#cast-section').getByRole('button', { name: 'Approve voices & continue' }).click();

  await expect(page.locator('h2', { hasText: 'Review script' })).toBeVisible();
  await expect(page.getByText('We go now.')).toBeVisible();
  await expect(page.getByText('Together.')).toBeVisible();
});

test('audiobook studio generates the final preview after the workflow reaches audio production', async ({ context, page }) => {
  await authenticate(context, page);
  const castReviewSnapshot = await loadWorkflowSnapshotFixture('cast-review');
  const castApprovedSnapshot = await loadWorkflowSnapshotFixture('cast-approved');
  const scriptApprovedSnapshot = await loadWorkflowSnapshotFixture('script-approved');
  const performanceReadySnapshot = await loadWorkflowSnapshotFixture('performance-ready');
  const audioGeneratedSnapshot = await loadWorkflowSnapshotFixture('audio-generated');
  let createAudioCalls = 0;
  let finalizeAudioCalls = 0;
  let performanceReady = false;
  await page.route('**/api/audiobooks/workflow/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/speaker-voice-analysis/cast-analysis/response.json'))
    });
  });
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(performanceReady ? performanceReadySnapshot : castReviewSnapshot)
    });
  });
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}/cast-approval`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(castApprovedSnapshot)
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
      body: JSON.stringify(scriptApprovedSnapshot)
    });
  });
  await page.route('**/api/audiobooks/workflow/emotion-annotation-analysis', async (route) => {
    performanceReady = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(performanceReadySnapshot)
    });
  });
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}/production-settings`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(performanceReadySnapshot)
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
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/single-speaker-render-plan/multi-speaker/response.json'))
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
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}/audio-generated`, async (route) => {
    finalizeAudioCalls += 1;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(audioGeneratedSnapshot)
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('textbox', { name: 'Story text' }).fill('Mara: We go now.\nJonas: Together.');
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();
  await page.waitForURL(`**/audiobook-studio/${testProjectId}`);
  await page.locator('#cast-section').getByRole('button', { name: 'Approve voices & continue' }).click();
  // Approve script and create performance notes (happens automatically via approveScriptAndContinueWorkflow)
  await Promise.all([
    page.waitForResponse((response) =>
      response.url().includes(`/api/audiobooks/workflow/projects/${testProjectId}/script-approval`) &&
      response.request().method() === 'POST'
    ),
    page.locator('#script-section').getByRole('button', { name: 'Approve script & continue' }).click()
  ]);
  // Wait for emotion annotation to complete (triggered automatically by approveScriptAndContinueWorkflow)
  await page.waitForResponse((response) =>
    response.url().includes('/api/audiobooks/workflow/emotion-annotation-analysis') &&
    response.request().method() === 'POST'
  );
  await expect(page.locator('#performance-section').getByRole('button', { name: 'Next: Prepare audiobook' })).toBeEnabled();
  await page.locator('#performance-section').getByRole('button', { name: 'Next: Prepare audiobook' }).click();

  await expect(page.getByRole('button', { name: 'Generate pending parts' })).toBeVisible();
  await page.getByRole('button', { name: 'Generate pending parts' }).click();

  await expect(page.getByText('Audiobook preview ready.')).toBeVisible();
  await expect(page.locator('.generated-audio-player')).toBeVisible();
  await expect(page.getByText('audiobook-preview-merged.mp3')).toBeVisible();
  await expect(page.getByRole('button', { name: 'All parts ready' })).toBeVisible();
  expect(createAudioCalls).toBe(5);
  expect(finalizeAudioCalls).toBe(1);
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('audiobook studio keeps performance notes stale after reloading an edited script', async ({ context, page }) => {
  await authenticate(context, page);
  await mockWorkflowSnapshot(page, 'script-approved-stale');

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

test('audiobook studio keeps the workflow progress bar sticky while scrolling through the workflow', async ({ context, page }) => {
  await authenticate(context, page);
  await mockWorkflowSnapshot(page);

  await page.goto(`/audiobook-studio/${testProjectId}`);

  const workflowProgress = page.getByTestId('workflow-progress');
  const currentTask = page.getByTestId('current-task');
  const audioSection = page.getByTestId('audio-section');

  const beforeScroll = await workflowProgress.boundingBox();
  expect(beforeScroll?.y ?? 0).toBeGreaterThan(0);

  const audioSectionTop = await audioSection.evaluate((element) => element.getBoundingClientRect().top + window.scrollY);
  await page.evaluate((scrollTop) => {
    window.scrollTo(0, Math.max(0, scrollTop - 140));
  }, audioSectionTop);
  await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));

  await expect(workflowProgress).toBeVisible();

  const afterScroll = await workflowProgress.boundingBox();
  const afterTask = await currentTask.boundingBox();
  expect(afterScroll?.y ?? 0).toBeGreaterThanOrEqual(8);
  expect(afterScroll?.y ?? 0).toBeLessThanOrEqual(48);
  expect(afterTask?.y ?? 0).toBeLessThan(0);
});

test('audiobook studio edits a script preview turn without freezing the app', async ({ context, page }) => {
  await authenticate(context, page);
  let performanceReady = false;
  const castReviewSnapshot = await loadWorkflowSnapshotFixture('cast-review');
  const castApprovedSnapshot = await loadWorkflowSnapshotFixture('cast-approved');
  const performanceReadySnapshot = await loadWorkflowSnapshotFixture('performance-ready');
  await page.route('**/api/audiobooks/workflow/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/speaker-voice-analysis/cast-analysis/response.json'))
    });
  });
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(performanceReady ? performanceReadySnapshot : castReviewSnapshot)
    });
  });
  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}/cast-approval`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(castApprovedSnapshot)
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
      body: JSON.stringify(await loadTestContractJson('audiobook-workflow/script-preview-save/default/response.json'))
    });
  });
  await page.route('**/api/audiobooks/workflow/emotion-annotation-analysis', async (route) => {
    const body = route.request().postDataJSON() as { projectId?: string; turns?: unknown };
    expect(body.projectId).toBe(testProjectId);
    expect(body.turns).toBeUndefined();
    performanceReady = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(await loadWorkflowSnapshotFixture('performance-ready'))
    });
  });

  await page.goto('/audiobook-studio');
  await page.getByRole('button', { name: 'Use sample story' }).click();
  await page.locator('#story-section').getByRole('button', { name: 'Find narrator & characters' }).click();
  await page.waitForURL(`**/audiobook-studio/${testProjectId}`);
    await expect(page.locator('.cast-card')).toHaveCount(2);
    await expect(page.getByText('Detected dialogue speaker')).toHaveCount(2);
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

test('audiobook studio shows saved audio setup and previously generated audio after page reload', async ({ context, page }) => {
  await authenticate(context, page);
  const audioGeneratedSnapshot = await loadWorkflowSnapshotFixture('audio-generated');

  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(audioGeneratedSnapshot)
    });
  });

  await page.goto(`/audiobook-studio/${testProjectId}`);

  const audioSection = page.getByTestId('audio-section');

  // Empty state must NOT appear when audio assets are present
  await expect(audioSection.getByText('The audiobook setup will appear after performance notes.')).toHaveCount(0);

  // Verify the "Audiobook setup" readonly summary appears in audio-section
  await expect(audioSection.locator('.plan-summary')).toBeVisible();
  await expect(audioSection.getByText('Generation state')).toBeVisible();
  await expect(audioSection.getByText('Ready to listen')).toBeVisible();

  // Verify all status items are visible
  await expect(audioSection.locator('.plan-summary').getByText('Audio parts')).toBeVisible();
  await expect(audioSection.locator('.plan-summary').getByText('Ready parts')).toBeVisible();
  await expect(audioSection.locator('.plan-summary').getByText('Needs generation')).toBeVisible();
  await expect(audioSection.locator('.plan-summary').getByText('Failed parts')).toBeVisible();
  await expect(audioSection.locator('.plan-summary').getByText('Language code')).toBeVisible();
  await expect(audioSection.locator('.plan-summary').getByText('Audio encoding')).toBeVisible();

  // Verify previously generated audio is inside a collapsible details section
  await expect(audioSection.locator('details.audio-parts-details summary')).toContainText('Previously generated audio');
});

test('audiobook studio shows the waveform player after page reload when mergedAudioUrl is present in the snapshot', async ({ context, page }) => {
  await authenticate(context, page);

  await page.route(`**/api/audiobooks/workflow/projects/${testProjectId}`, async (route) => {
    const snapshot = await loadWorkflowSnapshotFixture('audio-generated');
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(snapshot)
    });
  });

  await page.goto(`/audiobook-studio/${testProjectId}`);

  const audioSection = page.getByTestId('audio-section');
  await expect(audioSection.locator('.generated-audio-player')).toBeVisible();
  await expect(audioSection.getByRole('heading', { name: 'Audiobook preview', exact: true })).toBeVisible();
  await expect(audioSection.getByRole('button', { name: 'Download MP3' })).toBeVisible();
});



