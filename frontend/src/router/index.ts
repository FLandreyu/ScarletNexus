import { createRouter, createWebHistory } from 'vue-router'
import SystemPage from '../pages/SystemPage.vue'
import LoginPage from '../pages/LoginPage.vue'
import SkillsPage from '../pages/SkillsPage.vue'
import SkillPage from '../pages/SkillPage.vue'
import DraftPage from '../pages/DraftPage.vue'
import JobsPage from '../pages/JobsPage.vue'
import LearningPage from '../pages/LearningPage.vue'
import PathsPage from '../pages/PathsPage.vue'
import StudyPage from '../pages/StudyPage.vue'
import PracticePage from '../pages/PracticePage.vue'
import { useAuthStore } from '../stores/auth'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/learn' },
    { path: '/learn', component: LearningPage, meta: { private: true } },
    { path: '/paths', component: PathsPage, meta: { private: true } },
    { path: '/study/:id', component: StudyPage, meta: { private: true } },
    { path: '/practice/:id', component: PracticePage, meta: { private: true } },
    { path: '/system', name: 'system', component: SystemPage },
    { path: '/login', component: LoginPage },
    { path: '/skills', component: SkillsPage, meta: { private: true } },
    { path: '/ai/jobs', component: JobsPage, meta: { private: true } },
    { path: '/skills/:id', component: SkillPage, meta: { private: true } },
    {
      path: '/skills/:id/drafts',
      component: DraftPage,
      meta: { private: true },
    },
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
