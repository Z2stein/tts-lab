import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env['E2E_BASE_URL'] || 'http://127.0.0.1:4200';
const useLocalServers = process.env['E2E_USE_LOCAL_SERVERS'] !== 'false';

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: {
    timeout: 10_000
  },
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'on-first-retry'
  },
  webServer: useLocalServers ? [
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
  ] : undefined,
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
