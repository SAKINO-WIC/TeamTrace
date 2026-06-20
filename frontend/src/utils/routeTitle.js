import { studentMessageMap } from '../i18n/studentMessages'
import { teacherMessageMap } from '../i18n/teacherMessages'
import { getStudentLocale, STUDENT_WORKSPACE_EVENT } from './studentWorkspace'
import { getTeacherLocale, TEACHER_WORKSPACE_EVENT } from './teacherWorkspace'

function pickLocalized(pair, locale) {
  if (!pair) return ''
  return locale === 'en-US' ? pair[1] : pair[0]
}

function resolveRoleFromRoute(to) {
  for (let i = to.matched.length - 1; i >= 0; i -= 1) {
    const role = to.matched[i].meta?.role
    if (role) return role
  }
  return ''
}

function resolveMetaFromRoute(to) {
  for (let i = to.matched.length - 1; i >= 0; i -= 1) {
    const meta = to.matched[i].meta
    if (meta?.titleKey || meta?.title) return meta
  }
  return null
}

export function resolveRouteDocumentTitle(to) {
  const meta = resolveMetaFromRoute(to)
  if (!meta) return ''

  const role = resolveRoleFromRoute(to)
  const locale =
    role === 'student' ? getStudentLocale() : role === 'teacher' ? getTeacherLocale() : 'zh-CN'
  const map =
    role === 'student' ? studentMessageMap : role === 'teacher' ? teacherMessageMap : null

  if (meta.titleKey && map) {
    return pickLocalized(map[meta.titleKey], locale) || meta.title || ''
  }

  return meta.title || ''
}

export function applyDocumentTitle(to) {
  const title = resolveRouteDocumentTitle(to)
  document.title = title ? `${title} - TeamTrace` : 'TeamTrace'
}

export function bindDocumentTitleSync(router) {
  const refresh = () => applyDocumentTitle(router.currentRoute.value)

  window.addEventListener(TEACHER_WORKSPACE_EVENT, refresh)
  window.addEventListener(STUDENT_WORKSPACE_EVENT, refresh)

  return () => {
    window.removeEventListener(TEACHER_WORKSPACE_EVENT, refresh)
    window.removeEventListener(STUDENT_WORKSPACE_EVENT, refresh)
  }
}
