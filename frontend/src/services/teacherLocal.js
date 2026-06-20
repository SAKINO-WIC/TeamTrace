import http from './http'
import { getActiveSession, getTokenPayload } from '../utils/auth'
import {
  loadTeacherNotices,
  loadTeacherProfileCache,
  saveTeacherNotices,
  saveTeacherProfileCache,
} from '../utils/teacherWorkspace'

function normalizeNotice(raw, index = 0) {
  const readValue = raw?.isRead ?? raw?.read ?? false
  return {
    id: raw?.id ?? raw?.notificationId ?? `notice-${index}`,
    type: raw?.type ?? raw?.tag ?? 'system',
    tag: raw?.tag ?? raw?.type ?? '',
    title: raw?.title || '通知',
    content: raw?.content || '',
    relatedId: raw?.relatedId ?? null,
    isRead: readValue === true || readValue === 1 || readValue === '1',
    createdAt: raw?.createdAt || raw?.createdTime || '',
  }
}

function normalizeNoticeList(list) {
  return Array.isArray(list) ? list.map(normalizeNotice) : []
}

function buildFallbackProfile() {
  const payload = getTokenPayload() || {}
  const sessionUser = getActiveSession()?.user || {}

  return {
    userId: String(sessionUser?.id ?? payload?.sub ?? payload?.userId ?? '-'),
    name: sessionUser?.name || payload?.name || '教师账号',
    email: sessionUser?.email || payload?.email || '',
    role: '教师',
    status: 1,
    createdAt: '',
  }
}

export async function fetchTeacherNoticeCenter() {
  try {
    const { data } = await http.get('/notifications', { params: { page: 1, size: 50 } })
    const payload = data?.data || {}
    const rawList = payload.items || payload.list || payload.content || []
    const list = normalizeNoticeList(rawList)
    const unreadCount = Number(payload.unreadCount ?? list.filter((item) => !item.isRead).length)
    saveTeacherNotices(list)
    return { list, unreadCount }
  } catch {
    const list = normalizeNoticeList(loadTeacherNotices())
    return {
      list,
      unreadCount: list.filter((item) => !item.isRead).length,
    }
  }
}

export async function fetchTeacherNoticeUnreadCount() {
  try {
    const { data } = await http.get('/notifications/unread-count')
    return Number(data?.data?.unreadCount ?? 0)
  } catch {
    return normalizeNoticeList(loadTeacherNotices()).filter((item) => !item.isRead).length
  }
}

export async function markTeacherNoticeRead(id) {
  try {
    await http.put(`/notifications/${id}/read`)
  } catch { /* ignore */ }
  const list = normalizeNoticeList(loadTeacherNotices()).map((item) => {
    if (String(item.id) !== String(id)) return item
    return { ...item, isRead: true }
  })
  saveTeacherNotices(list)
  return {
    list,
    unreadCount: list.filter((item) => !item.isRead).length,
  }
}

export async function markAllTeacherNoticesRead() {
  try {
    await http.put('/notifications/read-all')
  } catch { /* ignore */ }
  const list = normalizeNoticeList(loadTeacherNotices()).map((item) => ({ ...item, isRead: true }))
  saveTeacherNotices(list)
  return { list, unreadCount: 0 }
}

export async function deleteTeacherNotice(id) {
  try {
    await http.delete(`/notifications/${id}`)
  } catch { /* ignore */ }
  const list = normalizeNoticeList(loadTeacherNotices()).filter((item) => String(item.id) !== String(id))
  saveTeacherNotices(list)
  return {
    list,
    unreadCount: list.filter((item) => !item.isRead).length,
  }
}

export async function deleteBatchTeacherNotices(ids) {
  try {
    await http.delete('/notifications/batch', { data: ids })
  } catch { /* ignore */ }
  const idSet = new Set(ids.map(String))
  const list = normalizeNoticeList(loadTeacherNotices()).filter((item) => !idSet.has(String(item.id)))
  saveTeacherNotices(list)
  return {
    list,
    unreadCount: list.filter((item) => !item.isRead).length,
  }
}

export async function fetchTeacherAccountProfile() {
  try {
    const { data } = await http.get('/user/profile')
    const payload = data?.data || {}
    const profile = {
      userId: String(payload.id ?? payload.userId ?? '-'),
      name: payload.name || '',
      email: payload.email || '',
      role: '教师',
      avatarUrl: payload.avatarUrl || '',
      createdAt: payload.createdAt || '',
    }
    saveTeacherProfileCache(profile)
    return profile
  } catch {
    // 回退到本地缓存
    const fallback = buildFallbackProfile()
    const profile = {
      ...fallback,
      ...(loadTeacherProfileCache({}) || {}),
    }
    saveTeacherProfileCache(profile)
    return profile
  }
}

export async function updateTeacherAccountProfile(payload) {
  try {
    const { data } = await http.put('/teacher/profile', { name: payload.name })
    const result = data?.data || {}
    const profile = {
      userId: String(result.userId ?? '-'),
      name: result.name || payload.name,
      email: result.email || payload.email || '',
      role: '教师',
      createdAt: result.createdAt || '',
    }
    saveTeacherProfileCache(profile)
    return profile
  } catch {
    // 回退到本地
    const profile = {
      ...buildFallbackProfile(),
      name: payload.name,
    }
    saveTeacherProfileCache(profile)
    return profile
  }
}

export async function changeTeacherAccountPassword(payload) {
  return http.put('/teacher/me/password', payload)
}
