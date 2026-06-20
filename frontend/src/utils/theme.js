import {
  TEACHER_THEME_DARK,
  TEACHER_THEME_LIGHT,
  TEACHER_THEME_KEY,
  getTeacherTheme,
  normalizeTeacherTheme,
} from './teacherWorkspace'
import {
  ADMIN_THEME_DARK,
  ADMIN_THEME_KEY,
  getAdminTheme,
  normalizeAdminTheme,
} from './adminWorkspace'
import {
  STUDENT_THEME_KEY,
  getStudentTheme,
  normalizeStudentTheme,
} from './studentWorkspace'

const LEGACY_THEME_STORAGE_KEY = 'theme'

export function applyHtmlColorScheme(isDark) {
  const html = document.documentElement
  if (isDark) {
    html.classList.add('dark')
    html.style.colorScheme = 'dark'
  } else {
    html.classList.remove('dark')
    html.style.colorScheme = 'light'
  }
}

export function applyTeacherThemeToDocument(theme) {
  const normalized = normalizeTeacherTheme(theme)
  document.documentElement.setAttribute('data-teacher-theme', normalized)
  document.documentElement.removeAttribute('data-student-theme')
  document.documentElement.removeAttribute('data-admin-theme')
  document.documentElement.removeAttribute('data-auth-theme')
  applyHtmlColorScheme(normalized === TEACHER_THEME_DARK)
  localStorage.setItem(TEACHER_THEME_KEY, normalized)
  localStorage.setItem(LEGACY_THEME_STORAGE_KEY, normalized === TEACHER_THEME_DARK ? 'dark' : 'light')
  return normalized
}

export function applyStudentThemeToDocument(theme) {
  const normalized = normalizeStudentTheme(theme)
  document.documentElement.setAttribute('data-student-theme', normalized)
  document.documentElement.removeAttribute('data-teacher-theme')
  document.documentElement.removeAttribute('data-admin-theme')
  document.documentElement.removeAttribute('data-auth-theme')
  applyHtmlColorScheme(normalized === 'dark')
  localStorage.setItem(STUDENT_THEME_KEY, normalized)
  return normalized
}

export function applyAdminThemeToDocument(theme) {
  const normalized = normalizeAdminTheme(theme ?? getAdminTheme())
  document.documentElement.setAttribute('data-admin-theme', normalized)
  document.documentElement.removeAttribute('data-teacher-theme')
  document.documentElement.removeAttribute('data-student-theme')
  document.documentElement.removeAttribute('data-auth-theme')
  applyHtmlColorScheme(normalized === ADMIN_THEME_DARK)
  localStorage.setItem(ADMIN_THEME_KEY, normalized)
  return normalized
}

/** 登录/注册页固定浅色，避免教师/学生暗色全局规则污染白底表单 */
export function applyAuthThemeToDocument() {
  document.documentElement.removeAttribute('data-teacher-theme')
  document.documentElement.removeAttribute('data-student-theme')
  document.documentElement.removeAttribute('data-admin-theme')
  document.documentElement.setAttribute('data-auth-theme', 'light')
  applyHtmlColorScheme(false)
}

export function syncThemeForRoute(path = '') {
  const routePath = String(path || '')
  if (routePath === '/auth' || routePath.startsWith('/auth/')) {
    applyAuthThemeToDocument()
    return
  }
  document.documentElement.removeAttribute('data-auth-theme')
  if (routePath.startsWith('/student')) {
    applyStudentThemeToDocument(getStudentTheme())
    return
  }
  if (routePath.startsWith('/admin')) {
    applyAdminThemeToDocument(getAdminTheme())
    return
  }
  applyTeacherThemeToDocument(getTeacherTheme())
}

export function getTheme() {
  return localStorage.getItem(LEGACY_THEME_STORAGE_KEY) || 'light'
}

export function applyTheme(theme) {
  applyHtmlColorScheme(theme === 'dark')
  localStorage.setItem(LEGACY_THEME_STORAGE_KEY, theme)
}

export function toggleTheme() {
  const nextTheme = document.documentElement.classList.contains('dark') ? 'light' : 'dark'
  applyTheme(nextTheme)
  return nextTheme
}

export function syncTeacherThemeWithHtml(teacherTheme) {
  applyTeacherThemeToDocument(teacherTheme)
}
