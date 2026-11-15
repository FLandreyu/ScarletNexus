import { expect, test } from '@playwright/test'
test('streams a real local HTTP protocol, preserves history and never retries a failed turn automatically', async ({
  page,
  request,
}, testInfo) => {
  const email = process.env.E2E_EMAIL!,
    password = process.env.E2E_PASSWORD!,
    provider = process.env.E2E_TUTOR_FIXTURE_URL!
  expect(provider).toMatch(/^http:\/\/127\.0\.0\.1:/)
  const before = (await (await request.get(provider + '/calls')).json())
    .calls as number
  await page.goto('/learn')
  await page.getByLabel('邮箱', { exact: true }).fill(email)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/skills$/)
  const task = await page.evaluate(async (suffix) => {
    const write = async (path: string, data: unknown) => {
      const csrf = await (await fetch('/api/v1/auth/csrf')).json()
      const response = await fetch('/api/v1' + path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify(data),
      })
      if (!response.ok) throw new Error('Fixture failed')
      return response.json()
    }
    const skill = await write('/skills', {
      name: `Tutor ${suffix}`,
      description: 'Local protocol only',
      category: '',
      status: 'ACTIVE',
    })
    const node = await write('/knowledge/nodes', {
      title: 'Tutor protocol acceptance',
      description: '',
      nodeType: 'CONCEPT',
      status: 'ACTIVE',
    })
    await write(`/skills/${skill.id}/nodes`, {
      nodeId: node.id,
      displayOrder: 0,
    })
    return write('/learning/tasks', {
      skillId: skill.id,
      nodeId: node.id,
      title: `Tutor ${suffix}`,
      plannedMinutes: 25,
    })
  }, `${testInfo.project.name}-${Date.now()}`)
  await page.goto(`/study/${task.id}`)
  await page.getByRole('button', { name: '新建节点对话', exact: true }).click()
  await page.getByLabel('向导师提问', { exact: true }).fill('success')
  await page
    .getByLabel('确认发送本次请求并占用配置预算', { exact: true })
    .check()
  await page.getByRole('button', { name: '发送新请求', exact: true }).click()
  await expect(page.getByText(/SUCCEEDED · local-stream-fixture/)).toBeVisible()
  await expect(
    page.getByText('测试协议增量：请先说明你的思路。', { exact: true }),
  ).toBeVisible()
  await page.getByLabel('向导师提问', { exact: true }).fill('fail')
  await page
    .getByLabel('确认发送本次请求并占用配置预算', { exact: true })
    .check()
  await page.getByRole('button', { name: '发送新请求', exact: true }).click()
  await expect(page.getByText(/FAILED · local-stream-fixture/)).toBeVisible()
  await expect(page.getByText(/AI_INCOMPLETE_OUTPUT/).first()).toBeVisible()
  await page.reload()
  await expect(page.getByText(/FAILED · local-stream-fixture/)).toBeVisible()
  await expect(page.getByText(/SUCCEEDED · local-stream-fixture/)).toBeVisible()
  expect((await (await request.get(provider + '/calls')).json()).calls).toBe(
    before + 2,
  )
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({
    path: testInfo.outputPath('tutor-protocol.png'),
    fullPage: true,
  })
})
