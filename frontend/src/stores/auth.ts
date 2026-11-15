import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, ApiProblem } from '../api/client'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<{ id: string; email: string } | null>(null)
  const loaded = ref(false)
  async function restore() {
    if (loaded.value) return
    try {
      user.value = await api('/me')
    } catch (error) {
      if (!(error instanceof ApiProblem) || error.status !== 401) throw error
      user.value = null
    }
    loaded.value = true
  }
  async function login(email: string, password: string) {
    user.value = await api('/auth/login', 'POST', { email, password })
    loaded.value = true
  }
  async function logout() {
    await api('/auth/logout', 'POST', {})
    clear()
  }
  function clear() {
    user.value = null
    loaded.value = true
  }
  return { user, loaded, restore, login, logout, clear }
})
