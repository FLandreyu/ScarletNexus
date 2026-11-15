import { ApiProblem } from './client'
export async function uploadImage(task: string, file: File) {
  const csrfResponse = await fetch('/api/v1/auth/csrf', {
    credentials: 'same-origin',
  })
  if (!csrfResponse.ok) throw new Error('无法取得安全凭证')
  const csrf = await csrfResponse.json(),
    form = new FormData()
  form.append('file', file)
  const response = await fetch(`/api/v1/learning/tasks/${task}/attachments`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: { [csrf.headerName]: csrf.token },
    body: form,
    signal: AbortSignal.timeout(30000),
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new ApiProblem(
      response.status,
      error.message ?? '图片上传失败',
      error.requestId,
    )
  }
  return response.json()
}
export async function streamTutor(
  conversation: string,
  body: unknown,
  key: string,
  signal: AbortSignal,
  event: (name: string, data: unknown) => void,
) {
  const csrfResponse = await fetch('/api/v1/auth/csrf', {
    credentials: 'same-origin',
    signal,
  })
  if (!csrfResponse.ok) throw new Error('无法取得安全凭证')
  const csrf = await csrfResponse.json()
  const response = await fetch(
    `/api/v1/tutor/conversations/${conversation}/stream`,
    {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        [csrf.headerName]: csrf.token,
        'Idempotency-Key': key,
      },
      body: JSON.stringify(body),
      signal,
    },
  )
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new ApiProblem(
      response.status,
      error.message ?? '导师请求失败',
      error.requestId,
    )
  }
  if (!response.body) throw new Error('浏览器不支持流式响应')
  const reader = response.body.getReader(),
    decoder = new TextDecoder()
  let buffer = '',
    complete = false
  try {
    for (;;) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      if (buffer.length > 2000000) throw new Error('响应超过大小限制')
      let split: number
      while ((split = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, split)
        buffer = buffer.slice(split + 2)
        const lines = frame.split('\n')
        const name =
          lines
            .find((l) => l.startsWith('event:'))
            ?.slice(6)
            .trim() ?? 'message'
        const content = lines
          .filter((l) => l.startsWith('data:'))
          .map((l) => l.slice(5).trimStart())
          .join('\n')
        if (content) {
          event(name, JSON.parse(content))
          if (name === 'done' || name === 'snapshot') complete = true
        }
      }
      if (done) break
    }
    if (!complete)
      throw new Error('响应中断，请刷新已保存记录；不会自动重新请求模型。')
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
