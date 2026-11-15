<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, errorMessage } from '../api/client'
import { allPages, duration } from '../api/learning'
import type { Skill, Graph, PlacedNode, Page } from '../types/catalog'
import type { Dashboard, Goal, Task, Entry, Progress } from '../types/learning'
import { taskStates, progressStates } from '../types/learning'
import type { Day, ReviewView } from '../types/planner'
import { localDate } from '../types/planner'
const todayPlan = ref<Day | null>(null),
  dueReviews = ref(0)
const router = useRouter(),
  data = ref<Dashboard | null>(null),
  error = ref(''),
  busy = ref(false)
const tasks = ref<Page<Task>>({ items: [], total: 0, page: 0, size: 20 }),
  taskPage = ref(0),
  skills = ref<Skill[]>([]),
  goals = ref<Goal[]>([]),
  nodes = ref<PlacedNode[]>([])
const form = ref({
  skillId: '',
  goalId: '',
  nodeId: '',
  title: '',
  plannedMinutes: 25,
  dueDate: '',
})
const nextTask = computed(() => data.value?.continuationTask)
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await action()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function load() {
  ;[data.value, tasks.value, todayPlan.value] = await Promise.all([
    api<Dashboard>('/learning/dashboard'),
    api<Page<Task>>(`/learning/tasks?page=${taskPage.value}&size=20`),
    api<Day>(`/planner/days/${localDate()}`),
  ])
  dueReviews.value = (
    await api<Page<ReviewView>>('/reviews/schedules?due=true&size=1')
  ).total
}
async function loadNodes() {
  nodes.value = form.value.skillId
    ? (await api<Graph>(`/skills/${form.value.skillId}/graph`)).nodes.filter(
        (n) => n.status === 'ACTIVE',
      )
    : []
  form.value.nodeId = nodes.value[0]?.id ?? ''
  form.value.goalId = ''
}
async function changeSkill() {
  await run(loadNodes)
}
async function create() {
  await run(async () => {
    const task = await api<Task>('/learning/tasks', 'POST', {
      ...form.value,
      goalId: form.value.goalId || null,
      dueDate: form.value.dueDate || null,
      enrollmentId: null,
      entryId: null,
    })
    await router.push(`/study/${task.id}`)
  })
}
async function createForEntry(entry: Entry) {
  await run(async () => {
    const en = data.value?.activeEnrollment
    if (!en) return
    const path = en.route.path
    const task = await api<Task>('/learning/tasks', 'POST', {
      skillId: path.skillId,
      goalId: path.goalId,
      nodeId: entry.nodeId,
      enrollmentId: en.enrollment.id,
      entryId: entry.id,
      title: entry.titleSnapshot,
      plannedMinutes: 25,
      dueDate: null,
    })
    await router.push(`/study/${task.id}`)
  })
}
function progress(entry: string): Progress {
  return (
    data.value?.activeEnrollment?.progress.find((p) => p.entryId === entry) ?? {
      entryId: entry,
      status: 'NOT_STARTED',
      revision: 0,
    }
  )
}
async function mark(entry: string, status: string) {
  await run(async () => {
    const en = data.value?.activeEnrollment
    if (!en) return
    await api(
      `/learning/enrollments/${en.enrollment.id}/progress/${entry}`,
      'PUT',
      { status, revision: progress(entry).revision },
    )
    await load()
  })
}
async function finishRoute() {
  await run(async () => {
    const en = data.value?.activeEnrollment
    if (!en) return
    await api(`/learning/enrollments/${en.enrollment.id}`, 'PATCH', {
      status: 'COMPLETED',
    })
    await load()
  })
}
async function page(delta: number) {
  await run(async () => {
    taskPage.value += delta
    await load()
  })
}
onMounted(
  () =>
    void run(async () => {
      await load()
      ;[skills.value, goals.value] = await Promise.all([
        allPages<Skill>('/skills?status=ACTIVE'),
        allPages<Goal>('/learning/goals'),
      ])
      form.value.skillId = skills.value[0]?.id ?? ''
      await loadNodes()
    }),
)
</script>
<template>
  <section class="workspace-heading">
    <p class="eyebrow">YOUR LEARNING DESK</p>
    <h1>学习首页</h1>
    <p>从上次停下的地方继续。学习状态由你记录，能力证据可在练习页确认。</p>
    <p v-if="todayPlan?.activePlan">
      今日计划：{{ todayPlan.activePlan.plannedMinutes }} /
      {{ todayPlan.activePlan.plan.budgetMinutes }} 分钟 ·
      {{ todayPlan.activePlan.items.length }} 项
    </p>
    <p v-else>今日尚无确认的计划。</p>
    <p>当前到期复习：{{ dueReviews }} 项</p>
    <div class="row-actions">
      <RouterLink to="/planner">规划今日学习</RouterLink>
      <RouterLink to="/reviews">查看到期复习</RouterLink>
      <RouterLink to="/paths">管理目标与路线</RouterLink
      ><button :disabled="busy" @click="run(load)">刷新学习记录</button>
    </div>
  </section>
  <p v-if="error" role="alert" class="error-box">{{ error }}</p>
  <section class="tree-panel learning-section" aria-label="继续学习">
    <h2>继续学习</h2>
    <template v-if="data?.openSession"
      ><p>
        有{{
          data.openSession.session.state === 'RUNNING' ? '正在计时' : '已暂停'
        }}的学习时段，已记录
        {{ duration(data.openSession.elapsedSeconds) }}（截至刷新时）。
      </p>
      <RouterLink :to="`/study/${data.openSession.session.taskId}`"
        >恢复学习工作台</RouterLink
      ></template
    ><template v-else-if="nextTask"
      ><p>{{ nextTask.title }} · {{ taskStates[nextTask.status] }}</p>
      <RouterLink :to="`/study/${nextTask.id}`"
        >继续上次任务</RouterLink
      ></template
    >
    <p v-else>尚无待学习任务。选择路线节点或手动建立任务。</p>
  </section>
  <fieldset class="learning-fieldset" :disabled="busy">
    <section v-if="data?.activeEnrollment" class="tree-panel learning-section">
      <h2>
        {{ data.activeEnrollment.route.path.title }} · v{{
          data.activeEnrollment.route.path.versionNumber
        }}
      </h2>
      <p>
        必修已学 {{ data.activeEnrollment.completedRequired }} /
        {{ data.activeEnrollment.totalRequired }}。状态是自报学习进度。
      </p>
      <div v-for="stage in data.activeEnrollment.route.stages" :key="stage.id">
        <h3>{{ stage.title }}</h3>
        <ul class="skill-list">
          <li
            v-for="entry in data.activeEnrollment.route.entries.filter(
              (e) => e.stageId === stage.id,
            )"
            :key="entry.id"
            class="skill-card"
          >
            <h4>{{ entry.titleSnapshot }}</h4>
            <p>
              {{ entry.required ? '必修' : '选修' }} · 目标等级
              {{ entry.targetLevel }} ·
              {{ progressStates[progress(entry.id).status] }}
            </p>
            <div class="row-actions">
              <button @click="createForEntry(entry)">
                建立任务：{{ entry.titleSnapshot }}</button
              ><button
                v-if="progress(entry.id).status !== 'COMPLETED'"
                @click="mark(entry.id, 'COMPLETED')"
              >
                标记已学 {{ entry.titleSnapshot }}</button
              ><button v-else @click="mark(entry.id, 'IN_PROGRESS')">
                恢复学习 {{ entry.titleSnapshot }}
              </button>
            </div>
          </li>
        </ul>
      </div>
      <button
        :disabled="
          data.activeEnrollment.completedRequired !==
          data.activeEnrollment.totalRequired
        "
        @click="finishRoute"
      >
        完成此路线
      </button>
    </section>
    <div class="workspace-columns learning-section">
      <section>
        <h2>学习任务</h2>
        <p v-if="!tasks.items.length" class="empty-state">尚无学习任务。</p>
        <ul class="skill-list">
          <li v-for="task in tasks.items" :key="task.id" class="skill-card">
            <h3>
              <RouterLink :to="`/study/${task.id}`">{{
                task.title
              }}</RouterLink>
            </h3>
            <p>
              {{ taskStates[task.status] }} · 预计
              {{ task.plannedMinutes }} 分钟 · 截止
              {{ task.dueDate ?? '未设置' }}
            </p>
          </li>
        </ul>
        <div class="pagination">
          <button :disabled="taskPage === 0" @click="page(-1)">
            上一页任务</button
          ><span
            >{{ taskPage + 1 }} /
            {{ Math.max(1, Math.ceil(tasks.total / 20)) }}</span
          ><button
            :disabled="(taskPage + 1) * 20 >= tasks.total"
            @click="page(1)"
          >
            下一页任务
          </button>
        </div>
      </section>
      <form class="editor" @submit.prevent="create">
        <h2>手动建立任务</h2>
        <label for="task-skill">任务技能</label
        ><select
          id="task-skill"
          v-model="form.skillId"
          required
          @change="changeSkill"
        >
          <option v-for="skill in skills" :key="skill.id" :value="skill.id">
            {{ skill.name }}
          </option></select
        ><label for="task-node">学习节点</label
        ><select id="task-node" v-model="form.nodeId" required>
          <option v-for="node in nodes" :key="node.id" :value="node.id">
            {{ node.title }}
          </option></select
        ><label for="task-goal">任务目标</label
        ><select id="task-goal" v-model="form.goalId">
          <option value="">不关联目标</option>
          <option
            v-for="goal in goals.filter((g) => g.skillId === form.skillId)"
            :key="goal.id"
            :value="goal.id"
          >
            {{ goal.title }}
          </option></select
        ><label for="task-title">任务名称</label
        ><input
          id="task-title"
          v-model="form.title"
          maxlength="160"
          required
        /><label for="task-minutes">预计分钟</label
        ><input
          id="task-minutes"
          v-model.number="form.plannedMinutes"
          type="number"
          min="1"
          max="1440"
          required
        /><label for="task-due">任务截止日期</label
        ><input id="task-due" v-model="form.dueDate" type="date" /><button>
          创建学习任务
        </button>
      </form>
    </div>
  </fieldset>
</template>
