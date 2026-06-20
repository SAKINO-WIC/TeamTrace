<script setup>
import '../../styles/teacher-theme.css'
import '../../styles/teacher-content-canvas.css'
import '../../styles/teacher-dark-surfaces.css'
import '../../styles/teacher-sidebar.css'
import '../../styles/teacher-dark-pages.css'
import { useWorkspaceDialogInset } from '../../composables/useWorkspaceDialogInset'
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { scheduleIdle } from '../../utils/scheduleIdle'
import { warmBackendOnce } from '../../utils/warmBackend'
import { useRoute, useRouter } from 'vue-router'
import { fetchTeacherClasses } from '../../services/teacher'
import { isTeacherClassTaskDetailPath, resolveTeacherTaskDetailEntry } from '../../utils/teacherTaskNavigation'
import { clearTokenAndRemoveSession, getActiveSession, getTokenPayload } from '../../utils/auth'
import { applyTeacherThemeToDocument } from '../../utils/theme'
import { resolveMediaUrl } from '../../utils/mediaUrl'
import {
  TEACHER_THEME_DARK,
  TEACHER_THEME_LIGHT,
  TEACHER_WORKSPACE_EVENT,
  TEACHER_CLASSES_CHANGED_EVENT,
  TEACHER_CLASSES_INJECT_KEY,
  TEACHER_LOCALE_KEY,
  TEACHER_THEME_KEY,
  getTeacherLocale,
  getTeacherSidebarCollapsed,
  getTeacherTheme,
  loadTeacherNotices,
  loadTeacherProfileCache,
  saveTeacherProfileCache,
  setTeacherLocale,
  setTeacherSidebarCollapsed,
  setTeacherTheme,
} from '../../utils/teacherWorkspace'
import { fetchTeacherAccountProfile, fetchTeacherNoticeUnreadCount } from '../../services/teacherLocal'
import IconSystem from '../common/IconSystem.vue'
import SystemAnnouncementModal from '../common/SystemAnnouncementModal.vue'
const ChangePasswordDialog = defineAsyncComponent(() =>
  import('../common/ChangePasswordDialog.vue'),
)
const AccountSettingsPanel = defineAsyncComponent(() => import('../common/AccountSettingsPanel.vue'))
import { TEACHER_LOCALE_INJECT_KEY } from '../../composables/useTeacherLocale'

const router = useRouter()
const route = useRoute()

const collapsed = ref(getTeacherSidebarCollapsed())
const mobileSidebarOpen = ref(false)
const classes = ref([])
const locale = ref(getTeacherLocale())
provide(TEACHER_LOCALE_INJECT_KEY, locale)
provide(TEACHER_CLASSES_INJECT_KEY, { list: classes, reload: loadClasses })
const theme = ref(getTeacherTheme())
const notices = ref(loadTeacherNotices())
const profileCache = ref(loadTeacherProfileCache({}))
const activePanel = ref('')
const showSettings = ref(false)
const showProfileCenter = ref(false)
const showChangePassword = ref(false)
const classSwitcherOpen = ref(false)
const notificationUnreadCount = ref(0)
let notificationUnreadTimer = null

const payload = computed(() => getTokenPayload() || {})
const classRouteId = computed(() => String(route.params.classId || ''))
/** 班级/任务切换时强制重挂载子路由，避免复用组件导致右侧内容不刷新 */
/** 侧边栏一级页保留实例，避免切回「班级/申诉/评分」时整页重载 */
const TEACHER_KEEP_ALIVE_ROUTE_NAMES = new Set([
  'teacher-classes',
  'teacher-appeals-center',
  'teacher-scores-center',
])

function teacherRouteViewKey(currentRoute) {
  const name = String(currentRoute.name || currentRoute.path)
  if (TEACHER_KEEP_ALIVE_ROUTE_NAMES.has(currentRoute.name)) {
    return name
  }
  const classId = currentRoute.params.classId ? String(currentRoute.params.classId) : ''
  const taskId = currentRoute.params.taskId ? String(currentRoute.params.taskId) : ''
  if (classId) {
    return `${name}:${classId}:${taskId}`
  }
  return name
}
const sessionUser = computed(() => getActiveSession()?.user || {})
const teacherName = computed(() => profileCache.value?.name || sessionUser.value?.name || payload.value?.name || '教师')
const avatarLoadFailed = ref(false)
const rawAvatarUrl = computed(() => profileCache.value?.avatarUrl || sessionUser.value?.avatarUrl || '')
const avatarUrl = computed(() => {
  if (avatarLoadFailed.value) return ''
  return resolveMediaUrl(rawAvatarUrl.value)
})
const currentHour = computed(() => new Date().getHours())
const currentClass = computed(() => classes.value.find((item) => item.classId === classRouteId.value) || null)

