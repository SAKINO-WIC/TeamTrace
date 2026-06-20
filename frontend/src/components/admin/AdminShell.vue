<script setup>
import '../../styles/admin-theme.css'
import '../../styles/admin-workspace.css'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearToken } from '../../utils/auth'
import IconSystem from '../common/IconSystem.vue'
import SystemAnnouncementModal from '../common/SystemAnnouncementModal.vue'
import { applyAdminThemeToDocument } from '../../utils/theme'
import {
  ADMIN_THEME_DARK,
  ADMIN_THEME_LIGHT,
  ADMIN_WORKSPACE_EVENT,
  getAdminLocale,
  getAdminTheme,
  setAdminLocale,
  setAdminTheme,
} from '../../utils/adminWorkspace'

const route = useRoute()
const router = useRouter()

const collapsed = ref(false)
const mobileSidebarOpen = ref(false)
const showSettings = ref(false)
const settingsTab = ref('preferences')
const settingsMessage = ref('')
const locale = ref(getAdminLocale())
const theme = ref(getAdminTheme())

const title = computed(() => route.meta?.title || (locale.value === 'en-US' ? 'Admin' : '管理员'))
const description = computed(() => route.meta?.description || '')
const sectionTag = computed(() => route.meta?.sectionTag || 'Admin workspace')

const navItems = computed(() => ([
  { label: locale.value === 'en-US' ? 'Overview' : '系统总览', to: '/admin', icon: 'home' },
  { label: locale.value === 'en-US' ? 'Teacher Invites' : '教师邀请码', to: '/admin/invites', icon: 'key' },
  { label: locale.value === 'en-US' ? 'Users' : '账户管理', to: '/admin/users', icon: 'users' },
  { label: locale.value === 'en-US' ? 'Emails' : '邮件中心', to: '/admin/emails', icon: 'bell' },
  { label: locale.value === 'en-US' ? 'Announcements' : '系统公告', to: '/admin/announcements', icon: 'monitor' },
  { label: locale.value === 'en-US' ? 'Security' : '安全设置', to: '/admin/security', icon: 'shield' },
  { label: locale.value === 'en-US' ? 'Monitoring' : '系统监控', to: '/admin/monitor', icon: 'monitor' },
  { label: locale.value === 'en-US' ? 'Logs' : '操作日志', to: '/admin/logs', icon: 'log' },
]))

const activePath = computed(() => route.path)

function isActive(path) {
  if (path === '/admin') return activePath.value === '/admin'
  return activePath.value === path || activePath.value.startsWith(path + '/')
}

function navigate(path) {
  router.push(path)
  mobileSidebarOpen.value = false
}

function openHiddenMoyuPage() {
  router.push({ path: '/moyu-terminator', query: { returnTo: route.fullPath || '/admin' } })
  mobileSidebarOpen.value = false
}

function logout() {
  clearToken()
  router.replace('/auth')
}

function toggleSidebar() {
  collapsed.value = !collapsed.value
}

function openSettings() {
  showSettings.value = true
}

function closeSettings() {
  showSettings.value = false
}

function flashSettingsMessage(text) {
  settingsMessage.value = text
  window.setTimeout(() => {
    settingsMessage.value = ''
  }, 2000)
}

function applyTheme(next) {
  const normalized = applyAdminThemeToDocument(next)
  if (theme.value !== normalized) theme.value = normalized
  setAdminTheme(normalized)
  flashSettingsMessage(normalized === ADMIN_THEME_DARK ? '已切换为深色主题' : '已切换为浅色主题')
}

function applyLanguage(next) {
  const normalized = next === 'en-US' ? 'en-US' : 'zh-CN'
  if (locale.value !== normalized) locale.value = normalized
  setAdminLocale(normalized)
  flashSettingsMessage(normalized === 'en-US' ? 'Language switched to English' : '语言已切换为简体中文')
}

function handleWorkspaceChange(event) {
  if (event?.detail?.key === 'teamtrace_admin_theme') theme.value = getAdminTheme()
  if (event?.detail?.key === 'teamtrace_admin_locale') locale.value = getAdminLocale()
}

