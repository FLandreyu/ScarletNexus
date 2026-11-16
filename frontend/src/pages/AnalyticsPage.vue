<script setup lang="ts">
import { defineAsyncComponent, onMounted, ref } from 'vue'
import { api, errorMessage } from '../api/client'
import { allPages } from '../api/learning'
import type { Skill, Page } from '../types/catalog'
import type { AnalyticsReport, AssessmentView } from '../types/analytics'
import { percentage, studyTime } from '../types/analytics'
import { localDate } from '../types/planner'
import type { Evidence } from '../types/planner'
const traces = ref<Record<string, { evidence: Evidence; taskId: string }>>({})
async function trace(id: string) {
  await run(async () => {
    traces.value[id] = await api(`/analytics/evidence/${id}`)
  })
}
const AnalyticsCharts = defineAsyncComponent(
  () => import('../components/AnalyticsCharts.vue'),
)
const end = new Date(),
  start = new Date()
start.setDate(start.getDate() - 29)
const from = ref(localDate(start)),
  to = ref(localDate(end)),
  zone = ref(Intl.DateTimeFormat().resolvedOptions().timeZone),
  skillId = ref(''),
  skills = ref<Skill[]>([]),
  report = ref<AnalyticsReport | null>(null),
  assessments = ref<Page<AssessmentView>>({
    items: [],
    page: 0,
    size: 20,
    total: 0,
  }),
  page = ref(0),
  error = ref(''),
  busy = ref(false)
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
function query(date: string, timeZone: string, id: string | null) {
  const p = new URLSearchParams({ to: date, timeZone })
  if (id) p.set('skillId', id)
  return p
}
async function load() {
  const p = query(to.value, zone.value, skillId.value)
  p.set('from', from.value)
  const [value, rows] = await Promise.all([
    api<AnalyticsReport>(`/analytics?${p}`),
    api<Page<AssessmentView>>(
      `/analytics/assessments?${query(to.value, zone.value, skillId.value)}&page=0&size=20`,
    ),
  ])
  report.value = value
  assessments.value = rows
  page.value = 0
}
async function refresh() {
  await run(load)
}
async function turn(delta: number) {
  await run(async () => {
    if (!report.value) return
    const next = page.value + delta
    const rows = await api<Page<AssessmentView>>(
      `/analytics/assessments?${query(report.value.to, report.value.timeZone, report.value.skillId)}&page=${next}&size=20`,
    )
    assessments.value = rows
    page.value = next
  })
}
onMounted(() =>
  run(async () => {
    skills.value = await allPages<Skill>('/skills?status=ALL')
    await load()
  }),
)
</script>
<template>
  <section class="workspace-page analytics-page">
    <p class="eyebrow">TIME · EVIDENCE · GROWTH</p>
    <h1>成长分析</h1>
    <p>学习完成、自报能力和规则证据分别呈现；不计算混合“掌握度百分比”。</p>
    <p v-if="error" role="alert" class="error-box">{{ error }}</p>
    <fieldset :disabled="busy" class="learning-fieldset">
      <form class="editor" @submit.prevent="refresh">
        <label for="analytics-from">开始日期</label
        ><input id="analytics-from" v-model="from" type="date" required /><label
          for="analytics-to"
          >结束日期（含当天）</label
        ><input id="analytics-to" v-model="to" type="date" required /><label
          for="analytics-zone"
          >统计时区</label
        ><input
          id="analytics-zone"
          v-model="zone"
          required
          maxlength="80"
        /><label for="analytics-skill">统计技能</label
        ><select id="analytics-skill" v-model="skillId">
          <option value="">所有技能（含归档）</option>
          <option v-for="s in skills" :key="s.id" :value="s.id">
            {{ s.name }}
          </option></select
        ><button>刷新分析</button>
      </form>
    </fieldset>
    <template v-if="report"
      ><p>
        实际统计范围：{{ report.from }} 至 {{ report.to }} ·
        {{ report.timeZone }}；生成于 {{ report.generatedAt }}
      </p>
      <div class="metric-grid">
        <article class="skill-card">
          <h2>已结束学习</h2>
          <p>{{ studyTime(report.totals.studyMilliseconds) }}</p>
          <small>进行中/暂停未结束不计</small>
        </article>
        <article class="skill-card">
          <h2>任务完成率</h2>
          <p>
            {{
              percentage(report.totals.doneTasks, report.totals.taskDenominator)
            }}
          </p>
          <small
            >{{ report.totals.doneTasks }} /
            {{ report.totals.taskDenominator }}；取消
            {{ report.totals.cancelledTasks }} 项</small
          >
        </article>
        <article class="skill-card">
          <h2>独立正确率（规则）</h2>
          <p>
            {{
              percentage(
                report.totals.correctIndependentAttempts,
                report.totals.judgedIndependentAttempts,
              )
            }}
          </p>
          <small
            >{{ report.totals.correctIndependentAttempts }} /
            {{ report.totals.judgedIndependentAttempts }}
            个独立且明确对错的作答</small
          >
        </article>
      </div>
      <p>
        总作答 {{ report.totals.attempts }} 次 · 独立
        {{ report.totals.independentAttempts }} 次 · 无规则比对或不确定
        {{ report.totals.unsureOrUnreviewedAttempts }} 次
      </p>
      <section class="learning-section">
        <AnalyticsCharts :report="report" />
      </section>
      <section class="learning-section">
        <h2>技能投入与任务明细</h2>
        <div class="table-scroll">
          <table>
            <caption>
              技能维度，取消任务不进完成率分母
            </caption>
            <thead>
              <tr>
                <th>技能</th>
                <th>已结束时长</th>
                <th>完成 / 可统计</th>
                <th>独立正确 / 明确对错</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in report.skills" :key="s.id">
                <td>
                  <RouterLink :to="`/skills/${s.id}`">{{ s.name }}</RouterLink>
                  · {{ s.status }}
                </td>
                <td>{{ studyTime(s.totals.studyMilliseconds) }}</td>
                <td>
                  {{ s.totals.doneTasks }} / {{ s.totals.taskDenominator }}
                </td>
                <td>
                  {{ s.totals.correctIndependentAttempts }} /
                  {{ s.totals.judgedIndependentAttempts }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!report.skills.length">尚无技能数据。</p>
      </section>
      <details class="learning-section">
        <summary>查看每日真实数值</summary>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>结束日期</th>
                <th>学习分钟</th>
                <th>作答</th>
                <th>独立正确 / 明确对错</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in report.days" :key="d.date">
                <td>{{ d.date }}</td>
                <td>{{ (d.studyMilliseconds / 60000).toFixed(2) }}</td>
                <td>{{ d.attempts }}</td>
                <td>
                  {{ d.correctIndependentAttempts }} /
                  {{ d.judgedIndependentAttempts }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
      <section class="learning-section">
        <h2>当前路线 · 自报学习完成</h2>
        <p v-for="r in report.routes" :key="r.enrollmentId">
          {{ r.title }}：必修 {{ r.completedRequired }} /
          {{ r.totalRequired }}，不等同能力已验证。
        </p>
        <p v-if="!report.routes.length">当前筛选下没有ACTIVE学习路线。</p>
      </section>
      <section class="learning-section">
        <h2>能力等级与证据</h2>
        <p>
          截至结束日期，按节点和来源各取最近评估；规则与自评分开保留。L0–L5是等级，不是百分比。
        </p>
        <article
          v-for="row in assessments.items"
          :key="row.assessment.id"
          class="skill-card"
        >
          <h3>{{ row.assessment.title }} · L{{ row.assessment.level }}</h3>
          <p>
            {{
              row.assessment.assessorType === 'RULE' ? '规则评估' : '本人自评'
            }}
            · 可信度 {{ row.assessment.confidence }} ·
            {{ row.assessment.assessedAt }}
          </p>
          <p>{{ row.assessment.reason }}</p>
          <details>
            <summary>追溯评估与证据标识</summary>
            <p>
              节点 {{ row.assessment.nodeId }} · 评估 {{ row.assessment.id }}
            </p>
            <ul>
              <li v-for="id in row.evidenceIds" :key="id">
                <button :disabled="busy" @click="trace(id)">
                  查看证据 {{ id.slice(0, 8) }}
                </button>
                <template v-if="traces[id]"
                  ><p>
                    {{ traces[id].evidence.outcome }} ·
                    {{ traces[id].evidence.verification }} ·
                    {{ traces[id].evidence.independent ? '独立' : '非独立' }} ·
                    {{ traces[id].evidence.observedAt }}
                  </p>
                  <RouterLink :to="`/practice/${traces[id].taskId}`"
                    >前往原练习与证据</RouterLink
                  ></template
                >
              </li>
            </ul>
            <p v-if="!row.evidenceIds.length">
              此评估未关联证据，不能据此宣称已验证掌握。
            </p>
          </details>
        </article>
        <p v-if="!assessments.items.length">尚无符合范围的能力评估。</p>
        <div class="row-actions">
          <button :disabled="busy || page === 0" @click="turn(-1)">
            上一页评估</button
          ><span>第{{ page + 1 }}页 · 共{{ assessments.total }}项</span
          ><button
            :disabled="busy || (page + 1) * 20 >= assessments.total"
            @click="turn(1)"
          >
            下一页评估
          </button>
        </div>
      </section>
      <details class="learning-section">
        <summary>查看统计口径</summary>
        <p v-for="definition in report.definitions" :key="definition">
          {{ definition }}
        </p>
      </details></template
    >
  </section>
</template>
<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}
.metric-grid h2 {
  font-size: 17px;
}
.metric-grid p {
  font-size: 25px;
}
.analytics-page p,
.analytics-page td,
.analytics-page li {
  overflow-wrap: anywhere;
}
.table-scroll {
  overflow-x: auto;
  max-width: 100%;
}
table {
  width: 100%;
  border-collapse: collapse;
  min-width: 540px;
}
th,
td {
  text-align: left;
  padding: 12px;
  border-bottom: 1px solid #4c3e52;
  max-width: 260px;
}
caption {
  text-align: left;
  color: #c0b7c9;
  margin: 12px 0;
}
details summary {
  cursor: pointer;
  color: #e7b7c5;
  padding: 8px 0;
}
</style>
