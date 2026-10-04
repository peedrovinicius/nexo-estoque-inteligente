import { expect, test } from '@playwright/test';

test('home renders core product experience', async ({ page }) => {
  const url = process.env.NEXO_BASE_URL || 'http://127.0.0.1:4173';
  await page.goto(url, { waitUntil: 'networkidle' });

  await expect(page.getByRole('heading', { name: /O estoque/i })).toBeVisible();
  await expect(page.getByText('Produto', { exact: true })).toBeVisible();
  await expect(page.getByText('Recursos', { exact: true })).toBeVisible();
  await expect(page.getByText(/Sistema inteligente de gestão de estoque/i).first()).toBeVisible();
});
