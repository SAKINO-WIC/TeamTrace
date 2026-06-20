import http from './http'
import { getRuntimeMode } from './runtime'
import { fetchWithCache, invalidateCacheByPrefix } from '../utils/fetchCache'
import { getCurrentUserId } from '../utils/auth'
import { resolvePositiveId } from '../utils/routeIds'

function assertClassTaskIds(classId, taskId) {
  const cid = resolvePositiveId(classId)
  const tid = resolvePositiveId(taskId)
  if (!cid || !tid) {
    throw new Error('页面参数无效，请从任务中心重新进入该任务')
  }
  return { classId: cid, taskId: tid }
}

function assertGroupRouteIds(classId, taskId, groupId) {
  const { classId: cid, taskId: tid } = assertClassTaskIds(classId, taskId)
  const gid = resolvePositiveId(groupId)
  if (!gid) {
    throw new Error('尚未加入有效小组，请先在班级小组页完成入组')
  }
  return { classId: cid, taskId: tid, groupId: gid }
}

function cachedStudentGet(key, fetcher, ttlMs = 120000) {
  return fetchWithCache(`student:user:${getStudentCacheUserId()}:${key}`, fetcher, ttlMs)
}

function getStudentCacheUserId() {
  return getCurrentUserId() || 'anonymous'
}

function withStudentClassCacheInvalidation(classId, request) {
  return Promise.resolve(request).then((response) => {
    invalidateStudentClassCache(classId)
    return response
  })
}

function withStudentCacheInvalidation(request) {
  return Promise.resolve(request).then((response) => {
    invalidateStudentClassCache()
    return response
  })
}

export function invalidateStudentClassCache(classId) {
  if (classId === undefined || classId === null || classId === '') {
    invalidateCacheByPrefix(`student:user:${getStudentCacheUserId()}:`)
    return
  }
  invalidateCacheByPrefix(`student:user:${getStudentCacheUserId()}:student:class:${classId}:`)
}

export function joinStudentClass(inviteCode) {
  return withStudentCacheInvalidation(http.post('/student/classes/join', { inviteCode }))
}

export function fetchStudentCollaborationSpaces() {
  return http.get('/student/collaboration-spaces')
}

export function createStudentCollaborationSpace(payload) {
  return withStudentCacheInvalidation(http.post('/student/collaboration-spaces', payload))
}

export function fetchStudentCollaborationSpace(spaceId) {
  const sid = resolvePositiveId(spaceId)
  if (!sid) {
    return Promise.reject(new Error('协作空间参数无效'))
  }
  return http.get(`/student/collaboration-spaces/${sid}`)
}

export function generateStudentCollaborationInviteCode(spaceId) {
  const sid = resolvePositiveId(spaceId)
  if (!sid) {
    return Promise.reject(new Error('协作空间参数无效'))
  }
  return withStudentCacheInvalidation(http.post(`/student/collaboration-spaces/${sid}/invite-codes`))
}

export function joinStudentCollaborationSpace(inviteCode) {
  return withStudentCacheInvalidation(http.post('/student/collaboration-spaces/join', { inviteCode }))
}

export function leaveStudentCollaborationSpace(spaceId) {
  const sid = resolvePositiveId(spaceId)
  if (!sid) {
    return Promise.reject(new Error('协作空间参数无效'))
  }
  return withStudentCacheInvalidation(http.post(`/student/collaboration-spaces/${sid}/leave`))
}

export function removeStudentCollaborationMember(spaceId, studentId) {
  const sid = resolvePositiveId(spaceId)
  const uid = resolvePositiveId(studentId)
  if (!sid || !uid) {
    return Promise.reject(new Error('成员参数无效'))
  }
  return withStudentCacheInvalidation(http.delete(`/student/collaboration-spaces/${sid}/members/${uid}`))
}

