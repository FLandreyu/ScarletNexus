export interface Totals {
  studyMilliseconds: number
  tasks: number
  doneTasks: number
  cancelledTasks: number
  taskDenominator: number
  attempts: number
  independentAttempts: number
  judgedIndependentAttempts: number
  correctIndependentAttempts: number
  unsureOrUnreviewedAttempts: number
}
export interface AnalyticsDay {
  date: string
  studyMilliseconds: number
  attempts: number
  independentAttempts: number
  judgedIndependentAttempts: number
  correctIndependentAttempts: number
}
export interface AnalyticsSkill {
  id: string
  name: string
  status: string
  totals: Totals
}
export interface AnalyticsReport {
  from: string
  to: string
  timeZone: string
  skillId: string | null
  generatedAt: string
  totals: Totals
  skills: AnalyticsSkill[]
  days: AnalyticsDay[]
  routes: {
    enrollmentId: string
    pathId: string
    title: string
    completedRequired: number
    totalRequired: number
  }[]
  definitions: string[]
}
export interface AssessmentView {
  assessment: {
    id: string
    nodeId: string
    title: string
    level: number
    confidence: string
    assessorType: string
    reason: string
    assessedAt: string
  }
  evidenceIds: string[]
}
export function percentage(numerator: number, denominator: number) {
  return denominator > 0
    ? `${((numerator / denominator) * 100).toFixed(1)}%`
    : '暂无可比结果'
}
export function studyTime(milliseconds: number) {
  return `${(milliseconds / 3600000).toFixed(2)} 小时`
}