watch(() => route.fullPath, () => {
  mobileSidebarOpen.value = false
})

onMounted(() => {
  theme.value = applyAdminThemeToDocument(theme.value)
  window.addEventListener(ADMIN_WORKSPACE_EVENT, handleWorkspaceChange)
})

onBeforeUnmount(() => {
  window.removeEventListener(ADMIN_WORKSPACE_EVENT, handleWorkspaceChange)
})
</script>

<template>
  <div class="admin-layout">
    <div v-if="mobileSidebarOpen" class="layout-mask" @click="mobileSidebarOpen = false" />

    <aside class="admin-sidebar" :class="{ collapsed, open: mobileSidebarOpen }">
      <div class="sidebar-header">
        <button class="brand" type="button" @click="openHiddenMoyuPage">
          <span class="brand-mark"><img class="brand-logo" src="/TeamTraceLogo.png" alt="TeamTrace" /></span>
          <span v-if="!collapsed" class="brand-copy">
            <strong>TeamTrace</strong>
            <span>管理员后台</span>
          </span>
        </button>
      </div>

      <nav class="sidebar-nav">
        <button
          v-for="item in navItems"
          :key="item.to"
          class="nav-item"
          :class="{ active: isActive(item.to) }"
          type="button"
          :title="item.label"
          @click="navigate(item.to)"
        >
          <span class="nav-icon-wrap"><IconSystem :name="item.icon" :size="18" /></span>
          <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
        </button>
      </nav>

      <div class="sidebar-footer">
        <button class="nav-item" type="button" @click="logout">
          <span class="nav-icon-wrap"><IconSystem name="logout" :size="18" /></span>
          <span v-if="!collapsed" class="nav-label">退出登录</span>
        </button>
        <button class="collapse-btn desktop-only" type="button" @click="toggleSidebar">
          <IconSystem :name="collapsed ? 'chevronRight' : 'chevronLeft'" :size="16" />
          <span v-if="!collapsed">收起</span>
        </button>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-header">
        <div class="header-primary">
          <button class="mobile-menu mobile-only" type="button" @click="mobileSidebarOpen = !mobileSidebarOpen">
            <IconSystem name="menu" :size="18" />
          </button>
          <div class="welcome-block">
            <p class="welcome-eyebrow">{{ sectionTag }}</p>
            <h1 class="welcome-title">{{ title }}</h1>
            <p v-if="description" class="welcome-desc">{{ description }}</p>
          </div>
        </div>
        <div class="header-tools">
          <button class="tool-btn" type="button" @click="openSettings">
            <IconSystem name="settings" :size="16" />
            <span>{{ locale === 'en-US' ? 'Settings' : '系统设置' }}</span>
          </button>
          <span class="status-badge">{{ locale === 'en-US' ? 'Online' : '在线' }}</span>
        </div>
      </header>

      <main class="content">
        <div class="content-shell admin-content-shell">
          <RouterView />
        </div>
      </main>
    </div>

    <Teleport to="body">
      <div v-if="showSettings" class="settings-overlay" @click.self="closeSettings">
        <div class="settings-panel">
          <div class="settings-header">
            <div>
              <h2>{{ locale === 'en-US' ? 'Settings' : '系统设置' }}</h2>
            </div>
            <button class="close-btn" type="button" @click="closeSettings">
              <IconSystem name="close" :size="18" />
            </button>
          </div>
          <div class="settings-tabs">
            <button class="settings-tab" :class="{ active: settingsTab === 'preferences' }" type="button" @click="settingsTab = 'preferences'">
              {{ locale === 'en-US' ? 'Preferences' : '偏好设置' }}
            </button>
            <button class="settings-tab" :class="{ active: settingsTab === 'account' }" type="button" @click="settingsTab = 'account'">
              {{ locale === 'en-US' ? 'Profile' : '个人信息' }}
            </button>
          </div>
          <p v-if="settingsMessage" class="settings-message">{{ settingsMessage }}</p>
          <div v-if="settingsTab === 'preferences'" class="settings-body">
            <div class="setting-row">
              <div class="setting-label">
                <p class="setting-title">{{ locale === 'en-US' ? 'Language' : '语言' }}</p>
              </div>
              <div class="setting-control">
                <button class="option-btn" :class="{ active: locale === 'zh-CN' }" type="button" @click="applyLanguage('zh-CN')">简体中文</button>
                <button class="option-btn" :class="{ active: locale === 'en-US' }" type="button" @click="applyLanguage('en-US')">English</button>
              </div>
            </div>
            <div class="setting-row">
              <div class="setting-label">
                <p class="setting-title">{{ locale === 'en-US' ? 'Theme' : '主题' }}</p>
              </div>
              <div class="setting-control">
                <button class="option-btn" :class="{ active: theme === ADMIN_THEME_LIGHT }" type="button" @click="applyTheme(ADMIN_THEME_LIGHT)">
                  {{ locale === 'en-US' ? 'Light' : '浅色' }}
                </button>
                <button class="option-btn" :class="{ active: theme === ADMIN_THEME_DARK }" type="button" @click="applyTheme(ADMIN_THEME_DARK)">
                  {{ locale === 'en-US' ? 'Dark' : '深色' }}
                </button>
              </div>
            </div>
          </div>
          <div v-if="settingsTab === 'account'" class="settings-body">
            <div class="account-card">
              <div class="account-avatar">A</div>
              <div class="account-info">
                <div class="account-row">
                  <span class="account-label">Role</span>
                  <span class="account-value">管理员</span>
                </div>
              </div>
            </div>
            <div class="account-actions">
              <button class="panel-btn danger" type="button" @click="logout">退出登录</button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
    <SystemAnnouncementModal />
  </div>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--tt-sidebar-width) minmax(0, 1fr);
  background: var(--tt-bg);
  color: var(--tt-text);
  font-family: var(--tt-font);
}