export function transferStudentCollaborationOwner(spaceId, newOwnerId) {
  const sid = resolvePositiveId(spaceId)
  const uid = resolvePositiveId(newOwnerId)
  if (!sid || !uid) {
    return Promise.reject(new Error('成员参数无效'))
  }
  return withStudentCacheInvalidation(
    http.post(`/student/collaboration-spaces/${sid}/transfer-owner`, { newOwnerId: uid }),
  )
}

export function fetchStudentCollaborationDashboard() {
  return http.get('/student/collaboration-spaces/dashboard')
}

export function fetchStudentCollaborationProjects(spaceId) {
  const sid = resolvePositiveId(spaceId)
  if (!sid) {
    return Promise.reject(new Error('协作空间参数无效'))
  }
  return http.get(`/student/collaboration-spaces/${sid}/projects`)
}

export function createStudentCollaborationProject(spaceId, payload) {
  const sid = resolvePositiveId(spaceId)
  if (!sid) {
    return Promise.reject(new Error('协作空间参数无效'))
  }
  return withStudentCacheInvalidation(http.post(`/student/collaboration-spaces/${sid}/projects`, payload))
}

export function createStudentCollaborationProjectWithTasks(spaceId, payload) {
  const sid = resolvePositiveId(spaceId)
  if (!sid) {
    return Promise.reject(new Error('协作空间参数无效'))
  }
  return withStudentCacheInvalidation(
    http.post(`/student/collaboration-spaces/${sid}/projects/with-tasks`, payload),
  )
}

export function updateStudentCollaborationProject(spaceId, projectId, payload) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  if (!sid || !pid) {
    return Promise.reject(new Error('项目参数无效'))
  }
  return withStudentCacheInvalidation(
    http.patch(`/student/collaboration-spaces/${sid}/projects/${pid}`, payload),
  )
}

export function archiveStudentCollaborationProject(spaceId, projectId) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  if (!sid || !pid) {
    return Promise.reject(new Error('项目参数无效'))
  }
  return withStudentCacheInvalidation(
    http.post(`/student/collaboration-spaces/${sid}/projects/${pid}/archive`),
  )
}

export function fetchStudentCollaborationProject(spaceId, projectId) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  if (!sid || !pid) {
    return Promise.reject(new Error('项目参数无效'))
  }
  return http.get(`/student/collaboration-spaces/${sid}/projects/${pid}`)
}

export function fetchStudentCollaborationTasks(spaceId, projectId) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  if (!sid || !pid) {
    return Promise.reject(new Error('项目参数无效'))
  }
  return http.get(`/student/collaboration-spaces/${sid}/projects/${pid}/tasks`)
}

export function createStudentCollaborationTask(spaceId, projectId, payload) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  if (!sid || !pid) {
    return Promise.reject(new Error('项目参数无效'))
  }
  return withStudentCacheInvalidation(
    http.post(`/student/collaboration-spaces/${sid}/projects/${pid}/tasks`, payload),
  )
}

export function updateStudentCollaborationTask(spaceId, projectId, taskId, payload) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  const tid = resolvePositiveId(taskId)
  if (!sid || !pid || !tid) {
    return Promise.reject(new Error('任务参数无效'))
  }
  return withStudentCacheInvalidation(
    http.patch(`/student/collaboration-spaces/${sid}/projects/${pid}/tasks/${tid}`, payload),
  )
}

export function archiveStudentCollaborationTask(spaceId, projectId, taskId) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  const tid = resolvePositiveId(taskId)
  if (!sid || !pid || !tid) {
    return Promise.reject(new Error('任务参数无效'))
  }
  return withStudentCacheInvalidation(
    http.post(`/student/collaboration-spaces/${sid}/projects/${pid}/tasks/${tid}/archive`),
  )
}

export function claimStudentCollaborationTask(spaceId, projectId, taskId) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  const tid = resolvePositiveId(taskId)
  if (!sid || !pid || !tid) {
    return Promise.reject(new Error('任务参数无效'))
  }
  return withStudentCacheInvalidation(
    http.post(`/student/collaboration-spaces/${sid}/projects/${pid}/tasks/${tid}/claim`),
  )
}

