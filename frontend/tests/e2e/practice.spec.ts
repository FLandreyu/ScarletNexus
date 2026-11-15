import { expect, test } from '@playwright/test'
test('submits proof before hints, confirms self evidence and privately stores images without AI', async ({
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
      if (!response.ok) throw new Error('Real fixture creation failed')
      return response.json()
    }
    const skill = await write('/skills', {
      name: `Practice ${suffix}`,
      description: '',
      category: '',
      status: 'ACTIVE',
    })
    const node = await write('/knowledge/nodes', {
      title: '不等式证明',
      description: '',
      nodeType: 'PRACTICE',
      status: 'ACTIVE',
    })
    await write(`/skills/${skill.id}/nodes`, {
      nodeId: node.id,
      displayOrder: 0,
    })
    return write('/learning/tasks', {
      skillId: skill.id,
      nodeId: node.id,
      title: `证明练习 ${suffix}`,
      plannedMinutes: 25,
    })
  }, `${testInfo.project.name}-${Date.now()}`)
  await page.goto(`/practice/${task.id}`)
  await page.getByRole('button', { name: '手动添加题目', exact: true }).click()
  await page.getByLabel('题目名称', { exact: true }).fill('平方非负的应用')
  await page
    .getByLabel('题目内容（支持数学公式）', { exact: true })
    .fill('证明 $a^2+b^2\\geq 2ab$。<img src=x onerror=alert(1)>')
  await page
    .getByLabel('服务端参考答案或解析', { exact: true })
    .fill('私有解析：由 (a-b)^2 非负推出结论。')
  await page
    .getByLabel('预先准备的一步提示', { exact: true })
    .fill('先考虑一个平方。')
  await page.getByRole('button', { name: '保存题目', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText(
    '题目已保存。答案不会随练习读取返回。',
  )
  await expect(page.locator('.katex').first()).toBeVisible()
  await expect(page.locator('img')).toHaveCount(0)
  await expect(
    page.getByText('私有解析：由 (a-b)^2 非负推出结论。', { exact: true }),
  ).toHaveCount(0)
  await expect(
    page.getByRole('button', { name: '查看一步提示', exact: true }),
  ).toHaveCount(0)
  await page
    .getByLabel('独立作答内容', { exact: true })
    .fill('我先展开平方，得到所需的不等式。')
  await page.getByRole('button', { name: '提交本次作答', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText(
    '作答已保存。之后可以选择提示或请求复核。',
  )
  await page.getByRole('button', { name: '查看一步提示', exact: true }).click()
  await expect(
    page.getByText('先考虑一个平方。', { exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: '请求规则比对', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: '不确定 · 规则比对', exact: true }),
  ).toBeVisible()
  await page.getByLabel('复核结论', { exact: true }).selectOption('CORRECT')
  await page
    .getByLabel('复核依据', { exact: true })
    .fill('本人逐步检查展开与移项，记录为自评。')
  await page
    .getByLabel('确认保存本人的复核意见，不视为权威验证', { exact: true })
    .check()
  await page.getByRole('button', { name: '保存复核意见', exact: true }).click()
  const selfReview = page.locator('article').filter({
    has: page.getByRole('heading', { name: '正确 · 人工自评', exact: true }),
  })
  await selfReview
    .getByRole('button', { name: '确认记录为证据', exact: true })
    .click()
  await expect(page.getByRole('status')).toHaveText(
    '证据已确认，能力评估仍需单独保存。',
  )
  const evidence = page.locator('section').filter({
    has: page.getByRole('heading', {
      name: '节点证据与能力历史',
      exact: true,
    }),
  })
  await evidence.getByRole('checkbox').first().check()
  await page
    .getByLabel('评估说明', { exact: true })
    .fill('只有人工自评，规则不应把它当验证通过。')
  await page.getByRole('button', { name: '保存能力评估', exact: true }).click()
  await expect(
    page.getByRole('heading', {
      name: 'L0 · 保守规则 · 低可信度',
      exact: true,
    }),
  ).toBeVisible()
  await page.getByLabel('上传作答图片', { exact: true }).setInputFiles({
    name: 'too-large.png',
    mimeType: 'image/png',
    buffer: Buffer.alloc(5242881),
  })
  await page.getByRole('button', { name: '上传图片', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('图片文件不能超过5MiB')
  const image = await page.evaluate(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 48
    canvas.height = 32
    const ctx = canvas.getContext('2d')!
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, 48, 32)
    return canvas.toDataURL('image/png').split(',')[1]!
  })
  await page.getByLabel('上传作答图片', { exact: true }).setInputFiles({
    name: 'proof.png',
    mimeType: 'image/png',
    buffer: Buffer.from(image, 'base64'),
  })
  await page.getByRole('button', { name: '上传图片', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText(
    '图片已安全处理并保存，未发送给模型。',
  )
  const imageElement = page.getByRole('img', { name: /^作答图片/ }).first()
  await expect(imageElement).toBeVisible()
  const response = await page.request.get(
    (await imageElement.getAttribute('src'))!,
  )
  expect(response.status()).toBe(200)
  expect(response.headers()['content-type']).toBe('image/png')
  expect(response.headers()['cache-control']).toContain('no-store')
  await page
    .getByLabel('独立作答内容', { exact: true })
    .fill('提示后补充的证明和图片。')
  await page
    .getByLabel(/^附图 /)
    .first()
    .check()
  await page.getByRole('button', { name: '提交本次作答', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText(
    '作答已保存。之后可以选择提示或请求复核。',
  )
  await expect(page.getByText(/曾获帮助或未声明独立/)).toBeVisible()
  await expect(
    page.getByText('导师尚未启用。作答、提示、人工复核和学习记录仍可使用。', {
      exact: true,
    }),
  ).toBeVisible()
  await page.reload()
  await page
    .getByRole('button', { name: /^查看作答 / })
    .first()
    .click()
  await expect(
    page.getByText('提示后补充的证明和图片。', { exact: true }),
  ).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy()
  expect(errors).toEqual([])
  await page.screenshot({
    path: testInfo.outputPath('practice-evidence.png'),
    fullPage: true,
  })
})
