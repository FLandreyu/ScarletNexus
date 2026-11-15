<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage } from '../api/client'
import { allPages } from '../api/learning'
import type { Skill } from '../types/catalog'
import type { Task } from '../types/learning'
import type {
  Day,
  Draft,
  DraftView,
  Plan,
  PlanView,
  AdjustmentView,
} from '../types/planner'
import { localDate } from '../types/planner'
const route = useRoute(),
  date = ref(localDate()),
  zone = ref(Intl.DateTimeFormat().resolvedOptions().timeZone),
  budget = ref(90),
  skills = ref<Skill[]>([]),
  scope = ref<Record<string, boolean>>({}),
  caps = ref<Record<string, number>>({}),
  day = ref<Day | null>(null),
  drafts = ref<Draft[]>([]),
  history = ref<Plan[]>([]),
  draft = ref<DraftView | null>(null),
  selected = ref<string[]>([]),
  savedSelection = ref(''),
  confirmed = ref(false),
  cancelConfirmed = ref(false),
  reason = ref(''),
  error = ref(''),
  message = ref(''),
  busy = ref(false),
  confirmKey = ref(crypto.randomUUID()),
  historical = ref<PlanView | null>(null)
const dirty = computed(
  () => JSON.stringify(selected.value) !== savedSelection.value,
)
const tasks = ref<Task[]>([]),
  taskId = ref(typeof route.query.task === 'string' ? route.query.task : ''),
  kind = ref('DEFER'),
  dueDate = ref(localDate()),
  adjustmentReason = ref(''),
  parts = ref([
    { title: '第一部分', minutes: 10, dueDate: '' },
    { title: '第二部分', minutes: 15, dueDate: '' },
  ]),
  adjustments = ref<AdjustmentView[]>([]),
  adjustmentKey = ref(crypto.randomUUID())