export function claimStudentCollaborationFlowNode(spaceId, projectId, taskId, nodeId) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  const tid = resolvePositiveId(taskId)
  const nid = resolvePositiveId(nodeId)
  if (!sid || !pid || !tid || !nid) {
    return Promise.reject(new Error('流程节点参数无效'))
  }
  return withStudentCacheInvalidation(
    http.post(`/student/collaboration-spaces/${sid}/projects/${pid}/tasks/${tid}/flow-nodes/${nid}/claim`),
  )
}

export function submitStudentCollaborationTask(spaceId, projectId, taskId, payload) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  const tid = resolvePositiveId(taskId)
  if (!sid || !pid || !tid) {
    return Promise.reject(new Error('任务参数无效'))
  }
  return withStudentCacheInvalidation(
    http.post(`/student/collaboration-spaces/${sid}/projects/${pid}/tasks/${tid}/submit`, payload),
  )
}

export function acceptStudentCollaborationTask(spaceId, projectId, taskId, payload) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  const tid = resolvePositiveId(taskId)
  if (!sid || !pid || !tid) {
    return Promise.reject(new Error('任务参数无效'))
  }
  return withStudentCacheInvalidation(
    http.post(`/student/collaboration-spaces/${sid}/projects/${pid}/tasks/${tid}/accept`, payload),
  )
}

export function returnStudentCollaborationTask(spaceId, projectId, taskId, payload) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  const tid = resolvePositiveId(taskId)
  if (!sid || !pid || !tid) {
    return Promise.reject(new Error('任务参数无效'))
  }
  return withStudentCacheInvalidation(
    http.post(`/student/collaboration-spaces/${sid}/projects/${pid}/tasks/${tid}/return`, payload),
  )
}

export function fetchStudentCollaborationProgress(spaceId, projectId) {
  const sid = resolvePositiveId(spaceId)
  const pid = resolvePositiveId(projectId)
  if (!sid || !pid) {
    return Promise.reject(new Error('项目参数无效'))
  }
  return http.get(`/student/collaboration-spaces/${sid}/projects/${pid}/progress`)
}

export function fetchStudentCollaborationActivityLogs(spaceId, projectId = null) {
  const sid = resolvePositiveId(spaceId)
  if (!sid) {
    return Promise.reject(new Error('协作空间参数无效'))
  }
  const params = projectId ? { projectId } : undefined
  return http.get(`/student/collaboration-spaces/${sid}/activity-logs`, { params })
}

export function fetchStudentClassTasks(classId) {
  const cid = resolvePositiveId(classId)
  if (!cid) {
    return Promise.reject(new Error('班级参数无效，请从首页或任务中心重新进入'))
  }
  return cachedStudentGet(`student:class:${cid}:tasks`, () =>
    http.get(`/student/classes/${cid}/tasks`)
  )
}

export function fetchStudentClassDetail(classId) {
  const cid = resolvePositiveId(classId)
  if (!cid) {
    return Promise.reject(new Error('班级参数无效，请从首页或任务中心重新进入'))
  }
  return cachedStudentGet(`student:class:${cid}:detail`, () =>
    http.get(`/student/classes/${cid}`)
  )
}

export function fetchStudentClassmates(classId) {
  const cid = resolvePositiveId(classId)
  if (!cid) {
    return Promise.reject(new Error('班级参数无效，请从首页或任务中心重新进入'))
  }
  return cachedStudentGet(`student:class:${cid}:classmates`, () =>
    http.get(`/student/classes/${cid}/classmates`),
    60000,
  )
}

export function fetchStudentTaskDetail(classId, taskId) {
  const { classId: cid, taskId: tid } = assertClassTaskIds(classId, taskId)
  return cachedStudentGet(`student:class:${cid}:task:${tid}:detail`, () =>
    http.get(`/student/classes/${cid}/tasks/${tid}`)
  )
}

