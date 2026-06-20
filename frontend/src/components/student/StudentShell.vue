<script setup>
import '../../styles/student-theme.css'
import '../../styles/student-content-canvas.css'
import '../../styles/student-dialog-panels.css'
import '../../styles/student-pages.css'
import '../../styles/student-dark-surfaces.css'
import '../../styles/student-ui-polish.css'
import '../../styles/student-sidebar.css'
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchStudentClasses, fetchStudentNotificationUnreadCount } from '../../services/student'
import { clearTokenAndRemoveSession, getActiveSession, getTokenPayload } from '../../utils/auth'
import { buildStudentTaskCenterCrumbLocation, buildStudentTaskDetailLocation } from '../../utils/studentTaskNavigation'
import IconSystem from '../common/IconSystem.vue'
import SystemAnnouncementModal from '../common/SystemAnnouncementModal.vue'
const ChangePasswordDialog = defineAsyncComponent(() =>
  import('../common/ChangePasswordDialog.vue'),
)
const AccountSettingsPanel = defineAsyncComponent(() =>
  import('../common/AccountSettingsPanel.vue'),
)
import { applyStudentThemeToDocument } from '../../utils/theme'
import { resolveMediaUrl } from '../../utils/mediaUrl'
import {
  STUDENT_THEME_DARK,
  STUDENT_THEME_LIGHT,
  STUDENT_WORKSPACE_EVENT,
  STUDENT_LOCALE_KEY,
  STUDENT_THEME_KEY,
  getStudentLocale,
  getStudentSidebarCollapsed,
  getStudentTheme,
  setStudentLocale,
  setStudentSidebarCollapsed,
  setStudentTheme,
} from '../../utils/studentWorkspace'
import { STUDENT_LOCALE_INJECT_KEY, useStudentLocale } from '../../composables/useStudentLocale'
import { useWorkspaceDialogInset } from '../../composables/useWorkspaceDialogInset'
import { warmBackendOnce } from '../../utils/warmBackend'

const router = useRouter()
const route = useRoute()

const STUDENT_KEEP_ALIVE_ROUTE_NAMES = new Set([
  'student-home',
  'student-free-collaboration',
  'student-free-collaboration-section',
  'student-free-notifications',
  'student-tasks',
  'student-appeals',
  'student-notifications',
])

function studentRouteViewKey(childRoute) {
  const name = String(childRoute.name || childRoute.path)
  if (childRoute.name === 'student-free-collaboration-section') {
    return `${name}:${String(childRoute.params.section || '')}`
  }
  if (childRoute.name === 'student-free-notifications') {
    return String(childRoute.name)
  }
  if (STUDENT_KEEP_ALIVE_ROUTE_NAMES.has(childRoute.name)) {
    return name
  }
  const classId = childRoute.params.classId ? String(childRoute.params.classId) : ''
  const taskId = childRoute.params.taskId ? String(childRoute.params.taskId) : ''
  if (classId) {
    return `${name}:${classId}:${taskId}`
  }
  return name
}

const collapsed = ref(getStudentSidebarCollapsed())
const mobileSidebarOpen = ref(false)
const classes = ref([])
const activePanel = ref('')
const sessionVersion = ref(0)
const showSettings = ref(false)
const showProfileCenter = ref(false)
const locale = ref(getStudentLocale())
provide(STUDENT_LOCALE_INJECT_KEY, locale)
const theme = ref(getStudentTheme())

const showChangePassword = ref(false)
const showDeleteConfirm = ref(false)
const notificationUnreadCount = ref(0)
let notificationUnreadTimer = null

const { t, tm, isEn } = useStudentLocale()

const payload = computed(() => getTokenPayload() || {})
const sessionUser = computed(() => {
  sessionVersion.value
  return getActiveSession()?.user || {}
})
const studentName = computed(() => sessionUser.value?.name || payload.value?.name || t('同学', 'Student'))

const avatarLoadFailed = ref(false)
const rawAvatarUrl = computed(() => sessionUser.value?.avatarUrl || '')
const avatarUrl = computed(() => {
  if (avatarLoadFailed.value) return ''
  return resolveMediaUrl(rawAvatarUrl.value)
})

const isFreeCollaborationMode = computed(() => route.path.startsWith('/student/free'))
const currentWorkspaceMode = computed(() => isFreeCollaborationMode.value ? 'free' : 'classroom')
const workspaceModeLabel = computed(() => currentWorkspaceMode.value === 'free' ? '\u81ea\u7531\u534f\u4f5c' : '\u8bfe\u5802\u534f\u4f5c')

const classroomNavItems = computed(() => [
  { path: '/student', label: tm('shell.home'), icon: 'home' },
  { path: '/student/tasks', label: tm('shell.tasks'), icon: 'task' },
  { path: '/student/appeals', label: tm('shell.appeals'), icon: 'appeal' },
  { path: '/student/notifications', label: tm('shell.notifications'), icon: 'bell', badge: notificationUnreadCount.value },
])

