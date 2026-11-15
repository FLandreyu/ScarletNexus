import { expect, test } from '@playwright/test'

test('uses the enabled local protocol fixture to generate, review and publish', async ({
  page,
}, testInfo) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/login')
  await page.getByLabel('邮箱', { exact: true }).fill(process.env.E2E_EMAIL!)
  await page.getByLabel('密码', { exact: true }).fill(process.env.E2E_PASSWORD!)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/skills$/)
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json()
  const created = await page.request.post('/api/v1/skills', {
    headers: { 'X-CSRF-TOKEN': csrf.token },
    data: {
      name: `Local protocol ${testInfo.project.name} ${Date.now()}`,
      description: 'Isolated protocol acceptance',
      category: 'test',
      status: 'ACTIVE',
    },
  })
  expect(created.ok()).toBeTruthy()
  const skill = await created.json()
  await page.goto(`/skills/${skill.id}/drafts`)
  await expect(page.getByLabel('学习主题与目标', { exact: true })).toBeVisible()
  await page.getByLabel('学习主题与目标', { exact: true }).fill('success')
  await page
    .getByRole('checkbox', {
      name: '我确认使用已配置模型和预算生成候选草案',
      exact: true,
    })
    .check()
  await page.getByRole('button', { name: '生成候选草案', exact: true }).click()
  await expect(page.getByRole('status')).toContainText(
    '候选草案已生成，尚未发布',
  )
  await expect(
    page.getByLabel('高级 JSON 编辑（节点、位置与关系）'),
  ).toHaveValue(/本地协议验收节点/)
  await page
    .getByRole('button', { name: '校验与发布预览', exact: true })
    .click()
  await expect(page.getByLabel('发布预览')).toContainText(
    '新增节点：本地协议验收节点',
  )
  await page
    .getByRole('checkbox', {
      name: '我已审核节点、关系与跨技能影响，确认发布',
      exact: true,
    })
    .check()
  await page.getByRole('button', { name: '确认发布版本', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('版本已发布')
  await page.getByLabel('学习主题与目标', { exact: true }).fill('invalid')
  await page
    .getByRole('checkbox', {
      name: '我确认使用已配置模型和预算生成候选草案',
      exact: true,
    })
    .check()
  await page.getByRole('button', { name: '生成候选草案', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('AI_INVALID_GRAPH')
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({
    path: testInfo.outputPath('enabled-generation.png'),
    fullPage: true,
  })
  await page
    .getByRole('link', { name: '查看生成任务记录', exact: true })
    .click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    '生成任务记录',
  )
  await expect(
    page.getByText('错误代码：AI_INVALID_GRAPH', { exact: true }).first(),
  ).toBeVisible()
  expect(errors).toEqual([])
})
