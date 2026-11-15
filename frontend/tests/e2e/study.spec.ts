import { expect, test } from '@playwright/test'

test('learns a route without AI, resumes timer and notes, and preserves route history', async ({
  page,
}, testInfo) => {
  test.setTimeout(90000)
  const email = process.env.E2E_EMAIL!,
    password = process.env.E2E_PASSWORD!
  expect(email).toBeTruthy()
  expect(password).toBeTruthy()
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/learn')
  await page.getByLabel('邮箱', { exact: true }).fill(email)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/skills$/)
  // Prepare a real manual catalog over authenticated HTTP; the P3 journey uses UI forms.
  const suffix = `${testInfo.project.name}-${Date.now()}`
  const fixture = await page.evaluate(async (suffix) => {
    const write = async (path: string, body: unknown) => {
      const csrf = await (await fetch('/api/v1/auth/csrf')).json()
      const response = await fetch('/api/v1' + path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify(body),
      })
      if (!response.ok) throw new Error('Catalog fixture failed')
      return response.json()
    }
    const skill = await write('/skills', {
      name: `Study ${suffix}`,
      description: 'Disposable browser acceptance',
      category: '',
      status: 'ACTIVE',
    })
    const node = await write('/knowledge/nodes', {
      title: `基础操作 ${suffix}`,
      description: '学习界面与基础操作',
      nodeType: 'CONCEPT',
      status: 'ACTIVE',
    })
    await write(`/skills/${skill.id}/nodes`, {
      nodeId: node.id,
      parentNodeId: null,
      displayOrder: 0,
    })
    return { skill: skill.id, node: node.id, nodeTitle: node.title }
  }, suffix)
  const goal = `入门目标 ${suffix}`,
    route = `基础路线 ${suffix}`
  await page.getByRole('link', { name: '学习路线', exact: true }).click()
  await page.getByLabel('目标技能', { exact: true }).selectOption(fixture.skill)
  await page.getByLabel('目标名称', { exact: true }).fill(goal)
  await page.getByLabel('预期成果', { exact: true }).fill('独立完成一个小作品')
  await page.getByRole('button', { name: '保存目标', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('目标已保存。')
  await page.getByLabel('路线技能', { exact: true }).selectOption(fixture.skill)
  await page
    .getByLabel('关联目标', { exact: true })
    .selectOption({ label: goal })
  await page.getByLabel('路线名称', { exact: true }).fill(route)
  await page
    .getByRole('button', { name: '添加阶段 1 的节点', exact: true })
    .click()
  await page
    .getByLabel('阶段 1 节点 1', { exact: true })
    .selectOption(fixture.node)
  await page.getByRole('button', { name: '保存路线版本', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('路线版本 1 已保存')
  await page
    .getByRole('button', { name: `选择学习 ${route} v1`, exact: true })
    .click()
  await expect(page).toHaveURL(/\/learn$/)
  await expect(
    page.getByText('必修已学 0 / 1。状态是自报学习进度。', { exact: true }),
  ).toBeVisible()
  await page
    .getByRole('button', {
      name: `建立任务：${fixture.nodeTitle}`,
      exact: true,
    })
    .click()
  await expect(page).toHaveURL(/\/study\//)
  const taskUrl = page.url()
  await page.getByRole('button', { name: '开始计时', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '暂停计时', exact: true }),
  ).toBeVisible()
  await page
    .getByLabel('本次思考与学习记录', { exact: true })
    .fill('已经掌握基础操作。<script>只是笔记文本</script>')
  await page.getByRole('button', { name: '保存笔记', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('笔记已保存。')
  await page.getByLabel('资料名称', { exact: true }).fill('参考资料')
  await page
    .getByLabel('资料链接', { exact: true })
    .fill('https://example.test/reference')
  await page.getByRole('button', { name: '保存资料', exact: true }).click()
  await expect(
    page.getByRole('link', { name: '参考资料', exact: true }),
  ).toHaveAttribute('rel', 'noopener noreferrer')
  await page.getByRole('button', { name: '暂停计时', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '继续计时', exact: true }),
  ).toBeVisible()
  await page.screenshot({
    path: testInfo.outputPath('study-paused.png'),
    fullPage: true,
  })
  await page.reload()
  await expect(
    page.getByLabel('本次思考与学习记录', { exact: true }),
  ).toHaveValue('已经掌握基础操作。<script>只是笔记文本</script>')
  await expect(
    page.getByRole('button', { name: '继续计时', exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.getByLabel('邮箱', { exact: true }).fill(email)
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/skills$/)
  await page.getByRole('link', { name: '学习首页', exact: true }).click()
  await page.getByRole('link', { name: '恢复学习工作台', exact: true }).click()
  await expect(page).toHaveURL(taskUrl)
  await expect(
    page.getByLabel('本次思考与学习记录', { exact: true }),
  ).toHaveValue('已经掌握基础操作。<script>只是笔记文本</script>')
  await page.getByRole('button', { name: '继续计时', exact: true }).click()
  await page.getByRole('button', { name: '结束本次学习', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '开始计时', exact: true }),
  ).toBeVisible()
  await page.getByLabel('任务状态', { exact: true }).selectOption('DONE')
  await page.getByRole('button', { name: '保存任务安排', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('任务已保存。')
  await page
    .getByRole('button', { name: '标记路线节点已学', exact: true })
    .click()
  await expect(page.getByRole('status')).toContainText('已记录自报学习完成')
  await page.getByRole('link', { name: '学习路线', exact: true }).click()
  await page
    .getByRole('button', { name: `查看并修订 ${route} v1`, exact: true })
    .click()
  await page.getByLabel('阶段 1 名称', { exact: true }).fill('修订后的阶段')
  await page.getByRole('button', { name: '保存路线版本', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('路线版本 2 已保存')
  await page.goto(taskUrl)
  await expect(
    page.getByText(new RegExp(`路线：${route} · v1 / 基础阶段`)),
  ).toBeVisible()
  await expect(page.getByLabel('任务状态', { exact: true })).toHaveValue('DONE')
  await expect(page.getByText(/已结束$/)).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy()
  expect(errors).toEqual([])
  await page.screenshot({
    path: testInfo.outputPath('study-complete.png'),
    fullPage: true,
  })
})
