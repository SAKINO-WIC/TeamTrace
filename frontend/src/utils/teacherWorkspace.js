import { getActiveAccountId } from './auth'

const TEACHER_THEME_KEY = 'teamtrace_teacher_theme'
const TEACHER_LOCALE_KEY = 'teamtrace_teacher_locale'
const TEACHER_SIDEBAR_KEY = 'teamtrace_teacher_sidebar_collapsed'
const TEACHER_NOTICES_KEY = 'teamtrace_teacher_notices'
const TEACHER_PROFILE_CACHE_KEY = 'teamtrace_teacher_profile_cache'
export const TEACHER_WORKSPACE_EVENT = 'teamtrace:teacher-workspace-change'
export const TEACHER_CLASSES_CHANGED_EVENT = 'teamtrace:teacher-classes-changed'
/** Shell 提供的班级列表上下文（避免各页重复拉 /teacher/classes） */
export const TEACHER_CLASSES_INJECT_KEY = Symbol('teacherClasses')
export { TEACHER_THEME_KEY, TEACHER_LOCALE_KEY }

export function notifyTeacherClassesChanged() {
  if (typeof window === 'undefined') {
    return
  }
  window.dispatchEvent(new CustomEvent(TEACHER_CLASSES_CHANGED_EVENT))
}

export const TEACHER_THEME_LIGHT = 'apple-graphite'
export const TEACHER_THEME_DARK = 'midnight-classroom'

function readJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) {
      return fallback
    }
    return JSON.parse(raw)
  } catch {
    return fallback
  }
}

function writeJson(key, value) {
  localStorage.setItem(key, JSON.stringify(value))
}

function teacherScopedKey(baseKey) {
  const userId = getActiveAccountId('teacher')
  return userId ? `${baseKey}_teacher_${userId}` : baseKey
}

function emitWorkspaceChange(key, value) {
  if (typeof window === 'undefined') {
    return
  }

  window.dispatchEvent(
    new CustomEvent(TEACHER_WORKSPACE_EVENT, {
      detail: { key, value },
    }),
  )
}

export function normalizeTeacherTheme(raw) {
  if (raw === TEACHER_THEME_DARK || raw === 'dark' || raw === 'midnight-classroom') {
    return TEACHER_THEME_DARK
  }
  if (raw === TEACHER_THEME_LIGHT || raw === 'light' || raw === 'apple-graphite') {
    return TEACHER_THEME_LIGHT
  }
  return TEACHER_THEME_LIGHT
}

export function getTeacherTheme() {
  return normalizeTeacherTheme(localStorage.getItem(teacherScopedKey(TEACHER_THEME_KEY)))
}

export function setTeacherTheme(theme) {
  const normalized = normalizeTeacherTheme(theme)
  localStorage.setItem(teacherScopedKey(TEACHER_THEME_KEY), normalized)
  emitWorkspaceChange(TEACHER_THEME_KEY, normalized)
}

export function getTeacherLocale() {
  const raw = localStorage.getItem(teacherScopedKey(TEACHER_LOCALE_KEY))
  return raw || 'zh-CN'
}

export function setTeacherLocale(locale) {
  localStorage.setItem(teacherScopedKey(TEACHER_LOCALE_KEY), locale)
  emitWorkspaceChange(TEACHER_LOCALE_KEY, locale)
}

export function getTeacherSidebarCollapsed() {
  return readJson(teacherScopedKey(TEACHER_SIDEBAR_KEY), false) === true
}

export function setTeacherSidebarCollapsed(collapsed) {
  writeJson(teacherScopedKey(TEACHER_SIDEBAR_KEY), Boolean(collapsed))
  emitWorkspaceChange(TEACHER_SIDEBAR_KEY, Boolean(collapsed))
}

function buildDefaultTeacherNotices() {
  return [
    {
      id: 'teacher-notice-1',
      title: '班级“软件工程 2026 春”有新的入班申请待处理',
      content: '请进入班级管理查看学生申请记录，并确认是否允许该学生加入当前班级。',
      tag: '班级动态',
      createdAt: '2026-04-25T09:00:00+08:00',
      isRead: false,
    },
    {
      id: 'teacher-notice-2',
      title: '任务“项目中期检查”新增 2 条学生申诉',
      content: '请优先进入申诉中心核对评分争议，避免影响任务成绩结算。',
      tag: '申诉通知',
      createdAt: '2026-04-25T10:30:00+08:00',
      isRead: false,
    },
    {
      id: 'teacher-notice-3',
      title: '评分中心本地草稿已更新',
      content: '你最近一次在评分中心保存的本地备注仍可继续编辑，正式写接口接通后可再统一提交。',
      tag: '任务提醒',
      createdAt: '2026-04-24T18:00:00+08:00',
      isRead: true,
    },
    {
      id: 'teacher-notice-4',
      title: '教师端页面说明文档已同步刷新',
      content: '帮助中心、通知中心和个人中心的当前边界已经同步到前端文档，协作时请以最新页面事实为准。',
      tag: '系统提醒',
      createdAt: '2026-04-24T19:30:00+08:00',
      isRead: false,
    },
  ]
}

export function loadTeacherNotices() {
  const notices = readJson(teacherScopedKey(TEACHER_NOTICES_KEY), null)
  if (!Array.isArray(notices) || !notices.length) {
    const defaults = buildDefaultTeacherNotices()
    saveTeacherNotices(defaults)
    return defaults
  }
  return notices
}

export function saveTeacherNotices(notices) {
  writeJson(teacherScopedKey(TEACHER_NOTICES_KEY), notices)
  emitWorkspaceChange(TEACHER_NOTICES_KEY, notices)
}

export function loadTeacherProfileCache(fallback = null) {
  const profile = readJson(teacherScopedKey(TEACHER_PROFILE_CACHE_KEY), fallback)
  if (!profile || typeof profile !== 'object') {
    return fallback
  }
  return profile
}

export function saveTeacherProfileCache(profile) {
  writeJson(teacherScopedKey(TEACHER_PROFILE_CACHE_KEY), profile)
  emitWorkspaceChange(TEACHER_PROFILE_CACHE_KEY, profile)
}