const freeCollaborationNavItems = computed(() => [
  { path: '/student/free', label: '\u81ea\u7531\u9996\u9875', icon: 'home' },
  { path: '/student/free/spaces', label: '\u534f\u4f5c\u7a7a\u95f4', icon: 'users' },
  { path: '/student/free/tasks', label: '\u4efb\u52a1\u9762\u677f', icon: 'task' },
  { path: '/student/free/progress', label: '\u9879\u76ee\u8fdb\u5ea6', icon: 'task' },
  { path: '/student/free/contributions', label: '\u534f\u4f5c\u75d5\u8ff9', icon: 'chart' },
  { path: '/student/free/notifications', label: '\u534f\u4f5c\u901a\u77e5', icon: 'bell', badge: notificationUnreadCount.value },
])

const navItems = computed(() => isFreeCollaborationMode.value
  ? freeCollaborationNavItems.value
  : classroomNavItems.value)


const breadcrumb = computed(() => {
  const items = [{ label: t('学生端', 'Student'), path: '/student' }]
  const fromTaskCenter = String(route.query.from || '') === 'task-center'
  const { classId, taskId } = route.params

  if (route.path === '/student' || route.path === '/student/') {
    items.push({ label: tm('shell.home'), path: '/student' })
  } else if (route.path.startsWith('/student/free')) {
    items.push({ label: '自由协作', path: '/student/free' })
    const freeSection = route.path.startsWith('/student/free/notifications')
      ? 'notifications'
      : String(route.params.section || '')
    const sectionLabelMap = {
      spaces: '协作空间',
      tasks: '任务面板',
      progress: '项目进度',
      contributions: '协作痕迹',
      notifications: '协作通知',
    }
    if (sectionLabelMap[freeSection]) {
      items.push({ label: sectionLabelMap[freeSection], path: route.path })
    }
  } else if (fromTaskCenter && taskId) {
    items.push({ label: tm('shell.taskCenter'), ...buildStudentTaskCenterCrumbLocation(route.query) })
  } else if (route.path.startsWith('/student/classes') && classId) {
    items.push({ label: tm('shell.home'), path: '/student' })
    const matched = classes.value.find((c) => String(c.id) === String(classId))
    items.push({
      label: matched?.name || `${tm('common.class')} ${classId}`,
      path: `/student/classes/${classId}`,
      query: taskId ? { tab: 'tasks' } : undefined,
    })
    if (route.path.endsWith('/groups')) {
      items.push({ label: tm('shell.myGroup'), path: route.path })
    }
  } else if (route.path.startsWith('/student/tasks')) {
    items.push({ label: tm('shell.taskCenter'), path: '/student/tasks' })
  } else if (route.path.startsWith('/student/appeals')) {
    items.push({ label: tm('shell.appeals'), path: '/student/appeals' })
  } else if (route.path.startsWith('/student/notifications')) {
    items.push({ label: tm('shell.notifications'), path: '/student/notifications' })
  }

  if (taskId && classId) {
    items.push({
      label: `${tm('common.task')} ${taskId}`,
      ...buildStudentTaskDetailLocation(classId, taskId, route.query),
    })
    const activeTab = String(route.query.tab || '')
    if (activeTab === 'peer') {
      items.push({
        label: tm('shell.peerReview'),
        ...buildStudentTaskDetailLocation(classId, taskId, { ...route.query, tab: 'peer' }),
      })
    } else if (activeTab === 'score') {
      items.push({
        label: tm('shell.scores'),
        ...buildStudentTaskDetailLocation(classId, taskId, { ...route.query, tab: 'score' }),
      })
    } else if (activeTab === 'appeals') {
      items.push({
        label: tm('shell.appeal'),
        ...buildStudentTaskDetailLocation(classId, taskId, { ...route.query, tab: 'appeals' }),
      })
    }
  }

  return items
})

function navigate(target) {
  if (!target) return
  if (typeof target === 'string') {
    router.push(target)
  } else {
    router.push({
      path: target.path,
      query: target.query,
    })
  }
  mobileSidebarOpen.value = false
}

function openHiddenMoyuPage() {
  router.push({ path: '/moyu-terminator', query: { returnTo: route.fullPath || '/student' } })
  mobileSidebarOpen.value = false
}

function switchWorkspaceMode(mode) {
  closePanels()
  if (mode === currentWorkspaceMode.value) return
  navigate(mode === 'free' ? '/student/free' : '/student')
}

function toggleWorkspaceMode() {
  switchWorkspaceMode(currentWorkspaceMode.value === 'free' ? 'classroom' : 'free')
}

