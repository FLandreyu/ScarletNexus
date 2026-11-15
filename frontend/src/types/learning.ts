import type { KnowledgeNode, Page } from './catalog'
export type { Page }
export interface Goal {
  id: string
  skillId: string
  title: string
  targetDescription: string
  deadline: string | null
  priority: number
  status: string
  revision: number
}
export interface PathVersion {
  id: string
  skillId: string
  goalId: string | null
  familyId: string
  versionNumber: number
  previousVersionId: string | null
  title: string
  createdAt: string
}
export interface Stage {
  id: string
  pathId: string
  title: string
  displayOrder: number
}
export interface Entry {
  id: string
  pathId: string
  stageId: string
  nodeId: string
  titleSnapshot: string
  displayOrder: number
  targetLevel: number
  required: boolean
}
export interface PathDetail {
  path: PathVersion
  stages: Stage[]
  entries: Entry[]
}
export interface Progress {
  entryId: string
  status: string
  revision: number
}
export interface Enrollment {
  id: string
  pathId: string
  status: string
  startedAt: string
}
export interface EnrollmentDetail {
  enrollment: Enrollment
  route: PathDetail
  progress: Progress[]
  completedRequired: number
  totalRequired: number
}
export interface Task {
  id: string
  skillId: string
  goalId: string | null
  nodeId: string
  enrollmentId: string | null
  entryId: string | null
  pathId: string | null
  title: string
  plannedMinutes: number
  dueDate: string | null
  status: string
  revision: number
}
export interface Session {
  id: string
  taskId: string
  state: string
  startedAt: string
  segmentStartedAt: string | null
  endedAt: string | null
  accumulatedMillis: number
  revision: number
}
export interface SessionView {
  session: Session
  elapsedSeconds: number
  serverNow: string
}
export interface Note {
  id: string
  taskId: string
  body: string
  revision: number
  updatedAt: string
}
export interface Resource {
  id: string
  title: string
  resourceType: string
  url: string
  status: string
  revision: number
}
export interface Workbench {
  task: Task
  node: KnowledgeNode
  route: EnrollmentDetail | null
  prerequisites: KnowledgeNode[]
  note: Note | null
  openSession: SessionView | null
}
export interface Dashboard {
  continuationTask: Task | null
  openSession: SessionView | null
  activeEnrollment: EnrollmentDetail | null
  tasks: Page<Task>
}
export const taskStates: Record<string, string> = {
  TODO: '待学习',
  DOING: '学习中',
  DONE: '已完成',
  DEFERRED: '已延期',
  CANCELLED: '已取消',
}
export const progressStates: Record<string, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '学习中',
  COMPLETED: '已学',
}