const chosenTask = computed(() =>
  tasks.value.find((t) => t.id === taskId.value),
)
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
async function loadDay() {
  ;[day.value, drafts.value, history.value] = await Promise.all([
    api<Day>(`/planner/days/${date.value}`),
    allPages<Draft>(`/planner/drafts?date=${date.value}`),
    allPages<Plan>(`/planner/days/${date.value}/history`),
  ])
  historical.value = null
}
function adopt(value: DraftView) {
  draft.value = value
  selected.value = value.items.filter((i) => i.selected).map((i) => i.id)
  savedSelection.value = JSON.stringify(selected.value)
  confirmed.value = false
  confirmKey.value = crypto.randomUUID()
}
async function changeDate() {
  await run(async () => {
    await loadDay()
    draft.value = null
    confirmed.value = false
    cancelConfirmed.value = false
  })
}
async function generate() {
  await run(async () => {
    const value = await api<DraftView>('/planner/drafts', 'POST', {
      planDate: date.value,
      timeZone: zone.value,
      budgetMinutes: budget.value,
      skillLimits: skills.value
        .filter((s) => scope.value[s.id])
        .map((s) => ({ skillId: s.id, maxMinutes: caps.value[s.id] })),
    })
    adopt(value)
    await loadDay()
    message.value = '草案已保存，正式计划和原任务未改变。'
  })
}
async function openDraft(id: string) {
  await run(async () => adopt(await api<DraftView>(`/planner/drafts/${id}`)))
}
async function save() {
  await run(async () => {
    if (!draft.value) return
    adopt(
      await api<DraftView>(`/planner/drafts/${draft.value.draft.id}`, 'PATCH', {
        revision: draft.value.draft.revision,
        itemIds: selected.value,
      }),
    )
    message.value = '草案选择已保存，尚未生效。'
  })
}
async function confirm() {
  await run(async () => {
    if (!draft.value || !day.value || dirty.value || !confirmed.value) return
    await api<PlanView>(
      `/planner/drafts/${draft.value.draft.id}/confirm`,
      'POST',
      {
        revision: draft.value.draft.revision,
        dayRevision: day.value.revision,
        confirmed: true,
      },
      { 'Idempotency-Key': confirmKey.value },
    )
    adopt(await api<DraftView>(`/planner/drafts/${draft.value.draft.id}`))
    tasks.value = await allPages<Task>('/learning/tasks')
    await loadDay()
    message.value = '计划已确认生效，历史版本已保留。'
  })
}
async function cancel() {
  await run(async () => {
    if (!day.value || !cancelConfirmed.value) return
    await api(`/planner/days/${date.value}/cancel`, 'POST', {
      revision: day.value.revision,
      confirmed: true,
      reason: reason.value,
    })
    await loadDay()
    cancelConfirmed.value = false
    message.value = '当前计划已取消，历史仍可查。'
  })
}
async function loadAdjustments() {
  adjustments.value = taskId.value
    ? await allPages<AdjustmentView>(
        `/planner/tasks/${taskId.value}/adjustments`,
      )
    : []
}
async function changeTask() {
  await run(async () => {
    await loadAdjustments()
    adjustmentKey.value = crypto.randomUUID()
  })
}
async function adjust() {
  await run(async () => {
    if (!chosenTask.value) return
    await api(
      `/planner/tasks/${taskId.value}/adjustments`,
      'POST',
      {
        kind: kind.value,
        revision: chosenTask.value.revision,
        dueDate: kind.value === 'DEFER' ? dueDate.value : null,
        reason: adjustmentReason.value,
        parts:
          kind.value === 'SPLIT'
            ? parts.value.map((p) => ({ ...p, dueDate: p.dueDate || null }))
            : [],
      },
      { 'Idempotency-Key': adjustmentKey.value },
    )
    adjustmentKey.value = crypto.randomUUID()
    tasks.value = await allPages<Task>('/learning/tasks')
    await loadAdjustments()
    await loadDay()
    message.value = '调整已保存，原任务及学习记录已保留。'
  })
}
async function openHistory(id: string) {
  await run(async () => {
    historical.value = await api<PlanView>(`/planner/plans/${id}`)
  })
}
onMounted(() =>
  run(async () => {
    ;[skills.value, tasks.value] = await Promise.all([
      allPages<Skill>('/skills?status=ACTIVE'),
      allPages<Task>('/learning/tasks'),
    ])
    for (const s of skills.value) {
      scope.value[s.id] = false
      caps.value[s.id] = 30
    }
    await loadDay()
    await loadAdjustments()
  }),
)
</script>
<template>
  <section class="workspace-page planner-page">
    <p class="eyebrow">PLAN · REVIEW · CONTINUE</p>
    <h1>每日计划</h1>
    <p>
      规则
      v1：到期复习优先，跨技能轮选整项任务。先修完成来自任务或路线自报，不代表能力已验证。这里没有请求
      AI。
    </p>
    <p v-if="error" role="alert" class="error-box">{{ error }}</p>
    <p v-if="message" role="status">{{ message }}</p>
    <fieldset :disabled="busy" class="learning-fieldset">
      <form class="editor" @submit.prevent="generate">
        <h2>时间预算与规划范围</h2>
        <label
          >计划日期<input
            v-model="date"
            type="date"
            required
            @change="changeDate" /></label
        ><label>时区<input v-model="zone" required maxlength="80" /></label
        ><label
          >总预算（分钟）<input
            v-model.number="budget"
            type="number"
            min="0"
            max="1440"
            required
        /></label>
        <p>请选择本次规划的1至100项技能，各技能上限与总预算同时生效。</p>
        <div class="budget-list" aria-label="技能预算列表">
          <div v-for="skill in skills" :key="skill.id" class="budget-row">
            <label
              ><input v-model="scope[skill.id]" type="checkbox" />{{
                skill.name
              }}</label
            ><label
              >技能上限（分钟）<input
                v-model.number="caps[skill.id]"
                type="number"
                min="0"
                max="1440"
                :required="scope[skill.id]"
                :aria-label="`${skill.name}预算`"
            /></label>
          </div>
        </div>
        <p v-if="!skills.length">
          暂无可规划技能，请先在技能空间建立技能和任务。
        </p>
        <button type="submit" :disabled="!skills.some((s) => scope[s.id])">
          生成规则草案
        </button>
      </form>
    </fieldset>
    <section v-if="day" class="learning-section">
      <h2>正式计划 · {{ day.planDate }}</h2>
      <template v-if="day.activePlan"
        ><p>
          版本 {{ day.activePlan.plan.versionNumber }} ·
          {{ day.activePlan.plannedMinutes }} /
          {{ day.activePlan.plan.budgetMinutes }} 分钟 ·
          {{ day.activePlan.plan.timeZone }}
        </p>
        <article
          v-for="entry in day.activePlan.items"
          :key="entry.item.id"
          class="skill-card"
        >
          <h3>{{ entry.item.titleSnapshot }}</h3>
          <p>
            {{ entry.item.plannedMinutes }} 分钟 · 当前状态
            {{ entry.current.status
            }}<span v-if="entry.changed"> · 确认后记录已变化</span>
          </p>
          <p>{{ entry.item.reason }}</p>
          <RouterLink :to="`/study/${entry.item.taskId}`">开始学习</RouterLink>
        </article>
        <form class="editor" @submit.prevent="cancel">
          <label
            >取消计划原因<textarea
              v-model="reason"
              required
              maxlength="2000"
              :disabled="busy"
            /></label
          ><label
            ><input
              v-model="cancelConfirmed"
              type="checkbox"
              :disabled="busy"
            />确认取消当前计划并保留历史</label
          ><button :disabled="busy || !cancelConfirmed">取消当前计划</button>
        </form></template
      >
      <p v-else>这一天尚无生效计划。生成草案不会覆盖正式计划。</p>
    </section>
    <section v-if="draft" class="learning-section">
      <h2>计划草案</h2>
      <p>
        {{ draft.draft.planDate }} · {{ draft.draft.timeZone }} ·
        {{ draft.draft.state }} · 已保存 {{ draft.selectedMinutes }} /
        {{ draft.draft.budgetMinutes }} 分钟
      </p>
      <p v-if="draft.sourceChanged" role="alert" class="error-box">
        源记录已变化，请重新生成草案后确认。
      </p>
      <p v-if="dirty">选择尚未保存；先保存，再确认。</p>
      <fieldset
        class="learning-fieldset"
        :disabled="busy || draft.draft.state !== 'DRAFT' || draft.sourceChanged"
      >
        <article v-for="item in draft.items" :key="item.id" class="skill-card">
          <label
            ><input
              v-model="selected"
              :value="item.id"
              type="checkbox"
              :disabled="!item.eligible"
            />{{ item.kind === 'REVIEW' ? '复习' : '学习' }} ·
            {{ item.title }} · {{ item.minutes }} 分钟</label
          >
          <p>{{ item.reason }}</p>
        </article>
        <p v-if="!draft.items.length">
          当前范围没有候选任务；可返回学习首页创建任务。
        </p>
        <button :disabled="!dirty" @click="save">保存草案选择</button
        ><label
          ><input
            v-model="confirmed"
            type="checkbox"
          />我已检查时间预算，确认以此草案替换当天计划</label
        ><button :disabled="dirty || !confirmed" @click="confirm">
          确认计划生效
        </button>
      </fieldset>
    </section>
    <section class="learning-section">
      <h2>草案与版本历史</h2>
      <div class="row-actions">
        <button
          v-for="d in drafts"
          :key="d.id"
          :disabled="busy"
          @click="openDraft(d.id)"
        >
          草案 {{ d.state }} · {{ d.id.slice(0, 8) }}
        </button>
      </div>
      <div class="row-actions">
        <button
          v-for="p in history"
          :key="p.id"
          :disabled="busy"
          @click="openHistory(p.id)"
        >
          查看版本 {{ p.versionNumber }}
        </button>
      </div>
      <article v-if="historical" class="skill-card">
        <h3>
          历史版本 {{ historical.plan.versionNumber }} ·
          {{ historical.plannedMinutes }} 分钟
        </h3>
        <p v-for="entry in historical.items" :key="entry.item.id">
          {{ entry.item.titleSnapshot }} · {{ entry.item.plannedMinutes }} 分钟
          · {{ entry.item.reason }}
        </p>
      </article>
    </section>
    <section class="learning-section">
      <h2>未完成任务调整</h2>
      <fieldset :disabled="busy" class="learning-fieldset">
        <form class="editor" @submit.prevent="adjust">
          <label
            >调整任务<select v-model="taskId" required @change="changeTask">
              <option value="">请选择</option>
              <option v-for="t in tasks" :key="t.id" :value="t.id">
                {{ t.title }} · {{ t.status }} · {{ t.plannedMinutes }}分钟
              </option>
            </select></label
          ><label
            >调整方式<select v-model="kind">
              <option value="DEFER">延期</option>
              <option value="SPLIT">拆分</option>
              <option value="CANCEL">取消</option>
            </select></label
          ><label v-if="kind === 'DEFER'"
            >新截止日期<input v-model="dueDate" type="date" required /></label
          ><template v-if="kind === 'SPLIT'"
            ><p>2 至 8 项，分钟之和须等于原任务；原任务取消并保留记录。</p>
            <div v-for="(part, index) in parts" :key="index" class="skill-card">
              <label
                >子任务 {{ index + 1 }} 标题<input
                  v-model="part.title"
                  required
                  maxlength="160" /></label
              ><label
                >分钟<input
                  v-model.number="part.minutes"
                  type="number"
                  min="1"
                  max="1440"
                  required /></label
              ><label
                >截止日期<input v-model="part.dueDate" type="date" /></label
              ><button
                type="button"
                :disabled="parts.length <= 2"
                @click="parts.splice(index, 1)"
              >
                移除子任务
              </button>
            </div>
            <button
              type="button"
              :disabled="parts.length >= 8"
              @click="parts.push({ title: '', minutes: 10, dueDate: '' })"
            >
              添加子任务
            </button></template
          ><label
            >调整原因<textarea
              v-model="adjustmentReason"
              required
              maxlength="2000"
            /></label
          ><button
            :disabled="
              !chosenTask || ['DONE', 'CANCELLED'].includes(chosenTask.status)
            "
          >
            保存任务调整
          </button>
        </form>
      </fieldset>
      <article
        v-for="a in adjustments"
        :key="a.adjustment.id"
        class="skill-card"
      >
        <p>
          {{ a.adjustment.kind }} · {{ a.adjustment.previousStatus }} →
          {{ a.adjustment.nextStatus }} ·
          {{ a.adjustment.previousDue || '未定日期' }} →
          {{ a.adjustment.nextDue || '未定日期' }}
        </p>
        <p>{{ a.adjustment.reason }}</p>
        <RouterLink
          v-for="child in a.childTaskIds"
          :key="child"
          :to="`/study/${child}`"
          >查看拆分任务
        </RouterLink>
      </article>
    </section>
  </section>
</template>
<style scoped>
.budget-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}
.budget-list {
  max-height: 340px;
  overflow-y: auto;
  padding: 6px;
  border: 1px solid #4c3e52;
  border-radius: 6px;
}
.budget-row > label {
  flex: 1;
  min-width: 140px;
}
.planner-page p,
.planner-page label {
  overflow-wrap: anywhere;
}
.planner-page input[type='checkbox'] {
  width: auto;
  min-height: auto;
}
.planner-page .row-actions {
  flex-wrap: wrap;
}
</style>
