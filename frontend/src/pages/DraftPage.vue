<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { api, errorMessage } from '../api/client'
import type { Graph } from '../types/catalog'
interface Draft {
  id: string
  skillId: string
  document: string
  revision: number
  status: string
}
interface Version {
  id: string
  versionNumber: number
  document: string
}
interface Preview {
  valid: boolean
  issues: string[]
  potentiallyAffectedSkills: string[]
  sourceChanged: boolean
  nodeCount: number
  relationCount: number
}
const skillId = String(useRoute().params.id)
const generationEnabled = ref(false),
  topic = ref(''),
  generationConfirmed = ref(false)
let pendingGeneration: { topic: string; key: string } | null = null
async function generate() {
  await run(async () => {
    if (!generationEnabled.value || !generationConfirmed.value || dirty.value)
      return
    if (!pendingGeneration || pendingGeneration.topic !== topic.value)
      pendingGeneration = {
        topic: topic.value,
        key: `gen-${crypto.randomUUID()}`,
      }
    const job = await api<{
      state: string
      draftId: string
      errorCode: string | null
    }>(
      `/skills/${skillId}/graph/generation`,
      'POST',
      { topic: pendingGeneration.topic },
      { 'Idempotency-Key': pendingGeneration.key },
    )
    pendingGeneration = null
    await refresh()
    const draft = drafts.value.find((d) => d.id === job.draftId)
    if (draft) select(draft)
    generationConfirmed.value = false
    if (job.state === 'SUCCEEDED')
      notice.value = '候选草案已生成，尚未发布，请校验并审核。'
    else
      error.value = `生成未完成：${job.errorCode ?? job.state}。请查看任务记录；手动草案仍可使用。`
  })
}
const drafts = ref<Draft[]>([]),
  versions = ref<Version[]>([]),
  current = ref<Draft | null>(null),
  text = ref(''),
  saved = ref(''),
  error = ref(''),
  notice = ref(''),
  busy = ref(false),
  preview = ref<Preview | null>(null),
  confirmed = ref(false),
  history = ref('')
const undo = ref<string[]>([]),
  redo = ref<string[]>([]),
  title = ref(''),
  parent = ref('')
