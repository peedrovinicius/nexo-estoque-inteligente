import { expect, test } from '@playwright/test';

test('production: home, login, read-only demo, dashboard and logout', async ({ page }) => {
  const url = process.env.NEXO_BASE_URL || 'http://127.0.0.1:4173';
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
