/**
 * 多角色 + 同角色多账号会话
 *
 * - 角色：student / teacher / admin 可并存
 * - 同角色多账号：teamtrace_session_{role}_{userId}，如两个学生（手机号 / 邮箱）可同时保留
 * - 当前标签页：sessionStorage 记录 active_role + active_{role}_user_id
 */

import { invalidateCacheByPrefix } from './fetchCache'

const SESSION_PREFIX = 'teamtrace_session_'
const SESSION_INDEX_KEY = 'teamtrace_session_index'
const ACTIVE_ROLE_KEY = 'teamtrace_active_role'
const REMEMBERED_EMAIL_KEY = 'teamtrace_remembered_email'
const LEGACY_REMEMBERED_PHONE_KEY = 'teamtrace_remembered_phone'

const activeRoleStorage = sessionStorage

const ROLES = ['student', 'teacher', 'admin']

function activeAccountKey(role) {
  return `teamtrace_active_${role}_user_id`
}

function legacySessionKey(role) {
  return `${SESSION_PREFIX}${role}`
}

function accountSessionKey(role, userId) {
  return `${SESSION_PREFIX}${role}_${userId}`
}

function decodeBase64Url(input) {
  if (!input) return null
  const base64 = input.replace(/-/g, '+').replace(/_/g, '/')
  const padded = `${base64}${'='.repeat((4 - (base64.length % 4)) % 4)}`
  try {
    const binary = atob(padded)
    const bytes = Array.from(binary, (char) => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`).join('')
    return decodeURIComponent(bytes)
  } catch {
    return null
  }
}

function parseUserIdFromToken(token) {
  if (!token) return null
  const [, payload] = token.split('.')
  const decoded = decodeBase64Url(payload)
  if (!decoded) return null
  try {
    const json = JSON.parse(decoded)
    const raw = json?.sub ?? json?.subject ?? json?.userId ?? null
    if (raw === null || raw === undefined) return null
    return String(raw)
  } catch {
    return null
  }
}

export function parseUserId(token, user = null) {
  if (user?.id !== null && user?.id !== undefined && user?.id !== '') {
    return String(user.id)
  }
  return parseUserIdFromToken(token)
}

function readIndex() {
  try {
    const raw = localStorage.getItem(SESSION_INDEX_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

function writeIndex(index) {
  localStorage.setItem(SESSION_INDEX_KEY, JSON.stringify(index))
}

function readSessionRaw(key) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function writeSessionRaw(key, data) {
  localStorage.setItem(key, JSON.stringify(data))
}

function removeSessionRaw(key) {
  localStorage.removeItem(key)
}

let legacyMigrated = false

/** 将旧版每角色单会话迁移到按 userId 分桶 */
export function migrateLegacySessions() {
  if (legacyMigrated) return
  legacyMigrated = true

  const index = readIndex()
  let changed = false

  for (const role of ROLES) {
    const legacy = readSessionRaw(legacySessionKey(role))
    if (!legacy?.token) continue

    const userId = parseUserId(legacy.token, legacy.user)
    if (!userId) continue

    const key = accountSessionKey(role, userId)
    if (!readSessionRaw(key)) {
      writeSessionRaw(key, { ...legacy, userId })
      changed = true
    }
    if (!index[role]) index[role] = []
    if (!index[role].includes(userId)) {
      index[role].push(userId)
      changed = true
    }
    removeSessionRaw(legacySessionKey(role))
    changed = true
  }

  if (changed) writeIndex(index)
}

function invalidateRoleCaches(role) {
  if (role === 'teacher') invalidateCacheByPrefix('teacher:')
  if (role === 'student') invalidateCacheByPrefix('student:')
}

// ─── 活跃角色 / 账号 ─────────────────────────────────────

export function getActiveRole() {
  return activeRoleStorage.getItem(ACTIVE_ROLE_KEY)
}

export function setActiveRole(role) {
  activeRoleStorage.setItem(ACTIVE_ROLE_KEY, role)
}

export function getActiveAccountId(role = getActiveRole()) {
  if (!role) return null
  return activeRoleStorage.getItem(activeAccountKey(role))
}

export function setActiveAccountId(role, userId) {
  if (!role || !userId) return
  activeRoleStorage.setItem(activeAccountKey(role), String(userId))
}

// ─── 会话读写 ───────────────────────────────────────────

export function readSessionByUserId(role, userId) {
  migrateLegacySessions()
  if (!role || !userId) return null
  return readSessionRaw(accountSessionKey(role, String(userId)))
}

function readLegacySession(role) {
  return readSessionRaw(legacySessionKey(role))
}

function readActiveSession(role = getActiveRole()) {
  migrateLegacySessions()
  if (!role) return null

  const index = readIndex()
  const ids = index[role] || []
  let userId = getActiveAccountId(role)

  if (userId && ids.includes(userId)) {
    const session = readSessionByUserId(role, userId)
    const tokenUserId = parseUserIdFromToken(session?.token)
    if (session?.token && tokenUserId && String(tokenUserId) === String(userId)) return session
  }

  // 不回退到其他用户 —— 每个标签页只用自己 sessionStorage 里记录的活跃账户
  // 找不到当前账户时返回 null，让路由守卫重定向到登录页
  return null
}

export function getActiveSession() {
  return readActiveSession()
}

export function hasActiveAccountSession(role = getActiveRole()) {
  const activeUserId = getActiveAccountId(role)
  const session = readActiveSession(role)
  if (!session?.token) return false

  const tokenUserId = parseUserIdFromToken(session.token)
  if (activeUserId && tokenUserId && String(activeUserId) !== String(tokenUserId)) {
    return false
  }

  return true
}

export function getToken() {
  const role = getActiveRole()
  const activeUserId = getActiveAccountId(role)
  const session = readActiveSession(role)
  if (!session?.token) return null

  const tokenUserId = parseUserIdFromToken(session.token)
  if (activeUserId && tokenUserId && String(activeUserId) !== String(tokenUserId)) {
    return null
  }

  return session.token
}

export function setToken(token) {
  const role = getActiveRole()
  const userId = getActiveAccountId(role)
  if (!role || !userId) return
  const session = readSessionByUserId(role, userId) || {}
  session.token = token
  writeSessionRaw(accountSessionKey(role, userId), session)
}

export function getRole() {
  return getActiveRole()
}

export function setRole(role) {
  setActiveRole(role)
}

export function clearTokenAndRemoveSession() {
  const role = getActiveRole()
  if (!role) {
    activeRoleStorage.removeItem(ACTIVE_ROLE_KEY)
    return
  }
  const userId = getActiveAccountId(role)
  if (userId) {
    removeSessionByUserId(role, userId)
  } else {
    removeSessionRaw(legacySessionKey(role))
  }
  activeRoleStorage.removeItem(ACTIVE_ROLE_KEY)
  activeRoleStorage.removeItem(activeAccountKey(role))
  invalidateRoleCaches(role)
}

export function clearToken() {
  const role = getActiveRole()
  if (!role) {
    activeRoleStorage.removeItem(ACTIVE_ROLE_KEY)
    return
  }
  const userId = getActiveAccountId(role)
  // 只清除当前标签页的 sessionStorage 引用，不删除 localStorage 里的会话数据
  // 这样其他标签页的登录状态不受影响，"切换账户"后仍可找回之前的账户
  activeRoleStorage.removeItem(ACTIVE_ROLE_KEY)
  activeRoleStorage.removeItem(activeAccountKey(role))
  invalidateRoleCaches(role)
}

// ─── 多角色 / 多账号操作 ─────────────────────────────────

export function saveSession(role, token, user = null) {
  migrateLegacySessions()
  const userId = parseUserId(token, user)

  if (!userId) {
    writeSessionRaw(legacySessionKey(role), { token, role, user })
    setActiveRole(role)
    invalidateRoleCaches(role)
    return
  }

  const existing = readSessionByUserId(role, userId)
  const mergedUser = {
    ...(existing?.user || {}),
    ...(user || {}),
  }
  const data = {
    ...(existing || {}),
    token,
    role,
    user: Object.keys(mergedUser).length ? mergedUser : null,
    userId,
  }
  writeSessionRaw(accountSessionKey(role, userId), data)

  const index = readIndex()
  if (!index[role]) index[role] = []
  if (!index[role].includes(userId)) index[role].push(userId)
  writeIndex(index)

  setActiveRole(role)
  setActiveAccountId(role, userId)
  invalidateRoleCaches(role)
}

export function listRoleAccounts(role) {
  migrateLegacySessions()
  const index = readIndex()
  return (index[role] || [])
    .map((userId) => {
      const session = readSessionByUserId(role, userId)
      if (!session?.token) return null
      return {
        userId,
        user: session.user || null,
        role,
      }
    })
    .filter(Boolean)
}

export function listSavedAccounts() {
  migrateLegacySessions()
  const items = []
  for (const role of ROLES) {
    for (const account of listRoleAccounts(role)) {
      items.push(account)
    }
  }
  return items
}

export function switchRole(role) {
  migrateLegacySessions()
  const accounts = listRoleAccounts(role)
  if (!accounts.length) return false
  setActiveRole(role)
  const activeId = getActiveAccountId(role)
  const hasActive = activeId && accounts.some((a) => a.userId === activeId)
  if (!hasActive) {
    setActiveAccountId(role, accounts[accounts.length - 1].userId)
  }
  invalidateRoleCaches(role)
  return true
}

export function switchAccount(role, userId) {
  const session = readSessionByUserId(role, userId)
  if (!session?.token) return false
  setActiveRole(role)
  setActiveAccountId(role, userId)
  invalidateRoleCaches(role)
  return true
}

export function hasSession(role) {
  return listRoleAccounts(role).length > 0
}

export function listSessions() {
  return ROLES.filter((r) => hasSession(r))
}

export function getSession(role) {
  return readActiveSession(role)
}

export function removeSessionByUserId(role, userId) {
  migrateLegacySessions()
  removeSessionRaw(accountSessionKey(role, String(userId)))
  const index = readIndex()
  if (index[role]) {
    index[role] = index[role].filter((id) => id !== String(userId))
    if (!index[role].length) delete index[role]
    writeIndex(index)
  }
  if (getActiveAccountId(role) === String(userId)) {
    activeRoleStorage.removeItem(activeAccountKey(role))
  }
}

export function removeSessionByRole(role) {
  migrateLegacySessions()
  for (const account of listRoleAccounts(role)) {
    removeSessionByUserId(role, account.userId)
  }
  removeSessionRaw(legacySessionKey(role))
  activeRoleStorage.removeItem(activeAccountKey(role))
  if (getActiveRole() === role) {
    const remaining = listSessions().filter((r) => r !== role)
    if (remaining.length) {
      switchRole(remaining[0])
    } else {
      activeRoleStorage.removeItem(ACTIVE_ROLE_KEY)
    }
  }
}

// ─── 记住邮箱 ───────────────────────────────────────────

export function getRememberedEmail() {
  return localStorage.getItem(REMEMBERED_EMAIL_KEY)
    || localStorage.getItem(LEGACY_REMEMBERED_PHONE_KEY)
    || ''
}

export function rememberEmail(email) {
  const normalized = String(email || '').trim()
  if (!normalized) {
    localStorage.removeItem(REMEMBERED_EMAIL_KEY)
    return
  }
  localStorage.setItem(REMEMBERED_EMAIL_KEY, normalized)
}

export function clearRememberedEmail() {
  localStorage.removeItem(REMEMBERED_EMAIL_KEY)
  localStorage.removeItem(LEGACY_REMEMBERED_PHONE_KEY)
}

const REMEMBERED_PW_PREFIX = 'teamtrace_remembered_pw_'

export function rememberPassword(email, password) {
  const normalized = String(email || '').trim()
  if (!normalized || !password) return
  localStorage.setItem(`${REMEMBERED_PW_PREFIX}${normalized}`, password)
}

export function getRememberedPassword(email) {
  const normalized = String(email || '').trim()
  if (!normalized) return ''
  return localStorage.getItem(`${REMEMBERED_PW_PREFIX}${normalized}`) || ''
}

export function clearRememberedPassword(email) {
  const normalized = String(email || '').trim()
  if (!normalized) return
  localStorage.removeItem(`${REMEMBERED_PW_PREFIX}${normalized}`)
}

/** @deprecated */
export function getRememberedPhone() {
  return getRememberedEmail()
}

/** @deprecated */
export function rememberPhone(email) {
  rememberEmail(email)
}

/** @deprecated */
export function clearRememberedPhone() {
  clearRememberedEmail()
}

export function getDisplayAccount(user = null, payload = null) {
  const fromUser = user?.email || user?.phone
  if (fromUser) return fromUser
  const fromPayload = payload?.email || payload?.phone
  return fromPayload || '—'
}

export function getAccountLabel(account) {
  if (!account) return ''
  const user = account.user || {}
  const name = user.name ? `${user.name} · ` : ''
  const login = user.email || user.phone || `ID ${account.userId}`
  const roleLabel = account.role === 'teacher' ? '教师' : account.role === 'student' ? '学生' : account.role === 'admin' ? '管理员' : account.role
  return `${roleLabel}：${name}${login}`
}

export function getTokenPayload() {
  const token = getToken()
  if (!token) return null

  const [, payload] = token.split('.')
  const decoded = decodeBase64Url(payload)
  if (!decoded) return null

  try {
    return JSON.parse(decoded)
  } catch {
    return null
  }
}

export function getCurrentUserId() {
  const payload = getTokenPayload()
  const raw = payload?.sub ?? payload?.subject ?? payload?.userId ?? null
  const parsed = Number(raw)
  return Number.isNaN(parsed) ? null : parsed
}

/** 更新当前活跃会话中的 user 展示字段（如改名） */
export function updateActiveSessionUser(patch = {}) {
  const role = getActiveRole()
  const userId = getActiveAccountId(role)
  if (!role || !userId) return
  const session = readSessionByUserId(role, userId)
  if (!session) return
  session.user = { ...(session.user || {}), ...patch }
  writeSessionRaw(accountSessionKey(role, userId), session)
}
