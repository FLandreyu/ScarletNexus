<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, errorMessage } from '../api/client'
import type {
  Graph,
  KnowledgeNode,
  Page,
  PlacedNode,
  Relation,
} from '../types/catalog'
const route = useRoute(),
  skillId = String(route.params.id)
const graph = ref<Graph | null>(null),
  error = ref(''),
  busy = ref(false)
const library = ref<KnowledgeNode[]>([]),
  libraryQuery = ref(''),
  mode = ref('new'),
  reference = ref('')
const selected = ref<string | null>(null)
const editorForm = ref<HTMLFormElement | null>(null)
function focusEditor() {
  void nextTick(() => {
    editorForm.value?.scrollIntoView({ block: 'start' })
    editorForm.value?.querySelector('input')?.focus({ preventScroll: true })
  })
}
function beginCreate() {
  reset()
  focusEditor()
}
const definition = ref({
  title: '',
  nodeType: 'CONCEPT',
  description: '',
  status: 'ACTIVE',
})
const position = ref({ parentNodeId: '', displayOrder: 0 })
const relationForm = ref({
  sourceNodeId: '',
  targetNodeId: '',
  relationType: 'PREREQUISITE_OF',
})
const relationId = ref<string | null>(null)
const titleMap = computed(
  () => new Map(graph.value?.nodes.map((n) => [n.id, n.title])),
)
const flattened = computed(() => {
  const result: { node: PlacedNode; depth: number }[] = [],
    seen = new Set<string>()
  function visit(parent: string | null, depth: number) {
    for (const node of graph.value?.nodes.filter(
      (n) => n.parentNodeId === parent,
    ) ?? []) {
      if (seen.has(node.id)) continue
      seen.add(node.id)
      result.push({ node, depth: Math.min(depth, 8) })
      visit(node.id, depth + 1)
    }
  }
  visit(null, 0)
  return result
})
async function load() {
  graph.value = await api(`/skills/${skillId}/graph`)
}
async function findLibrary() {
  try {
    library.value = (
      await api<Page<KnowledgeNode>>(
        `/knowledge/nodes?q=${encodeURIComponent(libraryQuery.value)}&size=100`,
      )
    ).items
  } catch (e) {
    error.value = errorMessage(e)
  }
}
function reset() {
  selected.value = null
  definition.value = {
    title: '',
    nodeType: 'CONCEPT',
    description: '',
    status: 'ACTIVE',
  }
  position.value = { parentNodeId: '', displayOrder: 0 }
  mode.value = 'new'
  reference.value = ''
}
function edit(node: PlacedNode) {
  selected.value = node.id
  definition.value = {
    title: node.title,
    nodeType: node.nodeType,
    description: node.description,
    status: node.status,
  }
  position.value = {
    parentNodeId: node.parentNodeId ?? '',
    displayOrder: node.displayOrder,
  }
  focusEditor()
}
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await action()
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function saveDefinition() {
  await run(async () => {
    if (selected.value) {
      await api(`/knowledge/nodes/${selected.value}`, 'PATCH', definition.value)
      return
    }
    let nodeId = reference.value
    if (mode.value === 'new') {
      const node = await api<KnowledgeNode>(
        '/knowledge/nodes',
        'POST',
        definition.value,
      )
      nodeId = node.id
      reference.value = node.id
      mode.value = 'reuse'
      await findLibrary()
    }
    try {
      await api(`/skills/${skillId}/nodes`, 'POST', {
        nodeId,
        parentNodeId: position.value.parentNodeId || null,
        displayOrder: position.value.displayOrder,
      })
    } catch (e) {
      throw new Error(
        errorMessage(e) + ' 节点定义仍保留在节点库，可选择“引用已有节点”重试。',
      )
    }
    reset()
  })
}
async function move() {
  await run(async () => {
    await api(`/skills/${skillId}/nodes/${selected.value}`, 'PATCH', {
      parentNodeId: position.value.parentNodeId || null,
      displayOrder: position.value.displayOrder,
    })
  })
}
async function detach(node: PlacedNode) {
  if (
    !window.confirm(`移出“${node.title}”在当前技能中的引用？节点定义会保留。`)
  )
    return
  await run(async () => {
    await api(`/skills/${skillId}/nodes/${node.id}`, 'DELETE')
    if (selected.value === node.id) reset()
  })
}
function editRelation(relation: Relation) {
  relationId.value = relation.id
  relationForm.value = {
    sourceNodeId: relation.sourceNodeId,
    targetNodeId: relation.targetNodeId,
    relationType: relation.relationType,
  }
}
async function saveRelation() {
  await run(async () => {
    await api(
      relationId.value
        ? `/knowledge/relations/${relationId.value}`
        : '/knowledge/relations',
      relationId.value ? 'PATCH' : 'POST',
      relationForm.value,
    )
    relationId.value = null
  })
}
async function deleteRelation(id: string) {
  await run(async () => {
    await api(`/knowledge/relations/${id}`, 'DELETE')
    if (relationId.value === id) relationId.value = null
  })
}
onMounted(async () => {
  try {
    await load()
    await findLibrary()
  } catch (e) {
    error.value = errorMessage(e)
  }
})
</script>
<template>
  <section class="workspace-heading">
    <RouterLink to="/skills">← 技能空间</RouterLink>
    <p class="eyebrow">KNOWLEDGE TREE</p>
    <h1>{{ graph?.skill.name ?? '知识树' }}</h1>
    <RouterLink :to="`/skills/${skillId}/drafts`">草案与版本历史 →</RouterLink>
    <p>{{ graph?.skill.description }}</p>
  </section>
  <p v-if="error" role="alert" class="error-box">{{ error }}</p>
  <p v-if="graph?.versioned" class="help-text">
    此图谱已发布，请通过草案审核修改。
  </p>
  <div
    v-if="graph"
    class="workspace-columns"
    :style="graph.versioned ? { gridTemplateColumns: 'minmax(0, 1fr)' } : {}"
  >
    <div>
      <section class="tree-panel" aria-label="知识树">
        <h2>知识树</h2>
        <div class="row-actions">
          <button :disabled="graph.versioned" @click="beginCreate">
            添加节点
          </button>
        </div>
        <p v-if="!graph.nodes.length" class="empty-state">
          添加第一个知识节点。
        </p>
        <ul class="tree">
          <li
            v-for="{ node, depth } in flattened"
            :key="node.id"
            :style="{ paddingLeft: `${Math.min(depth * 14, 70)}px` }"
          >
            <article class="node-row">
              <div>
                <strong>{{ node.title }}</strong><small
                  >{{
                    node.nodeType === 'CATEGORY'
                      ? '分类'
                      : node.nodeType === 'PRACTICE'
                        ? '实践'
                        : '知识点'
                  }}
                  · {{ node.status === 'ARCHIVED' ? '已归档' : '使用中' }} ·
                  顺序 {{ node.displayOrder }}</small
                >
              </div>
              <div class="row-actions">
                <button
                  :disabled="graph.versioned"
                  @click="edit(node)"
                  :aria-label="`编辑节点 ${node.title}`"
                >
                  编辑</button
                ><button
                  :disabled="busy || graph.versioned"
                  @click="detach(node)"
                  :aria-label="`移出节点 ${node.title}`"
                >
                  移出
                </button>
              </div>
            </article>
          </li>
        </ul>
      </section>
      <section class="tree-panel">
        <h2>依赖与关联</h2>
        <p class="help-text">先修 → 后继。先修关系不能成环。</p>
        <ul class="relation-list">
          <li v-for="relation in graph.relations" :key="relation.id">
            <span
              >{{ titleMap.get(relation.sourceNodeId) }} →
              {{ titleMap.get(relation.targetNodeId) }}
              <small>{{
                relation.relationType === 'PREREQUISITE_OF' ? '先修' : '关联'
              }}</small></span
            >
            <div class="row-actions">
              <button
                :disabled="graph.versioned"
                @click="editRelation(relation)"
              >
                修改关系</button
              ><button
                :disabled="busy || graph.versioned"
                @click="deleteRelation(relation.id)"
              >
                移除关系
              </button>
            </div>
          </li>
        </ul>
        <p v-if="!graph.relations.length" class="help-text">
          当前技能还没有关系。
        </p>
        <form
          v-if="!graph.versioned"
          class="editor inline-editor"
          @submit.prevent="saveRelation"
        >
          <label for="relation-source">起点 / 先修节点</label
          ><select
            id="relation-source"
            v-model="relationForm.sourceNodeId"
            required
          >
            <option value="" disabled>选择起点</option>
            <option
              v-for="node in graph.nodes.filter((n) => n.status === 'ACTIVE')"
              :key="node.id"
              :value="node.id"
            >
              {{ node.title }}
            </option></select
          ><label for="relation-target">终点 / 后继节点</label
          ><select
            id="relation-target"
            v-model="relationForm.targetNodeId"
            required
          >
            <option value="" disabled>选择终点</option>
            <option
              v-for="node in graph.nodes.filter((n) => n.status === 'ACTIVE')"
              :key="node.id"
              :value="node.id"
            >
              {{ node.title }}
            </option></select
          ><label for="relation-type">关系类型</label
          ><select id="relation-type" v-model="relationForm.relationType">
            <option value="PREREQUISITE_OF">先修关系</option>
            <option value="RELATED_TO">相关知识</option></select
          ><button class="primary" :disabled="busy || graph.versioned">
            {{ relationId ? '保存关系' : '建立关系' }}</button
          ><button v-if="relationId" type="button" @click="relationId = null">
            取消修改关系
          </button>
        </form>
      </section>
    </div>
    <div>
      <form
        v-if="!graph.versioned"
        ref="editorForm"
        class="editor"
        @submit.prevent="saveDefinition"
      >
        <h2>{{ selected ? '编辑节点定义' : '添加节点' }}</h2>
        <template v-if="!selected"
          ><label for="node-mode">添加方式</label
          ><select id="node-mode" v-model="mode">
            <option value="new">创建新节点</option>
            <option value="reuse">引用已有节点</option>
          </select></template
        >
        <template v-if="mode === 'new' || selected"
          ><label for="node-title">节点名称</label
          ><input
            id="node-title"
            v-model="definition.title"
            required
            maxlength="160"
          /><label for="node-type">节点类型</label
          ><select id="node-type" v-model="definition.nodeType">
            <option value="CONCEPT">知识点</option>
            <option value="CATEGORY">分类</option>
            <option value="PRACTICE">实践</option></select
          ><label for="node-description">节点描述</label
          ><textarea
            id="node-description"
            v-model="definition.description"
            rows="3"
            maxlength="8000"
          ></textarea
          ><template v-if="selected"
            ><label for="node-status">节点状态（所有引用同步）</label
            ><select id="node-status" v-model="definition.status">
              <option value="ACTIVE">使用中</option>
              <option value="ARCHIVED">归档（可恢复）</option>
            </select>
            <p class="help-text">
              修改定义会同步到所有引用此节点的技能，节点 ID 保持不变。
            </p></template
          ></template
        >
        <template v-else
          ><label for="library-query">查找节点库</label
          ><input
            id="library-query"
            v-model="libraryQuery"
            maxlength="160"
          /><button type="button" @click="findLibrary">查找已有节点</button
          ><label for="node-reference"
            >选择已有节点（最多显示100项，可缩小搜索）</label
          ><select id="node-reference" v-model="reference" required>
            <option value="" disabled>选择节点</option>
            <option v-for="node in library" :key="node.id" :value="node.id">
              {{ node.title }}
            </option>
          </select></template
        >
        <template v-if="!selected"
          ><label for="new-node-parent">父节点</label
          ><select id="new-node-parent" v-model="position.parentNodeId">
            <option value="">顶层节点</option>
            <option v-for="node in graph.nodes" :key="node.id" :value="node.id">
              {{ node.title }}
            </option></select
          ><label for="new-node-order">显示顺序</label
          ><input
            id="new-node-order"
            v-model.number="position.displayOrder"
            type="number"
            min="0"
            max="2147483647"
            required
        /></template>
        <button class="primary" :disabled="busy || graph.versioned">
          {{ selected ? '保存节点定义' : '添加到知识树' }}</button><button
          v-if="selected"
          type="button"
          @click="reset"
        >
          取消编辑 / 新增节点
        </button>
      </form>
      <form
        v-if="selected && !graph.versioned"
        class="editor"
        @submit.prevent="move"
      >
        <h2>调整当前技能中的位置</h2>
        <label for="move-parent">父节点</label
        ><select id="move-parent" v-model="position.parentNodeId">
          <option value="">顶层节点</option>
          <option
            v-for="node in graph.nodes.filter((n) => n.id !== selected)"
            :key="node.id"
            :value="node.id"
          >
            {{ node.title }}
          </option></select
        ><label for="move-order">显示顺序</label
        ><input
          id="move-order"
          v-model.number="position.displayOrder"
          type="number"
          min="0"
          max="2147483647"
          required
        /><button :disabled="busy || graph.versioned">保存节点位置</button>
        <p class="help-text">顺序与父节点仅影响当前技能。</p>
      </form>
    </div>
  </div>
</template>
