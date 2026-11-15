<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errorMessage } from '../api/client'
interface Job {
  id: string
  skillId: string
  state: string
  usageSource: string
  reservedTokens: number
  chargedTokens: number | null
  errorCode: string | null
  draftId: string
  createdAt: string
  recoverable: boolean
}
const jobs = ref<Job[]>([]),
  page = ref(0),
  busy = ref(false),
  error = ref(''),
  notice = ref('')
const confirmed = ref<Record<string, boolean>>({})
async function load(next = page.value) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    jobs.value = await api(`/ai/jobs?page=${next}&size=20`)
    page.value = next
    confirmed.value = {}
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function recover(job: Job) {
  if (busy.value || !confirmed.value[job.id]) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const result = await api<Job>(`/ai/jobs/${job.id}/recover`, 'POST', {
      confirmed: true,
    })
    jobs.value = jobs.value.map((j) => (j.id === result.id ? result : j))
    notice.value = '任务已结束，预算按未知用量保守占用，草案保持不变。'
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
onMounted(() => load())
</script>
<template>
  <section class="workspace-heading">
    <RouterLink to="/skills">← 技能空间</RouterLink>
    <h1>生成任务记录</h1>
    <p>这里显示已有任务。生成入口尚未开放。Token 配额不代表货币费用。</p>
  </section>
  <p v-if="error" role="alert" class="error-box">{{ error }}</p>
  <p v-if="notice" role="status">{{ notice }}</p>
  <button :disabled="busy" @click="load()">刷新任务</button>
  <p v-if="busy" role="status">正在读取任务…</p>
  <p v-if="!busy && !error && !jobs.length" class="empty-state">
    暂无生成任务。
  </p>
  <ul class="skill-list">
    <li v-for="job in jobs" :key="job.id" class="skill-card">
      <h2>任务 {{ job.id.slice(0, 8) }}</h2>
      <p>
        {{
          job.state === 'RUNNING'
            ? '执行中'
            : job.state === 'SUCCEEDED'
              ? '已生成草案'
              : '未完成生成'
        }}
        · {{ new Date(job.createdAt).toLocaleString() }}
      </p>
      <p>
        预留 {{ job.reservedTokens }} tokens · 结算
        {{ job.chargedTokens ?? '待确认' }} ·
        {{
          job.usageSource === 'PROVIDER'
            ? '供应商用量'
            : job.usageSource === 'NOT_SENT'
              ? '未发送'
              : '用量未知，保守占用'
        }}
      </p>
      <p v-if="job.errorCode">错误代码：{{ job.errorCode }}</p>
      <RouterLink :to="`/skills/${job.skillId}/drafts`"
        >查看相关草案</RouterLink
      >
      <template v-if="job.state === 'RUNNING'">
        <p>
          超过五分钟的任务可确认结束。此操作不撤销上游请求、不释放预算、不重新生成；迟到的结果不会覆盖草案。
        </p>
        <label v-if="job.recoverable"
          ><input v-model="confirmed[job.id]" type="checkbox" />
          我确认结束此任务并保留预算占用</label
        >
        <button
          :disabled="busy || !job.recoverable || !confirmed[job.id]"
          @click="recover(job)"
        >
          结束中断任务
        </button>
      </template>
    </li>
  </ul>
  <div class="row-actions">
    <button :disabled="busy || page === 0" @click="load(page - 1)">
      上一页
    </button>
    <span>第 {{ page + 1 }} 页</span>
    <button :disabled="busy || jobs.length < 20" @click="load(page + 1)">
      下一页
    </button>
  </div>
</template>
<style scoped>
input[type='checkbox'] {
  width: auto;
  margin-right: 8px;
}
.skill-card {
  overflow-wrap: anywhere;
}
.skill-card button {
  display: block;
  margin-top: 12px;
}
</style>
