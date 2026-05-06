import { test, expect } from '@playwright/test';

test('frontend can reach and process the real backend health response', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('link', { name: 'TTS Lab home' })).toBeVisible();

  const result = await page.evaluate(async () => {
    const response = await fetch('/api/health', {
      headers: { Accept: 'application/json' }
    });
    const bodyText = await response.text();

    let body: unknown;
    try {
      body = JSON.parse(bodyText) as unknown;
    } catch {
      body = bodyText;
    }

    if (!response.ok) {
      return { ok: false, status: response.status, body };
    }

    const status = typeof body === 'object' && body !== null && 'status' in body
      ? String((body as { status: unknown }).status)
      : '';

    const output = document.createElement('output');
    output.dataset['testid'] = 'real-backend-health-status';
    output.textContent = `Backend status: ${status}`;
    document.body.appendChild(output);

    return { ok: true, status: response.status, body, processedStatus: status };
  });

  expect(result, `Expected /api/health to return 200 with {"status":"ok"}; received ${JSON.stringify(result)}`).toEqual({
    ok: true,
    status: 200,
    body: { status: 'ok' },
    processedStatus: 'ok'
  });
  await expect(page.getByTestId('real-backend-health-status')).toHaveText('Backend status: ok');
});
