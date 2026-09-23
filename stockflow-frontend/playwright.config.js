import { defineConfig, devices } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const external = Boolean(process.env.E2E_BASE_URL);
if (external && (process.env.E2E_ALLOW_WRITES !== 'true' || !process.env.E2E_USERNAME || !process.env.E2E_PASSWORD))
  throw new Error('External E2E requires E2E_ALLOW_WRITES=true and credentials for a disposable demo database.');
if (!external) {
  process.env.E2E_ALLOW_WRITES = 'true';
  process.env.E2E_USERNAME = 'e2e.admin';
  process.env.E2E_PASSWORD ||= randomBytes(24).toString('base64');
}

export default defineConfig({
  testDir: './e2e', fullyParallel: false, workers: 1, retries: 0, timeout: 120_000,
  expect: { timeout: 15_000 }, forbidOnly: Boolean(process.env.CI),
  outputDir: './test-results', reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:4173',
    ...devices['Desktop Chrome'], channel: process.env.E2E_BROWSER_CHANNEL || undefined,
    trace: 'off', screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium' }],
  webServer: external ? undefined : [
    { name: 'Isolated backend', cwd: path.join(root, 'stockflow-backend'),
      command: `${process.platform === 'win32' ? 'mvnw.cmd' : 'sh ./mvnw'} -B "-Dmaven.repo.local=${path.join(root, '.tools/repository')}" -Dspring-boot.run.main-class=com.stockflow.E2eApplication spring-boot:test-run`,
      env: { ...process.env, MAVEN_USER_HOME: path.join(root, '.tools/maven') },
      wait: { stdout: /STOCKFLOW_E2E_READY/ }, timeout: 180_000, reuseExistingServer: false },
    { name: 'Frontend', command: 'npm run dev -- --host 127.0.0.1 --port 4173',
      env: { ...process.env, VITE_API_BASE_URL: 'http://127.0.0.1:18080' },
      url: 'http://127.0.0.1:4173', reuseExistingServer: false }
  ]
});