.settings-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(8px);
}

.settings-panel {
  width: 92%;
  max-width: 480px;
  border-radius: 20px;
  background: var(--tt-bg-elevated);
  border: 1px solid var(--tt-divider);
  box-shadow: var(--tt-shadow-xl);
  overflow: hidden;
  animation: tt-modal-pop 180ms var(--tt-ease, cubic-bezier(0.25, 0.1, 0.25, 1));
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--tt-space-2);
  height: 34px;
  padding: 0 var(--tt-space-3);
  border: 1px solid var(--tt-border);
  border-radius: var(--tt-radius-full);
  background: var(--tt-surface);
  color: var(--tt-text);
  cursor: pointer;
  font-family: var(--tt-font);
}

.settings-header,
.settings-tabs,
.settings-body,
.settings-message {
  padding: 18px;
}

.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--tt-divider);
}

.settings-header h2 {
  margin: 0;
  font-size: var(--tt-text-xl);
}

.close-btn {
  border: 0;
  background: transparent;
  color: var(--tt-text-secondary);
  cursor: pointer;
}

.settings-tabs {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--tt-divider);
}

.settings-tab,
.option-btn,
.panel-btn {
  border: 1px solid var(--tt-border);
  border-radius: var(--tt-radius-md);
  background: var(--tt-surface);
  color: var(--tt-text);
  cursor: pointer;
  font-weight: 700;
}

.settings-tab,
.option-btn {
  min-height: 36px;
  padding: 0 12px;
}

.settings-tab.active,
.option-btn.active {
  border-color: var(--tt-accent-border);
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
}

.settings-message {
  color: var(--tt-success);
  border-bottom: 1px solid var(--tt-divider);
}

.settings-body {
  display: grid;
  gap: 14px;
}

.setting-row,
.account-card {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
}

.setting-title,
.account-label,
.account-value {
  margin: 0;
}

.setting-control,
.account-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.account-avatar {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  font-weight: 800;
}

.account-info {
  flex: 1;
}

.account-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.panel-btn {
  min-height: 38px;
  padding: 0 14px;
}

.panel-btn.danger {
  color: var(--tt-danger);
}

