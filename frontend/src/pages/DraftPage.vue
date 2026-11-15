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
  origin: string
  createdAt: string
  updatedAt: string
}
interface Version {
  id: string
  versionNumber: number
  publishedAt: string
  document: string
}
interface Preview {
  warnings: string[]
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
interface EditableNode {
  id: string
  title: string
  nodeType: string
  description: string
  status: string
  parentNodeId: string | null
  displayOrder: number
}
interface EditableRelation {
  id: string
  sourceNodeId: string
  targetNodeId: string
  relationType: string
}
const editNodeId = ref(''),
  editNode = ref<EditableNode | null>(null),
  editRelationId = ref('')
const relationEdit = ref({
  sourceNodeId: '',
  targetNodeId: '',
  relationType: 'PREREQUISITE_OF',
})
const relationOptions = computed<EditableRelation[]>(() => {
  try {
    const v = JSON.parse(text.value).relations
    return Array.isArray(v)
      ? v.filter(
          (r) =>
            r &&
            typeof r.id === 'string' &&
            typeof r.sourceNodeId === 'string' &&
            typeof r.targetNodeId === 'string',
        )
      : []
  } catch {
    return []
  }
})
function chooseNode() {
  try {
    editNode.value = structuredClone(
      JSON.parse(text.value).nodes.find(
        (n: EditableNode) => n.id === editNodeId.value,
      ) ?? null,
    )
  } catch {
    editNode.value = null
  }
}
function applyNode() {
  try {
    const doc = JSON.parse(text.value)
    const index = doc.nodes.findIndex(
      (n: EditableNode) => n.id === editNodeId.value,
    )
    if (index < 0 || !editNode.value) throw new Error()
    doc.nodes[index] = {
      ...editNode.value,
      parentNodeId: editNode.value.parentNodeId || null,
    }
    replace(JSON.stringify(doc, null, 2))
  } catch {
    error.value = '请先修复JSON并选择有效节点。'
  }
}
function removeNode() {
  if (
    !editNode.value ||
    !window.confirm(
      '从草案移出节点，并提升其子节点、移除草案内相关关系？正式节点定义和证据会保留，发布前可撤销。',
    )
  )
    return
  try {
    const doc = JSON.parse(text.value),
      old = doc.nodes.find((n: EditableNode) => n.id === editNodeId.value)
    if (!old) throw new Error()
    doc.nodes = doc.nodes
      .filter((n: EditableNode) => n.id !== old.id)
      .map((n: EditableNode) => ({
        ...n,
        parentNodeId:
          n.parentNodeId === old.id ? old.parentNodeId : n.parentNodeId,
      }))
    doc.relations = doc.relations.filter(
      (r: EditableRelation) =>
        r.sourceNodeId !== old.id && r.targetNodeId !== old.id,
    )
    replace(JSON.stringify(doc, null, 2))
    editNodeId.value = ''
    editNode.value = null
  } catch {
    error.value = '请先修复JSON并选择有效节点。'
  }
}
function chooseRelation() {
  const r = relationOptions.value.find((r) => r.id === editRelationId.value)
  relationEdit.value = r
    ? {
        sourceNodeId: r.sourceNodeId,
        targetNodeId: r.targetNodeId,
        relationType: r.relationType,
      }
    : { sourceNodeId: '', targetNodeId: '', relationType: 'PREREQUISITE_OF' }
}
function applyRelation(remove = false) {
  try {
    const doc = JSON.parse(text.value)
    if (editRelationId.value)
      doc.relations = doc.relations.filter(
        (r: EditableRelation) => r.id !== editRelationId.value,
      )
    if (!remove)
      doc.relations.push({
        id: editRelationId.value || crypto.randomUUID(),
        ...relationEdit.value,
      })
    replace(JSON.stringify(doc, null, 2))
    editRelationId.value = ''
    chooseRelation()
  } catch {
    error.value = '请先修复JSON结构。'
  }
}
const jointScope = ref(false)
function changeScope() {
  preview.value = null
  confirmed.value = false
}
const operation = ref('MERGE'),
  sourceNode = ref(''),
  targetNode = ref(''),
  splitTitles = ref('')
const mappings = computed<
  {
    id: string
    kind: string
    originals: { title: string }[]
    targets: string[]
  }[]
>(() => {
  try {
    const values = JSON.parse(text.value).mappings
    return Array.isArray(values)
      ? values.filter(
          (m) =>
            m &&
            typeof m.id === 'string' &&
            Array.isArray(m.originals) &&
            m.originals.every(
              (n: { title: unknown }) => n && typeof n.title === 'string',
            ) &&
            Array.isArray(m.targets) &&
            m.targets.every((id: unknown) => typeof id === 'string'),
        )
      : []
  } catch {
    return []
  }
})
async function transform() {
  await run(async () => {
    if (!current.value || dirty.value) return
    const d = await api<Draft>(
      `/graph/drafts/${current.value.id}/transform`,
      'POST',
      {
        revision: current.value.revision,
        kind: operation.value,
        source: sourceNode.value,
        target: operation.value === 'MERGE' ? targetNode.value : null,
        titles:
          operation.value === 'SPLIT'
            ? splitTitles.value
                .split('\n')
                .map((t) => t.trim())
                .filter(Boolean)
            : [],
      },
    )
    replace(JSON.stringify(JSON.parse(d.document), null, 2))
    current.value = d
    saved.value = text.value
    sourceNode.value = ''
    targetNode.value = ''
    splitTitles.value = ''
    await refresh()
    notice.value = '合并拆分已保存到草案，尚未发布；可撤销后保存。'
  })
}
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
  editNodeId.value = ''
  editNode.value = null
  editRelationId.value = ''
  chooseRelation()
  jointScope.value = false
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
  editNodeId.value = ''
  editNode.value = null
  editRelationId.value = ''
  chooseRelation()
  jointScope.value = false
  preview.value = null
  confirmed.value = false
}
function redoEdit() {
  if (!redo.value.length) return
  undo.value.push(text.value)
  text.value = redo.value.pop()!
  editNodeId.value = ''
  editNode.value = null
  editRelationId.value = ''
  chooseRelation()
  jointScope.value = false
  preview.value = null
  confirmed.value = false
}
function select(draft: Draft) {
  if (dirty.value && !window.confirm('切换会丢弃当前未保存的编辑，继续？'))
    return
  current.value = draft
  editNodeId.value = ''
  editNode.value = null
  editRelationId.value = ''
  chooseRelation()
  jointScope.value = false
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
        { includeAffectedSkills: jointScope.value },
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
      {
        revision: current.value.revision,
        confirmed: true,
        includeAffectedSkills: jointScope.value,
      },
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
          <button :disabled="busy" @click="select(d)">
            打开草案 {{ d.id.slice(0, 8) }}
          </button>
          <p>
            revision {{ d.revision }} ·
            {{ d.status === 'PUBLISHED' ? '已发布' : '编辑中' }}
          </p>
          <p>
            来源：{{
              d.origin === 'AI_ASSISTED'
                ? 'AI候选与人工编辑'
                : d.origin === 'AI_BASE'
                  ? 'AI任务基础草案（未成功生成）'
                  : d.origin === 'JOINT_PUBLISHED'
                    ? '联合发布'
                    : '手动'
            }}
            · 更新于 {{ new Date(d.updatedAt).toLocaleString() }}
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
          <small>发布于 {{ new Date(v.publishedAt).toLocaleString() }}</small>
        </li>
      </ul>
      <pre v-if="history" class="snapshot" aria-label="历史版本快照">{{
        history
      }}</pre>
    </section>
    <section v-if="current" class="editor">
      <h2>草案编辑 · revision {{ current.revision }}</h2>
      <template v-if="current.status === 'EDITING'">
        <details>
          <summary>编辑节点与位置</summary>
          <label for="draft-edit-node">选择编辑节点</label
          ><select
            id="draft-edit-node"
            v-model="editNodeId"
            @change="chooseNode"
          >
            <option value="">选择节点</option>
            <option v-for="n in nodes" :key="n.id" :value="n.id">
              {{ n.title }}
            </option>
          </select>
          <form
            v-if="editNode"
            class="editor inline-editor"
            @submit.prevent="applyNode"
          >
            <label for="draft-edit-title">草案节点名称</label
            ><input
              id="draft-edit-title"
              v-model="editNode.title"
              required
              maxlength="160"
            />
            <label for="draft-edit-description">草案节点描述</label
            ><textarea
              id="draft-edit-description"
              v-model="editNode.description"
              maxlength="8000"
              rows="3"
            ></textarea>
            <label for="draft-edit-type">草案节点类型</label
            ><select id="draft-edit-type" v-model="editNode.nodeType">
              <option value="CONCEPT">知识点</option>
              <option value="CATEGORY">分类</option>
              <option value="PRACTICE">实践</option>
            </select>
            <label for="draft-edit-status">草案节点状态</label
            ><select id="draft-edit-status" v-model="editNode.status">
              <option value="ACTIVE">使用中</option>
              <option value="ARCHIVED">归档</option>
            </select>
            <label for="draft-edit-parent">草案父节点</label
            ><select id="draft-edit-parent" v-model="editNode.parentNodeId">
              <option :value="null">顶层</option>
              <option
                v-for="n in nodes.filter((n) => n.id !== editNodeId)"
                :key="n.id"
                :value="n.id"
              >
                {{ n.title }}
              </option>
            </select>
            <label for="draft-edit-order">草案显示顺序</label
            ><input
              id="draft-edit-order"
              v-model.number="editNode.displayOrder"
              type="number"
              min="0"
              max="2147483647"
              required
            />
            <button :disabled="busy">应用节点编辑</button
            ><button type="button" :disabled="busy" @click="removeNode">
              从草案移出节点
            </button>
          </form>
        </details>
        <details>
          <summary>编辑依赖与关联</summary>
          <form
            class="editor inline-editor"
            @submit.prevent="applyRelation(false)"
          >
            <label for="draft-relation">选择编辑关系</label
            ><select
              id="draft-relation"
              v-model="editRelationId"
              @change="chooseRelation"
            >
              <option value="">新建关系</option>
              <option v-for="r in relationOptions" :key="r.id" :value="r.id">
                {{ nodes.find((n) => n.id === r.sourceNodeId)?.title }} →
                {{ nodes.find((n) => n.id === r.targetNodeId)?.title }}
              </option>
            </select>
            <label for="draft-relation-source">草案关系起点</label
            ><select
              id="draft-relation-source"
              v-model="relationEdit.sourceNodeId"
              required
            >
              <option value="" disabled>选择节点</option>
              <option v-for="n in nodes" :key="n.id" :value="n.id">
                {{ n.title }}
              </option>
            </select>
            <label for="draft-relation-target">草案关系终点</label
            ><select
              id="draft-relation-target"
              v-model="relationEdit.targetNodeId"
              required
            >
              <option value="" disabled>选择节点</option>
              <option v-for="n in nodes" :key="n.id" :value="n.id">
                {{ n.title }}
              </option>
            </select>
            <label for="draft-relation-type">草案关系类型</label
            ><select
              id="draft-relation-type"
              v-model="relationEdit.relationType"
            >
              <option value="PREREQUISITE_OF">先修</option>
              <option value="RELATED_TO">关联</option>
            </select>
            <button :disabled="busy">应用关系编辑</button
            ><button
              type="button"
              :disabled="busy || !editRelationId"
              @click="applyRelation(true)"
            >
              从草案移除关系
            </button>
          </form>
        </details>
      </template>
      <form
        v-if="current.status === 'EDITING'"
        class="editor inline-editor"
        @submit.prevent="transform"
      >
        <h3>合并与拆分</h3>
        <p>
          先保存当前编辑。只修改草案并记录来源，旧节点定义和历史保留。合并会合并重叠关系并移除自环；拆分把原关系和子节点先连接到第一个新节点，请在发布预览前检查。
        </p>
        <label for="transform-kind">转换操作</label
        ><select id="transform-kind" v-model="operation" :disabled="busy">
          <option value="MERGE">合并到已有节点</option>
          <option value="SPLIT">拆分成新节点</option>
        </select>
        <label for="transform-source">转换源节点</label
        ><select
          id="transform-source"
          v-model="sourceNode"
          required
          :disabled="busy"
        >
          <option value="" disabled>选择源节点</option>
          <option v-for="n in nodes" :key="n.id" :value="n.id">
            {{ n.title }}
          </option>
        </select>
        <template v-if="operation === 'MERGE'"
          ><label for="transform-target">合并保留节点</label
          ><select
            id="transform-target"
            v-model="targetNode"
            required
            :disabled="busy"
          >
            <option value="" disabled>选择保留节点</option>
            <option
              v-for="n in nodes.filter((n) => n.id !== sourceNode)"
              :key="n.id"
              :value="n.id"
            >
              {{ n.title }}
            </option>
          </select></template
        >
        <template v-else
          ><label for="split-titles">拆分名称（每行一个）</label
          ><textarea
            id="split-titles"
            v-model="splitTitles"
            rows="3"
            maxlength="1280"
            required
            :disabled="busy"
          ></textarea>
          <p>填写2至8个不同名称。将生成新ID。</p></template
        >
        <button :disabled="busy || dirty || !sourceNode">
          应用转换并保存草案
        </button>
      </form>
      <section v-if="mappings.length" aria-label="节点来源映射">
        <h3>节点来源映射</h3>
        <p>
          草案中的用户编辑记录；来源快照随发布版本保留，不自动迁移旧证据。撤销会一并还原映射。
        </p>
        <ul>
          <li v-for="m in mappings" :key="m.id">
            {{ m.kind === 'MERGE' ? '合并' : '拆分' }}：{{
              m.originals.map((n) => n.title).join('、')
            }}
            →
            {{
              m.targets
                .map(
                  (id) =>
                    nodes.find((n) => n.id === id)?.title ??
                    `已移出当前草案 ${id.slice(0, 8)}`,
                )
                .join('、')
            }}
          </li>
        </ul>
      </section>
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
        :disabled="busy"
        rows="20"
        spellcheck="false"
        @input="replace(($event.target as HTMLTextAreaElement).value)"
      ></textarea>
      <div v-if="current.status === 'EDITING'" class="row-actions">
        <label
          ><input
            v-model="jointScope"
            :disabled="busy"
            type="checkbox"
            @change="changeScope"
          />允许联合发布受影响的其他已发布技能（必须重新预览）</label
        >
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
        <section v-if="preview.warnings.length" aria-label="重复候选提示">
          <p>
            名称按字符规范化、空白和大小写识别，仅作疑似重复提示，不是语义判断。
          </p>
          <ul>
            <li v-for="warning in preview.warnings" :key="warning">
              {{ warning }}
            </li>
          </ul>
        </section>
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