function isNavActive(path) {
  const fromTaskCenter = String(route.query.from || '') === 'task-center'
  if (path.startsWith('/student/free')) {
    if (path === '/student/free') return route.path === '/student/free'
    return route.path === path || route.path.startsWith(`${path}/`)
  }
  if (path === '/student') {
    if (route.path === '/student') return true
    if (route.path.startsWith('/student/classes')) {
      return !(fromTaskCenter && route.params.taskId)
    }
    return false
  }
  if (path === '/student/tasks') {
    if (route.path === '/student/tasks' || route.path.startsWith('/student/tasks/')) return true
    if (fromTaskCenter && route.params.taskId) return true
    return false
  }
  return route.path === path || route.path.startsWith(`${path}/`)
}

function syncWorkspacePreferences() {
  locale.value = getStudentLocale()
  theme.value = getStudentTheme()
  applyTheme(theme.value)
  collapsed.value = getStudentSidebarCollapsed()
}

function handleWorkspacePreferenceChange(event) {
  const key = event?.detail?.key
  if (!key || key === STUDENT_THEME_KEY) {
    theme.value = getStudentTheme()
    applyTheme(theme.value)
  }
  if (!key || key === STUDENT_LOCALE_KEY) {
    locale.value = getStudentLocale()
  }
  if (!key) {
    collapsed.value = getStudentSidebarCollapsed()
  }
}

function applyTheme(nextTheme) {
  const normalized = applyStudentThemeToDocument(nextTheme)
  if (theme.value !== normalized) {
    theme.value = normalized
  }
  setStudentTheme(normalized)
}

function switchLocale(nextLocale) {
  if (locale.value === nextLocale) {
    closePanels()
    return
  }
  locale.value = nextLocale
  closePanels()
}

function switchTheme(nextTheme) {
  const normalized = nextTheme === STUDENT_THEME_DARK ? STUDENT_THEME_DARK : STUDENT_THEME_LIGHT
  if (theme.value === normalized) {
    closePanels()
    return
  }
  theme.value = normalized
  closePanels()
}

function toggleSidebar() { collapsed.value = !collapsed.value }
function openPanel(name) { activePanel.value = activePanel.value === name ? '' : name }
function closePanels() { activePanel.value = '' }
function logout() { clearTokenAndRemoveSession(); router.replace('/auth') }
function openSettings() { showSettings.value = true; closePanels() }
function closeSettings() { showSettings.value = false }
function openProfileCenter() { showProfileCenter.value = true; closePanels() }
function closeProfileCenter() { showProfileCenter.value = false }

function openChangePassword() {
  if (document.activeElement instanceof HTMLElement) {
    document.activeElement.blur()
  }
  showChangePassword.value = true
  closeSettings()
}

function confirmDeleteAccount() {
  showDeleteConfirm.value = true
  closeSettings()
}

async function deleteAccount() {
  try {
    clearTokenAndRemoveSession()
    router.replace('/auth')
  } catch {
    alert(tm('shell.deleteFailed'))
  }
  showDeleteConfirm.value = false
}

function handleDocumentClick(e) { if (e.target instanceof HTMLElement && !e.target.closest('.tool-anchor')) closePanels() }
function handleKeydown(e) {
  if (e.key === 'Escape') {
    closePanels()
    closeSettings()
    closeProfileCenter()
  }
}
function handleSessionUpdated(event) { if (!event?.detail?.role || event.detail.role === 'student') sessionVersion.value += 1 }

function handleAvatarError() {
  avatarLoadFailed.value = true
}

const isStudentDialogFullscreen = () =>
  window.innerWidth <= 1120 || mobileSidebarOpen.value

useWorkspaceDialogInset({
  mainSelector: '.student-main',
  leftVar: '--student-dialog-inset-left',
  rightVar: '--student-dialog-inset-right',
  isFullscreen: isStudentDialogFullscreen,
  watchSources: [collapsed, mobileSidebarOpen, () => route.fullPath],
})

async function loadData() {
  try { const cRes = await fetchStudentClasses(); classes.value = cRes?.data?.data || [] } catch {}
}

async function refreshNotificationUnreadCount() {
  try {
    const { data } = await fetchStudentNotificationUnreadCount()
    notificationUnreadCount.value = Number(data?.data?.unreadCount ?? 0)
  } catch {
    notificationUnreadCount.value = 0
  }
}

function handleNotificationsChanged() {
  refreshNotificationUnreadCount()
}

watch(() => theme.value, (v) => applyTheme(v), { immediate: true })
watch(() => locale.value, (v) => setStudentLocale(v), { immediate: true })
watch(() => collapsed.value, (v) => {
  setStudentSidebarCollapsed(v)
}, { immediate: true })
watch(() => route.fullPath, () => { mobileSidebarOpen.value = false; closePanels() })
watch(rawAvatarUrl, () => {
  avatarLoadFailed.value = false
})