/** 任务详情页聚合数据（1 次 HTTP，替代 3～5 次分散请求）。 */
export function fetchStudentTaskWorkspace(classId, taskId) {
  const { classId: cid, taskId: tid } = assertClassTaskIds(classId, taskId)
  return cachedStudentGet(
    `student:class:${cid}:task:${tid}:workspace`,
    () => http.get(`/student/classes/${cid}/tasks/${tid}/workspace`),
    90000,
  )
}

export function prefetchStudentTaskWorkspace(classId, taskId) {
  const cid = resolvePositiveId(classId)
  const tid = resolvePositiveId(taskId)
  if (!cid || !tid) {
    return
  }
  void fetchStudentTaskWorkspace(cid, tid).catch(() => {})
}

export function fetchStudentClassGroups(classId) {
  const cid = resolvePositiveId(classId)
  if (!cid) {
    return Promise.reject(new Error('班级参数无效，请从首页或任务中心重新进入'))
  }
  return cachedStudentGet(`student:class:${cid}:groups`, () =>
    http.get(`/student/classes/${cid}/groups`)
  )
}

export function fetchStudentGroupSubtasks(classId, taskId, groupId) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return cachedStudentGet(
    `student:class:${ids.classId}:task:${ids.taskId}:group:${ids.groupId}:subtasks`,
    () =>
      http.get(
        `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/subtasks`,
      ),
    30000,
  )
}

export function fetchStudentGroupSubtaskProgress(classId, taskId, groupId) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return cachedStudentGet(
    `student:class:${ids.classId}:task:${ids.taskId}:group:${ids.groupId}:progress`,
    () =>
      http.get(
        `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/subtasks/progress`,
      ),
    30000,
  )
}

export function createStudentSubtask(classId, taskId, groupId, payload) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.post(
      `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/subtasks`,
      payload,
    ),
  )
}

export function claimStudentSubtask(classId, taskId, groupId, subtaskId) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.post(
      `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/subtasks/${subtaskId}/claim`,
    ),
  )
}

export function submitStudentSubtask(classId, taskId, groupId, subtaskId, payload) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.put(
      `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/subtasks/${subtaskId}/submit`,
      payload,
    ),
  )
}

export function reviewStudentSubtask(classId, taskId, groupId, subtaskId, payload) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.put(
      `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/subtasks/${subtaskId}/review`,
      payload,
    ),
  )
}

export function fetchStudentTaskGroupReport(classId, taskId, groupId) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return cachedStudentGet(
    `student:class:${ids.classId}:task:${ids.taskId}:group:${ids.groupId}:group-report`,
    () =>
      http.get(
        `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/group-report`,
      ),
    30000,
  )
}

export function submitStudentTaskGroupReport(classId, taskId, groupId, payload) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.put(
      `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/group-report`,
      payload,
    ),
  )
}

export function sendBackStudentSubtask(classId, taskId, groupId, subtaskId, payload) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.put(
      `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/subtasks/${subtaskId}/send-back`,
      payload,
    ),
  )
}

export function fetchStudentGroupPeerReviews(classId, taskId, groupId) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return http.get(
    `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/peer-reviews`,
  )
}

export function submitStudentPeerReview(classId, taskId, groupId, payload) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.post(
      `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/peer-reviews`,
      payload,
    ),
  )
}

export function fetchStudentGroupScoreSummary(classId, taskId, groupId) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return http.get(
    `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/score-summary`,
  )
}

export function fetchStudentTaskAppeals(taskId) {
  return http.get(`/student/tasks/${taskId}/appeals`)
}

export function createStudentAppeal(taskId, payload) {
  return withStudentCacheInvalidation(http.post(`/student/tasks/${taskId}/appeals`, payload))
}

export function createStudentTaskAppeal(taskId, reason) {
  return createStudentAppeal(taskId, { reason })
}