const greetingText = computed(() => {
  if (locale.value === 'en-US') {
    if (currentHour.value < 12) return `Good morning, ${teacherName.value}`
    if (currentHour.value < 18) return `Good afternoon, ${teacherName.value}`
    return `Good evening, ${teacherName.value}`
  }
  if (currentHour.value < 12) return `上午好，${teacherName.value}`
  if (currentHour.value < 18) return `下午好，${teacherName.value}`
  return `晚上好，${teacherName.value}`
})

const mainNavItems = computed(() => [
  { path: '/teacher/classes', label: locale.value === 'en-US' ? 'Classes' : '班级中心', icon: 'class' },
])



const workflowNavItems = computed(() => [
  { path: '/teacher/scores', label: locale.value === 'en-US' ? 'Scoring' : '评分中心', icon: 'score' },
  { path: '/teacher/appeals', label: locale.value === 'en-US' ? 'Appeals' : '申诉中心', icon: 'appeal' },
])

const supportNavItems = computed(() => [
  { path: '/teacher/notifications-logs', label: locale.value === 'en-US' ? 'Notifications' : '通知', icon: 'bell', badge: notificationUnreadCount.value },
])

const classWorkspaceTabs = computed(() => {
  if (!classRouteId.value) return []
  const classPath = `/teacher/classes/${classRouteId.value}`
  return [
    { id: 'overview', path: classPath, label: locale.value === 'en-US' ? 'Overview' : '班级概览' },
    { id: 'students', path: `${classPath}/students`, label: locale.value === 'en-US' ? 'Students' : '学生管理' },
    { id: 'groups', path: `${classPath}/groups`, label: locale.value === 'en-US' ? 'Groups' : '分组管理' },
    { id: 'tasks', path: `${classPath}/tasks`, label: locale.value === 'en-US' ? 'Tasks' : '任务管理' },
    { id: 'task-detail', path: null, label: locale.value === 'en-US' ? 'Task Detail' : '任务详情' },
  ]
})

const breadcrumb = computed(() => {
  const items = [{ label: locale.value === 'en-US' ? 'Teacher' : '教师端', path: '/teacher/classes' }]
  if (route.path.startsWith('/teacher/classes')) {
    items.push({ label: locale.value === 'en-US' ? 'Classes' : '班级中心', path: '/teacher/classes' })
  } else if (route.path.startsWith('/teacher/scores')) {
    items.push({ label: locale.value === 'en-US' ? 'Scoring' : '评分中心', path: '/teacher/scores' })
  } else if (route.path.startsWith('/teacher/appeals')) {
    items.push({ label: locale.value === 'en-US' ? 'Appeals' : '申诉中心', path: '/teacher/appeals' })
  } else if (route.path.startsWith('/teacher/notifications')) {
    items.push({ label: locale.value === 'en-US' ? 'Notifications' : '通知', path: '/teacher/notifications-logs' })
  } else if (route.path.startsWith('/teacher/profile') || route.path.startsWith('/teacher/settings')) {
    items.push({ label: locale.value === 'en-US' ? 'Profile' : '个人中心', path: '/teacher/profile' })
  }
  if (classRouteId.value) {
    items.push({ label: currentClass.value?.name || `${locale.value === 'en-US' ? 'Class' : '班级'} ${classRouteId.value}`, path: `/teacher/classes/${classRouteId.value}` })
  }
  if (isTeacherClassTaskDetailPath(route.path, classRouteId.value)) {
    items.push({
      label: locale.value === 'en-US' ? 'Task Detail' : '任务详情',
      path: route.fullPath,
    })
  }
  return items
})

const showClassSwitcher = computed(() => Boolean(classRouteId.value) && route.path.startsWith('/teacher/classes/'))
const displayBreadcrumb = computed(() => {
  if (!showClassSwitcher.value) {
    return breadcrumb.value
  }
  const classPath = `/teacher/classes/${classRouteId.value}`
  return breadcrumb.value.filter((item) => item.path !== classPath)
})

function navigate(path) {
  classSwitcherOpen.value = false
  mobileSidebarOpen.value = false
  closePanels()
  router.push(path)
}

