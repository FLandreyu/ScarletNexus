import { api } from './client'
import type { Page } from '../types/catalog'

// Choice lists use the paginated API without silently omitting older records.
export async function allPages<T>(path: string): Promise<T[]> {
  const result: T[] = []
  for (let page = 0; ; page++) {
    const rows = await api<Page<T>>(
      `${path}${path.includes('?') ? '&' : '?'}page=${page}&size=100`,
    )
    result.push(...rows.items)
    if (result.length >= rows.total || rows.items.length === 0) return result
  }
}
export function duration(seconds: number) {
  const value = Math.max(0, Math.floor(seconds))
  return `${Math.floor(value / 3600)
    .toString()
    .padStart(2, '0')}:${Math.floor((value % 3600) / 60)
    .toString()
    .padStart(2, '0')}:${(value % 60).toString().padStart(2, '0')}`
}
