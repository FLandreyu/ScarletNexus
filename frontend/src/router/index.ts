import { createRouter, createWebHistory } from 'vue-router'
import SystemPage from '../pages/SystemPage.vue'
import LoginPage from '../pages/LoginPage.vue'
import SkillsPage from '../pages/SkillsPage.vue'
import SkillPage from '../pages/SkillPage.vue'
import { useAuthStore } from '../stores/auth'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/skills' },
    { path: '/system', name: 'system', component: SystemPage },
    { path: '/login', component: LoginPage },
    { path: '/skills', component: SkillsPage, meta: { private: true } },
    { path: '/skills/:id', component: SkillPage, meta: { private: true } },
    { path: '/:pathMatch(.*)*', redirect: '/skills' },
  ],
})
router.beforeEach(async (to) => {
  if (!to.meta.private) return
  const auth = useAuthStore()
  try {
    await auth.restore()
  } catch {
    return '/system'
  }
  if (!auth.user) return '/login'
})