onMounted(() => {
  warmBackendOnce()
  loadData()
  refreshNotificationUnreadCount()
  notificationUnreadTimer = window.setInterval(refreshNotificationUnreadCount, 30000)
  syncWorkspacePreferences()
  window.addEventListener(STUDENT_WORKSPACE_EVENT, handleWorkspacePreferenceChange)
  window.addEventListener('click', handleDocumentClick)
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('teamtrace-session-updated', handleSessionUpdated)
  window.addEventListener('teamtrace-notifications-changed', handleNotificationsChanged)
})
onBeforeUnmount(() => {
  if (notificationUnreadTimer) {
    window.clearInterval(notificationUnreadTimer)
    notificationUnreadTimer = null
  }
  window.removeEventListener(STUDENT_WORKSPACE_EVENT, handleWorkspacePreferenceChange)
  window.removeEventListener('click', handleDocumentClick)
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('teamtrace-session-updated', handleSessionUpdated)
  window.removeEventListener('teamtrace-notifications-changed', handleNotificationsChanged)
})
</script>

<template>
  <div class="student-layout" :class="{ 'sidebar-collapsed': collapsed }">
    <div v-if="mobileSidebarOpen" class="layout-mask" @click="mobileSidebarOpen = false" />

    <aside class="student-sidebar" :class="{ collapsed, open: mobileSidebarOpen }">
      <div class="sidebar-header">
        <div class="brand" role="group" aria-label="TeamTrace ???">
          <button class="brand-mark" type="button" aria-label="??????????" @click="openHiddenMoyuPage">
            <img class="brand-logo" src="/TeamTraceLogo.png" alt="TeamTrace" />
          </button>
          <span v-if="!collapsed" class="brand-copy">
            <span class="brand-title-row">
              <button class="brand-home" type="button" @click="navigate(currentWorkspaceMode === 'free' ? '/student/free' : '/student')">TeamTrace</button>
              <button class="mode-switch-btn" type="button" :title="currentWorkspaceMode === 'free' ? '切换到课堂协作' : '切换到自由协作'" @click="toggleWorkspaceMode">
                  {{ workspaceModeLabel }}
                  <span v-if="currentWorkspaceMode === 'free'" class="mode-beta-badge">Beta</span>
                  <IconSystem name="chevronRight" :size="12" />
              </button>
            </span>
            <span>{{ currentWorkspaceMode === 'free' ? workspaceModeLabel : tm('shell.workspace') }}</span>
          </span>
        </div>
      </div>

      <nav class="sidebar-nav">
        <button
          v-for="item in navItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: isNavActive(item.path) }"
          type="button"
          :title="item.label"
          @click="navigate(item.path)"
        >
          <span class="nav-icon-wrap"><IconSystem :name="item.icon" :size="18" /></span>
          <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
          <span v-if="item.badge > 0" class="nav-badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
        </button>
      </nav>

      <div class="sidebar-footer">
        <button class="collapse-btn desktop-only" type="button" @click="toggleSidebar">
          <IconSystem :name="collapsed ? 'chevronRight' : 'chevronLeft'" :size="16" />
          <span v-if="!collapsed">{{ tm('shell.collapse') }}</span>
        </button>
      </div>
    </aside>

    <div class="student-main">
      <header class="student-header">
        <div class="header-primary">
          <button class="mobile-menu mobile-only" type="button" @click="mobileSidebarOpen = !mobileSidebarOpen">
            <IconSystem name="menu" :size="18" />
          </button>
        </div>
        <div class="header-tools">
          <div class="tool-anchor quick-tool-anchor">
            <button class="quick-tool-btn" :class="{ active: activePanel === 'language' }" type="button" :title="tm('shell.language')" @click.stop="openPanel('language')">
              <IconSystem name="language" :size="17" />
              <span class="quick-tool-label">{{ locale === 'en-US' ? 'EN' : '中文' }}</span>
            </button>
            <transition name="panel-fade">
              <section v-if="activePanel === 'language'" class="tool-popover quick-popover right-align" @click.stop>
                <p class="quick-popover-title">{{ tm('shell.language') }}</p>
                <div class="quick-option-list">
                  <button class="quick-option-btn" :class="{ active: locale === 'zh-CN' }" type="button" @click="switchLocale('zh-CN')">简体中文</button>
                  <button class="quick-option-btn" :class="{ active: locale === 'en-US' }" type="button" @click="switchLocale('en-US')">English</button>
                </div>
              </section>
            </transition>
          </div>
          <div class="tool-anchor quick-tool-anchor">
            <button class="quick-tool-btn" :class="{ active: activePanel === 'theme' }" type="button" :title="tm('shell.theme')" @click.stop="openPanel('theme')">
              <IconSystem :name="theme === STUDENT_THEME_DARK ? 'moon' : 'sun'" :size="17" />
              <span class="quick-tool-label">{{ theme === STUDENT_THEME_DARK ? tm('shell.dark') : tm('shell.light') }}</span>
            </button>
            <transition name="panel-fade">
              <section v-if="activePanel === 'theme'" class="tool-popover quick-popover right-align" @click.stop>
                <p class="quick-popover-title">{{ tm('shell.theme') }}</p>
                <div class="quick-option-list">
                  <button class="quick-option-btn" :class="{ active: theme === STUDENT_THEME_LIGHT }" type="button" @click="switchTheme(STUDENT_THEME_LIGHT)">
                    <IconSystem name="sun" :size="15" /> {{ tm('shell.light') }}
                  </button>
                  <button class="quick-option-btn" :class="{ active: theme === STUDENT_THEME_DARK }" type="button" @click="switchTheme(STUDENT_THEME_DARK)">
                    <IconSystem name="moon" :size="15" /> {{ tm('shell.dark') }}
                  </button>
                </div>
              </section>
            </transition>
          </div>
          <div class="tool-anchor">
            <button class="profile-btn" :class="{ active: activePanel === 'profile' }" type="button" @click.stop="openPanel('profile')">
              <span class="profile-avatar"><img v-if="avatarUrl" :src="avatarUrl" class="avatar-thumb" @error="handleAvatarError" /><span v-else>{{ studentName.slice(0, 1) }}</span></span>
              <span class="profile-name">{{ studentName }}</span>
            </button>
            <transition name="panel-fade">
              <section v-if="activePanel === 'profile'" class="tool-popover right-align" @click.stop>
                <div class="profile-card">
                  <div class="profile-avatar large"><img v-if="avatarUrl" :src="avatarUrl" class="avatar-thumb" @error="handleAvatarError" /><span v-else>{{ studentName.slice(0, 1) }}</span></div>
                  <div>
                    <p class="popover-name">{{ studentName }}</p>
                    <p class="popover-role">{{ t('学生', 'Student') }}</p>
                  </div>
                </div>
                <div class="panel-actions column">
                  <button class="panel-btn" type="button" @click="openSettings">
                    <IconSystem name="settings" :size="16" /> {{ tm('shell.settings') }}
                  </button>
                  <button class="panel-btn" type="button" @click="openProfileCenter">
                    <IconSystem name="user" :size="16" /> {{ t('个人中心', 'Profile') }}
                  </button>
                  <button class="panel-btn danger" type="button" @click="logout">
                    <IconSystem name="logout" :size="16" /> {{ tm('shell.logout') }}
                  </button>
                </div>
              </section>
            </transition>
          </div>
        </div>
      </header>

      <main class="content">
        <div class="content-shell">
          <section class="shell-topline">
            <div class="topline-row">
              <div class="crumbs">
                <template v-for="(item, index) in breadcrumb" :key="`${item.label}-${index}`">
                  <button class="crumb" type="button" @click="navigate(item)">{{ item.label }}</button>
                  <span v-if="index < breadcrumb.length - 1" class="crumb-sep">
                    <IconSystem name="chevronRight" :size="12" />
                  </span>
                </template>
              </div>
            </div>
          </section>
          <div class="route-viewport">
            <RouterView v-slot="{ Component, route: childRoute }">
              <keep-alive :max="6">
                <component :is="Component" :key="studentRouteViewKey(childRoute)" />
              </keep-alive>
            </RouterView>
          </div>
        </div>
      </main>
    </div>

    <Teleport to="body">
      <div v-if="showSettings" class="settings-overlay" @click.self="closeSettings">
        <div class="settings-panel">
          <div class="settings-header">
            <h2>{{ tm('shell.settings') }}</h2>
            <button class="close-btn" type="button" @click="closeSettings"><IconSystem name="close" :size="18" /></button>
          </div>
          <div class="settings-body">
            <div class="setting-row">
              <div class="setting-label"><p class="setting-title">{{ tm('shell.language') }}</p></div>
              <div class="setting-control">
                <button class="option-btn" :class="{ active: locale === 'zh-CN' }" type="button" @click="switchLocale('zh-CN')">简体中文</button>
                <button class="option-btn" :class="{ active: locale === 'en-US' }" type="button" @click="switchLocale('en-US')">English</button>
              </div>
            </div>
            <div class="setting-row">
              <div class="setting-label"><p class="setting-title">{{ tm('shell.theme') }}</p></div>
              <div class="setting-control">
                <button class="option-btn" :class="{ active: theme === STUDENT_THEME_LIGHT }" type="button" @click="switchTheme(STUDENT_THEME_LIGHT)">{{ tm('shell.light') }}</button>
                <button class="option-btn" :class="{ active: theme === STUDENT_THEME_DARK }" type="button" @click="switchTheme(STUDENT_THEME_DARK)">{{ tm('shell.dark') }}</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="showProfileCenter" class="settings-overlay" @click.self="closeProfileCenter">
        <div class="settings-panel profile-center-panel">
          <div class="settings-header">
            <h2>{{ t('个人中心', 'Profile') }}</h2>
            <button class="close-btn" type="button" @click="closeProfileCenter"><IconSystem name="close" :size="18" /></button>
          </div>
          <div class="settings-body">
            <AccountSettingsPanel
              :visible="showProfileCenter"
              :role="'student'"
              @close="closeProfileCenter"
              @openChangePassword="openChangePassword()"
            />
          </div>
        </div>
      </div>
    </Teleport>

    <ChangePasswordDialog
      :visible="showChangePassword"
      :user-email="sessionUser.email || payload.email || ''"
      :is-en="isEn"
      @close="showChangePassword = false"
    />

    <Teleport to="body">
      <div v-if="showDeleteConfirm" class="confirm-overlay" @click.self="showDeleteConfirm = false">
        <div class="confirm-panel">
          <h3 class="confirm-title">{{ tm('shell.deleteConfirmTitle') }}</h3>
          <p class="confirm-desc">{{ tm('shell.deleteConfirmDesc') }}</p>
          <div class="confirm-actions">
            <button class="confirm-btn" type="button" @click="showDeleteConfirm = false">{{ tm('common.cancel') }}</button>
            <button class="confirm-btn danger" type="button" @click="deleteAccount">{{ tm('shell.confirmDelete') }}</button>
          </div>
        </div>
      </div>
    </Teleport>
    <SystemAnnouncementModal />
  </div>
