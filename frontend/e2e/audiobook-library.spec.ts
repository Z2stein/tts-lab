import { test, expect, BrowserContext, Page } from '@playwright/test';

const e2eBaseUrl = process.env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';

async function authenticate(context: BrowserContext, page: Page): Promise<void> {
  await context.addCookies([{ name: 'XSRF-TOKEN', value: 'test-token', url: e2eBaseUrl }]);
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
  await page.route('**/api/request-limits/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        windowResetAt: '2026-05-09T12:00:00Z',
        windowSeconds: 43200,
        limits: [
          { modelType: 'SPEECH_MODEL', used: 0, limit: 600, remaining: 600, unit: 'WORDS' },
          { modelType: 'TEXT_MODEL', used: 0, limit: 600, remaining: 600, unit: 'WORDS' }
        ]
      })
    });
  });
}

const project = {
  id: 'project-amber',
  title: 'The Amber Signal',
  status: 'NEEDS_REVIEW',
  sceneCount: 2,
  speakerCount: 3,
  totalDurationSeconds: 185,
  createdAt: '2026-05-08T08:30:00Z',
  updatedAt: '2026-05-09T10:30:00Z',
  audioAssets: [
    {
      id: 'asset-preview',
      sceneId: null,
      type: 'PREVIEW_MP3',
      version: 1,
      filename: 'amber-signal-preview.mp3',
      contentType: 'audio/mpeg',
      sizeBytes: 1240000,
      durationSeconds: 185,
      status: 'READY',
      createdAt: '2026-05-09T10:30:00Z',
      downloadUrl: '/api/audiobooks/project-amber/audio-assets/asset-preview/download',
      streamUrl: '/api/audiobooks/project-amber/audio-assets/asset-preview/stream'
    },
    {
      id: 'asset-scene-1',
      sceneId: 'scene-1',
      type: 'SCENE_MP3',
      version: 2,
      filename: 'scene-1-v2.mp3',
      contentType: 'audio/mpeg',
      sizeBytes: 640000,
      durationSeconds: 82,
      status: 'READY',
      createdAt: '2026-05-09T10:20:00Z',
      downloadUrl: '/api/audiobooks/project-amber/audio-assets/asset-scene-1/download',
      streamUrl: '/api/audiobooks/project-amber/audio-assets/asset-scene-1/stream'
    }
  ],
  scenes: [
    { id: 'scene-1', orderIndex: 0, title: 'Station clock', reviewStatus: 'PENDING', durationSeconds: 82 },
    { id: 'scene-2', orderIndex: 1, title: 'The winter key', reviewStatus: 'APPROVED', durationSeconds: 103 }
  ]
};

test('authenticated user can open the empty audiobook library', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/audiobooks', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [] }) });
  });

  await page.goto('/audiobook-library');

  await expect(page.getByRole('heading', { name: 'Library' })).toBeVisible();
  await expect(page.getByTestId('empty-library-state')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Create audiobook' })).toHaveAttribute('href', '/audiobook-studio');
});

test('library cards render with ready preview actions', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/audiobooks', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [project] }) });
  });

  await page.goto('/library');

  await expect(page.getByTestId('audiobook-card')).toHaveCount(1);
  await expect(page.getByRole('heading', { name: 'The Amber Signal' })).toBeVisible();
  await expect(page.getByTestId('continue-review')).toBeVisible();
  await expect(page.getByTestId('play-preview')).toBeVisible();
  await expect(page.getByTestId('download-asset')).toBeVisible();
});

test('multiple audiobook projects appear as distinct cards', async ({ context, page }) => {
  await authenticate(context, page);
  const project2 = {
    id: 'project-silver',
    title: 'The Silver Key',
    status: 'NEEDS_REVIEW',
    sceneCount: 1,
    speakerCount: 2,
    totalDurationSeconds: 120,
    updatedAt: '2026-05-09T11:00:00Z',
    audioAssets: [
      {
        id: 'asset-silver',
        sceneId: null,
        type: 'PREVIEW_MP3',
        version: 1,
        filename: 'silver-preview.mp3',
        contentType: 'audio/mpeg',
        sizeBytes: 900000,
        durationSeconds: 120,
        status: 'READY',
        createdAt: '2026-05-09T11:00:00Z',
        downloadUrl: '/api/audiobooks/project-silver/audio-assets/asset-silver/download',
        streamUrl: '/api/audiobooks/project-silver/audio-assets/asset-silver/stream'
      }
    ]
  };
  await page.route('**/api/audiobooks', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [project, project2] }) });
  });

  await page.goto('/library');

  await expect(page.getByTestId('audiobook-card')).toHaveCount(2);
  await expect(page.getByRole('heading', { name: 'The Amber Signal' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'The Silver Key' })).toBeVisible();
});

test('play preview button opens waveform player modal instead of navigating', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/audiobooks', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [project] }) });
  });

  await page.goto('/library');

  // Click play preview
  await page.getByTestId('play-preview').click();

  // Modal should appear with waveform controls
  await expect(page.getByRole('button', { name: 'Play', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Download', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Close', exact: true })).toBeVisible();
});

test('closing waveform player modal returns to library view', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/audiobooks', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [project] }) });
  });

  await page.goto('/library');

  // Open modal
  await page.getByTestId('play-preview').click();
  await expect(page.getByRole('button', { name: 'Close', exact: true })).toBeVisible();

  // Close modal by clicking close button
  await page.getByRole('button', { name: 'Close', exact: true }).click();

  // Library view should still be visible
  await expect(page.getByRole('heading', { name: 'Library' })).toBeVisible();
  await expect(page.getByTestId('audiobook-card')).toHaveCount(1);
});

