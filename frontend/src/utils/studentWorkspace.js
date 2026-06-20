import { getActiveAccountId } from './auth'

const STUDENT_THEME_KEY = 'teamtrace_theme'
const STUDENT_LOCALE_KEY = 'teamtrace_lang'
const STUDENT_SIDEBAR_KEY = 'teamtrace_student_sidebar_collapsed'
export const STUDENT_WORKSPACE_EVENT = 'teamtrace:student-workspace-change'
export { STUDENT_THEME_KEY, STUDENT_LOCALE_KEY, STUDENT_SIDEBAR_KEY }

export const STUDENT_THEME_LIGHT = 'light'
export const STUDENT_THEME_DARK = 'dark'

function emitWorkspaceChange(key, value) {
  if (typeof window === 'undefined') {
    return
  }

  window.dispatchEvent(
    new CustomEvent(STUDENT_WORKSPACE_EVENT, {
      detail: { key, value },
    }),
  )
}

export function normalizeStudentTheme(raw) {
  if (raw === STUDENT_THEME_DARK || raw === 'dark') {
    return STUDENT_THEME_DARK
  }
  return STUDENT_THEME_LIGHT
}

export function normalizeStudentLocale(raw) {
  if (raw === 'en' || raw === 'en-US') {
    return 'en-US'
  }
  return 'zh-CN'
}

function studentScopedKey(baseKey) {
  const userId = getActiveAccountId('student')
  return userId ? `${baseKey}_student_${userId}` : baseKey
}

export function getStudentTheme() {
  return normalizeStudentTheme(localStorage.getItem(studentScopedKey(STUDENT_THEME_KEY)))
}

export function setStudentTheme(theme) {
  const normalized = normalizeStudentTheme(theme)
  localStorage.setItem(studentScopedKey(STUDENT_THEME_KEY), normalized)
  emitWorkspaceChange(STUDENT_THEME_KEY, normalized)
}

export function getStudentLocale() {
  return normalizeStudentLocale(localStorage.getItem(studentScopedKey(STUDENT_LOCALE_KEY)))
}

export function setStudentLocale(locale) {
  const normalized = normalizeStudentLocale(locale)
  localStorage.setItem(studentScopedKey(STUDENT_LOCALE_KEY), normalized)
  emitWorkspaceChange(STUDENT_LOCALE_KEY, normalized)
}

export function getStudentSidebarCollapsed() {
  return localStorage.getItem(studentScopedKey(STUDENT_SIDEBAR_KEY)) === '1'
}

export function setStudentSidebarCollapsed(collapsed) {
  localStorage.setItem(studentScopedKey(STUDENT_SIDEBAR_KEY), collapsed ? '1' : '0')
  emitWorkspaceChange(STUDENT_SIDEBAR_KEY, collapsed)
}