function openHiddenMoyuPage() {
  classSwitcherOpen.value = false
  mobileSidebarOpen.value = false
  closePanels()
  router.push({ path: '/moyu-terminator', query: { returnTo: route.fullPath || '/teacher/classes' } })
}
async function switchClass(targetClassId) {
  const nextClassId = String(targetClassId || '')
  if (!nextClassId || nextClassId === classRouteId.value) {
    classSwitcherOpen.value = false
    return
  }

  const currentBase = `/teacher/classes/${classRouteId.value}`
  const nextBase = `/teacher/classes/${nextClassId}`
  if (route.path.startsWith(currentBase)) {
    const suffix = route.path.slice(currentBase.length)
    // 保留班级二级页；任务详情页切到目标班级的任务详情入口，避免回落到任务列表。
    let nextSuffix = ''
    if (suffix === '/students' || suffix === '/groups' || suffix === '/tasks') {
      nextSuffix = suffix
    } else if (suffix.startsWith('/tasks/')) {
      const location = await resolveTeacherTaskDetailEntry(nextClassId)
      router.push(location)
      classSwitcherOpen.value = false
      return
    }
    router.push(`${nextBase}${nextSuffix}`)
  } else {
    router.push(nextBase)
  }
  classSwitcherOpen.value = false
}
function normalizeClass(raw) {
  return {
    classId: String(raw?.classId ?? raw?.id ?? ''),
    name: raw?.name ?? raw?.className ?? '未命名班级',
    statusLabel: raw?.statusLabel ?? (Number(raw?.status) === 1 ? '进行中' : '已归档'),
  }
}
function isNavActive(path) {
  if (path === '/teacher') return route.path === '/teacher'
  if (path === '/teacher/classes' && classRouteId.value) return false
  return route.path === path || route.path.startsWith(`${path}/`)
}
function isClassWorkspaceTabActive(tab) {
  const classPath = `/teacher/classes/${classRouteId.value}`
  if (tab.id === 'overview') return route.path === classPath
  if (tab.id === 'tasks') return route.path === tab.path
  if (tab.id === 'task-detail') return isTeacherClassTaskDetailPath(route.path, classRouteId.value)
  return route.path === tab.path || route.path.startsWith(`${tab.path}/`)
}

async function navigateClassWorkspace(tab) {
  classSwitcherOpen.value = false
  mobileSidebarOpen.value = false
  if (tab.id === 'task-detail') {
    const location = await resolveTeacherTaskDetailEntry(classRouteId.value, route.params.taskId)
    router.push(location)
    return
  }
  router.push(tab.path)
}
function syncWorkspacePreferences() {
  locale.value = getTeacherLocale()
  theme.value = getTeacherTheme()
  applyTheme(theme.value)
  collapsed.value = getTeacherSidebarCollapsed()
  notices.value = loadTeacherNotices()
  profileCache.value = loadTeacherProfileCache({})
}

function handleWorkspacePreferenceChange(event) {
  const key = event?.detail?.key
  if (!key || key === TEACHER_THEME_KEY) {
    theme.value = getTeacherTheme()
    applyTheme(theme.value)
  }
  if (!key || key === TEACHER_LOCALE_KEY) {
    locale.value = getTeacherLocale()
  }
  if (!key) {
    collapsed.value = getTeacherSidebarCollapsed()
    notices.value = loadTeacherNotices()
    profileCache.value = loadTeacherProfileCache({})
  }
}
async function loadClasses() {
  try {
    const response = await fetchTeacherClasses()
    classes.value = Array.isArray(response?.data?.data) ? response.data.data.map(normalizeClass) : []
  } catch { classes.value = [] }
}
function applyTheme(nextTheme) {
  const normalized = applyTeacherThemeToDocument(nextTheme)
  if (theme.value !== normalized) {
    theme.value = normalized
  }
  setTeacherTheme(normalized)
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
  const normalized = nextTheme === TEACHER_THEME_DARK ? TEACHER_THEME_DARK : TEACHER_THEME_LIGHT
  if (theme.value === normalized) {
    closePanels()
    return
  }
  theme.value = normalized
  closePanels()
}

function openPanel(name) { activePanel.value = activePanel.value === name ? '' : name }
function closePanels() { activePanel.value = '' }
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
function toggleSidebar() { collapsed.value = !collapsed.value }
function logout() { clearTokenAndRemoveSession(); router.replace('/auth') }

function handleDocumentClick(event) {
  if (!(event.target instanceof HTMLElement)) {
    return
  }
  if (
    event.target.closest('.tool-anchor') ||
    event.target.closest('.class-switcher-anchor') ||
    event.target.closest('.sidebar-switcher-menu')
  ) {
    return
  }
  closePanels()
  classSwitcherOpen.value = false
}

function handleAvatarError() {
  avatarLoadFailed.value = true
}

function handleWindowKeydown(event) {
  if (event.key === 'Escape') { closePanels(); closeSettings(); closeProfileCenter() }
}

async function refreshNotificationUnreadCount() {
  try {
    notificationUnreadCount.value = await fetchTeacherNoticeUnreadCount()
  } catch {
    notificationUnreadCount.value = 0
  }
}

function handleNotificationsChanged() {
  refreshNotificationUnreadCount()
}

const isTeacherDialogFullscreen = () =>
  window.innerWidth <= 1120 || mobileSidebarOpen.value

useWorkspaceDialogInset({
  mainSelector: '.teacher-main',
  leftVar: '--teacher-dialog-inset-left',
  rightVar: '--teacher-dialog-inset-right',
  isFullscreen: isTeacherDialogFullscreen,
  watchSources: [collapsed, mobileSidebarOpen, () => route.fullPath],
})

