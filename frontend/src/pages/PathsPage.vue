<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, errorMessage } from '../api/client'
import { allPages } from '../api/learning'
import type { Skill, Graph, PlacedNode, Page } from '../types/catalog'
import type { Goal, PathVersion, PathDetail } from '../types/learning'
const router = useRouter(),
  error = ref(''),
  message = ref(''),
  busy = ref(false)
const skills = ref<Skill[]>([]),
  goals = ref<Goal[]>([]),
  paths = ref<Page<PathVersion>>({ items: [], total: 0, page: 0, size: 10 }),
  pathPage = ref(0)
const goalId = ref<string | null>(null)
const goalForm = ref({
  skillId: '',
  title: '',
  targetDescription: '',
  deadline: '',
  priority: 3,
  status: 'ACTIVE',
  revision: 0,
})
interface DraftStage {
  title: string
  nodes: { nodeId: string; targetLevel: number; required: boolean }[]
}
const pathForm = ref<{
  skillId: string
  goalId: string
  title: string
  baseVersionId: string | null
  stages: DraftStage[]
}>({
  skillId: '',
  goalId: '',
  title: '',
  baseVersionId: null,
  stages: [{ title: '基础阶段', nodes: [] }],
})
const nodes = ref<PlacedNode[]>([]),
  selectedVersion = ref<PathDetail | null>(null)
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
  ;[skills.value, goals.value, paths.value] = await Promise.all([
    allPages<Skill>('/skills?status=ACTIVE'),
    allPages<Goal>('/learning/goals'),
    api<Page<PathVersion>>(`/learning/paths?page=${pathPage.value}&size=10`),
  ])
}
async function page(delta: number) {
  await run(async () => {
    pathPage.value += delta
    await load()
  })
}
function resetGoal() {
  goalId.value = null
  goalForm.value = {
    skillId: skills.value[0]?.id ?? '',
    title: '',
    targetDescription: '',
    deadline: '',
    priority: 3,
    status: 'ACTIVE',
    revision: 0,
  }
}
function editGoal(goal: Goal) {
  goalId.value = goal.id
  goalForm.value = { ...goal, deadline: goal.deadline ?? '' }
  document.getElementById('goal-form')?.scrollIntoView({ block: 'start' })
}
async function saveGoal() {
  await run(async () => {
    await api(
      goalId.value ? `/learning/goals/${goalId.value}` : '/learning/goals',
      goalId.value ? 'PATCH' : 'POST',
      { ...goalForm.value, deadline: goalForm.value.deadline || null },
    )
    resetGoal()
    await load()
    message.value = '目标已保存。'
  })
}
async function loadNodes() {
  nodes.value = pathForm.value.skillId
    ? (
        await api<Graph>(`/skills/${pathForm.value.skillId}/graph`)
      ).nodes.filter((n) => n.status === 'ACTIVE')
    : []
}
async function changeSkill() {
  await run(async () => {
    pathForm.value.goalId = ''
    pathForm.value.stages = [{ title: '基础阶段', nodes: [] }]
    await loadNodes()
  })
}
async function editPath(id: string) {
  await run(async () => {
    const detail = await api<PathDetail>(`/learning/paths/${id}`)
    selectedVersion.value = detail
    pathForm.value = {
      skillId: detail.path.skillId,
      goalId: detail.path.goalId ?? '',
      title: detail.path.title,
      baseVersionId: id,
      stages: detail.stages.map((s) => ({
        title: s.title,
        nodes: detail.entries
          .filter((e) => e.stageId === s.id)
          .map((e) => ({
            nodeId: e.nodeId,
            targetLevel: e.targetLevel,
            required: e.required,
          })),
      })),
    }
    await loadNodes()
    document.getElementById('path-form')?.scrollIntoView({ block: 'start' })
  })
}
function resetPath() {
  selectedVersion.value = null
  pathForm.value = {
    skillId: skills.value[0]?.id ?? '',
    goalId: '',
    title: '',
    baseVersionId: null,
    stages: [{ title: '基础阶段', nodes: [] }],
  }
  void run(loadNodes)
}
function move<T>(rows: T[], index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= rows.length) return
  const item = rows.splice(index, 1)[0]!
  rows.splice(target, 0, item)
}
async function savePath() {
  await run(async () => {
    const saved = await api<PathDetail>('/learning/paths', 'POST', {
      ...pathForm.value,
      goalId: pathForm.value.goalId || null,
    })
    pathForm.value.baseVersionId = saved.path.id
    selectedVersion.value = saved
    pathPage.value = 0
    await load()
    message.value = `路线版本 ${saved.path.versionNumber} 已保存，原版本与学习记录保留。`
  })
}
async function choose(id: string) {
  await run(async () => {
    await api('/learning/enrollments', 'POST', { pathId: id })
    await router.push('/learn')
  })
}
onMounted(
  () =>
    void run(async () => {
      await load()
      resetGoal()
      pathForm.value.skillId = skills.value[0]?.id ?? ''
      await loadNodes()
    }),
)
</script>
<template>
  <section class="workspace-heading">
    <p class="eyebrow">LEARNING PATHS</p>
    <h1>目标与学习路线</h1>
    <p>把想学的知识安排成阶段。路线修订保留旧版，目标等级不代表已掌握。</p>
    <RouterLink to="/learn">返回学习首页</RouterLink>
  </section>
  <p v-if="error" role="alert" class="error-box">{{ error }}</p>
  <p v-if="message" role="status">{{ message }}</p>
  <p v-if="!skills.length" class="empty-state">
    请先在技能空间创建技能并添加知识节点。
  </p>
  <fieldset :disabled="busy" class="learning-fieldset">
    <div class="workspace-columns">
      <section>
        <h2>学习目标</h2>
        <p v-if="!goals.length">尚无目标，可先创建一个小目标。</p>
        <ul class="skill-list">
          <li v-for="goal in goals" :key="goal.id" class="skill-card">
            <h3>{{ goal.title }}</h3>
            <p>{{ goal.targetDescription }}</p>
            <p>
              截止：{{ goal.deadline ?? '未设置' }} · 优先级
              {{ goal.priority }} ·
              {{
                goal.status === 'ARCHIVED'
                  ? '已归档'
                  : goal.status === 'COMPLETED'
                    ? '已完成'
                    : '进行中'
              }}
            </p>
            <button @click="editGoal(goal)">编辑目标 {{ goal.title }}</button>
          </li>
        </ul>
      </section>
      <form id="goal-form" class="editor" @submit.prevent="saveGoal">
        <h2>{{ goalId ? '编辑目标' : '创建目标' }}</h2>
        <label for="goal-skill">目标技能</label
        ><select
          id="goal-skill"
          v-model="goalForm.skillId"
          :disabled="!!goalId"
          required
        >
          <option v-for="s in skills" :key="s.id" :value="s.id">
            {{ s.name }}
          </option></select
        ><label for="goal-title">目标名称</label
        ><input
          id="goal-title"
          v-model="goalForm.title"
          maxlength="160"
          required
        /><label for="goal-description">预期成果</label
        ><textarea
          id="goal-description"
          v-model="goalForm.targetDescription"
          maxlength="8000"
        /><label for="goal-deadline">目标截止日期</label
        ><input
          id="goal-deadline"
          v-model="goalForm.deadline"
          type="date"
        /><label for="goal-priority">优先级（1最高）</label
        ><input
          id="goal-priority"
          v-model.number="goalForm.priority"
          type="number"
          min="1"
          max="5"
          required
        /><label for="goal-state">目标状态</label
        ><select id="goal-state" v-model="goalForm.status">
          <option value="ACTIVE">进行中</option>
          <option value="COMPLETED">已完成</option>
          <option value="ARCHIVED">已归档</option>
        </select>
        <div class="row-actions">
          <button>保存目标</button
          ><button type="button" @click="resetGoal">新建另一目标</button>
        </div>
      </form>
    </div>
    <section class="learning-section">
      <h2>路线版本</h2>
      <p v-if="!paths.items.length">尚无路线，手动添加阶段与节点即可开始。</p>
      <ul class="skill-list">
        <li v-for="path in paths.items" :key="path.id" class="skill-card">
          <h3>{{ path.title }} · v{{ path.versionNumber }}</h3>
          <p>{{ new Date(path.createdAt).toLocaleString() }}</p>
          <div class="row-actions">
            <button @click="choose(path.id)">
              选择学习 {{ path.title }} v{{ path.versionNumber }}</button
            ><button @click="editPath(path.id)">
              查看并修订 {{ path.title }} v{{ path.versionNumber }}
            </button>
          </div>
        </li>
      </ul>
      <div class="pagination">
        <button :disabled="pathPage === 0" @click="page(-1)">上一页路线</button
        ><span
          >{{ pathPage + 1 }} /
          {{ Math.max(1, Math.ceil(paths.total / 10)) }}</span
        ><button
          :disabled="(pathPage + 1) * 10 >= paths.total"
          @click="page(1)"
        >
          下一页路线
        </button>
      </div>
    </section>
    <form
      id="path-form"
      class="editor learning-section"
      @submit.prevent="savePath"
    >
      <h2>
        {{ pathForm.baseVersionId ? '修订路线（保存为新版本）' : '创建路线' }}
      </h2>
      <p v-if="selectedVersion">
        当前基于 v{{
          selectedVersion.path.versionNumber
        }}；旧选择与历史保持原版本。只有最新版本可以继续修订。
      </p>
      <label for="path-skill">路线技能</label
      ><select
        id="path-skill"
        v-model="pathForm.skillId"
        :disabled="!!pathForm.baseVersionId"
        required
        @change="changeSkill"
      >
        <option v-for="s in skills" :key="s.id" :value="s.id">
          {{ s.name }}
        </option></select
      ><label for="path-goal">关联目标</label
      ><select id="path-goal" v-model="pathForm.goalId">
        <option value="">不关联目标</option>
        <option
          v-for="g in goals.filter((g) => g.skillId === pathForm.skillId)"
          :key="g.id"
          :value="g.id"
        >
          {{ g.title }}
        </option></select
      ><label for="path-title">路线名称</label
      ><input
        id="path-title"
        v-model="pathForm.title"
        maxlength="160"
        required
      />
      <section
        v-for="(stage, i) in pathForm.stages"
        :key="i"
        class="stage-editor"
      >
        <label :for="`stage-${i}`">阶段 {{ i + 1 }} 名称</label
        ><input
          :id="`stage-${i}`"
          v-model="stage.title"
          maxlength="160"
          required
        />
        <div class="row-actions">
          <button
            type="button"
            :disabled="i === 0"
            @click="move(pathForm.stages, i, -1)"
          >
            阶段上移</button
          ><button
            type="button"
            :disabled="i === pathForm.stages.length - 1"
            @click="move(pathForm.stages, i, 1)"
          >
            阶段下移</button
          ><button
            type="button"
            :disabled="pathForm.stages.length === 1"
            @click="pathForm.stages.splice(i, 1)"
          >
            移除阶段
          </button>
        </div>
        <div
          v-for="(entry, j) in stage.nodes"
          :key="j"
          class="path-node-editor"
        >
          <label :for="`entry-${i}-${j}`"
            >阶段 {{ i + 1 }} 节点 {{ j + 1 }}</label
          ><select :id="`entry-${i}-${j}`" v-model="entry.nodeId" required>
            <option value="">选择知识节点</option>
            <option v-for="n in nodes" :key="n.id" :value="n.id">
              {{ n.title }}
            </option>
            <option
              v-if="entry.nodeId && !nodes.some((n) => n.id === entry.nodeId)"
              :value="entry.nodeId"
            >
              原节点已移出或归档，请重新选择
            </option></select
          ><label :for="`level-${i}-${j}`"
            >目标等级 {{ i + 1 }}-{{ j + 1 }}（0–5）</label
          ><input
            :id="`level-${i}-${j}`"
            v-model.number="entry.targetLevel"
            type="number"
            min="0"
            max="5"
            required
          /><label class="check-label"
            ><input v-model="entry.required" type="checkbox" />必修节点
            {{ i + 1 }}-{{ j + 1 }}</label
          >
          <div class="row-actions">
            <button
              type="button"
              :disabled="j === 0"
              @click="move(stage.nodes, j, -1)"
            >
              节点上移</button
            ><button
              type="button"
              :disabled="j === stage.nodes.length - 1"
              @click="move(stage.nodes, j, 1)"
            >
              节点下移</button
            ><button type="button" @click="stage.nodes.splice(j, 1)">
              移除节点
            </button>
          </div>
        </div>
        <button
          type="button"
          :disabled="stage.nodes.length >= 300"
          @click="
            stage.nodes.push({ nodeId: '', targetLevel: 1, required: true })
          "
        >
          添加阶段 {{ i + 1 }} 的节点
        </button>
      </section>
      <div class="row-actions">
        <button
          type="button"
          :disabled="pathForm.stages.length >= 30"
          @click="pathForm.stages.push({ title: '', nodes: [] })"
        >
          添加阶段</button
        ><button>保存路线版本</button
        ><button type="button" @click="resetPath">新建另一条路线</button>
      </div>
    </form>
  </fieldset>
</template>
