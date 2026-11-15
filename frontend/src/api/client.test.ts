import { afterEach, expect, it, vi } from 'vitest'
import { api, ApiProblem } from './client'
afterEach(() => vi.unstubAllGlobals())

it('freezes submitted values while the CSRF credential is being fetched', async () => {
  let respond!: (response: Response) => void
  const fetcher = vi
    .fn()
    .mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          respond = resolve
        }),
    )
    .mockResolvedValueOnce(new Response('{}'))
  vi.stubGlobal('fetch', fetcher)
  const body = { name: 'submitted' }
  const pending = api('/skills', 'POST', body)
  body.name = 'edited after submit'
  respond(
    new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test' })),
  )
  await pending
  expect(fetcher.mock.calls[1]?.[1].body).toBe(
    JSON.stringify({ name: 'submitted' }),
  )
})

it('obtains a session CSRF token before a write and uses same-origin cookies', async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-token' }),
      ),
    )
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ id: 'persisted-id' }), { status: 201 }),
    )
  vi.stubGlobal('fetch', fetcher)
  expect(await api('/skills', 'POST', { name: 'Java' })).toEqual({
    id: 'persisted-id',
  })
  expect(fetcher.mock.calls[0]?.[0]).toBe('/api/v1/auth/csrf')
  expect(fetcher.mock.calls[1]?.[1]).toMatchObject({
    credentials: 'same-origin',
    headers: { 'X-CSRF-TOKEN': 'test-token' },
    body: JSON.stringify({ name: 'Java' }),
  })
})
it('does not issue or automatically retry a write when the CSRF request fails', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response('{}', { status: 503 }))
  vi.stubGlobal('fetch', fetcher)
  await expect(api('/skills', 'POST', {})).rejects.toBeInstanceOf(ApiProblem)
  expect(fetcher).toHaveBeenCalledTimes(1)
})
it('retains the safe server error and request ID instead of inventing success', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockResolvedValue(
        new Response(
          JSON.stringify({ message: '资源不存在', requestId: 'correlation' }),
          { status: 404 },
        ),
      ),
  )
  await expect(api('/skills/not-owned')).rejects.toMatchObject({
    status: 404,
    message: '资源不存在',
    requestId: 'correlation',
  })
})
