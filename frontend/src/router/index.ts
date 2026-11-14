import { createRouter, createWebHistory } from 'vue-router'
import SystemPage from '../pages/SystemPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', name: 'system', component: SystemPage }],
})
