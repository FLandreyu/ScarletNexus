import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import './styles.css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/tag/style/css'

createApp(App).use(createPinia()).use(router).mount('#app')
