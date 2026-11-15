import { afterEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import PlannerPage from './PlannerPage.vue'
import { zonedDate } from '../types/planner'
afterEach(() => vi.unstubAllGlobals())
it('requires explicit confirmation and retains the draft after an API conflict', async () => {
  const requests: { path: string; method: string }[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      requests.push({ path: input, method })
      let data: unknown = { items: [], total: 0, page: 0, size: 100 },
        code = 200
      if (input.includes('/auth/csrf'))
        data = { token: 'csrf', headerName: 'X-CSRF-TOKEN' }
      else if (input.includes('/skills?'))
        data = {
          items: [{ id: 'skill', name: 'Java', status: 'ACTIVE' }],
          total: 1,
          page: 0,
          size: 100,
        }
      else if (input.includes('/planner/days/') && !input.includes('/history'))
        data = { planDate: '2026-11-16', revision: 0, activePlan: null }
      else if (input.endsWith('/planner/drafts') && method === 'POST')
        data = {
          draft: {
            id: 'draft',
            planDate: '2026-11-16',
            timeZone: 'Asia/Shanghai',
            budgetMinutes: 90,
            revision: 0,
            state: 'DRAFT',
          },
          skillLimits: [{ skillId: 'skill', maxMinutes: 30 }],
          items: [
            {
              id: 'item',
              kind: 'LEARN',
              skillId: 'skill',
              title: 'Practice Java',
              minutes: 25,
              eligible: true,
              selected: true,
              reason: 'Rule reason',
            },
          ],
          selectedMinutes: 25,
          sourceChanged: false,
        }
      else if (input.endsWith('/confirm')) {
        data = {
          code: 'PLANNING_CONFLICT',
          message: '草案过期',
          requestId: 'planning-conflict',
        }
        code = 409
      }
      return new Response(JSON.stringify(data), { status: code })
    }),
  )
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: PlannerPage }],
  })
  await router.push('/')
  await router.isReady()
  const view = mount(PlannerPage, { global: { plugins: [router] } })
  await flushPromises()
  await view
    .findAll('label')
    .find((l) => l.text() === 'Java')!
    .get('input')
    .setValue(true)
  await view.get('form').trigger('submit')
  await flushPromises()
  const confirm = view
    .findAll('button')
    .find((b) => b.text() === '确认计划生效')!
  expect(confirm.attributes('disabled')).toBeDefined()
  expect(requests.filter((r) => r.path.endsWith('/confirm'))).toHaveLength(0)
  const checkbox = view
    .findAll('label')
    .find((l) => l.text().includes('我已检查时间预算'))!
    .get('input')
  await checkbox.setValue(true)
  await confirm.trigger('click')
  await flushPromises()
  expect(view.get('[role="alert"]').text()).toContain('草案过期')
  expect(view.text()).toContain('Practice Java')
  expect(requests.filter((r) => r.path.endsWith('/confirm'))).toHaveLength(1)
  view.unmount()
})
it('formats the schedule date in its own time zone across midnight', () => {
  expect(zonedDate('2026-11-15T18:00:00Z', 'Asia/Shanghai')).toBe('2026-11-16')
  expect(zonedDate('2026-11-15T18:00:00Z', 'America/Los_Angeles')).toBe(
    '2026-11-15',
  )
})
