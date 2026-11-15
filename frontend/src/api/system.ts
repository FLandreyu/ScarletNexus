export interface SystemStatus {
  service: 'scarlet-nexus'
  status: 'UP'
  database: 'UP'
  schemaVersion: string
  phase: 'P0' | 'P1'
  aiConfigured: boolean
}

export class ApiFailure extends Error {
  constructor(
    message: string,
    public readonly requestId?: string,
  ) {
    super(message)
  }
}

export async function fetchSystemStatus(): Promise<SystemStatus> {
  let response: Response
  try {
    response = await fetch('/api/v1/system/status', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(5000),
    })
  } catch {
    throw new ApiFailure('无法连接服务，请检查后端与数据库后重试。')
  }
  if (!response.ok) {
    throw new ApiFailure(
      response.status === 503
        ? '服务暂不可用，请稍后重试。'
        : '状态查询失败，请稍后重试。',
      response.headers.get('X-Request-ID') ?? undefined,
    )
  }
  let data: Partial<SystemStatus>
  try {
    data = await response.json()
  } catch {
    throw new ApiFailure('服务返回了无法识别的状态。')
  }
  if (
    !data ||
    data.service !== 'scarlet-nexus' ||
    data.status !== 'UP' ||
    data.database !== 'UP' ||
    typeof data.schemaVersion !== 'string' ||
    !['P0', 'P1'].includes(data.phase ?? '') ||
    typeof data.aiConfigured !== 'boolean'
  ) {
    throw new ApiFailure('服务返回了无法识别的状态。')
  }
  return data as SystemStatus
}