watch(() => theme.value, (v) => applyTheme(v), { immediate: true })
watch(() => locale.value, (v) => setTeacherLocale(v), { immediate: true })
watch(() => collapsed.value, (v) => {
  setTeacherSidebarCollapsed(v)
}, { immediate: true })
watch(() => route.fullPath, () => { mobileSidebarOpen.value = false; notices.value = loadTeacherNotices(); closePanels(); classSwitcherOpen.value = false })
watch(rawAvatarUrl, () => {
  avatarLoadFailed.value = false
})

onMounted(async () => {
  warmBackendOnce()
  loadClasses()
  refreshNotificationUnreadCount()
  notificationUnreadTimer = window.setInterval(refreshNotificationUnreadCount, 30000)
  syncWorkspacePreferences()
  scheduleIdle(async () => {
    try {
      const profile = await fetchTeacherAccountProfile()
      if (profile?.name) {
        saveTeacherProfileCache({ name: profile.name, avatarUrl: profile.avatarUrl || '' })
        profileCache.value = { name: profile.name, avatarUrl: profile.avatarUrl || '' }
      }
    } catch {
      /* profile is non-blocking */
    }
  }, 400)
  window.addEventListener(TEACHER_WORKSPACE_EVENT, handleWorkspacePreferenceChange)
  window.addEventListener(TEACHER_CLASSES_CHANGED_EVENT, loadClasses)
  window.addEventListener('click', handleDocumentClick)
  window.addEventListener('keydown', handleWindowKeydown)
  window.addEventListener('teamtrace-notifications-changed', handleNotificationsChanged)
})
onBeforeUnmount(() => {
  if (notificationUnreadTimer) {
    window.clearInterval(notificationUnreadTimer)
    notificationUnreadTimer = null
  }
  window.removeEventListener(TEACHER_CLASSES_CHANGED_EVENT, loadClasses)
  window.removeEventListener(TEACHER_WORKSPACE_EVENT, handleWorkspacePreferenceChange)
  window.removeEventListener('click', handleDocumentClick)
  window.removeEventListener('keydown', handleWindowKeydown)
  window.removeEventListener('teamtrace-notifications-changed', handleNotificationsChanged)
})
</script>

