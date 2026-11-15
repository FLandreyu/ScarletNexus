<script setup lang="ts">
import { defineAsyncComponent, onMounted, onUnmounted, ref } from 'vue'
import { api, errorMessage } from '../api/client'
import { allPages } from '../api/learning'
import { streamTutor } from '../api/tutor'
import type { Page } from '../types/catalog'
const MathText = defineAsyncComponent(() => import('./MathText.vue'))
interface Conversation {
  id: string
  taskId: string
  questionId: string | null
  title: string
}
export interface TutorTurn {
  id: string
  attemptId: string | null
  userText: string
  assistantText: string
  state: string
  errorCode: string | null
  model: string
  currency: string
  chargedMoney: number | null
  usageSource: string
  createdAt: string
}
const props = withDefaults(
  defineProps<{
    taskId: string
    questionId?: string | null
    attemptId?: string | null
  }>(),
  { questionId: null, attemptId: null },
)
const emit = defineEmits<{ review: [id: string] }>()
const enabled = ref(false),
  conversations = ref<Conversation[]>([]),
  selected = ref(''),
  turns = ref<Page<TutorTurn>>({ items: [], total: 0, page: 0, size: 10 }),
  page = ref(0)
const error = ref(''),
  busy = ref(false),
  text = ref(''),
  partial = ref(''),
  mode = ref('SOCRATIC'),
  confirmed = ref(false)
let controller: AbortController | null = null
async function load() {
  enabled.value = (await api<{ enabled: boolean }>('/tutor/status')).enabled
  conversations.value = (
    await allPages<Conversation>(`/tutor/tasks/${props.taskId}/conversations`)
  ).filter((c) => c.questionId === props.questionId)
  if (!conversations.value.some((c) => c.id === selected.value))
    selected.value = conversations.value[0]?.id ?? ''
  await loadTurns()
}
async function loadTurns() {
  if (selected.value)
    turns.value = await api(
      `/tutor/conversations/${selected.value}/turns?page=${page.value}&size=10`,
    )
}
function changeConversation() {
  page.value = 0
  void run(loadTurns)
}
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
async function create() {
  await run(async () => {
    const c = await api<Conversation>(
      `/tutor/tasks/${props.taskId}/conversations`,
      'POST',
      {
        questionId: props.questionId,
        title: props.questionId ? '练习讨论' : '节点学习讨论',
      },
    )
    selected.value = c.id
    page.value = 0
    await load()
  })
}
async function send() {
  await run(async () => {
    if (!confirmed.value) throw new Error('请确认本次请求会占用配置预算')
    if (!selected.value) throw new Error('请先创建对话')
    partial.value = ''
    controller = new AbortController()
    const timeout = setTimeout(() => controller?.abort(), 130000)
    try {
      await streamTutor(
        selected.value,
        {
          text: text.value,
          mode: mode.value,
          attemptId: props.attemptId,
          confirmed: confirmed.value,
        },
        crypto.randomUUID(),
        controller.signal,
        (name, value) => {
          if (name === 'delta')
            partial.value += (value as { text: string }).text
          else if (name === 'done' || name === 'snapshot') {
            const turn = value as TutorTurn
            partial.value = turn.assistantText
            if (turn.state === 'FAILED')
              error.value = `请求失败：${turn.errorCode}。作答与提问仍已保存，未自动重试。`
          }
        },
      )
      text.value = ''
    } finally {
      clearTimeout(timeout)
      controller = null
      confirmed.value = false
      await loadTurns()
    }
  })
}
async function recover(id: string) {
  await run(async () => {
    if (!window.confirm('结束超过五分钟的中断请求并保留未知费用占用？')) return
    await api(`/tutor/turns/${id}/recover`, 'POST', { confirmed: true })
    await loadTurns()
  })
}
async function changePage(delta: number) {
  await run(async () => {
    page.value += delta
    await loadTurns()
  })
}
onMounted(() => void run(load))
onUnmounted(() => controller?.abort())
</script>
<template>
  <section class="tree-panel learning-section" aria-label="AI导师">
    <h2>{{ questionId ? '练习导师' : '节点导师' }}</h2>
    <p v-if="!enabled">
      导师尚未启用。作答、提示、人工复核和学习记录仍可使用。
    </p>
    <p>
      AI内容是待核对建议，不会更改学习状态。图片暂不发送给模型，请先转录关键信息。
    </p>
    <p v-if="error" role="alert" class="error-box">{{ error }}</p>
    <fieldset :disabled="busy" class="learning-fieldset">
      <div class="row-actions">
        <button @click="create">
          新建{{ questionId ? '练习' : '节点' }}对话</button
        ><button @click="run(load)">刷新导师记录</button>
      </div>
      <label :for="`conversation-${taskId}`">选择对话</label
      ><select
        :id="`conversation-${taskId}`"
        v-model="selected"
        @change="changeConversation"
      >
        <option value="">尚未选择</option>
        <option v-for="c in conversations" :key="c.id" :value="c.id">
          {{ c.title }} · {{ c.id.slice(0, 8) }}
        </option>
      </select>
      <form class="editor inline-editor" @submit.prevent="send">
        <label :for="`tutor-mode-${taskId}`">导师模式</label
        ><select :id="`tutor-mode-${taskId}`" v-model="mode">
          <option value="SOCRATIC">苏格拉底引导</option>
          <option value="EXPLAIN">讲解与复核建议</option>
          <option v-if="questionId" value="FULL_SOLUTION">
            完整解析（记录曝光）
          </option></select
        ><label :for="`tutor-text-${taskId}`">向导师提问</label
        ><textarea
          :id="`tutor-text-${taskId}`"
          v-model="text"
          maxlength="8000"
          required
        /><label class="check-label"
          ><input
            v-model="confirmed"
            type="checkbox"
          />确认发送本次请求并占用配置预算{{
            questionId ? '，之后作答不再标记为未获帮助' : ''
          }}</label
        ><button
          :disabled="
            !enabled || !selected || !confirmed || (!!questionId && !attemptId)
          "
        >
          发送新请求
        </button>
      </form>
    </fieldset>
    <div v-if="busy && partial" role="status"><MathText :text="partial" /></div>
    <article
      v-for="turn in turns.items"
      :key="turn.id"
      class="skill-card learning-section"
    >
      <p>
        {{ turn.state }} · {{ turn.model }} · {{ turn.usageSource }} ·
        {{
          turn.chargedMoney === null
            ? '费用待结算'
            : `${turn.chargedMoney} ${turn.currency}（配置费率）`
        }}
      </p>
      <h3>提问</h3>
      <MathText :text="turn.userText" />
      <h3>导师建议</h3>
      <MathText :text="turn.assistantText || '尚无回复文本'" />
      <p v-if="turn.errorCode" class="error-box">
        {{ turn.errorCode }}；内容已保存，不会自动重试。
      </p>
      <button
        v-if="turn.state === 'RUNNING'"
        :disabled="busy"
        @click="recover(turn.id)"
      >
        结束中断请求</button
      ><button
        v-if="
          attemptId &&
          turn.attemptId === attemptId &&
          turn.state === 'SUCCEEDED'
        "
        :disabled="busy"
        @click="emit('review', turn.id)"
      >
        引用此建议进行人工复核
      </button>
    </article>
    <div class="pagination">
      <button :disabled="busy || page === 0" @click="changePage(-1)">
        上一页对话</button
      ><button
        :disabled="busy || (page + 1) * 10 >= turns.total"
        @click="changePage(1)"
      >
        下一页对话
      </button>
    </div>
  </section>
</template>
