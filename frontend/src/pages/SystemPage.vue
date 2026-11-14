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
    <h1>把学习，连接成体系。</h1>
    <p class="hero-description">
      从一个目标开始，让知识、实践与证据相互连接。<br
        class="hidden sm:block"
      />你的私人学习空间，正在建立。
    </p>
    <div class="phase-line">
      <ElTag effect="dark" type="info">P0 · 工程基线</ElTag
      ><span>后续功能按阶段验收开放</span>
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
      <h2 id="next-title">身份、技能与手动知识树</h2>
      <p>下一阶段建立私有登录与技能管理，再把知识点组织成可持续维护的结构。</p>
      <p class="scope-note">
        当前尚未开放学习业务；不会生成示例进度，也不会调用外部 AI。
      </p>
    </div>
  </section>
</template>