export function fetchStudentNotifications(params) {
  return http.get('/notifications', { params })
}

export function fetchStudentNotificationUnreadCount() {
  return http.get('/notifications/unread-count')
}

export function markStudentNotificationRead(notificationId) {
  return http.put(`/notifications/${notificationId}/read`)
}

export function markAllStudentNotificationsRead() {
  return http.put('/notifications/read-all')
}

export function deleteStudentNotification(notificationId) {
  return http.delete(`/notifications/${notificationId}`)
}

export function deleteBatchStudentNotifications(ids) {
  return http.delete('/notifications/batch', { data: ids })
}

export function fetchStudentClasses() {
  return http.get('/student/classes')
}

export function fetchStudentAllTasks(params) {
  return http.get('/student/tasks', { params })
}

export function fetchStudentAllAppeals() {
  return http.get('/student/appeals')
}

export function fetchStudentProfile() {
  return http.get('/student/profile')
}

export function updateStudentProfile(payload) {
  return http.put('/student/profile', payload)
}

export function deleteStudentAccount(password) {
  return http.delete('/student/account', { data: { password } })
}

export function reassignStudentSubtask(classId, taskId, groupId, subtaskId, payload) {
  const ids = assertGroupRouteIds(classId, taskId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.put(
      `/student/classes/${ids.classId}/tasks/${ids.taskId}/groups/${ids.groupId}/subtasks/${subtaskId}/reassign`,
      payload,
    ),
  )
}

export function createStudentGroup(classId, payload) {
  const cid = resolvePositiveId(classId)
  if (!cid) {
    return Promise.reject(new Error('班级参数无效，请从首页或任务中心重新进入'))
  }
  return withStudentClassCacheInvalidation(
    cid,
    http.post(`/student/classes/${cid}/groups`, payload),
  )
}

export function joinStudentGroup(classId, inviteCode) {
  const cid = resolvePositiveId(classId)
  if (!cid) {
    return Promise.reject(new Error('班级参数无效，请从首页或任务中心重新进入'))
  }
  return withStudentClassCacheInvalidation(
    cid,
    http.post(`/student/classes/${cid}/groups/join`, { inviteCode }),
  )
}

function assertClassGroupIds(classId, groupId) {
  const cid = resolvePositiveId(classId)
  const gid = resolvePositiveId(groupId)
  if (!cid || !gid) {
    throw new Error('班级或小组参数无效，请刷新页面后重试')
  }
  return { classId: cid, groupId: gid }
}

export function approveGroupMember(classId, groupId, userId) {
  const ids = assertClassGroupIds(classId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.put(`/student/classes/${ids.classId}/groups/${ids.groupId}/members/${userId}/approve`),
  )
}

export function rejectGroupMember(classId, groupId, userId) {
  const ids = assertClassGroupIds(classId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.put(`/student/classes/${ids.classId}/groups/${ids.groupId}/members/${userId}/reject`),
  )
}

export function refreshGroupInviteCode(classId, groupId) {
  const ids = assertClassGroupIds(classId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.post(`/student/classes/${ids.classId}/groups/${ids.groupId}/invite-code/refresh`),
  )
}

export function removeGroupMember(classId, groupId, userId) {
  const ids = assertClassGroupIds(classId, groupId)
  return withStudentClassCacheInvalidation(
    ids.classId,
    http.delete(`/student/classes/${ids.classId}/groups/${ids.groupId}/members/${userId}`),
  )
}

export function fetchGroupJoinPending(classId, groupId) {
  const ids = assertClassGroupIds(classId, groupId)
  return http.get(`/student/classes/${ids.classId}/groups/${ids.groupId}/join-pending`)
}

export const fetchNotifications = fetchStudentNotifications
export const fetchNotificationUnreadCount = fetchStudentNotificationUnreadCount
export const markNotificationRead = markStudentNotificationRead
export const markAllNotificationsRead = markAllStudentNotificationsRead
