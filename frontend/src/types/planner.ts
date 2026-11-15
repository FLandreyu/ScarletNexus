import type { Task } from './learning'
export interface SkillLimit {
  skillId: string
  maxMinutes: number
}
export interface Draft {
  id: string
  planDate: string
  timeZone: string
  budgetMinutes: number
  revision: number
  state: string
}
export interface DraftItem {
  id: string
  kind: string
  sourceId: string
  taskId: string | null
  reviewId: string | null
  skillId: string
  title: string
  minutes: number
  eligible: boolean
  selected: boolean
  reason: string
}
export interface DraftView {
  draft: Draft
  skillLimits: SkillLimit[]
  items: DraftItem[]
  selectedMinutes: number
  sourceChanged: boolean
}
export interface Plan {
  id: string
  planDate: string
  timeZone: string
  versionNumber: number
  budgetMinutes: number
  confirmedAt: string
}
export interface PlanView {
  plan: Plan
  skillLimits: SkillLimit[]
  plannedMinutes: number
  items: {
    item: {
      id: string
      taskId: string
      reviewId: string | null
      titleSnapshot: string
      plannedMinutes: number
      reason: string
    }
    current: Task
    changed: boolean
  }[]
}
export interface Day {
  planDate: string
  revision: number
  activePlan: PlanView | null
}
export interface Schedule {
  id: string
  originalTaskId: string
  nodeId: string
  timeZone: string
  nextReviewAt: string
  intervalDays: number
  cycleNumber: number
  revision: number
  status: string
  reason: string
}
export interface ReviewView {
  schedule: Schedule
  title: string
  cycleTaskId: string | null
}
export interface ReviewEvent {
  id: string
  kind: string
  outcome: string | null
  verification: string | null
  independent: boolean | null
  previousDue: string | null
  nextDue: string
  previousInterval: number | null
  nextInterval: number
  reason: string
  createdAt: string
}
export interface Evidence {
  id: string
  nodeId: string
  outcome: string
  verification: string
  independent: boolean
  observedAt: string
}
export interface AdjustmentView {
  adjustment: {
    id: string
    kind: string
    reason: string
    previousDue: string | null
    nextDue: string | null
    previousStatus: string
    nextStatus: string
    createdAt: string
  }
  childTaskIds: string[]
}
export function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
export function zonedDate(instant: string, zone: string) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(instant))
  const get = (name: string) => parts.find((p) => p.type === name)?.value ?? ''
  return `${get('year')}-${get('month')}-${get('day')}`
}