</template>

<style scoped>
.student-layout {
  height: 100vh;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  background: var(--tt-bg);
  color: var(--tt-text);
  font-family: var(--tt-font);
  overflow: hidden;
}

.student-layout::before {
  content: '';
  position: fixed;
  right: -140px;
  top: 84px;
  width: 320px;
  height: 320px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(0, 82, 255, 0.12), transparent 68%);
  filter: blur(6px);
  pointer-events: none;
  animation: tt-float-soft 8s var(--tt-ease-ios) infinite;
}

.layout-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 80;
  backdrop-filter: blur(4px);
}

.student-sidebar {
  width: var(--tt-sidebar-width);
  height: 100vh;
  z-index: 90;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--student-sidebar-bg, var(--tt-bg-overlay));
  border-right: 1px solid var(--tt-divider);
  backdrop-filter: blur(18px);
  transition: width var(--tt-duration-normal) var(--tt-ease),
              transform var(--tt-duration-normal) var(--tt-ease);
}

.student-sidebar.collapsed {
  width: var(--tt-sidebar-collapsed-width);
}

.sidebar-header {
  padding: var(--tt-space-5) var(--tt-space-4);
  border-bottom: 1px solid var(--tt-divider);
}

.brand {
  width: 100%;
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  gap: var(--tt-space-3);
  padding: 0;
  color: inherit;
  text-align: left;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border: 0;
  padding: 0;
  border-radius: var(--tt-radius-lg);
  background: transparent;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  position: relative;
}

