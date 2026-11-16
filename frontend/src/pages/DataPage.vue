<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, errorMessage } from '../api/client'
import { allPages } from '../api/learning'
import type { Skill } from '../types/catalog'
interface Preview {
  id: string
  sourceHash: string
  state: string
  revision: number
  skills: number
  nodes: number
  positions: number
  relations: number
  warnings: string[]
}
interface Result {
  importId: string
  skillIds: Record<string, string>
  nodeIds: Record<string, string>
  relationIds: Record<string, string>
}
const route = useRoute(),
  router = useRouter(),
  skills = ref<Skill[]>([]),
  skillId = ref(''),
  documentText = ref(''),
  preview = ref<Preview | null>(null),
  result = ref<Result | null>(null),
  confirmed = ref(false),
  error = ref(''),
  message = ref(''),
  busy = ref(false),
  key = ref(crypto.randomUUID())
watch(documentText, () => {
  preview.value = null
  confirmed.value = false
  result.value = null
})
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
async function download(kind: 'catalog' | 'snapshot') {
  await run(async () => {
    const value = await api(
      kind === 'snapshot'
        ? '/data/snapshot'
        : `/data/catalog${skillId.value ? '?skillId=' + skillId.value : ''}`,
    )
    const blob = new Blob([JSON.stringify(value, null, 2)], {
        type: 'application/json',
      }),
      url = URL.createObjectURL(blob),
      link = document.createElement('a')
    link.href = url
    link.download =
      kind === 'snapshot'
        ? 'scarlet-nexus-learning-snapshot.json'
        : 'scarlet-nexus-catalog.json'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    message.value = 'JSON已生成并交给浏览器下载；请妥善保存。'
  })
}
async function file(event: Event) {
  await run(async () => {
    const selected = (event.target as HTMLInputElement).files?.[0]
    if (!selected) return
    if (selected.size > 2097152) throw new Error('知识目录不能超过2MiB')
    documentText.value = await selected.text()
  })
}
async function prepare() {
  await run(async () => {
    preview.value = await api<Preview>('/data/catalog/imports', 'POST', {
      documentText: documentText.value,
    })
    confirmed.value = false
    key.value = crypto.randomUUID()
    await router.replace({ path: '/data', query: { import: preview.value.id } })
    message.value = '目录已校验并保存预览，现有技能和学习记录未改变。'
  })
}
async function confirm() {
  await run(async () => {
    if (!preview.value || !confirmed.value) return
    result.value = await api<Result>(
      `/data/catalog/imports/${preview.value.id}/confirm`,
      'POST',
      {
        revision: preview.value.revision,
        sourceHash: preview.value.sourceHash,
        confirmed: true,
      },
      { 'Idempotency-Key': key.value },
    )
    preview.value = await api<Preview>(
      `/data/catalog/imports/${preview.value.id}`,
    )
    skills.value = await allPages<Skill>('/skills?status=ALL')
    confirmed.value = false
    message.value = '知识目录已新增导入，原数据未覆盖，图谱尚未自动发布。'
  })
}
onMounted(() =>
  run(async () => {
    skills.value = await allPages<Skill>('/skills?status=ALL')
    if (typeof route.query.import === 'string')
      preview.value = await api<Preview>(
        `/data/catalog/imports/${route.query.import}`,
      )
  }),
)
</script>
<template>
  <section class="workspace-page data-page">
    <p class="eyebrow">EXPORT · REVIEW · IMPORT</p>
    <h1>数据管理</h1>
    <p>
      导出文件包含私有学习文本，目录可能包含你的知识定义；请勿公开分享含个人内容的文件。
    </p>
    <p v-if="error" role="alert" class="error-box">{{ error }}</p>
    <p v-if="message" role="status">{{ message }}</p>
    <section class="learning-section">
      <h2>导出JSON</h2>
      <fieldset :disabled="busy" class="learning-fieldset">
        <div class="editor">
          <label for="export-skill">知识目录范围</label
          ><select id="export-skill" v-model="skillId">
            <option value="">全部技能（最多100项）</option>
            <option v-for="skill in skills" :key="skill.id" :value="skill.id">
              {{ skill.name }}
            </option></select
          ><button @click="download('catalog')">下载知识目录JSON</button>
          <p>
            目录包含所选技能、引用节点、分类位置与范围内关系。单技能导出不包含范围外关系，导入只新增知识目录。
          </p>
          <button @click="download('snapshot')">下载本人学习快照JSON</button>
          <p>
            学习快照包含当前用户任务、路线、作答、题目答案、证据、计划与复习等业务记录；附件仅元数据/校验和，不含图片二进制、磁盘路径、密码、会话、Key或审计事件。不是可直接恢复的数据库备份，不能导入下方目录入口。
          </p>
        </div>
      </fieldset>
    </section>
    <section class="learning-section">
      <h2>知识目录导入</h2>
      <fieldset :disabled="busy" class="learning-fieldset">
        <form class="editor" @submit.prevent="prepare">
          <label for="catalog-file">选择目录JSON（最多2MiB）</label
          ><input
            id="catalog-file"
            type="file"
            accept="application/json,.json"
            @change="file"
          /><label for="catalog-json">目录JSON文本</label
          ><textarea
            id="catalog-json"
            v-model="documentText"
            required
            rows="8"
            spellcheck="false"
          /><button :disabled="!documentText">校验并预览目录</button>
        </form>
      </fieldset>
      <article v-if="preview" class="skill-card">
        <h3>目录导入预览 · {{ preview.state }}</h3>
        <p>
          新增技能 {{ preview.skills }} · 节点 {{ preview.nodes }} · 分类位置
          {{ preview.positions }} · 关系 {{ preview.relations }}
        </p>
        <p v-for="warning in preview.warnings" :key="warning">{{ warning }}</p>
        <p>预览编号 {{ preview.id }}</p>
        <label v-if="preview.state === 'DRAFT'"
          ><input
            v-model="confirmed"
            type="checkbox"
            :disabled="busy"
          />确认仅新增目录，不覆盖原技能或导入学习记录</label
        >
        <div class="row-actions">
          <button
            v-if="preview.state === 'DRAFT'"
            :disabled="busy || !confirmed"
            @click="confirm"
          >
            确认新增目录
          </button>
        </div>
        <p v-if="preview.state === 'CONFIRMED'">此预览已确认，不会重复导入。</p>
      </article>
      <div v-if="result" class="row-actions">
        <RouterLink
          v-for="(id, oldId) in result.skillIds"
          :key="oldId"
          :to="`/skills/${id}`"
          >查看导入的技能 {{ id.slice(0, 8) }}</RouterLink
        >
      </div>
    </section>
    <section class="learning-section">
      <h2>完整备份与恢复</h2>
      <p>
        完整备份与隔离恢复由部署维护者按运维文档使用加密备份工具执行，需要PostgreSQL与私有附件共同校验。上述JSON不等于完整备份；此页面只支持新增知识目录。
      </p>
    </section>
  </section>
</template>
<style scoped>
.data-page p,
.data-page a {
  overflow-wrap: anywhere;
}
.data-page input[type='checkbox'] {
  width: auto;
}
.data-page input[type='file'] {
  max-width: 100%;
}
</style>