<template>
  <div class="teacher-layout" :class="{ 'sidebar-collapsed': collapsed }">
    <div v-if="mobileSidebarOpen" class="layout-mask" @click="mobileSidebarOpen = false" />

    <aside class="teacher-sidebar" :class="{ collapsed, open: mobileSidebarOpen }">
      <div class="sidebar-header">
        <button class="brand" type="button" @click="openHiddenMoyuPage">
          <span class="brand-mark">
            <img class="brand-logo" src="/TeamTraceLogo.png" alt="TeamTrace" />
          </span>
          <span v-if="!collapsed" class="brand-copy">
            <strong>TeamTrace</strong>
            <span>{{ locale === 'en-US' ? 'Teacher' : '教师工作区' }}</span>
          </span>
        </button>
      </div>

      <div class="sidebar-scroll">
        <section class="nav-section">
          <p v-if="!collapsed" class="section-label">{{ locale === 'en-US' ? 'MAIN' : '主导航' }}</p>
          <nav class="sidebar-nav">
            <button
              v-for="item in mainNavItems"
              :key="item.path"
              class="nav-item"
              :class="{ active: isNavActive(item.path) }"
              type="button"
              @click="navigate(item.path)"
            >
              <span class="nav-icon-wrap"><IconSystem :name="item.icon" :size="18" /></span>
              <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
            </button>
          </nav>
        </section>

        <section v-if="classWorkspaceTabs.length && !collapsed" class="nav-section workspace-section" :class="{ 'menu-open': classSwitcherOpen }">
          <p class="section-label">{{ locale === 'en-US' ? 'CURRENT CLASS' : '当前班级' }}</p>
          <div class="workspace-card" :class="{ 'switcher-open': classSwitcherOpen }">
            <div class="workspace-head class-switcher-anchor">
              <p class="workspace-title" :title="currentClass?.name || `班级 ${classRouteId}`">
                {{ currentClass?.name || `班级 ${classRouteId}` }}
              </p>
              <button
                class="class-switcher-btn compact"
                :class="{ active: classSwitcherOpen }"
                type="button"
                :title="locale === 'en-US' ? 'Switch class' : '切换班级'"
                @click.stop="classSwitcherOpen = !classSwitcherOpen"
              >
                <IconSystem name="chevronDown" :size="12" />
              </button>
              <transition name="panel-fade">
                <div v-if="classSwitcherOpen" class="class-switcher-menu sidebar-switcher-menu" @click.stop>
                  <button
                    v-for="item in classes"
                    :key="item.classId"
                    class="class-switcher-item"
                    :class="{ active: item.classId === classRouteId }"
                    type="button"
                    @click="switchClass(item.classId)"
                  >
                    <span class="class-switcher-item-name">{{ item.name }}</span>
                    <span v-if="item.classId === classRouteId" class="class-switcher-item-mark">当前</span>
                  </button>
                  <p v-if="!classes.length" class="class-switcher-empty">暂无可切换班级</p>
                </div>
              </transition>
            </div>
            <div class="workspace-tabs">
              <button
                v-for="item in classWorkspaceTabs"
                :key="item.id"
                class="workspace-tab"
                :class="{ active: isClassWorkspaceTabActive(item) }"
                type="button"
                @click="navigateClassWorkspace(item)"
              >{{ item.label }}</button>
            </div>
          </div>
        </section>

        <section class="nav-section">
          <p v-if="!collapsed" class="section-label">{{ locale === 'en-US' ? 'WORKFLOW' : '集中处理' }}</p>
          <nav class="sidebar-nav">
            <button
              v-for="item in workflowNavItems"
              :key="item.path"
              class="nav-item"
              :class="{ active: isNavActive(item.path) }"
              type="button"
              @click="navigate(item.path)"
            >
              <span class="nav-icon-wrap"><IconSystem :name="item.icon" :size="18" /></span>
              <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
            </button>
          </nav>
        </section>

        <section class="nav-section">
          <p v-if="!collapsed" class="section-label">{{ locale === 'en-US' ? 'MORE' : '辅助功能' }}</p>
          <nav class="sidebar-nav">
            <button
              v-for="item in supportNavItems"
              :key="item.path"
              class="nav-item"
              :class="{ active: isNavActive(item.path) }"
              type="button"
              @click="navigate(item.path)"
            >
              <span class="nav-icon-wrap"><IconSystem :name="item.icon" :size="18" /></span>
              <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
              <span v-if="item.badge > 0" class="nav-badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
            </button>
          </nav>
        </section>
      </div>

      <div class="sidebar-footer">
        <button class="collapse-btn desktop-only" type="button" @click="toggleSidebar">
          <IconSystem :name="collapsed ? 'chevronRight' : 'chevronLeft'" :size="16" />
          <span v-if="!collapsed">{{ locale === 'en-US' ? 'Collapse' : '收起' }}</span>
        </button>
      </div>
    </aside>

    <div class="teacher-main">
      <header class="teacher-header">
        <div class="header-primary">
          <button class="mobile-menu mobile-only" type="button" @click="mobileSidebarOpen = !mobileSidebarOpen">
            <IconSystem name="menu" :size="18" />
          </button>
        </div>
        <div class="header-tools">
          <div class="tool-anchor quick-tool-anchor">
            <button class="quick-tool-btn" :class="{ active: activePanel === 'language' }" type="button" :title="locale === 'en-US' ? 'Language' : '语言'" @click.stop="openPanel('language')">
              <IconSystem name="language" :size="17" />
              <span class="quick-tool-label">{{ locale === 'en-US' ? 'EN' : '中文' }}</span>
            </button>
            <transition name="panel-fade">
              <section v-if="activePanel === 'language'" class="tool-popover quick-popover right-align" @click.stop>
                <p class="quick-popover-title">{{ locale === 'en-US' ? 'Language' : '语言' }}</p>
                <div class="quick-option-list">
                  <button class="quick-option-btn" :class="{ active: locale === 'zh-CN' }" type="button" @click="switchLocale('zh-CN')">简体中文</button>
                  <button class="quick-option-btn" :class="{ active: locale === 'en-US' }" type="button" @click="switchLocale('en-US')">English</button>
                </div>
              </section>
            </transition>
          </div>
          <div class="tool-anchor quick-tool-anchor">
            <button class="quick-tool-btn" :class="{ active: activePanel === 'theme' }" type="button" :title="locale === 'en-US' ? 'Theme' : '主题'" @click.stop="openPanel('theme')">
              <IconSystem :name="theme === TEACHER_THEME_DARK ? 'moon' : 'sun'" :size="17" />
              <span class="quick-tool-label">{{ theme === TEACHER_THEME_DARK ? (locale === 'en-US' ? 'Dark' : '暗色') : (locale === 'en-US' ? 'Light' : '亮色') }}</span>
            </button>
            <transition name="panel-fade">
              <section v-if="activePanel === 'theme'" class="tool-popover quick-popover right-align" @click.stop>
                <p class="quick-popover-title">{{ locale === 'en-US' ? 'Theme' : '主题' }}</p>
                <div class="quick-option-list">
                  <button class="quick-option-btn" :class="{ active: theme === TEACHER_THEME_LIGHT }" type="button" @click="switchTheme(TEACHER_THEME_LIGHT)">
                    <IconSystem name="sun" :size="15" /> {{ locale === 'en-US' ? 'Light' : '亮色' }}
                  </button>
                  <button class="quick-option-btn" :class="{ active: theme === TEACHER_THEME_DARK }" type="button" @click="switchTheme(TEACHER_THEME_DARK)">
                    <IconSystem name="moon" :size="15" /> {{ locale === 'en-US' ? 'Dark' : '暗色' }}
                  </button>
                </div>
              </section>
            </transition>
          </div>
          <div class="tool-anchor">
            <button class="profile-btn" :class="{ active: activePanel === 'profile' }" type="button" @click.stop="openPanel('profile')">
              <span class="profile-avatar"><img v-if="avatarUrl" :src="avatarUrl" class="avatar-thumb" @error="handleAvatarError" /><span v-else>{{ teacherName.slice(0, 1) }}</span></span>
              <span class="profile-name">{{ teacherName }}</span>
            </button>
            <transition name="panel-fade">
              <section v-if="activePanel === 'profile'" class="tool-popover right-align" @click.stop>
                <div class="profile-card">
                  <div class="profile-avatar large"><img v-if="avatarUrl" :src="avatarUrl" class="avatar-thumb" @error="handleAvatarError" /><span v-else>{{ teacherName.slice(0, 1) }}</span></div>
                  <div>
                    <p class="popover-name">{{ teacherName }}</p>
                    <p class="popover-role">{{ locale === 'en-US' ? 'Teacher' : '教师' }}</p>
                  </div>
                </div>
                <div class="panel-actions column">
                  <button class="panel-btn" type="button" @click="openSettings">
                    <IconSystem name="settings" :size="16" /> {{ locale === 'en-US' ? 'Settings' : '系统设置' }}
                  </button>
                  <button class="panel-btn" type="button" @click="openProfileCenter">
                    <IconSystem name="user" :size="16" /> {{ locale === 'en-US' ? 'Profile' : '个人中心' }}
                  </button>
                  <button class="panel-btn danger" type="button" @click="logout">
                    <IconSystem name="logout" :size="16" /> {{ locale === 'en-US' ? 'Logout' : '退出登录' }}
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
                <template v-for="(item, index) in displayBreadcrumb" :key="`${item.path}-${index}`">
                  <button class="crumb" type="button" @click="navigate(item.path)">{{ item.label }}</button>
                  <span v-if="index < displayBreadcrumb.length - 1" class="crumb-sep">
                    <IconSystem name="chevronRight" :size="12" />
                  </span>
                </template>
              </div>
            </div>
          </section>
          <div class="route-viewport">
            <RouterView v-slot="{ Component, route: childRoute }">
              <keep-alive :max="8">
                <component :is="Component" :key="teacherRouteViewKey(childRoute)" />
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
            <h2>{{ locale === 'en-US' ? 'Settings' : '系统设置' }}</h2>
            <button class="close-btn" type="button" @click="closeSettings">
              <IconSystem name="close" :size="18" />
            </button>
          </div>
          <div class="settings-body">
            <div class="setting-row">
              <div class="setting-label">
                <p class="setting-title">{{ locale === 'en-US' ? 'Language' : '语言' }}</p>
              </div>
              <div class="setting-control">
                <button class="option-btn" :class="{ active: locale === 'zh-CN' }" type="button" @click="switchLocale('zh-CN')">简体中文</button>
                <button class="option-btn" :class="{ active: locale === 'en-US' }" type="button" @click="switchLocale('en-US')">English</button>
              </div>
            </div>
            <div class="setting-row">
              <div class="setting-label">
                <p class="setting-title">{{ locale === 'en-US' ? 'Theme' : '主题' }}</p>
              </div>
              <div class="setting-control">
                <button class="option-btn" :class="{ active: theme === TEACHER_THEME_LIGHT }" type="button" @click="switchTheme(TEACHER_THEME_LIGHT)">
                  {{ locale === 'en-US' ? 'Light' : '亮色' }}
                </button>
                <button class="option-btn" :class="{ active: theme === TEACHER_THEME_DARK }" type="button" @click="switchTheme(TEACHER_THEME_DARK)">
                  {{ locale === 'en-US' ? 'Dark' : '暗色' }}
                </button>
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
            <h2>{{ locale === 'en-US' ? 'Profile' : '个人中心' }}</h2>
            <button class="close-btn" type="button" @click="closeProfileCenter">
              <IconSystem name="close" :size="18" />
            </button>
          </div>
          <div class="settings-body">
            <AccountSettingsPanel
              :visible="showProfileCenter"
              :role="'teacher'"
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
      :is-en="locale === 'en-US'"
      @close="showChangePassword = false"
    />

    <SystemAnnouncementModal />
  </div>