.brand-logo {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.brand-copy {
  min-width: 0;
  display: grid;
  gap: 3px;
  font-size: var(--tt-text-xs);
  color: var(--tt-text-secondary);
}

.brand-title-row {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-switch-btn {
  height: 24px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 999px;
  padding: 0 8px 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(37, 99, 235, 0.08);
  color: var(--tt-accent);
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
}

.mode-switch-btn:hover,
.mode-switch-btn.active {
  border-color: rgba(37, 99, 235, 0.26);
  background: rgba(37, 99, 235, 0.13);
}

.mode-beta-badge {
  border-radius: 999px;
  padding: 1px 5px;
  background: rgba(245, 158, 11, 0.16);
  color: #b45309;
  font-size: 9px;
  line-height: 1.2;
  font-weight: 900;
}

.brand-home {
  width: fit-content;
  border: 0;
  padding: 0;
  background: transparent;
  font-size: var(--tt-text-base);
  color: var(--tt-text);
  font-weight: 700;
  cursor: pointer;
  text-align: left;
}

.sidebar-nav {
  padding: var(--tt-space-4) var(--tt-space-3);
  display: grid;
  gap: var(--tt-space-1);
  flex: 1;
}

.nav-item {
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--tt-text-secondary);
  border-radius: var(--tt-radius-md);
  padding: var(--tt-space-2) var(--tt-space-3);
  display: flex;
  align-items: center;
  gap: var(--tt-space-3);
  position: relative;
  text-align: left;
  cursor: pointer;
  font-family: var(--tt-font);
  font-size: var(--tt-text-sm);
  font-weight: 500;
  transition: all var(--tt-duration-fast) var(--tt-ease-ios);
}

.nav-item:hover {
  background: var(--tt-surface-hover);
  color: var(--tt-text);
  transform: translateX(2px);
}

.nav-item.active {
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  font-weight: 600;
  box-shadow: none;
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  bottom: 6px;
  width: 2.5px;
  border-radius: 999px;
  background: var(--tt-accent-gradient);
}

.nav-icon-wrap {
  width: 32px;
  height: 32px;
  border-radius: var(--tt-radius-sm);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: inherit;
  transition: all var(--tt-duration-fast) var(--tt-ease-ios);
}

.nav-item.active .nav-icon-wrap {
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  box-shadow: none;
}

.nav-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-badge {
  position: absolute;
  top: 8px;
  right: 10px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--student-danger, #ff3b30);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  line-height: 18px;
  text-align: center;
  box-shadow: 0 6px 14px rgba(255, 59, 48, 0.22);
  pointer-events: none;
}

.student-sidebar.collapsed .nav-badge {
  top: 6px;
  right: 8px;
}

.sidebar-footer {
  margin-top: auto;
  padding: var(--tt-space-3);
  border-top: 1px solid var(--tt-divider);
  flex-shrink: 0;
}

.collapse-btn {
  width: 100%;
  border: 0;
  background: transparent;
  padding: var(--tt-space-2) var(--tt-space-3);
  border-radius: var(--tt-radius-md);
  display: inline-flex;
  align-items: center;
  gap: var(--tt-space-2);
  font-family: var(--tt-font);
  font-size: var(--tt-text-xs);
  color: var(--tt-text-secondary);
  cursor: pointer;
  transition: all var(--tt-duration-fast) var(--tt-ease);
}

.collapse-btn:hover {
  background: var(--tt-surface-hover);
  color: var(--tt-text);
}

.student-main {
  min-width: 0;
  position: relative;
  z-index: 1;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-gutter: stable;
  height: 100vh;
  background: var(--tt-bg);
}

.student-header {
  position: sticky;
  top: 0;
  z-index: 70;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--tt-space-4);
  height: var(--tt-header-height);
  padding: 0 var(--tt-page-padding);
  background: var(--student-header-bg, var(--tt-bg-overlay));
  border-bottom: 1px solid var(--tt-divider);
  backdrop-filter: blur(12px);
}