.layout-mask {
  position: fixed;
  inset: 0;
  z-index: 900;
  background: rgba(0, 0, 0, 0.32);
}

.admin-sidebar {
  position: sticky;
  top: 0;
  z-index: 950;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--tt-bg-elevated);
  border-right: 1px solid var(--tt-divider);
  transition: width var(--tt-duration-normal) var(--tt-ease), transform var(--tt-duration-normal) var(--tt-ease);
}

.admin-sidebar.collapsed {
  width: var(--tt-sidebar-collapsed-width);
}

.sidebar-header,
.sidebar-footer {
  padding: 16px 12px;
}

.brand,
.nav-item,
.collapse-btn,
.mobile-menu {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-family: var(--tt-font);
}

.brand {
  width: 100%;
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  border-radius: var(--tt-radius-md);
}

.brand:hover,
.nav-item:hover,
.collapse-btn:hover,
.mobile-menu:hover {
  background: var(--tt-surface-hover);
}

.brand-mark,
.nav-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: pointer;
}

.brand-logo {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.brand-copy {
  display: grid;
  gap: 2px;
  text-align: left;
}

.brand-home {
  width: fit-content;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--tt-text);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
  text-align: left;
}

.brand-copy span {
  color: var(--tt-text-secondary);
  font-size: var(--tt-text-xs);
}

.sidebar-nav {
  flex: 1;
  display: grid;
  align-content: start;
  gap: 6px;
  padding: 6px 12px;
}

.nav-item {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  border-radius: var(--tt-radius-md);
  color: var(--tt-text-secondary);
  text-align: left;
}

.nav-item.active {
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
}

.nav-label {
  white-space: nowrap;
  font-weight: 700;
}

.collapse-btn {
  width: 100%;
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border-radius: var(--tt-radius-md);
  color: var(--tt-text-secondary);
}

.admin-main {
  min-width: 0;
  background:
    linear-gradient(180deg, color-mix(in oklab, var(--admin-bg-start) 92%, transparent), transparent 220px),
    var(--admin-bg-end);
}

.admin-header {
  position: sticky;
  top: 0;
  z-index: 500;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 28px;
  background: color-mix(in oklab, var(--tt-bg) 88%, transparent);
  border-bottom: 1px solid var(--tt-divider);
  backdrop-filter: blur(16px);
}