const dirty = computed(() => text.value !== saved.value)
const live = ref<Graph | null>(null)
const impactNames = ref<Record<string, string>>({})
const changes = computed(() => {
  try {
    const doc = JSON.parse(text.value)
    if (!Array.isArray(doc.nodes)) return []
    const old = new Map(live.value?.nodes.map((n) => [n.id, n]))
    const ids = new Set(doc.nodes.map((n: { id: string }) => n.id))
    const result: string[] = []
    for (const n of doc.nodes) {
      const before = old.get(n.id)
      if (!before) result.push(`新增节点：${n.title}`)
      else if (
        [
          'title',
          'nodeType',
          'description',
          'status',
          'parentNodeId',
          'displayOrder',
        ].some(
          (k) => (before as unknown as Record<string, unknown>)[k] !== n[k],
        )
      )
        result.push(`修改节点或位置：${before.title} → ${n.title}`)
    }
    for (const n of old.values())
      if (!ids.has(n.id)) result.push(`移出技能（保留节点定义）：${n.title}`)
    if (JSON.stringify(doc.relations) !== JSON.stringify(live.value?.relations))
      result.push(`关系将按草案替换为 ${doc.relations?.length ?? 0} 条`)
    return result
  } catch {
    return []
  }
})
const nodes = computed<{ id: string; title: string }[]>(() => {
  try {
    const value = JSON.parse(text.value).nodes
    return Array.isArray(value)
      ? value.filter(
          (n) => n && typeof n.id === 'string' && typeof n.title === 'string',
        )
      : []
  } catch {
    return []
  }
})
function replace(value: string) {
  undo.value.push(text.value)
  if (undo.value.length > 100) undo.value.shift()
  while (undo.value.reduce((sum, s) => sum + s.length, 0) > 10_000_000)
    undo.value.shift()
  redo.value = []
  text.value = value
  preview.value = null
  confirmed.value = false
}
function undoEdit() {
  if (!undo.value.length) return
  redo.value.push(text.value)
  text.value = undo.value.pop()!
  preview.value = null
  confirmed.value = false
}
function redoEdit() {
  if (!redo.value.length) return
  undo.value.push(text.value)
  text.value = redo.value.pop()!
  preview.value = null
  confirmed.value = false
}
function select(draft: Draft) {
  if (dirty.value && !window.confirm('切换会丢弃当前未保存的编辑，继续？'))
    return
  current.value = draft
  text.value = JSON.stringify(JSON.parse(draft.document), null, 2)
  saved.value = text.value
  undo.value = []
  redo.value = []
  preview.value = null
  confirmed.value = false
  error.value = ''
}
async function refresh() {
  live.value = await api(`/skills/${skillId}/graph`)
  drafts.value = await api(`/skills/${skillId}/graph/drafts`)
  versions.value = await api(`/skills/${skillId}/graph/versions`)
  try {
    generationEnabled.value = (
      await api<{ enabled: boolean }>('/ai/generation')
    ).enabled
  } catch {
    generationEnabled.value = false
  }
}
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
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
    const d = await api<Draft>(`/skills/${skillId}/graph/drafts`, 'POST', {})
    await refresh()
    select(d)
  })
}
async function save() {
  await run(async () => {
    if (!current.value) return
    const document = JSON.parse(text.value)
    const d = await api<Draft>(`/graph/drafts/${current.value.id}`, 'PATCH', {
      revision: current.value.revision,
      document,
    })
    current.value = d
    text.value = JSON.stringify(JSON.parse(d.document), null, 2)
    saved.value = text.value
    preview.value = null
    confirmed.value = false
    await refresh()
    notice.value = '草案已保存，正式图谱未改变。'
  })
}
async function validate() {
  await run(async () => {
    if (current.value)
      preview.value = await api(
        `/graph/drafts/${current.value.id}/validate`,
        'POST',
        {},
      )
    if (preview.value) {
      const results = await Promise.allSettled(
        preview.value.potentiallyAffectedSkills.map(async (id) => ({
          id,
          skill: await api<{ name: string }>(`/skills/${id}`),
        })),
      )
      impactNames.value = Object.fromEntries(
        results
          .filter((r) => r.status === 'fulfilled')
          .map((r) => {
            const value = (
              r as PromiseFulfilledResult<{
                id: string
                skill: { name: string }
              }>
            ).value
            return [value.id, value.skill.name]
          }),
      )
    }
  })
}
async function publish() {
  await run(async () => {
    if (
      !current.value ||
      dirty.value ||
      !preview.value?.valid ||
      !confirmed.value
    )
      return
    await api(
      `/graph/drafts/${current.value.id}/publish`,
      'POST',
      { revision: current.value.revision, confirmed: true },
      {
        'Idempotency-Key': `draft-${current.value.id}-${current.value.revision}`,
      },
    )
    await refresh()
    current.value = drafts.value.find((d) => d.id === current.value?.id) ?? null
    notice.value = '版本已发布。'
    preview.value = null
    confirmed.value = false
  })
}
function addNode() {
  try {
    const doc = JSON.parse(text.value)
    doc.nodes.push({
      id: crypto.randomUUID(),
      title: title.value,
      nodeType: 'CONCEPT',
      description: '',
      status: 'ACTIVE',
      parentNodeId: parent.value || null,
      displayOrder: doc.nodes.length,
    })
    replace(JSON.stringify(doc, null, 2))
    title.value = ''
  } catch {
    error.value = '请先修复JSON结构。'
  }
}
onMounted(() => run(refresh))
onBeforeRouteLeave(
  () =>
    !dirty.value || window.confirm('草案尚未保存，确认离开并丢弃这些修改？'),
)
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onUnmounted(() => window.removeEventListener('beforeunload', beforeUnload))
</script>
<template>
  <section class="workspace-heading">
    <RouterLink :to="`/skills/${skillId}`">← 返回知识树</RouterLink>
    <p class="eyebrow">DRAFTS & VERSIONS</p>
    <h1>知识草案与发布</h1>
    <p>
      草案保存不修改正式图谱。共享节点的发布修改可能影响其他技能，请审核后确认。
    </p>
  </section>
  <p v-if="error" role="alert" class="error-box">{{ error }}</p>
  <p v-if="notice" role="status">{{ notice }}</p>
  <div class="row-actions">
    <button :disabled="busy" @click="create">从当前图谱创建草案</button>
  </div>
  <section class="editor" aria-label="AI候选生成">
    <h2>AI 候选草案</h2>
    <p v-if="!generationEnabled">生成尚未启用，可继续使用手动草案。</p>
    <form v-else class="editor inline-editor" @submit.prevent="generate">
      <label for="generation-topic">学习主题与目标</label>
      <textarea
        id="generation-topic"
        v-model="topic"
        required
        maxlength="4000"
        rows="3"
        :disabled="busy"
      ></textarea>
      <p>
        主题会发送给服务器配置的模型。生成可能产生费用，结果仅进入草案，不自动发布。
      </p>
      <label
        ><input
          v-model="generationConfirmed"
          type="checkbox"
          :disabled="busy"
        />我确认使用已配置模型和预算生成候选草案</label
      >
      <button
        class="primary"
        :disabled="busy || dirty || !generationConfirmed || !topic.trim()"
      >
        生成候选草案
      </button>
    </form>
    <RouterLink to="/ai/jobs">查看生成任务记录</RouterLink>
  </section>
  <div class="workspace-columns">
    <section>
      <h2>草案列表</h2>
      <ul class="skill-list">
        <li v-for="d in drafts" :key="d.id" class="skill-card">
          <button @click="select(d)">打开草案 {{ d.id.slice(0, 8) }}</button>
          <p>
            revision {{ d.revision }} ·
            {{ d.status === 'PUBLISHED' ? '已发布' : '编辑中' }}
          </p>
        </li>
      </ul>
      <p v-if="!drafts.length" class="help-text">尚无草案。</p>
      <h2>已发布版本</h2>
      <ul class="relation-list">
        <li v-for="v in versions" :key="v.id">
          <button
            @click="history = JSON.stringify(JSON.parse(v.document), null, 2)"
          >
            查看版本 {{ v.versionNumber }}
          </button>
        </li>
      </ul>
      <pre v-if="history" class="snapshot" aria-label="历史版本快照">{{
        history
      }}</pre>
    </section>
    <section v-if="current" class="editor">
      <h2>草案编辑 · revision {{ current.revision }}</h2>
      <template v-if="current.status === 'EDITING'"
        ><form class="editor inline-editor" @submit.prevent="addNode">
          <label for="draft-node-title">新节点名称</label
          ><input
            id="draft-node-title"
            v-model="title"
            maxlength="160"
            required
          /><label for="draft-parent">父节点</label
          ><select id="draft-parent" v-model="parent">
            <option value="">顶层</option>
            <option v-for="n in nodes" :key="n.id" :value="n.id">
              {{ n.title }}
            </option></select
          ><button :disabled="busy">添加草案节点</button>
        </form></template
      >
      <label for="draft-json">高级 JSON 编辑（节点、位置与关系）</label
      ><textarea
        id="draft-json"
        class="json-editor"
        :value="text"
        :readonly="current.status === 'PUBLISHED'"
        rows="20"
        spellcheck="false"
        @input="replace(($event.target as HTMLTextAreaElement).value)"
      ></textarea>
      <div v-if="current.status === 'EDITING'" class="row-actions">
        <button :disabled="!undo.length || busy" @click="undoEdit">撤销</button
        ><button :disabled="!redo.length || busy" @click="redoEdit">重做</button
        ><button :disabled="busy || !dirty" @click="save">保存草案</button
        ><button :disabled="busy || dirty" @click="validate">
          校验与发布预览
        </button>
      </div>
      <p class="help-text">
        {{ dirty ? '有未保存的修改，请先保存再预览。' : '草案已保存。' }}
        若正式数据已变化，需创建新草案，旧草案会保留。
      </p>
      <section v-if="preview" aria-label="发布预览">
        <h2>发布预览</h2>
        <p>
          {{ preview.nodeCount }} 个节点 · {{ preview.relationCount }} 条关系
        </p>
        <ul>
          <li v-for="change in changes" :key="change">{{ change }}</li>
        </ul>
        <ul>
          <li v-for="issue in preview.issues" :key="issue" role="alert">
            {{ issue }}
          </li>
        </ul>
        <p>潜在影响的技能（保守列出所有引用技能）：</p>
        <ul>
          <li v-for="id in preview.potentiallyAffectedSkills" :key="id">
            <RouterLink :to="`/skills/${id}`">{{
              impactNames[id] ?? id
            }}</RouterLink>
          </li>
        </ul>
        <label
          ><input v-model="confirmed" type="checkbox" />
          我已审核节点、关系与跨技能影响，确认发布</label
        ><button
          class="primary"
          :disabled="busy || !preview.valid || !confirmed || dirty"
          @click="publish"
        >
          确认发布版本
        </button>
      </section>
    </section>
  </div>
</template>
<style scoped>
.snapshot {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 12px;
  max-height: 600px;
  overflow: auto;
}
.json-editor {
  font-family: monospace;
  font-size: 13px;
}
.row-actions {
  margin: 18px 0;
}
input[type='checkbox'] {
  width: auto;
  margin-right: 8px;
}
</style>
