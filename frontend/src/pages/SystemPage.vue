<script setup lang="ts">
import { onMounted } from 'vue'
import { ElButton, ElTag } from 'element-plus'
import { useSystemStore } from '../stores/system'

const system = useSystemStore()
onMounted(() => system.refresh())
</script>

<template>
  <section class="hero">
    <p class="eyebrow">A FOUNDATION FOR LIFELONG LEARNING</p>
    <h1>系统连接状态</h1>
    <p class="hero-description">
      技能与知识树可以手动管理。<br
        class="hidden sm:block"
      />这里显示服务与数据连接的实际状态。
    </p>
    <div class="phase-line">
      <ElTag effect="dark" type="info">P1 · 手动知识树</ElTag
      ><span>AI 功能尚未接入</span>
    </div>
  </section>

  <section
    class="status-panel"
    aria-labelledby="status-title"
    :aria-busy="system.loading"
  >
    <div class="panel-header">
      <div>
        <p class="eyebrow">SYSTEM CONNECTION</p>
        <h2 id="status-title">系统连接状态</h2>
      </div>
      <ElButton :loading="system.loading" @click="system.refresh">{{
        system.error ? '重试连接' : '刷新状态'
      }}</ElButton>
    </div>
    <div aria-live="polite" class="status-body">
      <p v-if="system.loading" class="status-message">正在检查后端与数据库…</p>
      <div v-else-if="system.error" role="alert" class="error-box">
        <p>{{ system.error }}</p>
        <small v-if="system.requestId">请求编号：{{ system.requestId }}</small>
      </div>
      <template v-else-if="system.status">
        <dl class="status-grid">
          <div>
            <dt>后端服务</dt>
            <dd><span class="status-dot" aria-hidden="true"></span>已连接</dd>
          </div>
          <div>
            <dt>PostgreSQL</dt>
            <dd><span class="status-dot" aria-hidden="true"></span>已连接</dd>
          </div>
          <div>
            <dt>数据库迁移</dt>
            <dd>V{{ system.status.schemaVersion }}</dd>
          </div>
          <div>
            <dt>AI 系统</dt>
            <dd class="muted">尚未接入</dd>
          </div>
        </dl>
        <p class="checked-at">
          上次检查 {{ system.checkedAt }} · 状态来自真实服务查询
        </p>
      </template>
    </div>
  </section>

  <section class="next-section" aria-labelledby="next-title">
    <div class="next-number" aria-hidden="true">01</div>
    <div>
      <p class="eyebrow">NEXT MILESTONE</p>
      <h2 id="next-title">AI 知识草案与版本发布</h2>
      <p>下一阶段增加可编辑草案、变更预览和用户确认发布。</p>
      <p class="scope-note">
        当前不调用外部 AI。知识节点与依赖关系由你手动管理。
      </p>
    </div>
  </section>
</template>