.header-primary,
.header-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.welcome-eyebrow {
  margin: 0;
  font-size: var(--tt-text-xs);
  color: var(--tt-text-tertiary);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.welcome-title {
  margin: 4px 0 0;
  color: var(--tt-text);
  font-size: clamp(22px, 3vw, 30px);
  line-height: 1.15;
}

.welcome-desc {
  margin: 6px 0 0;
  color: var(--tt-text-secondary);
  line-height: 1.45;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: var(--tt-radius-full);
  background: var(--tt-success-soft);
  color: var(--tt-success);
  font-size: var(--tt-text-xs);
  font-weight: 800;
}

.content {
  padding: 24px 28px 36px;
}

.content-shell {
  width: min(1280px, 100%);
  margin: 0 auto;
}

.admin-content-shell {
  display: grid;
}

.desktop-only {
  display: inline-flex;
}

.mobile-only {
  display: none;
}

@media (max-width: 900px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    position: fixed;
    left: 0;
    top: 0;
    width: min(280px, 86vw);
    transform: translateX(-100%);
  }

  .admin-sidebar.open {
    transform: translateX(0);
  }

  .admin-sidebar.collapsed {
    width: min(280px, 86vw);
  }

  .desktop-only {
    display: none;
  }

  .mobile-only {
    display: inline-flex;
  }

  .admin-header {
    align-items: flex-start;
    padding: 16px;
  }

  .header-tools {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .content {
    padding: 16px;
  }
}

@media (max-width: 640px) {
  .admin-header {
    flex-direction: column;
  }

  .header-tools {
    width: 100%;
    justify-content: flex-start;
  }

  .setting-row,
  .account-card {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>

<style scoped>
.admin-layout {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 16% 12%, oklch(0.86 0.1 207 / 0.58), transparent 30%),
    radial-gradient(circle at 84% 10%, oklch(0.9 0.08 344 / 0.38), transparent 27%),
    radial-gradient(circle at 54% 92%, oklch(0.9 0.09 190 / 0.32), transparent 34%),
    linear-gradient(145deg, var(--admin-bg-start), oklch(0.95 0.035 226) 48%, var(--admin-bg-end));
  color: var(--admin-text-primary);
}

.admin-layout::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(oklch(0.55 0.04 230 / 0.08) 1px, transparent 1px),
    linear-gradient(90deg, oklch(0.55 0.04 230 / 0.08) 1px, transparent 1px);
  background-size: 68px 68px;
  mask-image: radial-gradient(circle at 50% 40%, black, transparent 78%);
}

.admin-sidebar {
  background:
    radial-gradient(circle at 10% 10%, oklch(0.94 0.06 78 / 0.56), transparent 36%),
    oklch(0.99 0.014 220 / 0.78);
  border-right-color: var(--admin-shell-border);
  box-shadow: 18px 0 60px oklch(0.52 0.08 230 / 0.12);
  backdrop-filter: blur(18px);
}

.sidebar-header,
.sidebar-footer {
  border-color: oklch(0.76 0.045 224 / 0.28);
}

.brand-mark {
  width: 48px;
  height: 48px;
  border-radius: 18px;
  overflow: hidden;
  background: oklch(0.99 0.01 230);
  box-shadow:
    0 14px 32px oklch(0.5 0.09 230 / 0.16),
    0 0 0 6px oklch(0.99 0.012 220 / 0.48);
}

.brand-copy strong {
  letter-spacing: 0;
  color: var(--admin-text-primary);
}

.brand-copy span {
  color: var(--admin-text-tertiary);
  font-weight: 800;
}

.nav-item {
  min-height: 44px;
  border-radius: 999px;
  color: var(--admin-text-secondary);
  transition: background 180ms ease, color 180ms ease, transform 180ms ease, box-shadow 180ms ease;
}

.nav-item:hover {
  background: oklch(0.99 0.012 220 / 0.62);
  color: var(--admin-text-primary);
  transform: translateY(-1px);
  box-shadow: 0 12px 28px oklch(0.5 0.08 230 / 0.08);
}

.nav-item.active {
  background: linear-gradient(135deg, oklch(0.87 0.08 202 / 0.7), oklch(0.98 0.026 82 / 0.78));
  color: oklch(0.3 0.08 250);
  box-shadow: 0 16px 34px oklch(0.53 0.13 224 / 0.14);
}

.nav-icon-wrap {
  border-radius: 999px;
  background: oklch(0.97 0.018 220 / 0.82);
}

.collapse-btn,
.mobile-menu,
.tool-btn {
  border-color: var(--admin-border);
  border-radius: 999px;
  background: oklch(0.99 0.012 220 / 0.66);
  color: var(--admin-text-secondary);
}

.tool-btn {
  height: 38px;
  padding: 0 14px;
  box-shadow: var(--admin-soft-shadow);
}

.admin-main {
  position: relative;
  z-index: 1;
}

.admin-header {
  background: oklch(0.99 0.012 220 / 0.58);
  border-bottom-color: oklch(0.76 0.045 224 / 0.26);
  backdrop-filter: blur(18px);
}

.welcome-title {
  color: var(--admin-text-primary);
  font-size: clamp(24px, 3vw, 32px);
}

.welcome-desc {
  color: var(--admin-text-secondary);
  line-height: 1.55;
}

.welcome-eyebrow {
  color: var(--admin-text-tertiary);
  font-weight: 900;
}

.status-badge {
  background: oklch(0.9 0.08 160 / 0.42);
  color: var(--admin-success);
}

.settings-panel {
  border-radius: 28px;
  background: oklch(0.99 0.014 220 / 0.94);
  border-color: var(--admin-border);
  box-shadow: var(--admin-hover-shadow);
}

.content-shell {
  position: relative;
}
</style>
