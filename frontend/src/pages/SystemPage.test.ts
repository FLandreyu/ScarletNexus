import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { nextTick } from 'vue'
import SystemPage from './SystemPage.vue'

const healthy = {
  service: 'scarlet-nexus',
  status: 'UP',
  database: 'UP',
  schemaVersion: '1',
  phase: 'P0',
  aiConfigured: false,
}
function render() {
  return mount(SystemPage, { global: { plugins: [createPinia()] } })
}
afterEach(() => vi.unstubAllGlobals())

describe('system connection page', () => {
  it('shows enabled generation from server configuration', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ ...healthy, aiConfigured: true })),
        ),
    )
    const view = render()
    await flushPromises()
    expect(view.text()).toContain('生成已配置')
    expect(view.text()).not.toContain('生成未启用')
    view.unmount()
  })
  it('reads the real API contract and shows no learning statistics', async () => {
    let respond!: (response: Response) => void
    const fetcher = vi.fn().mockImplementation(
      () =>
        new Promise<Response>((resolve) => {
          respond = resolve
        }),
    )
    vi.stubGlobal('fetch', fetcher)
    const view = render()
    await nextTick()
    expect(view.text()).toContain('正在检查')
    respond(new Response(JSON.stringify(healthy)))
    await flushPromises()
    expect(fetcher.mock.calls[0]?.[0]).toBe('/api/v1/system/status')
    expect(view.text()).toContain('V1')
    expect(view.text()).toContain('生成未启用')
    expect(view.text()).not.toContain('%')
    view.unmount()
  })

  it('shows service errors and recovers only after explicit retry', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        new Response('{}', {
          status: 503,
          headers: { 'X-Request-ID': 'failure-123' },
        }),
      )
      .mockResolvedValueOnce(new Response(JSON.stringify(healthy)))
    vi.stubGlobal('fetch', fetcher)
    const view = render()
    await flushPromises()
    expect(view.get('[role="alert"]').text()).toContain('failure-123')
    expect(view.text()).not.toContain('V1')
    await view.get('button').trigger('click')
    await flushPromises()
    expect(view.find('[role="alert"]').exists()).toBe(false)
    expect(view.text()).toContain('V1')
    view.unmount()
  })

  it('handles network failure and never reports a successful connection', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')))
    const view = render()
    await flushPromises()
    expect(view.get('[role="alert"]').text()).toContain('无法连接')
    expect(view.text()).not.toContain('已连接')
    view.unmount()
  })

  it('rejects an invalid successful response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'UP' }))),
    )
    const view = render()
    await flushPromises()
    expect(view.get('[role="alert"]').text()).toContain('无法识别')
    expect(view.text()).not.toContain('已连接')
    view.unmount()
  })
})