.header-primary {
  display: flex;
  align-items: center;
  gap: var(--tt-space-3);
  min-width: 0;
}

.header-tools {
  display: flex;
  align-items: center;
  gap: var(--tt-space-2);
}

.quick-tool-anchor {
  flex: 0 0 auto;
}

.quick-tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--tt-space-2);
  height: 36px;
  min-width: 48px;
  padding: 0 var(--tt-space-3);
  border: 1px solid var(--tt-border);
  border-radius: var(--tt-radius-full);
  background: var(--tt-surface);
  color: var(--tt-text);
  cursor: pointer;
  font-family: var(--tt-font);
  font-size: var(--tt-text-xs);
  font-weight: 700;
  line-height: 1;
  transition: all var(--tt-duration-fast) var(--tt-ease);
}

.quick-tool-btn:hover,
.quick-tool-btn.active {
  border-color: var(--tt-border-strong);
  box-shadow: var(--tt-shadow-md);
  transform: translateY(-1px);
}

.quick-tool-label {
  max-width: 44px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-menu {
  display: none;
  border: 1px solid var(--tt-border);
  border-radius: var(--tt-radius-md);
  background: var(--tt-surface);
  color: var(--tt-text);
  padding: var(--tt-space-2);
  cursor: pointer;
}

.profile-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--tt-space-2);
  border: 1px solid var(--tt-border);
  border-radius: var(--tt-radius-full);
  padding: var(--tt-space-1) var(--tt-space-3) var(--tt-space-1) var(--tt-space-1);
  background: var(--tt-surface);
  color: var(--tt-text);
  cursor: pointer;
  font-family: var(--tt-font);
  transition: all var(--tt-duration-fast) var(--tt-ease);
}

.profile-btn:hover,
.profile-btn.active {
  border-color: var(--tt-border-strong);
  box-shadow: var(--tt-shadow-md);
  transform: translateY(-1px);
}

.profile-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--student-accent-gradient, var(--tt-accent-gradient, linear-gradient(135deg, #3b82f6, #60a5fa)));
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--tt-text-xs);
  font-weight: 700;
  flex-shrink: 0;
}

.profile-avatar.large {
  width: 44px;
  height: 44px;
  font-size: var(--tt-text-base);
}

.profile-name {
  font-size: var(--tt-text-sm);
  font-weight: 500;
}

.tool-anchor { position: relative; }

.tool-popover {
  position: absolute;
  top: calc(100% + var(--tt-space-2));
  width: 260px;
  border-radius: var(--tt-radius-lg);
  background: var(--tt-bg-elevated);
  box-shadow: var(--tt-shadow-xl);
  border: 1px solid var(--tt-border);
  padding: var(--tt-space-4);
  z-index: 120;
}

.tool-popover.right-align { right: 0; }

.quick-popover {
  width: 184px;
  padding: var(--tt-space-3);
}

