import { expect, test } from '@playwright/test'

test('authenticates, persists a manual knowledge tree, rejects cycles and logs out', async ({
  page,
  context,
}, testInfo) => {
  const email = process.env.E2E_EMAIL
  const password = process.env.E2E_PASSWORD
  expect(
    email,
    'Set E2E_EMAIL for an isolated acceptance administrator',
  ).toBeTruthy()
  expect(
    password,
    'Set E2E_PASSWORD locally; never put a real password in test source',
  ).toBeTruthy()
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  await page.goto('/skills')
  await expect(page).toHaveURL(/\/login$/)
  await page.screenshot({
    path: testInfo.outputPath('login.png'),
    fullPage: true,
  })
  await page.getByLabel('邮箱', { exact: true }).fill(email!)
  await page.getByLabel('密码', { exact: true }).fill(password!)
  const skillsLoaded = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/skills?') &&
      response.request().method() === 'GET' &&
      response.status() === 200,
  )
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/skills$/)
  await skillsLoaded
  await page.getByRole('link', { name: '生成任务', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(
    '生成任务记录',
  )
  await expect(page.getByText('暂无生成任务。', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '刷新任务', exact: true }).click()
  await expect(page.getByText('暂无生成任务。', { exact: true })).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({
    path: testInfo.outputPath('generation-jobs.png'),
    fullPage: true,
  })
  await page.getByRole('link', { name: '技能空间', exact: true }).click()
  const searchButton = await page
    .getByRole('button', { name: '搜索', exact: true })
    .boundingBox()
  const searchInput = await page
    .getByLabel('搜索技能', { exact: true })
    .boundingBox()
  expect(searchButton).not.toBeNull()
  expect(searchInput).not.toBeNull()
  expect(searchButton!.height).toBeLessThanOrEqual(searchInput!.height + 4)
  await page.screenshot({ path: testInfo.outputPath('skills.png') })
  const cookie = (await context.cookies()).find(
    (cookie) => cookie.name === 'SCARLET_SESSION',
  )
  expect(cookie?.httpOnly).toBe(true)
  expect(cookie?.sameSite).toBe('Lax')
  const skill = `Java 后端 ${testInfo.project.name} ${Date.now()}`
  await page.getByRole('button', { name: '新建技能', exact: true }).click()
  await expect(page.getByLabel('技能名称', { exact: true })).toBeFocused()
  await page.getByLabel('技能名称', { exact: true }).fill(skill)
  await page.getByLabel('分类', { exact: true }).fill('工程验收')
  await page
    .getByLabel('描述与学习目标')
    .fill('仅用于自动化验收，验证手动知识树持久化。')
  await page.getByRole('button', { name: '创建技能', exact: true }).click()
  await page.getByRole('link', { name: skill, exact: true }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(skill)
  for (const [title, parent] of [
    ['Java SE', ''],
    ['集合框架', 'Java SE'],
    ['线程与并发', 'Java SE'],
  ]) {
    await page.getByRole('button', { name: '添加节点', exact: true }).click()
    await expect(page.getByLabel('节点名称', { exact: true })).toBeFocused()
    await page.getByLabel('节点名称', { exact: true }).fill(title!)
    if (parent)
      await page
        .getByLabel('父节点', { exact: true })
        .selectOption({ label: parent })
    await page
      .getByRole('button', { name: '添加到知识树', exact: true })
      .click()
    await expect(
      page.getByRole('button', { name: `编辑节点 ${title}`, exact: true }),
    ).toBeVisible()
  }
  for (const [source, target] of [
    ['Java SE', '集合框架'],
    ['集合框架', '线程与并发'],
  ]) {
    await page.getByLabel('起点 / 先修节点').selectOption({ label: source })
    await page.getByLabel('终点 / 后继节点').selectOption({ label: target })
    await page.getByRole('button', { name: '建立关系', exact: true }).click()
  }
  await expect(page.locator('.relation-list li')).toHaveCount(2)
  await page.getByLabel('起点 / 先修节点').selectOption({ label: '线程与并发' })
  await page.getByLabel('终点 / 后继节点').selectOption({ label: 'Java SE' })
  await page.getByRole('button', { name: '建立关系', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('先修依赖不能形成环')
  await page.reload()
  await expect(page.locator('.node-row')).toHaveCount(3)
  await expect(page.locator('.relation-list li')).toHaveCount(2)
  await page
    .getByRole('button', { name: '编辑节点 集合框架', exact: true })
    .click()
  await expect(page.getByLabel('节点名称', { exact: true })).toBeFocused()
  await page.getByLabel('节点名称', { exact: true }).fill('集合与泛型')
  await page.getByRole('button', { name: '保存节点定义', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '编辑节点 集合与泛型', exact: true }),
  ).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({
    path: testInfo.outputPath('manual-knowledge.png'),
    fullPage: true,
  })
  await page
    .getByRole('link', { name: '草案与版本历史 →', exact: true })
    .click()
  await expect(
    page.getByText('生成尚未启用，可继续使用手动草案。', { exact: true }),
  ).toBeVisible()
  await page
    .getByRole('button', { name: '从当前图谱创建草案', exact: true })
    .click()
  await page.getByLabel('新节点名称', { exact: true }).fill('草案实践节点')
  await page.getByRole('button', { name: '添加草案节点', exact: true }).click()
  await page.getByRole('button', { name: '撤销', exact: true }).click()
  await page.getByRole('button', { name: '重做', exact: true }).click()
  await page.getByRole('button', { name: '保存草案', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('草案已保存')
  await page
    .getByRole('button', { name: '校验与发布预览', exact: true })
    .click()
  await expect(page.getByLabel('发布预览')).toContainText(
    '新增节点：草案实践节点',
  )
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '确认发布版本', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('版本已发布')
  await page.getByRole('button', { name: '查看版本 1', exact: true }).click()
  await expect(page.getByLabel('历史版本快照')).toContainText('草案实践节点')
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({
    path: testInfo.outputPath('draft-published.png'),
    fullPage: true,
  })
  await page.getByRole('link', { name: '← 返回知识树', exact: true }).click()
  await expect(page.locator('.node-row')).toHaveCount(4)
  await expect(
    page.getByText('此图谱已发布，请通过草案审核修改。', { exact: true }),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: '添加节点', exact: true }),
  ).toBeDisabled()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.reload()
  await page.goto('/skills')
  await expect(page).toHaveURL(/\/login$/)
  expect(pageErrors).toEqual([])
})
