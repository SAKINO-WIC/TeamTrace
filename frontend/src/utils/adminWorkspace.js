const ADMIN_THEME_KEY = 'teamtrace_admin_theme'
const ADMIN_LOCALE_KEY = 'teamtrace_admin_locale'
export const ADMIN_WORKSPACE_EVENT = 'teamtrace:admin-workspace-change'
export { ADMIN_THEME_KEY, ADMIN_LOCALE_KEY }

export const ADMIN_THEME_LIGHT = 'apple-graphite'
export const ADMIN_THEME_DARK = 'midnight-console'

function emitWorkspaceChange(key, value) {
  if (typeof window === 'undefined') {
    return
  }

  window.dispatchEvent(
    new CustomEvent(ADMIN_WORKSPACE_EVENT, {
      detail: { key, value },
    }),
  )
}

export function normalizeAdminTheme(raw) {
  if (raw === ADMIN_THEME_DARK || raw === 'dark' || raw === 'midnight-console') {
    return ADMIN_THEME_DARK
  }
  if (raw === ADMIN_THEME_LIGHT || raw === 'light' || raw === 'apple-graphite') {
    return ADMIN_THEME_LIGHT
  }
  return ADMIN_THEME_LIGHT
}

export function getAdminTheme() {
  return normalizeAdminTheme(localStorage.getItem(ADMIN_THEME_KEY))
}

export function setAdminTheme(theme) {
  const normalized = normalizeAdminTheme(theme)
  localStorage.setItem(ADMIN_THEME_KEY, normalized)
  emitWorkspaceChange(ADMIN_THEME_KEY, normalized)
}

export function getAdminLocale() {
  const raw = localStorage.getItem(ADMIN_LOCALE_KEY)
  return raw === 'en-US' ? 'en-US' : 'zh-CN'
}

export function setAdminLocale(locale) {
  const normalized = locale === 'en-US' ? 'en-US' : 'zh-CN'
  localStorage.setItem(ADMIN_LOCALE_KEY, normalized)
  emitWorkspaceChange(ADMIN_LOCALE_KEY, normalized)
}
