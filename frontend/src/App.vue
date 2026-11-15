<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { errorMessage } from './api/client'
const auth = useAuthStore(),
  router = useRouter(),
  error = ref(''),
  logoutBusy = ref(false)
function expired() {
  auth.clear()
  void router.replace('/login')
}
async function logout() {
  if (logoutBusy.value) return
  logoutBusy.value = true
  error.value = ''
  try {
    await auth.logout()
    await router.replace('/login')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    logoutBusy.value = false
  }
}
onMounted(() => window.addEventListener('session-expired', expired))
onUnmounted(() => window.removeEventListener('session-expired', expired))
</script>
<template>
  <div class="app-shell">
    <a class="skip-link" href="#main">跳到主要内容</a>
    <header class="site-header">
      <RouterLink to="/" class="brand" aria-label="Scarlet Nexus 首页">
        <span class="brand-mark" aria-hidden="true">S<span>N</span></span>
        <span
          >SCARLET <strong>NEXUS</strong><small>个人终身学习系统</small></span
        >
      </RouterLink>
      <nav class="top-nav" aria-label="主导航">
        <RouterLink to="/learn">学习首页</RouterLink>
        <RouterLink v-if="auth.user" to="/planner">每日计划</RouterLink>
        <RouterLink v-if="auth.user" to="/reviews">到期复习</RouterLink>
        <RouterLink v-if="auth.user" to="/paths">学习路线</RouterLink>
        <RouterLink to="/skills">技能空间</RouterLink
        ><RouterLink v-if="auth.user" to="/ai/jobs">生成任务</RouterLink
        ><RouterLink to="/system">系统状态</RouterLink
        ><button v-if="auth.user" :disabled="logoutBusy" @click="logout">
          退出登录
        </button>
      </nav>
    </header>
    <main id="main" tabindex="-1">
      <p v-if="error" role="alert" class="error-box">{{ error }}</p>
      <RouterView :key="$route.path" />
    </main>
    <footer class="site-footer">
      Scarlet Nexus <span>让每一步学习都有迹可循。</span>
    </footer>
  </div>
</template>
