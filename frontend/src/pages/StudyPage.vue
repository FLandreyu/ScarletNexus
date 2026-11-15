<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { api, errorMessage } from '../api/client'
import { duration } from '../api/learning'
import type { Page } from '../types/catalog'
import type {
  Workbench,
  Task,
  Note,
  SessionView,
  Resource,
} from '../types/learning'
import { taskStates, progressStates } from '../types/learning'
import TutorPanel from '../components/TutorPanel.vue'
const id = String(useRoute().params.id),
  data = ref<Workbench | null>(null),
  error = ref(''),
  message = ref(''),
  busy = ref(false)
const note = ref(''),
  savedNote = ref(''),
  noteRevision = ref(0),
  dirty = computed(() => note.value !== savedNote.value)
const resources = ref<Page<Resource>>({
    items: [],
    total: 0,
    page: 0,
    size: 20,
  }),
  resourcePage = ref(0),
  history = ref<Page<SessionView>>({ items: [], total: 0, page: 0, size: 20 }),
  historyPage = ref(0)
const form = ref({
  title: '',
  plannedMinutes: 25,
  dueDate: '',
  status: 'TODO',
  revision: 0,
})
const resourceForm = ref({ title: '', url: '', resourceType: 'WEB' }),
  resourceEditing = ref<Resource | null>(null)
const clock = ref(0),
  receivedAt = ref(0)
