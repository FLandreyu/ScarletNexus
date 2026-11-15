import { expect, test } from '@playwright/test'

test('shows actual backend and database state on desktop and mobile', async ({
  page,
  request,
}, testInfo) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  const response = await request.get('/api/v1/system/status')
  expect(response.ok()).toBeTruthy()
  expect(await response.json()).toMatchObject({
    database: 'UP',
    schemaVersion: '5',
    aiConfigured: false,
  })
  await page.goto('/system')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    '系统连接状态',
  )
  await expect(page.getByText('已连接', { exact: true })).toHaveCount(2)
  await expect(page.getByText('V5', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '刷新状态' }).click()
  await expect(page.getByText('已连接', { exact: true })).toHaveCount(2)
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy()
  expect(errors).toEqual([])
  await page.screenshot({
    path: testInfo.outputPath('system-status.png'),
    fullPage: true,
  })
})
