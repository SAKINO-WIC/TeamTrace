const ADMIN_INVITE_HISTORY_KEY = 'teamtrace_admin_invite_history'

const ROLE_LABELS = {
  admin: '管理员',
  teacher: '教师',
  student: '学生',
}

const MONITOR_STATUS_LABELS = {
  active: '进行中',
  archived: '已归档',
  pending: '待开始',
  open: '未开始',
  in_progress: '进行中',
  closed: '已结束',
}

export function getRoleLabel(role) {
  return ROLE_LABELS[role] || role || '-'
}

export function getStatusLabel(status) {
  return Number(status) === 1 ? '启用' : '禁用'
}

export function getStatusTone(status) {
  return Number(status) === 1 ? 'success' : 'danger'
}

export function formatDateTime(value) {
  if (!value) return '-'

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function formatMonitorStatus(value) {
  const raw = String(value || '').trim().toLowerCase()
  if (!raw) return '-'
  return MONITOR_STATUS_LABELS[raw] || String(value)
}

export function formatHttpStatus(value) {
  if (value === null || value === undefined || value === '') return '-'
  const parsed = Number(value)
  return Number.isNaN(parsed) ? String(value) : `${parsed}`
}

export function formatDuration(value) {
  if (value === null || value === undefined || value === '') return '-'
  const parsed = Number(value)
  return Number.isNaN(parsed) ? String(value) : `${parsed} ms`
}

export async function copyText(value) {
  if (!value) return false

  if (navigator?.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
    return true
  }

  const input = document.createElement('textarea')
  input.value = value
  input.setAttribute('readonly', 'true')
  input.style.position = 'fixed'
  input.style.opacity = '0'
  document.body.appendChild(input)
  input.select()
  const succeeded = document.execCommand('copy')
  document.body.removeChild(input)
  return succeeded
}

export function loadAdminInviteHistory() {
  try {
    const raw = localStorage.getItem(ADMIN_INVITE_HISTORY_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function saveAdminInviteHistory(history) {
  localStorage.setItem(ADMIN_INVITE_HISTORY_KEY, JSON.stringify(history))
}

export function upsertInviteHistoryItem(item) {
  const current = loadAdminInviteHistory()
  const next = [
    item,
    ...current.filter((historyItem) => historyItem.code !== item.code),
  ].slice(0, 20)
  saveAdminInviteHistory(next)
  return next
}

export function updateInviteHistoryStatus(code, status) {
  const current = loadAdminInviteHistory()
  const next = current.map((item) => (item.code === code ? { ...item, status } : item))
  saveAdminInviteHistory(next)
  return next
}