const current = computed(() =>
  data.value?.openSession?.session.taskId === id
    ? data.value.openSession
    : null,
)
const shownSeconds = computed(() =>
  current.value
    ? current.value.elapsedSeconds +
      (current.value.session.state === 'RUNNING'
        ? Math.max(0, clock.value - receivedAt.value)
        : 0)
    : 0,
)
const routeEntry = computed(() =>
  data.value?.route?.route.entries.find(
    (e) => e.id === data.value?.task.entryId,
  ),
)
const progress = computed(
  () =>
    data.value?.route?.progress.find(
      (p) => p.entryId === data.value?.task.entryId,
    ) ?? { status: 'NOT_STARTED', revision: 0 },
)
let ticker: ReturnType<typeof setInterval> | undefined
let startKey = crypto.randomUUID()
let pendingAction: {
  id: string
  action: string
  revision: number
  key: string
} | null = null
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
  const loaded = await api<Workbench>(`/learning/tasks/${id}/workbench`)
  data.value = loaded
  receivedAt.value = Math.floor(performance.now() / 1000)
  clock.value = receivedAt.value
  form.value = { ...loaded.task, dueDate: loaded.task.dueDate ?? '' }
  if (!dirty.value) {
    note.value = loaded.note?.body ?? ''
    savedNote.value = note.value
    noteRevision.value = loaded.note?.revision ?? 0
  }
  ;[resources.value, history.value] = await Promise.all([
    api<Page<Resource>>(
      `/learning/nodes/${loaded.task.nodeId}/resources?page=${resourcePage.value}&size=20`,
    ),
    api<Page<SessionView>>(
      `/learning/tasks/${id}/sessions?page=${historyPage.value}&size=20`,
    ),
  ])
}
async function saveTask() {
  await run(async () => {
    await api<Task>(`/learning/tasks/${id}`, 'PATCH', {
      ...form.value,
      dueDate: form.value.dueDate || null,
    })
    await load()
    message.value = '任务已保存。'
  })
}
async function start() {
  await run(async () => {
    await api(
      '/learning/sessions',
      'POST',
      { taskId: id },
      { 'Idempotency-Key': startKey },
    )
    startKey = crypto.randomUUID()
    await load()
  })
}
async function action(action: string) {
  await run(async () => {
    const session = current.value?.session
    if (!session) return
    if (pendingAction && pendingAction.action !== action)
      throw new Error('上一操作结果尚未确认，请重试同一操作或刷新记录。')
    pendingAction ??= {
      id: session.id,
      action,
      revision: session.revision,
      key: crypto.randomUUID(),
    }
    const command = pendingAction
    await api(
      `/learning/sessions/${command.id}/actions`,
      'POST',
      { action: command.action, revision: command.revision },
      { 'Idempotency-Key': command.key },
    )
    pendingAction = null
    await load()
  })
}
async function refresh() {
  await run(async () => {
    await load()
    pendingAction = null
  })
}
async function saveNote() {
  await run(async () => {
    const value = await api<Note>(`/learning/tasks/${id}/note`, 'PUT', {
      body: note.value,
      revision: noteRevision.value,
    })
    savedNote.value = value.body
    noteRevision.value = value.revision
    message.value = '笔记已保存。'
  })
}
async function saveProgress() {
  await run(async () => {
    const task = data.value?.task
    if (!task?.enrollmentId || !task.entryId) return
    await api(
      `/learning/enrollments/${task.enrollmentId}/progress/${task.entryId}`,
      'PUT',
      { status: 'COMPLETED', revision: progress.value.revision },
    )
    await load()
    message.value = '已记录自报学习完成，未改变能力评估。'
  })
}
async function saveResource() {
  await run(async () => {
    if (!data.value) return
    const edit = resourceEditing.value
    await api(
      edit
        ? `/learning/resources/${edit.id}`
        : `/learning/nodes/${data.value.task.nodeId}/resources`,
      edit ? 'PATCH' : 'POST',
      {
        ...resourceForm.value,
        status: edit?.status ?? 'ACTIVE',
        revision: edit?.revision ?? 0,
      },
    )
    resourceEditing.value = null
    resourceForm.value = { title: '', url: '', resourceType: 'WEB' }
    await load()
    message.value = '资料已保存。'
  })
}
async function resourceState(resource: Resource) {
  await run(async () => {
    await api(`/learning/resources/${resource.id}`, 'PATCH', {
      ...resource,
      status: resource.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE',
    })
    await load()
  })
}
function editResource(resource: Resource) {
  resourceEditing.value = resource
  resourceForm.value = {
    title: resource.title,
    url: resource.url,
    resourceType: resource.resourceType,
  }
}
function cancelResource() {
  resourceEditing.value = null
  resourceForm.value = { title: '', url: '', resourceType: 'WEB' }
}
async function page(kind: 'history' | 'resource', delta: number) {
  await run(async () => {
    if (kind === 'history') historyPage.value += delta
    else resourcePage.value += delta
    await load()
  })
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeRouteLeave(
  () => !dirty.value || window.confirm('笔记尚未保存，确定离开？'),
)
onMounted(() => {
  void run(load)
  ticker = setInterval(() => {
    clock.value = Math.floor(performance.now() / 1000)
  }, 1000)
  window.addEventListener('beforeunload', beforeUnload)
})
onUnmounted(() => {
  if (ticker) clearInterval(ticker)
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>
<template>
  <section class="workspace-heading">
    <p class="eyebrow">STUDY WORKBENCH</p>
    <h1>{{ data?.task.title ?? '学习工作台' }}</h1>
    <div class="row-actions">
      <RouterLink to="/learn">返回学习首页</RouterLink
      ><RouterLink :to="`/practice/${id}`">练习与能力证据</RouterLink
      ><button :disabled="busy" @click="refresh">刷新工作台</button>
    </div>
  </section>
  <p v-if="error" role="alert" class="error-box">{{ error }}</p>
  <p v-if="message" role="status">{{ message }}</p>
  <template v-if="data">
    <section class="tree-panel learning-section">
      <h2>{{ data.node.title }}</h2>
      <p class="plain-text">{{ data.node.description || '暂无节点说明。' }}</p>
      <p v-if="data.node.status === 'ARCHIVED'">
        节点已归档，历史任务与记录仍保留。
      </p>
      <p>
        先修：{{
          data.prerequisites.map((n) => n.title).join('、') || '未设置'
        }}
      </p>
      <p v-if="data.route">
        路线：{{ data.route.route.path.title }} · v{{
          data.route.route.path.versionNumber
        }}
        /
        {{
          data.route.route.stages.find((s) => s.id === routeEntry?.stageId)
            ?.title
        }}
        / {{ routeEntry?.titleSnapshot }} ·
        {{ progressStates[progress.status] }}
      </p>
      <button
        v-if="data.route && progress.status !== 'COMPLETED'"
        :disabled="busy"
        @click="saveProgress"
      >
        标记路线节点已学
      </button>
    </section>
    <section class="tree-panel learning-section" aria-label="学习计时">
      <h2>学习计时</h2>
      <template v-if="current"
        ><p role="timer" class="study-timer">{{ duration(shownSeconds) }}</p>
        <p>
          {{ current.session.state === 'RUNNING' ? '正在计时' : '已暂停' }}。{{
            current.session.state === 'RUNNING'
              ? '离开页面后计时继续，请在休息时暂停。'
              : '暂停期间不累计时间，继续后恢复。'
          }}
        </p>
        <div class="row-actions">
          <button
            v-if="current.session.state === 'RUNNING'"
            :disabled="busy"
            @click="action('PAUSE')"
          >
            暂停计时</button
          ><button v-else :disabled="busy" @click="action('RESUME')">
            继续计时</button
          ><button :disabled="busy" @click="action('FINISH')">
            结束本次学习
          </button>
        </div></template
      ><template v-else-if="data.openSession"
        ><p>另一个任务有未结束的学习时段，请先处理。</p>
        <RouterLink :to="`/study/${data.openSession.session.taskId}`"
          >前往未结束的学习</RouterLink
        ></template
      ><template v-else
        ><p>每个任务可多次学习，结束计时和完成任务分别保存。</p>
        <button
          :disabled="busy || ['DONE', 'CANCELLED'].includes(data.task.status)"
          @click="start"
        >
          开始计时
        </button></template
      >
    </section>
    <fieldset class="learning-fieldset" :disabled="busy">
      <div class="workspace-columns learning-section">
        <form class="editor" @submit.prevent="saveNote">
          <h2>学习笔记</h2>
          <label for="study-note">本次思考与学习记录</label
          ><textarea
            id="study-note"
            v-model="note"
            maxlength="20000"
            rows="12"
          />
          <p>
            {{ dirty ? '有未保存的修改' : '笔记已同步' }} · {{ note.length }} /
            20000
          </p>
          <button>保存笔记</button>
        </form>
        <form class="editor" @submit.prevent="saveTask">
          <h2>任务安排</h2>
          <label for="study-title">任务名称</label
          ><input
            id="study-title"
            v-model="form.title"
            maxlength="160"
            required
          /><label for="study-minutes">预计分钟</label
          ><input
            id="study-minutes"
            v-model.number="form.plannedMinutes"
            type="number"
            min="1"
            max="1440"
            required
          /><label for="study-due">任务截止日期</label
          ><input id="study-due" v-model="form.dueDate" type="date" /><label
            for="study-status"
            >任务状态</label
          ><select id="study-status" v-model="form.status">
            <option
              v-for="(label, status) in taskStates"
              :key="status"
              :value="status"
            >
              {{ label }}
            </option></select
          ><button>保存任务安排</button>
        </form>
      </div>
      <section class="tree-panel learning-section">
        <h2>学习资料</h2>
        <ul class="skill-list">
          <li
            v-for="resource in resources.items"
            :key="resource.id"
            class="skill-card"
          >
            <h3>
              <a
                :href="resource.url"
                target="_blank"
                rel="noopener noreferrer"
                >{{ resource.title }}</a
              >
            </h3>
            <p>
              {{ resource.resourceType }} ·
              {{ resource.status === 'ACTIVE' ? '使用中' : '已归档' }}
            </p>
            <div class="row-actions">
              <button @click="editResource(resource)">
                编辑资料 {{ resource.title }}</button
              ><button @click="resourceState(resource)">
                {{ resource.status === 'ACTIVE' ? '归档' : '恢复' }}资料
                {{ resource.title }}
              </button>
            </div>
          </li>
        </ul>
        <p v-if="!resources.items.length">尚无资料，添加一个参考链接。</p>
        <div class="pagination">
          <button :disabled="resourcePage === 0" @click="page('resource', -1)">
            上一页资料</button
          ><button
            :disabled="(resourcePage + 1) * 20 >= resources.total"
            @click="page('resource', 1)"
          >
            下一页资料
          </button>
        </div>
        <form class="editor inline-editor" @submit.prevent="saveResource">
          <h3>{{ resourceEditing ? '编辑资料' : '添加资料' }}</h3>
          <label for="resource-title">资料名称</label
          ><input
            id="resource-title"
            v-model="resourceForm.title"
            maxlength="160"
            required
          /><label for="resource-url">资料链接</label
          ><input
            id="resource-url"
            v-model="resourceForm.url"
            type="url"
            maxlength="2048"
            required
          /><label for="resource-type">资料类型</label
          ><select id="resource-type" v-model="resourceForm.resourceType">
            <option value="WEB">网页</option>
            <option value="VIDEO">视频</option>
            <option value="BOOK">图书链接</option>
            <option value="OTHER">其他</option></select
          ><button>保存资料</button
          ><button v-if="resourceEditing" type="button" @click="cancelResource">
            取消资料编辑
          </button>
        </form>
      </section>
      <section class="tree-panel learning-section">
        <h2>学习时段历史</h2>
        <p v-if="!history.items.length">尚无学习时段。</p>
        <ul class="skill-list">
          <li
            v-for="view in history.items"
            :key="view.session.id"
            class="skill-card"
          >
            {{ new Date(view.session.startedAt).toLocaleString() }} ·
            {{ duration(view.elapsedSeconds) }} ·
            {{
              view.session.state === 'ENDED'
                ? '已结束'
                : view.session.state === 'PAUSED'
                  ? '已暂停'
                  : '计时中'
            }}
          </li>
        </ul>
        <div class="pagination">
          <button :disabled="historyPage === 0" @click="page('history', -1)">
            上一页时段</button
          ><button
            :disabled="(historyPage + 1) * 20 >= history.total"
            @click="page('history', 1)"
          >
            下一页时段
          </button>
        </div>
      </section>
    </fieldset>
    <TutorPanel :task-id="id" />
  </template>
</template>
