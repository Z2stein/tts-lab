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