</template>

<style scoped>
.teacher-layout {
  height: 100vh;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  background: var(--tt-bg);
  color: var(--tt-text);
  font-family: var(--tt-font);
  overflow: hidden;
}

.teacher-layout::before {
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

/* ── Sidebar ── */
.teacher-sidebar {
  width: var(--tt-sidebar-width);
  height: 100vh;
  z-index: 90;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--teacher-sidebar-bg, var(--tt-bg-overlay));
  border-right: 1px solid var(--teacher-divider, var(--tt-divider));
  backdrop-filter: blur(18px);
  transition: width var(--tt-duration-normal) var(--tt-ease),
              transform var(--tt-duration-normal) var(--tt-ease);
}

.teacher-sidebar.collapsed {
  width: var(--tt-sidebar-collapsed-width);
}

.sidebar-header {
  padding: var(--tt-space-5) var(--tt-space-4);
  border-bottom: 1px solid var(--teacher-divider, var(--tt-divider));
}

.sidebar-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--tt-space-4) var(--tt-space-3);
}

.sidebar-scroll::-webkit-scrollbar {
  width: 4px;
}

.sidebar-scroll::-webkit-scrollbar-thumb {
  background: var(--tt-border);
  border-radius: 4px;
}

.nav-section + .nav-section {
  margin-top: var(--tt-space-6);
}

