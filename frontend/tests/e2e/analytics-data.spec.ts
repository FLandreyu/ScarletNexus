import { expect, test } from '@playwright/test'
import { readFile } from 'node:fs/promises'

test('reads real analytics and safely exports and imports a catalog without learning-history overwrite', async ({
  page,
}, testInfo) => {
  test.setTimeout(90000)
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/analytics')
  await page.getByLabel('邮箱', { exact: true }).fill(process.env.E2E_EMAIL!)
  await page.getByLabel('密码', { exact: true }).fill(process.env.E2E_PASSWORD!)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/skills$/)
  const suffix = `${testInfo.project.name}-${Date.now()}`
  const fixture = await page.evaluate(async (suffix) => {
    async function request(method: string, path: string, body?: unknown) {
      const csrf = await (await fetch('/api/v1/auth/csrf')).json()
      const response = await fetch('/api/v1' + path, {
        method,
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token,
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      })
      if (!response.ok) throw new Error(`P6 fixture failed: ${response.status}`)
      return response.json()
    }
    const skill = await request('POST', '/skills', {
        name: `Analytics ${suffix}`,
        description: 'Disposable acceptance',
        category: '',
        status: 'ACTIVE',
      }),
      node = await request('POST', '/knowledge/nodes', {
        title: `可追溯概念 ${suffix}`,
        nodeType: 'CONCEPT',
        description: '',
        status: 'ACTIVE',
      })
    await request('POST', `/skills/${skill.id}/nodes`, {
      nodeId: node.id,
      displayOrder: 0,
    })
    const task = await request('POST', '/learning/tasks', {
      skillId: skill.id,
      nodeId: node.id,
      title: `分析学习 ${suffix}`,
      plannedMinutes: 25,
    })
    const session = await request('POST', '/learning/sessions', {
      taskId: task.id,
    })
    await request('POST', `/learning/sessions/${session.session.id}/actions`, {
      action: 'FINISH',
      revision: 0,
    })
    await request('PATCH', `/learning/tasks/${task.id}`, {
      title: task.title,
      plannedMinutes: 25,
      status: 'DONE',
      revision: 1,
    })
    const question = await request(
        'POST',
        `/practice/tasks/${task.id}/questions`,
        {
          title: 'Recall',
          prompt: '输入CPU',
          questionType: 'EXACT_TEXT',
          answerKey: 'CPU',
          hintText: '三个字母',
        },
      ),
      attempt = await request('POST', `/practice/tasks/${task.id}/attempts`, {
        questionId: question.id,
        content: 'CPU',
        declaredIndependent: true,
        attachmentIds: [],
      }),
      review = await request('POST', `/practice/attempts/${attempt.id}/check`),
      evidence = await request(
        'POST',
        `/practice/reviews/${review.id}/evidence`,
        { confirmed: true },
      )
    for (const type of ['RULE', 'SELF'])
      await request('POST', `/practice/tasks/${task.id}/assessments`, {
        assessorType: type,
        level: 4,
        reason: '两种来源分别呈现',
        evidenceIds: [evidence.id],
      })
    return {
      skill: skill.id,
      node: node.id,
      task: task.id,
      name: skill.name,
      evidence: evidence.id,
    }
  }, suffix)
  await page.goto('/analytics')
  await page.getByLabel('统计技能', { exact: true }).selectOption(fixture.skill)
  await page.getByRole('button', { name: '刷新分析', exact: true }).click()
  await expect(
    page
      .getByRole('heading', { name: '任务完成率', exact: true })
      .locator('..'),
  ).toContainText('100.0%')
  await expect(
    page
      .getByRole('heading', { name: '独立正确率（规则）', exact: true })
      .locator('..'),
  ).toContainText('100.0%')
  await expect(
    page.getByText('本人自评 · 可信度', { exact: false }),
  ).toBeVisible()
  await expect(
    page.getByText('规则评估 · 可信度', { exact: false }),
  ).toBeVisible()
  await expect(
    page
      .getByRole('img', {
        name: '已结束学习时段的分钟统计，详细数值见下方表格',
        exact: true,
      })
      .locator('svg'),
  ).toBeVisible()
  await page.getByText('追溯评估与证据标识', { exact: true }).first().click()
  await page
    .getByRole('button', {
      name: `查看证据 ${fixture.evidence.slice(0, 8)}`,
      exact: true,
    })
    .first()
    .click()
  await expect(
    page.getByRole('link', { name: '前往原练习与证据', exact: true }),
  ).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({
    path: testInfo.outputPath('analytics-evidence.png'),
    fullPage: true,
  })
  await page.goto('/data')
  await page
    .getByLabel('知识目录范围', { exact: true })
    .selectOption(fixture.skill)
  const download = page.waitForEvent('download')
  await page
    .getByRole('button', { name: '下载知识目录JSON', exact: true })
    .click()
  const file = await download
  expect(file.suggestedFilename()).toBe('scarlet-nexus-catalog.json')
  const content = await readFile((await file.path())!, 'utf8'),
    catalog = JSON.parse(content)
  expect(catalog.format).toBe('scarlet-nexus-catalog/v1')
  expect(catalog.skills).toHaveLength(1)
  expect(catalog.nodes[0].id).toBe(fixture.node)
  const snapshotDownload = page.waitForEvent('download')
  await page
    .getByRole('button', { name: '下载本人学习快照JSON', exact: true })
    .click()
  const snapshot = JSON.parse(
    await readFile((await (await snapshotDownload).path())!, 'utf8'),
  )
  expect(snapshot.format).toBe('scarlet-nexus-learning-snapshot/v1')
  expect(
    snapshot.tables.learning_tasks.some(
      (t: { id: string }) => t.id === fixture.task,
    ),
  ).toBeTruthy()
  expect(JSON.stringify(snapshot)).not.toContain('password_hash')
  expect(JSON.stringify(snapshot)).not.toContain('storage_key')
  const before = await page.evaluate(
    async () =>
      (await (await fetch('/api/v1/skills?status=ALL&size=1')).json()).total,
  )
  await page
    .getByLabel('选择目录JSON（最多2MiB）', { exact: true })
    .setInputFiles({
      name: 'catalog.json',
      mimeType: 'application/json',
      buffer: Buffer.from(content),
    })
  await page
    .getByRole('button', { name: '校验并预览目录', exact: true })
    .click()
  await expect(page.getByRole('status')).toContainText(
    '现有技能和学习记录未改变',
  )
  await expect(
    page.getByRole('button', { name: '确认新增目录', exact: true }),
  ).toBeDisabled()
  expect(
    await page.evaluate(
      async () =>
        (await (await fetch('/api/v1/skills?status=ALL&size=1')).json()).total,
    ),
  ).toBe(before)
  await page.reload()
  await expect(
    page.getByRole('heading', { name: '目录导入预览 · DRAFT', exact: true }),
  ).toBeVisible()
  await page
    .getByLabel('确认仅新增目录，不覆盖原技能或导入学习记录', { exact: true })
    .check()
  await page.getByRole('button', { name: '确认新增目录', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('知识目录已新增导入')
  await expect(
    page.getByRole('heading', {
      name: '目录导入预览 · CONFIRMED',
      exact: true,
    }),
  ).toBeVisible()
  await expect(page.getByRole('link', { name: /查看导入的技能/ })).toHaveCount(
    1,
  )
  expect(
    await page.evaluate(
      async () =>
        (await (await fetch('/api/v1/skills?status=ALL&size=1')).json()).total,
    ),
  ).toBe(before + 1)
  expect(
    await page.evaluate(
      async (task) =>
        (await (await fetch(`/api/v1/learning/tasks/${task}`)).json()).status,
      fixture.task,
    ),
  ).toBe('DONE')
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy()
  expect(errors).toEqual([])
  await page.screenshot({
    path: testInfo.outputPath('catalog-confirmed.png'),
    fullPage: true,
  })
})