.quick-popover-title {
  margin: 0 0 var(--tt-space-2);
  color: var(--tt-text-muted);
  font-size: var(--tt-text-xs);
  font-weight: 700;
}

.quick-option-list {
  display: grid;
  gap: var(--tt-space-1);
}

.quick-option-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--tt-space-2);
  width: 100%;
  min-height: 34px;
  padding: 0 var(--tt-space-3);
  border: 0;
  border-radius: var(--tt-radius-md);
  background: transparent;
  color: var(--tt-text);
  cursor: pointer;
  font-family: var(--tt-font);
  font-size: var(--tt-text-sm);
  font-weight: 600;
  text-align: left;
  transition: all var(--tt-duration-fast) var(--tt-ease);
}

.quick-option-btn:hover {
  background: var(--tt-surface-hover);
}

.quick-option-btn.active {
  background: var(--tt-accent-soft, rgba(37, 99, 235, 0.12));
  color: var(--tt-accent, #2563eb);
}

.panel-fade-enter-active,
.panel-fade-leave-active {
  transition: opacity var(--tt-duration-fast) var(--tt-ease),
              transform var(--tt-duration-fast) var(--tt-ease);
}

.panel-fade-enter-from,
.panel-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.profile-card {
  display: flex;
  align-items: center;
  gap: var(--tt-space-3);
  padding-bottom: var(--tt-space-3);
  border-bottom: 1px solid var(--tt-divider);
  margin-bottom: var(--tt-space-3);
}

.popover-name {
  margin: 0;
  font-size: var(--tt-text-base);
  font-weight: 600;
  color: var(--tt-text);
}

.popover-role {
  margin: 2px 0 0;
  font-size: var(--tt-text-xs);
  color: var(--tt-text-tertiary);
}

.panel-actions {
  display: flex;
  gap: var(--tt-space-1);
}

.panel-actions.column {
  flex-direction: column;
}

.panel-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--tt-space-2);
  min-height: 34px;
  padding: 0 var(--tt-space-3);
  border-radius: var(--tt-radius-md);
  background: var(--tt-bg-elevated);
  color: var(--tt-text);
  border: 0;
  cursor: pointer;
  font-family: var(--tt-font);
  font-size: var(--tt-text-sm);
  font-weight: 500;
  transition: all var(--tt-duration-fast) var(--tt-ease);
}

.panel-btn:hover {
  background: var(--tt-surface-hover);
}

.panel-btn.danger {
  color: var(--tt-danger);
}

.panel-btn.danger:hover {
  background: var(--tt-danger-soft);
}

.content {
  padding: var(--tt-space-5) var(--tt-page-padding) var(--tt-space-8);
}

.content-shell {
  width: 100%;
  max-width: var(--tt-content-max-width);
  margin: 0 auto;
}

.shell-topline {
  margin-bottom: var(--tt-space-5);
}

.topline-row {
  display: flex;
  align-items: center;
  gap: var(--tt-space-2);
  flex-wrap: wrap;
}

.crumbs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--tt-space-1);
  align-items: center;
}

.crumb {
  border: 1px solid var(--tt-border-subtle);
  padding: 7px 12px;
  border-radius: var(--tt-radius-full);
  background: var(--tt-surface);
  color: var(--tt-text-secondary);
  cursor: pointer;
  font-family: var(--tt-font);
  font-size: var(--tt-text-xs);
  font-weight: 500;
  box-shadow: var(--tt-shadow-xs);
  transition: all var(--tt-duration-fast) var(--tt-ease-ios);
}

.crumb:hover {
  color: var(--tt-text);
  background: var(--tt-surface-hover);
  border-color: var(--tt-accent-border);
  box-shadow: var(--tt-shadow-sm);
}

.crumb-sep {
  color: var(--tt-text-tertiary);
  display: inline-flex;
  align-items: center;
}

/* Settings / confirm: workspace-settings.css + teacher-dialog.css */

.desktop-only { display: inline-flex; }
.mobile-only { display: none; }

@media (max-width: 1120px) {
  .student-layout {
    grid-template-columns: 1fr;
  }

  .student-sidebar {
    position: fixed;
    left: 0;
    transform: translateX(-100%);
  }

  .student-sidebar.collapsed {
    width: var(--tt-sidebar-width);
  }

  .student-sidebar.open {
    transform: translateX(0);
  }

  .desktop-only { display: none; }
  .mobile-only { display: inline-flex; }
}

@media (max-width: 900px) {
  .student-header {
    flex-direction: column;
    align-items: stretch;
    height: auto;
    padding: var(--tt-space-3) var(--tt-page-padding);
    gap: var(--tt-space-3);
  }

  .header-tools {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .profile-name { display: none; }
  .tool-popover { left: 0; right: auto; }
}

.profile-avatar img.avatar-thumb {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}
</style>

