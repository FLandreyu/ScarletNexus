import { expect, test } from '@playwright/test'

test('plans three skills within budgets and keeps task adjustments and review results', async ({
  page,
}, testInfo) => {
  test.setTimeout(120000)
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/planner')
  await page.getByLabel('邮箱', { exact: true }).fill(process.env.E2E_EMAIL!)
  await page.getByLabel('密码', { exact: true }).fill(process.env.E2E_PASSWORD!)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/skills$/)
  const suffix = `${testInfo.project.name}-${Date.now()}`
  const fixtures = await page.evaluate(async (suffix) => {
    async function write(path: string, body: unknown) {
      const csrf = await (await fetch('/api/v1/auth/csrf')).json()
      const r = await fetch('/api/v1' + path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify(body),
      })
      if (!r.ok) throw new Error(`P5 fixture failed ${r.status}`)
      return r.json()
    }
    const result: {
      skill: string
      name: string
      task: string
      title: string
    }[] = []
    for (const name of ['11408', 'Java', 'Blender']) {
      const skill = await write('/skills', {
        name: `${name} ${suffix}`,
        description: 'Disposable acceptance',
        category: '',
        status: 'ACTIVE',
      })
      const node = await write('/knowledge/nodes', {
        title: `${name} 基础 ${suffix}`,
        description: '',
        nodeType: 'CONCEPT',
        status: 'ACTIVE',
      })
      await write(`/skills/${skill.id}/nodes`, {
        nodeId: node.id,
        displayOrder: 0,
      })
      const task = await write('/learning/tasks', {
        skillId: skill.id,
        nodeId: node.id,
        title: `${name} 任务 ${suffix}`,
        plannedMinutes: 25,
      })
      result.push({
        skill: skill.id,
        name: skill.name,
        task: task.id,
        title: task.title,
      })
    }
    return result
  }, suffix)
  const date = new Date()
  date.setDate(date.getDate() + 1)
  const tomorrow = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  await page.goto('/planner')
  await expect(
    page.getByLabel(fixtures[0]!.name, { exact: true }),
  ).toBeVisible()
  for (const box of await page.getByRole('checkbox').all()) await box.uncheck()
  for (const f of fixtures) {
    await page.getByLabel(f.name, { exact: true }).check()
    await page.getByLabel(`${f.name}预算`, { exact: true }).fill('25')
  }
  await page.getByLabel('计划日期', { exact: true }).fill(tomorrow)
  await page.getByLabel('时区', { exact: true }).fill('Asia/Shanghai')
  await page.getByLabel('总预算（分钟）', { exact: true }).fill('75')
  await page.getByRole('button', { name: '生成规则草案', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('草案已保存')
  await expect(
    page.getByRole('button', { name: '确认计划生效', exact: true }),
  ).toBeDisabled()
  const initial = await page.evaluate(
    async (date) => (await fetch(`/api/v1/planner/days/${date}`)).json(),
    tomorrow,
  )
  await page
    .getByLabel('我已检查时间预算，确认以此草案替换当天计划', { exact: true })
    .check()
  await page.getByRole('button', { name: '确认计划生效', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('计划已确认生效')
  const planned = await page.evaluate(
    async (date) => (await fetch(`/api/v1/planner/days/${date}`)).json(),
    tomorrow,
  )
  expect(planned.revision).toBe(initial.revision + 1)
  expect(planned.activePlan.plannedMinutes).toBe(75)
  expect(
    new Set(
      planned.activePlan.items.map(
        (i: { current: { skillId: string } }) => i.current.skillId,
      ),
    ).size,
  ).toBe(3)
  expect(
    planned.activePlan.items.every(
      (i: { current: { dueDate: unknown } }) => i.current.dueDate === null,
    ),
  ).toBeTruthy()
  await page.getByRole('button', { name: '生成规则草案', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('草案已保存')
  expect(
    await page.evaluate(
      async (date) => (await fetch(`/api/v1/planner/days/${date}`)).json(),
      tomorrow,
    ),
  ).toEqual(planned)
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({
    path: testInfo.outputPath('three-skill-plan.png'),
    fullPage: true,
  })
  await page
    .getByRole('combobox', { name: '调整任务', exact: true })
    .selectOption(fixtures[0]!.task)
  await page
    .getByRole('combobox', { name: '调整方式', exact: true })
    .selectOption('DEFER')
  await page.getByLabel('新截止日期', { exact: true }).fill('2027-12-31')
  await page
    .getByLabel('调整原因', { exact: true })
    .fill('等待练习资料，保留原记录')
  await page.getByRole('button', { name: '保存任务调整', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('调整已保存')
  await expect(
    page.getByText('等待练习资料，保留原记录', { exact: true }),
  ).toBeVisible()
  await page
    .getByRole('combobox', { name: '调整任务', exact: true })
    .selectOption(fixtures[1]!.task)
  await page
    .getByRole('combobox', { name: '调整方式', exact: true })
    .selectOption('SPLIT')
  await page.getByLabel('子任务 1 标题', { exact: true }).fill('Java 子任务 A')
  await page.getByLabel('子任务 2 标题', { exact: true }).fill('Java 子任务 B')
  await page
    .getByLabel('调整原因', { exact: true })
    .fill('分两次完成，预计总时长不变')
  await page.getByRole('button', { name: '保存任务调整', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('调整已保存')
  await expect(
    page.getByRole('link', { name: '查看拆分任务', exact: true }),
  ).toHaveCount(2)
  await page.goto(`/reviews?task=${fixtures[0]!.task}`)
  await page.getByLabel('首次复习日期', { exact: true }).fill('2026-01-01')
  await page.getByLabel('时区', { exact: true }).fill('Asia/Shanghai')
  await page.getByLabel('预计复习分钟', { exact: true }).fill('10')
  await page.getByLabel('安排原因', { exact: true }).fill('基础概念需要复测')
  await page.getByRole('button', { name: '保存复习安排', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('复习安排已保存')
  const eid = await page.evaluate(async (task) => {
    async function write(path: string, body: unknown) {
      const csrf = await (await fetch('/api/v1/auth/csrf')).json()
      const r = await fetch('/api/v1' + path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token,
          'Idempotency-Key': crypto.randomUUID(),
        },
        body: JSON.stringify(body),
      })
      if (!r.ok) throw new Error(`Retest fixture failed ${r.status}`)
      return r.json()
    }
    const q = await write(`/practice/tasks/${task}/questions`, {
      title: 'Recall check',
      prompt: '输入已学概念名称',
      questionType: 'EXACT_TEXT',
      answerKey: 'CPU',
      hintText: '三个英文字母',
    })
    const a = await write(`/practice/tasks/${task}/attempts`, {
      questionId: q.id,
      content: 'CPU',
      declaredIndependent: true,
      attachmentIds: [],
    })
    const r = await write(`/practice/attempts/${a.id}/check`, null)
    return (
      await write(`/practice/reviews/${r.id}/evidence`, { confirmed: true })
    ).id
  }, fixtures[0]!.task)
  await page
    .locator('article')
    .filter({
      has: page.getByRole('heading', {
        name: `11408 基础 ${suffix}`,
        exact: true,
      }),
    })
    .getByRole('button', { name: '查看调度与复测', exact: true })
    .click()
  await page
    .getByRole('combobox', { name: '本次复测证据', exact: true })
    .selectOption(eid)
  await page
    .getByLabel('复测说明', { exact: true })
    .fill('独立复测正确，按规则延长间隔')
  await page
    .getByRole('button', { name: '确认证据并调度下次复习', exact: true })
    .click()
  await expect(page.getByRole('status')).toContainText('复测已确认')
  await expect(
    page.getByText(/规则v1：CORRECT.*间隔翻倍/).first(),
  ).toBeVisible()
  await expect(
    page.getByText('RETEST · 1 → 2 天', { exact: true }),
  ).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy()
  expect(errors).toEqual([])
  await page.screenshot({
    path: testInfo.outputPath('review-history.png'),
    fullPage: true,
  })
})
