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
      <ElTag effect="dark" type="info">知识草案与版本管理</ElTag
      ><span>{{
        system.status?.aiConfigured
          ? '生成已配置，候选仍需审核'
          : '未启用生成时，可继续手动管理'
      }}</span>
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
            <dd class="muted">
              {{ system.status.aiConfigured ? '生成已配置' : '生成未启用' }}
            </dd>
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
      <h2 id="next-title">学习路线与学习记录</h2>
      <p>
        草案生成、编辑和版本发布链路已实现。后续将加入目标、路线和学习记录。
      </p>
      <p class="scope-note">
        模型调用由服务器配置及预算控制，候选不会自动发布；无 AI
        配置时仍可手动编辑。
      </p>
    </div>
  </section>
</template>