.section-label {
  margin: 0 var(--tt-space-2) var(--tt-space-2);
  color: var(--tt-text-tertiary);
  font-size: var(--tt-text-xs);
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.brand {
  width: 100%;
  border: 0;
  padding: 0;
  display: flex;
  align-items: center;
  gap: var(--tt-space-3);
  background: transparent;
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
  display: grid;
  gap: 1px;
  font-size: var(--tt-text-xs);
  color: var(--tt-text-secondary);
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
  display: grid;
  gap: var(--tt-space-1);
}

.nav-item {
  width: 100%;
  border: 0;
  border-radius: var(--tt-radius-md);
  background: transparent;
  padding: var(--tt-space-2) var(--tt-space-3);
  display: flex;
  align-items: center;
  gap: var(--tt-space-3);
  position: relative;
  text-align: left;
  cursor: pointer;
  color: var(--tt-text-secondary);
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

.nav-item.active::after {
  display: none;
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

/* ── Workspace card ── */
.workspace-section {
  position: relative;
}

.workspace-section.menu-open {
  z-index: 40;
}

.workspace-card {
  position: relative;
  border-radius: 14px;
  padding: 10px;
  background: color-mix(in srgb, var(--tt-accent) 2.5%, var(--tt-surface));
  border: 1px solid color-mix(in srgb, var(--tt-accent) 10%, var(--tt-border-subtle));
  box-shadow: none;
  overflow: hidden;
  min-width: 0;
}

.workspace-card.switcher-open {
  z-index: 1;
}

.workspace-head.class-switcher-anchor {
  position: relative;
  z-index: 3;
}

.workspace-head {
  display: flex;
  align-items: center;
  gap: var(--tt-space-2);
  min-width: 0;
}

.workspace-title {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: var(--tt-text-sm);
  font-weight: 600;
  color: var(--tt-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-tabs {
  position: relative;
  z-index: 1;
  margin-top: var(--tt-space-2);
  display: grid;
  gap: var(--tt-space-1);
}

.workspace-tab {
  position: relative;
  min-height: 34px;
  border: 1px solid transparent;
  border-radius: var(--tt-radius-md);
  background: transparent;
  color: var(--tt-text-secondary);
  text-align: left;
  padding: 0 10px 0 14px;
  font-family: var(--tt-font);
  font-size: var(--tt-text-sm);
  font-weight: 500;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  transition:
    background var(--tt-duration-fast) var(--tt-ease),
    color var(--tt-duration-fast) var(--tt-ease),
    border-color var(--tt-duration-fast) var(--tt-ease);
}

.workspace-tab:hover {
  background: color-mix(in srgb, var(--tt-accent) 5%, var(--tt-surface-hover));
  color: var(--tt-text);
}

.workspace-tab.active {
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  font-weight: 600;
}

.workspace-tab.active::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 8px;
  bottom: 8px;
  width: 2.5px;
  border-radius: 999px;
  background: var(--tt-accent-gradient);
}

/* ── Footer ── */
.sidebar-footer {
  margin-top: auto;
  padding: var(--tt-space-3);
  border-top: 1px solid var(--tt-divider);
  display: grid;
  gap: var(--tt-space-1);
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

/* ── Main ── */
.teacher-main {
  min-width: 0;
  position: relative;
  z-index: 1;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-gutter: stable;
  overscroll-behavior: auto;
  -webkit-overflow-scrolling: touch;
  height: 100vh;
  background: var(--tt-bg);
}

.teacher-header {
  position: sticky;
  top: 0;
  z-index: 70;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--tt-space-4);
  height: var(--tt-header-height);
  padding: 0 var(--tt-page-padding);
  background: var(--teacher-header-bg, var(--tt-bg-overlay));
  border-bottom: 1px solid var(--teacher-divider, var(--tt-divider));
  backdrop-filter: blur(12px);
}

.header-primary {
  display: flex;
  align-items: center;
  gap: var(--tt-space-3);
  min-width: 0;
}

.welcome-eyebrow {
  margin: 0;
  font-size: var(--tt-text-xs);
  color: var(--tt-text-tertiary);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.welcome-title {
  margin: 2px 0 0;
  font-size: var(--tt-text-lg);
  font-weight: 700;
  line-height: 1.2;
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
  background: var(--tt-accent-gradient);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--tt-text-xs);
  font-weight: 700;
}

.profile-avatar.large {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  font-size: var(--tt-text-base);
}

.profile-name {
  font-size: var(--tt-text-sm);
  font-weight: 500;
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

/* ── Popover ── */
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
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
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

/* ── Content ── */
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

.class-switcher-anchor {
  position: relative;
  flex-shrink: 0;
}

.class-switcher-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: min(280px, 42vw);
  border: 1px solid var(--tt-accent-border);
  border-radius: var(--tt-radius-full);
  background: var(--tt-surface);
  color: var(--tt-text);
  padding: 7px 12px 7px 14px;
  cursor: pointer;
  font-family: var(--tt-font);
  font-size: var(--tt-text-xs);
  font-weight: 600;
  box-shadow: var(--tt-shadow-xs);
  transition: all var(--tt-duration-fast) var(--tt-ease-ios);
}

.class-switcher-btn:hover,
.class-switcher-btn.active {
  border-color: var(--tt-accent-border);
  background: var(--tt-surface-hover);
  box-shadow: var(--tt-shadow-sm);
}

.class-switcher-btn.compact {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  padding: 0;
  justify-content: center;
  max-width: none;
  border-radius: 8px;
  border: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface);
  box-shadow: none;
  color: var(--tt-text-secondary);
}

.class-switcher-btn.compact:hover,
.class-switcher-btn.compact.active {
  border-color: var(--tt-accent-border);
  background: var(--tt-surface-hover);
  color: var(--tt-accent);
}

.sidebar-switcher-menu {
  box-sizing: border-box;
}

.class-switcher-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.class-switcher-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 30;
  min-width: 220px;
  max-width: 320px;
  max-height: min(280px, calc(100vh - 160px));
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  padding: 6px;
  border-radius: 14px;
  border: 1px solid var(--tt-border);
  background: var(--tt-bg-elevated);
  box-shadow: var(--tt-shadow-lg);
}

/* 侧边栏班级切换：浮层覆盖，不挤动下方子菜单；限制在卡片宽高内 */
.class-switcher-menu.sidebar-switcher-menu {
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  margin: 0;
  min-width: 0;
  max-width: 100%;
  max-height: min(168px, 42vh);
  z-index: 5;
  border-radius: 10px;
  box-shadow: var(--tt-shadow-md);
  scrollbar-gutter: stable;
}

.class-switcher-menu.sidebar-switcher-menu::-webkit-scrollbar {
  width: 5px;
}

.class-switcher-menu.sidebar-switcher-menu::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--tt-text-tertiary) 55%, transparent);
  border-radius: 4px;
}

