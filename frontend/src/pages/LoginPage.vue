<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { errorMessage } from '../api/client'
const email = ref(''),
  password = ref(''),
  error = ref(''),
  busy = ref(false)
const auth = useAuthStore(),
  router = useRouter()
async function login() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await auth.login(email.value, password.value)
    password.value = ''
    await router.replace('/skills')
  } catch (failure) {
    error.value = errorMessage(failure)
    password.value = ''
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="workspace-heading">
    <p class="eyebrow">YOUR PRIVATE LEARNING SPACE</p>
    <h1>回到你的学习空间</h1>
    <p>登录后管理技能与知识结构。</p>
  </section>
  <form class="editor login-form" @submit.prevent="login">
    <label for="login-email">邮箱</label
    ><input
      id="login-email"
      v-model="email"
      type="email"
      autocomplete="username"
      maxlength="254"
      required
    />
    <label for="login-password">密码</label
    ><input
      id="login-password"
      v-model="password"
      type="password"
      autocomplete="current-password"
      maxlength="72"
      required
    />
    <p v-if="error" role="alert" class="error-box">{{ error }}</p>
    <button class="primary" :disabled="busy">
      {{ busy ? '正在登录…' : '登录' }}
    </button>
    <p class="help-text">
      首次使用请按 README 设置管理员邮箱与密码文件。系统不提供默认账号或密码。
    </p>
    <RouterLink to="/system">查看服务状态</RouterLink>
  </form>
</template>
