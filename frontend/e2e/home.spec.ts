import { expect, test } from '@playwright/test';

const productionApi = 'https://nexo-estoque-api-production.up.railway.app';

test('production: home, login, read-only demo, dashboard and logout', async ({ page }) => {
  const url = process.env.NEXO_BASE_URL || 'http://127.0.0.1:4173';

  // Pull-request previews run on localhost while the real API keeps a strict
  // production CORS allowlist. Proxy API calls through Playwright rather than
  // weakening production CORS just for CI.
  if (url.startsWith('http://localhost')) {
    await page.route(productionApi + '/**', async route => {
      const requestHeaders = { ...route.request().headers() };
      delete requestHeaders.origin;
      delete requestHeaders.referer;

      const upstream = await route.fetch({ headers: requestHeaders });
      await route.fulfill({
        response: upstream,
        headers: {
          ...upstream.headers(),
          'access-control-allow-origin': 'http://localhost:5173',
          'access-control-allow-methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
          'access-control-allow-headers': 'Authorization, Content-Type, Accept, Idempotency-Key'
        }
      });
    });
  }

  await page.goto(url, { waitUntil: 'networkidle' });

  await expect(page.getByRole('heading', { name: /O estoque/i })).toBeVisible();
  await expect(page.getByText('Produto', { exact: true })).toBeVisible();
  await expect(page.getByText('Recursos', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Entrar no Nexo' })).toBeVisible();

  await page.getByRole('button', { name: 'Entrar na demonstração' }).click();

  await expect(page.locator('.app-shell')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole('note')).toContainText('Modo demonstração');
  await expect(page.getByRole('heading', { name: 'Estado operacional' })).toBeVisible({ timeout: 20_000 });

  await page.getByRole('button', { name: /Sair/i }).click();
  await expect(page.getByRole('heading', { name: 'Entrar no Nexo' })).toBeVisible();
});