.class-switcher-menu.sidebar-switcher-menu::-webkit-scrollbar-thumb:hover {
  background: var(--tt-border);
}

.class-switcher-item {
  width: 100%;
  border: 0;
  border-radius: 10px;
  background: transparent;
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  text-align: left;
  cursor: pointer;
  font-family: var(--tt-font);
  font-size: var(--tt-text-xs);
  color: var(--tt-text-secondary);
  transition: background var(--tt-duration-fast) var(--tt-ease-ios);
}

.class-switcher-item:hover,
.class-switcher-item.active {
  background: var(--tt-surface-hover);
  color: var(--tt-text);
}

.class-switcher-item-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.class-switcher-item-mark {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  color: var(--tt-accent);
}

.class-switcher-empty {
  margin: 0;
  padding: 12px;
  font-size: var(--tt-text-xs);
  color: var(--tt-text-tertiary);
  text-align: center;
}

/* ── Settings / confirm：见 workspace-settings.css + teacher-dialog.css ── */

/* ── Responsive ── */
.desktop-only {
  display: inline-flex;
}

@media (max-width: 1120px) {
  .teacher-layout {
    grid-template-columns: 1fr;
  }

  .teacher-sidebar {
    position: fixed;
    left: 0;
    transform: translateX(-100%);
  }

  .teacher-sidebar.collapsed {
    width: var(--tt-sidebar-width);
  }

  .teacher-sidebar.open {
    transform: translateX(0);
  }

  .mobile-menu {
    display: inline-flex;
  }

  .desktop-only {
    display: none;
  }
}

@media (max-width: 920px) {
  .teacher-header {
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
  .profile-name {
    display: none;
  }

  .welcome-title {
    font-size: var(--tt-text-base);
  }

  .tool-popover {
    left: 0;
    right: auto;
  }
}
/* ── Route transition ── */
.route-viewport {
  animation: route-fade-in 0.22s ease both;
}

@keyframes route-fade-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}


.profile-avatar img.avatar-thumb {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.nav-badge {
  position: absolute;
  top: 8px;
  right: 10px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--teacher-danger, #ff3b30);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  line-height: 18px;
  text-align: center;
  box-shadow: 0 6px 14px rgba(255, 59, 48, 0.22);
  pointer-events: none;
}

.teacher-sidebar.collapsed .nav-badge {
  top: 6px;
  right: 8px;
}
</style>
