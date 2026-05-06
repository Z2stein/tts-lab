import { test, expect } from '@playwright/test';

test('tts workbench shows speaker voice analysis results', async ({ page }) => {
  await page.route('**/api/projects/tts-workbench/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        speakers: [
          { speakerName: 'Alice', roleDescription: 'Narrator', voiceSuggestion: 'Warm voice' }
        ]
      })
    });
  });

  await page.goto('/tts-workbench');

  await page.getByLabel('Raw dialogue').fill('Alice: Hello');
  await page.getByRole('button', { name: 'Analyze speakers' }).click();

  await expect(page.getByText('Alice')).toBeVisible();
  await expect(page.getByText('Narrator')).toBeVisible();
  await expect(page.getByText('Warm voice')).toBeVisible();
});

test('tts workbench shows UI error when backend responds with an error', async ({ page }) => {
  await page.route('**/api/projects/tts-workbench/speaker-voice-analysis', async (route) => {
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'simulated backend error' })
    });
  });

  await page.goto('/tts-workbench');

  await page.getByLabel('Raw dialogue').fill('Alice: Hello');
  await page.getByRole('button', { name: 'Analyze speakers' }).click();

  await expect(page.getByText('Speaker voice analysis failed.')).toBeVisible();
});
