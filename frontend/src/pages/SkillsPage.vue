<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { api, errorMessage } from '../api/client'
import type { Page, Skill } from '../types/catalog'
const rows = ref<Page<Skill>>({ items: [], page: 0, size: 12, total: 0 })
const query = ref(''),
  status = ref('ACTIVE'),
  page = ref(0),
  error = ref(''),
  busy = ref(false),
  editing = ref<string | null>(null)
const form = ref({ name: '', description: '', category: '', status: 'ACTIVE' })
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
async function load() {
  try {
    rows.value = await api(
      `/skills?q=${encodeURIComponent(query.value)}&status=${status.value}&page=${page.value}&size=12`,
    )
    error.value = ''
  } catch (e) {
    error.value = errorMessage(e)
  }
}
async function search() {
  page.value = 0
  await load()
}
async function paginate(delta: number) {
  page.value += delta
  await load()
}
function edit(skill: Skill) {
  editing.value = skill.id
  form.value = {
    name: skill.name,
    description: skill.description,
    category: skill.category,
    status: skill.status,
  }
  focusEditor()
}
function reset() {
  editing.value = null
  form.value = { name: '', description: '', category: '', status: 'ACTIVE' }
}
async function save() {
  if (busy.value) return
  busy.value = true
  try {
    await api(
      editing.value ? `/skills/${editing.value}` : '/skills',
      editing.value ? 'PATCH' : 'POST',
      form.value,
    )
    reset()
    await search()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
<template>
  <section class="workspace-heading">
    <p class="eyebrow">SKILL SPACES</p>
    <h1>技能空间</h1>
    <p>从一个想学的领域开始，逐步搭建自己的知识结构。</p>
    <div class="row-actions">
      <button @click="beginCreate">新建技能</button>
    </div>
  </section>
  <p v-if="error" role="alert" class="error-box">{{ error }}</p>
  <div class="workspace-columns">
    <section aria-label="技能列表">
      <form class="search-bar" @submit.prevent="search">
        <label class="sr-only" for="skill-query">搜索技能</label
        ><input
          id="skill-query"
          v-model="query"
          placeholder="搜索技能名称"
          maxlength="160"
        /><label class="sr-only" for="skill-status">技能状态</label
        ><select id="skill-status" v-model="status" @change="search">
          <option value="ACTIVE">使用中</option>
          <option value="ARCHIVED">已归档</option>
          <option value="ALL">全部技能</option></select
        ><button>搜索</button>
      </form>
      <p v-if="!rows.items.length" class="empty-state">
        这里还没有符合条件的技能。创建第一个技能，或调整搜索条件。
      </p>
      <ul class="skill-list">
        <li v-for="skill in rows.items" :key="skill.id" class="skill-card">
          <span class="eyebrow"
            >{{ skill.category || '未分类' }} ·
            {{ skill.status === 'ACTIVE' ? '使用中' : '已归档' }}</span
          >
          <h2>
            <RouterLink :to="`/skills/${skill.id}`">{{
              skill.name
            }}</RouterLink>
          </h2>
          <p>{{ skill.description || '还没有描述。' }}</p>
          <div class="row-actions">
            <RouterLink class="text-link" :to="`/skills/${skill.id}`"
              >打开知识树 →</RouterLink
            ><button
              type="button"
              @click="edit(skill)"
              :aria-label="`编辑技能 ${skill.name}`"
            >
              编辑
            </button>
          </div>
        </li>
      </ul>
      <div class="pagination">
        <button :disabled="page === 0" @click="paginate(-1)">上一页</button
        ><span>第 {{ page + 1 }} 页 · 共 {{ rows.total }} 个技能</span
        ><button :disabled="(page + 1) * 12 >= rows.total" @click="paginate(1)">
          下一页
        </button>
      </div>
    </section>
    <form ref="editorForm" class="editor" @submit.prevent="save">
      <h2>{{ editing ? '编辑技能' : '创建技能' }}</h2>
      <label for="skill-name">技能名称</label
      ><input
        id="skill-name"
        v-model="form.name"
        maxlength="120"
        required
      /><label for="skill-category">分类</label
      ><input
        id="skill-category"
        v-model="form.category"
        maxlength="80"
      /><label for="skill-description">描述与学习目标</label
      ><textarea
        id="skill-description"
        v-model="form.description"
        maxlength="4000"
        rows="4"
      ></textarea
      ><label for="edit-skill-status">状态</label
      ><select id="edit-skill-status" v-model="form.status">
        <option value="ACTIVE">使用中</option>
        <option value="ARCHIVED">归档（可恢复）</option></select
      ><button class="primary" :disabled="busy">
        {{ editing ? '保存技能' : '创建技能' }}</button
      ><button v-if="editing" type="button" @click="reset">取消编辑</button>
    </form>
  </div>
</template>
