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
  repairable: boolean
  repairDepth: number
  parentJobId: string | null
}
const jobs = ref<Job[]>([]),
  page = ref(0),
  busy = ref(false),
  error = ref(''),
  notice = ref('')
const confirmed = ref<Record<string, boolean>>({})
const generationEnabled = ref(false)
async function load(next = page.value) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const [records, availability] = await Promise.all([
      api<Job[]>(`/ai/jobs?page=${next}&size=20`),
      api<{ enabled: boolean }>('/ai/generation'),
    ])
    jobs.value = records
    generationEnabled.value = availability.enabled
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
async function repair(job: Job) {
  if (busy.value || !generationEnabled.value || !job.repairable) return
  if (
    !window.confirm(
      '修复会新建任务，再次调用模型并单独占用预算，最多两次。原草案保留，不自动发布。确认继续？',
    )
  )
    return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const child = await api<Job>(
      `/ai/jobs/${job.id}/repair`,
      'POST',
      { confirmed: true },
      { 'Idempotency-Key': `repair-${job.id}` },
    )
    jobs.value = await api(`/ai/jobs?page=${page.value}&size=20`)
    notice.value =
      child.state === 'SUCCEEDED'
        ? '修复候选已保存，请进入相关草案审核。'
        : `修复未完成：${child.errorCode ?? child.state}。原草案保留，可手动继续。`
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="workspace-heading">
    <RouterLink to="/skills">← 技能空间</RouterLink>
    <h1>生成任务记录</h1>
    <p>这里显示已有任务。生成由服务器配置启用，Token 配额不代表货币费用。</p>
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
      <p v-if="job.parentJobId">
        修复第 {{ job.repairDepth }} 次 · 来源任务
        {{ job.parentJobId.slice(0, 8) }}
      </p>
      <button
        v-if="job.repairable"
        :disabled="busy || !generationEnabled"
        @click="repair(job)"
      >
        确认修复并重试任务 {{ job.id.slice(0, 8) }}
      </button>
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
