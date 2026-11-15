export class ApiProblem extends Error {
  constructor(
    public status: number,
    message: string,
    public requestId?: string,
  ) {
    super(message)
  }
}

export async function api<T>(
  path: string,
  method = 'GET',
  body?: unknown,
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  try {
    const submittedBody = body === undefined ? undefined : JSON.stringify(body)
    if (method !== 'GET') {
      const response = await fetch('/api/v1/auth/csrf', {
        credentials: 'same-origin',
        signal: AbortSignal.timeout(10000),
      })
      if (!response.ok)
        throw new ApiProblem(
          response.status,
          '无法取得安全凭证，请刷新后重试。',
        )
      const csrf = (await response.json()) as {
        headerName: string
        token: string
      }
      headers[csrf.headerName] = csrf.token
    }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    const response = await fetch('/api/v1' + path, {
      method,
      headers,
      credentials: 'same-origin',
      signal: AbortSignal.timeout(15000),
      body: submittedBody,
    })
    if (!response.ok) {
      const failure = (await response.json().catch(() => ({}))) as {
        message?: string
        requestId?: string
      }
      if (response.status === 401 && path !== '/auth/login')
        window.dispatchEvent(new Event('session-expired'))
      throw new ApiProblem(
        response.status,
        failure.message ?? '请求失败，请稍后重试。',
        failure.requestId,
      )
    }
    return response.status === 204
      ? (undefined as T)
      : ((await response.json()) as T)
  } catch (error) {
    if (error instanceof ApiProblem) throw error
    throw new ApiProblem(0, '无法连接服务，请检查网络后重试。')
  }
}

export function errorMessage(error: unknown) {
  if (error instanceof ApiProblem)
    return (
      error.message + (error.requestId ? `（请求编号 ${error.requestId}）` : '')
    )
  return error instanceof Error ? error.message : '操作失败，请重试。'
}