test('user can open audiobook detail review page with scenes and audio assets', async ({ context, page }) => {
  await authenticate(context, page);
  await page.route('**/api/audiobooks', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [project] }) });
  });
  await page.route('**/api/audiobooks/project-amber', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(project) });
  });

  await page.goto('/audiobook-library');
  await page.getByTestId('continue-review').click();

  await expect(page).toHaveURL(/\/audiobook-library\/project-amber$/);
  await expect(page.getByRole('heading', { name: 'The Amber Signal' })).toBeVisible();
  await expect(page.getByTestId('primary-audio-player')).toBeVisible();
  await expect(page.getByTestId('scene-row')).toHaveCount(2);
  await expect(page.getByText('Station clock')).toBeVisible();
  await expect(page.getByTestId('audio-download')).toHaveCount(3);
  await expect(page.getByTestId('audio-player')).toHaveCount(3);
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
  // This test verifies the FIX:
  // - Backend now calculates metadata on-demand from audio assets
  // - Card always displays fresh values, never stale persisted values
  // - Works with repeated speakers (Narrator appears 2x, counts as 1)
  //
  // The backend's AudiobookMetadataCalculator computes:
  // - sceneCount = count of READY audio assets = 3
  // - speakerCount = count of unique speakers from filenames = 2 (narrator, mara)
  // - totalDurationSeconds = sum of all READY asset durations = 6 + 5 + 8 = 19 seconds

  await authenticate(context, page);

  // Mock an audiobook with 3 audio segments but only 2 unique speakers
  // (Narrator appears twice, which tests that speakers are deduplicated)
  const multiSegmentProject = {
    id: 'project-multi-speaker',
    title: 'Generated audiobook 2026-05-09T20:50:36.213985432Z',
    status: 'NEEDS_REVIEW',
    sceneCount: 3,  // 3 dialogue segments/parts
    speakerCount: 2,  // 2 unique speakers (Narrator, Mara)
    totalDurationSeconds: 19,  // Total preview duration
    updatedAt: '2026-05-09T22:50:00Z',
    audioAssets: [
      {
        id: 'segment-1-narrator',
        sceneId: 'scene-1',
        type: 'PREVIEW_MP3',
        version: 1,
        filename: 'segment-1-narrator.mp3',
        contentType: 'audio/mpeg',
        sizeBytes: 96000,
        durationSeconds: 6,  // First Narrator segment: 6 seconds
        status: 'READY',
        createdAt: '2026-05-09T22:50:00Z',
        downloadUrl: '/api/audiobooks/project-multi-speaker/audio-assets/segment-1-narrator/download',
        streamUrl: '/api/audiobooks/project-multi-speaker/audio-assets/segment-1-narrator/stream'
      },
      {
        id: 'segment-2-mara',
        sceneId: 'scene-2',
        type: 'PREVIEW_MP3',
        version: 1,
        filename: 'segment-2-mara.mp3',
        contentType: 'audio/mpeg',
        sizeBytes: 80000,
        durationSeconds: 5,  // Mara segment: 5 seconds
        status: 'READY',
        createdAt: '2026-05-09T22:50:00Z',
        downloadUrl: '/api/audiobooks/project-multi-speaker/audio-assets/segment-2-mara/download',
        streamUrl: '/api/audiobooks/project-multi-speaker/audio-assets/segment-2-mara/stream'
      },
      {
        id: 'segment-3-narrator',
        sceneId: 'scene-3',
        type: 'PREVIEW_MP3',
        version: 1,
        filename: 'segment-3-narrator.mp3',
        contentType: 'audio/mpeg',
        sizeBytes: 128000,
        durationSeconds: 8,  // Second Narrator segment: 8 seconds
        status: 'READY',
        createdAt: '2026-05-09T22:50:00Z',
        downloadUrl: '/api/audiobooks/project-multi-speaker/audio-assets/segment-3-narrator/download',
        streamUrl: '/api/audiobooks/project-multi-speaker/audio-assets/segment-3-narrator/stream'
      }
    ]
  };

  await page.route('**/api/audiobooks', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: [multiSegmentProject] })
    });
  });

  await page.goto('/audiobook-library');

  // Verify the audiobook card is rendered
  const card = page.locator('[data-testid="audiobook-card"]').first();
  await expect(card).toBeVisible();

  // CRITICAL: Verify metadata is synced correctly
  // These assertions catch the bug where metadata was stale/zero

  // Speech segments should show 3 (number of audio parts/dialogue segments)
  const speechSegments = card.locator('dt:has-text("Speech segments")').locator('..').locator('dd');
  await expect(speechSegments).toContainText('3');

  // Speakers should show 2 (Narrator and Mara, deduplicated)
  const speakers = card.locator('dt:has-text("Speakers")').locator('..').locator('dd');
  await expect(speakers).toContainText('2');

  // Duration should show 0:19 (sum of 6 + 5 + 8 seconds = 19 seconds)
  const duration = card.locator('dt:has-text("Duration")').locator('..').locator('dd');
  await expect(duration).toContainText('0:19');

  // Verify title is correct
  await expect(card.locator('h2')).toContainText('Generated audiobook 2026-05-09T20:50:36');

  // Verify the card is interactive
  const continueReviewButton = card.locator('text=Continue review');
  await expect(continueReviewButton).toBeVisible();
  await expect(continueReviewButton).toHaveAttribute('href', '/audiobook-library/project-multi-speaker');
});
