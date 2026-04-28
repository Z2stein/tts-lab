import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: {
    timeout: 10_000
  },
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4200',
    trace: 'on-first-retry'
  },
  webServer: [
    {
      command: 'cd ../backend && gradle bootRun',
      url: 'http://127.0.0.1:8080/health',
      reuseExistingServer: true,
      timeout: 120_000
    },
    {
      command: 'npm start',
      url: 'http://127.0.0.1:4200',
      reuseExistingServer: true,
      timeout: 120_000
    }
  ],
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
