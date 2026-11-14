import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ApiFailure, fetchSystemStatus, type SystemStatus } from '../api/system'

export const useSystemStore = defineStore('system', () => {
  const status = ref<SystemStatus | null>(null)
  const loading = ref(false)
  const error = ref('')
  const requestId = ref<string | undefined>()
  const checkedAt = ref<string | null>(null)

  async function refresh() {
    if (loading.value) return
    loading.value = true
    error.value = ''
    requestId.value = undefined
    status.value = null
    try {
      status.value = await fetchSystemStatus()
      checkedAt.value = new Date().toLocaleTimeString('zh-CN', {
        hour12: false,
      })
    } catch (failure) {
      error.value =
        failure instanceof Error ? failure.message : '状态查询失败。'
      requestId.value =
        failure instanceof ApiFailure ? failure.requestId : undefined
    } finally {
      loading.value = false
    }
  }

  return { status, loading, error, requestId, checkedAt, refresh }
})
