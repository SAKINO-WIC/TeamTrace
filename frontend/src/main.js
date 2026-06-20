import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import './styles/glass-system.css'
import './styles/theme.css'
import './styles/teacher-dialog.css'
import './styles/workspace-settings.css'
import './styles/dark-badge-contrast.css'
import './styles/auth-forms.css'
import './styles/empty-state-layout.css'
import './styles/icon-text-alignment.css'
import { syncThemeForRoute } from './utils/theme'

if (typeof document !== 'undefined') {
  syncThemeForRoute(window.location.pathname)
}

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
