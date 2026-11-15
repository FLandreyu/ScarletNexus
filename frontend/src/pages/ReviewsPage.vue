<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage } from '../api/client'
import { allPages } from '../api/learning'
import type { Task, Page } from '../types/learning'
import type { ReviewView, ReviewEvent, Evidence } from '../types/planner'
import { localDate, zonedDate } from '../types/planner'
const route = useRoute(),
  tasks = ref<Task[]>([]),
  taskId = ref(typeof route.query.task === 'string' ? route.query.task : ''),
  evidence = ref<Evidence[]>([]),
  initialEvidence = ref(''),
  firstDate = ref(localDate()),
  zone = ref(Intl.DateTimeFormat().resolvedOptions().timeZone),
  minutes = ref(15),
  reason = ref(''),
  rows = ref<Page<ReviewView>>({ items: [], page: 0, size: 20, total: 0 }),
  page = ref(0),
  dueOnly = ref(true),
  selected = ref<ReviewView | null>(null),
  nextDate = ref(''),
  status = ref('ACTIVE'),
  adjustReason = ref(''),
  completeReason = ref(''),
  completeEvidence = ref(''),
  retestEvidence = ref<Evidence[]>([]),
  events = ref<ReviewEvent[]>([]),
  busy = ref(false),
  error = ref(''),
  message = ref(''),
  completeKey = ref(crypto.randomUUID())
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await action()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function load() {
  rows.value = await api<Page<ReviewView>>(
    `/reviews/schedules?due=${dueOnly.value}&page=${page.value}&size=20`,
  )
}
async function refresh() {
  await run(load)
}
async function changeFilter() {
  await run(async () => {
    page.value = 0
    await load()
  })
}
async function turn(delta: number) {
  await run(async () => {
    page.value += delta
    await load()
  })
}
async function changeTask() {
  await run(async () => {
    initialEvidence.value = ''
    evidence.value = taskId.value
      ? await allPages<Evidence>(`/practice/tasks/${taskId.value}/evidence`)
      : []
  })
}
async function create() {
  await run(async () => {
    const result = await api<ReviewView>('/reviews/schedules', 'POST', {
      taskId: taskId.value,
      evidenceId: initialEvidence.value || null,
      firstDate: firstDate.value || null,
      timeZone: zone.value,
      plannedMinutes: minutes.value,
      reason: reason.value,
    })
    await select(result.schedule.id)
    await load()
    message.value =
      '复习安排已保存，尚未创建任务。确认每日计划时才创建周期任务。'
  })
}
async function select(id: string) {
  const value = await api<ReviewView>(`/reviews/schedules/${id}`)
  const [history, available] = await Promise.all([
    allPages<ReviewEvent>(`/reviews/schedules/${id}/history`),
    allPages<Evidence>(
      `/practice/tasks/${value.schedule.originalTaskId}/evidence`,
    ),
  ])
  selected.value = value
  events.value = history
  retestEvidence.value = available
  nextDate.value = zonedDate(
    value.schedule.nextReviewAt,
    value.schedule.timeZone,
  )
  status.value = value.schedule.status
  completeEvidence.value = ''
  completeKey.value = crypto.randomUUID()
}
async function open(id: string) {
  await run(() => select(id))
}
async function adjust() {
  await run(async () => {
    if (!selected.value) return
    const id = selected.value.schedule.id
    await api(`/reviews/schedules/${id}`, 'PATCH', {
      revision: selected.value.schedule.revision,
      nextDate: nextDate.value,
      status: status.value,
      reason: adjustReason.value,
    })
    await select(id)
    await load()
    message.value = '复习日期与状态已调整，原因已记录。'
  })
}
async function complete() {
  await run(async () => {
    if (!selected.value) return
    const id = selected.value.schedule.id
    await api(
      `/reviews/schedules/${id}/complete`,
      'POST',
      {
        revision: selected.value.schedule.revision,
        evidenceId: completeEvidence.value,
        reason: completeReason.value,
      },
      { 'Idempotency-Key': completeKey.value },
    )
    await select(id)
    await load()
    message.value = '复测已确认，新间隔与原因已记录。'
  })
}
function when(value: string, zone: string) {
  return new Date(value).toLocaleString('zh-CN', { timeZone: zone })
}
onMounted(() =>
  run(async () => {
    tasks.value = await allPages<Task>('/learning/tasks')
    await load()
    if (taskId.value)
      evidence.value = await allPages<Evidence>(
        `/practice/tasks/${taskId.value}/evidence`,
      )
  }),
)
</script>
<template>
  <section class="workspace-page reviews-page">
    <p class="eyebrow">RECALL · RETEST · SCHEDULE</p>
    <h1>到期复习</h1>
    <p>
      规则
      v1：独立且规则比对正确，间隔翻倍（最多365天）；提示后正确、自评、错误或不确定，保守回到1天。复测必须有新的作答与确认的证据，不会自动提高能力等级。
    </p>
    <p v-if="error" role="alert" class="error-box">{{ error }}</p>
    <p v-if="message" role="status">{{ message }}</p>
    <section class="learning-section">
      <h2>复习队列</h2>
      <label
        ><input
          v-model="dueOnly"
          type="checkbox"
          :disabled="busy"
          @change="changeFilter"
        />仅显示当前已到期且未暂停</label
      ><button :disabled="busy" @click="refresh">刷新队列</button>
      <article
        v-for="row in rows.items"
        :key="row.schedule.id"
        class="skill-card"
      >
        <h3>{{ row.title }}</h3>
        <p>
          {{ when(row.schedule.nextReviewAt, row.schedule.timeZone) }} ·
          {{ row.schedule.timeZone }} · {{ row.schedule.status }} · 间隔{{
            row.schedule.intervalDays
          }}天
        </p>
        <p>{{ row.schedule.reason }}</p>
        <div class="row-actions">
          <button :disabled="busy" @click="open(row.schedule.id)">
            查看调度与复测</button
          ><RouterLink
            v-if="row.cycleTaskId"
            :to="`/practice/${row.cycleTaskId}`"
            >进行复习练习</RouterLink
          ><RouterLink v-else :to="`/practice/${row.schedule.originalTaskId}`"
            >提交新的练习</RouterLink
          >
        </div>
      </article>
      <p v-if="!rows.items.length">当前筛选下没有复习安排。</p>
      <div class="row-actions">
        <button :disabled="busy || page === 0" @click="turn(-1)">上一页</button
        ><span>{{ page + 1 }} 页 · 共{{ rows.total }}项</span
        ><button
          :disabled="busy || (page + 1) * 20 >= rows.total"
          @click="turn(1)"
        >
          下一页
        </button>
      </div>
    </section>
    <section v-if="selected" class="learning-section">
      <h2>调度详情 · {{ selected.title }}</h2>
      <p>
        {{ selected.schedule.timeZone }} · 周期
        {{ selected.schedule.cycleNumber + 1 }} · {{ selected.schedule.reason }}
      </p>
      <fieldset :disabled="busy" class="learning-fieldset">
        <form class="editor" @submit.prevent="adjust">
          <h3>调整日期 / 暂停恢复</h3>
          <label
            >下次日期（每天当地09:00）<input
              v-model="nextDate"
              type="date"
              required /></label
          ><label
            >安排状态<select v-model="status">
              <option value="ACTIVE">启用</option>
              <option value="PAUSED">暂停</option>
            </select></label
          ><label
            >调度原因<textarea
              v-model="adjustReason"
              required
              maxlength="2000"
            /></label
          ><button>保存复习调度</button>
        </form>
        <form class="editor" @submit.prevent="complete">
          <h3>确认复测结果</h3>
          <p>
            先在练习页提交新作答、比对或复核并确认证据，再刷新此详情。旧作答或重复证据会被拒绝。
          </p>
          <RouterLink
            :to="`/practice/${selected.cycleTaskId || selected.schedule.originalTaskId}`"
            >前往练习并确认证据</RouterLink
          ><label
            >本次复测证据<select v-model="completeEvidence" required>
              <option value="">请选择新的证据</option>
              <option v-for="e in retestEvidence" :key="e.id" :value="e.id">
                {{ e.outcome }} · {{ e.verification }} ·
                {{ e.independent ? '独立' : '非独立' }} · {{ e.observedAt }}
              </option>
            </select></label
          ><label
            >复测说明<textarea
              v-model="completeReason"
              required
              maxlength="2000"
            /></label
          ><button :disabled="selected.schedule.status !== 'ACTIVE'">
            确认证据并调度下次复习
          </button>
        </form>
      </fieldset>
      <h3>调度与复测历史</h3>
      <article v-for="event in events" :key="event.id" class="skill-card">
        <p>
          {{ event.kind }} · {{ event.previousInterval ?? '初次' }} →
          {{ event.nextInterval }} 天
        </p>
        <p>{{ event.previousDue || '无原日期' }} → {{ event.nextDue }}</p>
        <p>{{ event.reason }}</p>
      </article>
    </section>
    <section class="learning-section">
      <h2>新增节点复习安排</h2>
      <fieldset :disabled="busy" class="learning-fieldset">
        <form class="editor" @submit.prevent="create">
          <label
            >来源任务<select v-model="taskId" required @change="changeTask">
              <option value="">请选择</option>
              <option v-for="t in tasks" :key="t.id" :value="t.id">
                {{ t.title }}
              </option>
            </select></label
          ><label
            >首次证据（可选）<select v-model="initialEvidence">
              <option value="">手动安排</option>
              <option v-for="e in evidence" :key="e.id" :value="e.id">
                {{ e.outcome }} · {{ e.verification }} · {{ e.observedAt }}
              </option>
            </select></label
          >
          <p>
            首次证据正确则3天后复习，其余1天；填写首次日期时以手动日期为准。
          </p>
          <label
            >首次复习日期<input
              v-model="firstDate"
              type="date"
              :required="!initialEvidence" /></label
          ><label>时区<input v-model="zone" required maxlength="80" /></label
          ><label
            >预计复习分钟<input
              v-model.number="minutes"
              type="number"
              min="1"
              max="1440"
              required /></label
          ><label
            >安排原因<textarea
              v-model="reason"
              required
              maxlength="2000"
            /></label
          ><button>保存复习安排</button>
        </form>
      </fieldset>
    </section>
  </section>
</template>
<style scoped>
.reviews-page p,
.reviews-page label {
  overflow-wrap: anywhere;
}
.reviews-page input[type='checkbox'] {
  width: auto;
  min-height: auto;
}
.reviews-page .row-actions {
  flex-wrap: wrap;
}
</style>
