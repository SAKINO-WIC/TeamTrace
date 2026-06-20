import http from './http'
import { fetchWithCache, invalidateCache, invalidateCacheByPrefix } from '../utils/fetchCache'

const TEACHER_CLASSES_CACHE_KEY = 'teacher:classes'

export function invalidateTeacherClassesListCache() {
  invalidateCache(TEACHER_CLASSES_CACHE_KEY)
}

function withTeacherClassesListInvalidation(request) {
  return Promise.resolve(request).then((response) => {
    invalidateTeacherClassesListCache()
    return response
  })
}

function withTeacherClassCacheInvalidation(classId, request) {
  return Promise.resolve(request).then((response) => {
    invalidateTeacherClassCache(classId)
    return response
  })
}

function cachedTeacherGet(key, fetcher, ttlMs = 120000) {
  return fetchWithCache(key, fetcher, ttlMs)
}

export function invalidateTeacherClassCache(classId) {
  if (classId === undefined || classId === null || classId === '') {
    invalidateCacheByPrefix('teacher:')
    return
  }
  invalidateCacheByPrefix(`teacher:class:${classId}:`)
  invalidateTeacherClassesListCache()
}

export function fetchTeacherClasses() {
  return cachedTeacherGet(TEACHER_CLASSES_CACHE_KEY, () =>
    http.get('/teacher/classes'),
    180000,
  )
}

export function fetchTeacherArchivedClasses() {
  return http.get('/teacher/classes', { params: { status: 'archived' } })
}

export function createTeacherClass(payload) {
  return withTeacherClassesListInvalidation(
    http.post('/teacher/classes', payload)
  )
}

export function fetchTeacherClassDetail(classId) {
  return cachedTeacherGet(`teacher:class:${classId}:detail`, () =>
    http.get(`/teacher/classes/${classId}`)
  )
}

export function deleteTeacherClass(classId) {
  return withTeacherClassesListInvalidation(
    http.delete(`/teacher/classes/${classId}`)
  )
}

export function restoreTeacherClass(classId) {
  return withTeacherClassesListInvalidation(
    http.post(`/teacher/classes/${classId}/restore`)
  )
}

export function generateTeacherClassInviteCode(classId) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.post(`/teacher/classes/${classId}/invite-codes`),
  )
}

export function setTeacherClassGroupingLock(classId, locked) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.put(`/teacher/classes/${classId}/grouping-lock`, { locked }),
  )
}

export function removeTeacherClassStudent(classId, studentId) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.delete(`/teacher/classes/${classId}/students/${studentId}`),
  )
}

export function fetchTeacherClassStudents(classId, params) {
  const paramsKey = params ? JSON.stringify(params) : 'default'
  return cachedTeacherGet(`teacher:class:${classId}:students:${paramsKey}`, () =>
    http.get(`/teacher/classes/${classId}/students`, { params }),
    30000,
  )
}

export function fetchTeacherClassTasks(classId) {
  return cachedTeacherGet(`teacher:class:${classId}:tasks`, () =>
    http.get(`/teacher/classes/${classId}/tasks`)
  )
}

export function createTeacherClassTask(classId, payload) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.post(`/teacher/classes/${classId}/tasks`, payload),
  )
}

export function fetchTeacherTaskDetail(classId, taskId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:detail`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}`)
  )
}

/** 部分更新任务（如 P1：仅延后互评截止时间 peerReviewDeadline） */
export function updateTeacherClassTask(classId, taskId, payload) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.put(`/teacher/classes/${classId}/tasks/${taskId}`, payload),
  )
}

export function fetchTeacherClassGroups(classId) {
  return cachedTeacherGet(`teacher:class:${classId}:groups`, () =>
    http.get(`/teacher/classes/${classId}/groups`)
  )
}

export function createTeacherClassGroup(classId, payload) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.post(`/teacher/classes/${classId}/groups`, payload),
  )
}

export function moveTeacherClassGroupMember(classId, fromGroupId, studentId, targetGroupId) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.put(`/teacher/classes/${classId}/groups/${fromGroupId}/members/${studentId}/move`, { targetGroupId }),
  )
}

export function addStudentToGroup(classId, targetGroupId, studentId) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.post(`/teacher/classes/${classId}/groups/${targetGroupId}/members/${studentId}/add`),
  )
}

export function fetchTeacherGroupSubtaskProgress(classId, taskId, groupId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:group:${groupId}:progress`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}/groups/${groupId}/subtasks/progress`),
    30000,
  )
}

export function fetchTeacherGroupSubtasks(classId, taskId, groupId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:group:${groupId}:subtasks`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}/groups/${groupId}/subtasks`),
    30000,
  )
}

export function fetchTeacherTaskGroupReport(classId, taskId, groupId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:group:${groupId}:group-report`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}/groups/${groupId}/group-report`),
    30000,
  )
}

export function fetchTeacherGroupPeerReviews(classId, taskId, groupId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:group:${groupId}:peer-reviews`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}/groups/${groupId}/peer-reviews`),
    30000,
  )
}

export function fetchTeacherGroupScoreSummaries(classId, taskId, groupId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:group:${groupId}:score-summaries`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}/groups/${groupId}/score-summaries`),
    30000,
  )
}

export function fetchTeacherTaskAppeals(classId, taskId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:appeals`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}/appeals`),
    30000,
  )
}

export function resolveTeacherTaskAppeal(classId, taskId, appealId, payload) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.put(`/teacher/classes/${classId}/tasks/${taskId}/appeals/${appealId}`, payload),
  ).then((response) => {
    invalidateCache('teacher:appeals-workspace')
    return response
  })
}

export function fetchCourseScores(classId) {
  return cachedTeacherGet(`teacher:class:${classId}:course-scores`, () =>
    http.get(`/teacher/classes/${classId}/course-scores`),
    30000,
  )
}

export function saveCourseScore(classId, payload) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.post(`/teacher/classes/${classId}/course-scores`, payload),
  )
}

export function fetchCourseGroupScores(classId) {
  return cachedTeacherGet(`teacher:class:${classId}:course-group-scores`, () =>
    http.get(`/teacher/classes/${classId}/course-scores/groups`),
    30000,
  )
}

export function saveCourseGroupScore(classId, payload) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.post(`/teacher/classes/${classId}/course-scores/groups`, payload),
  )
}

export function fetchTeacherScores(classId, taskId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:scores`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}/scores`),
    60000,
  )
}

export function saveTeacherScore(classId, taskId, payload) {
  return withTeacherClassCacheInvalidation(
    classId,
    http.post(`/teacher/classes/${classId}/tasks/${taskId}/scores`, payload),
  )
}


export function fetchTeacherClassOverview(classId) {
  return cachedTeacherGet(`teacher:class:${classId}:overview`, () =>
    http.get(`/teacher/classes/${classId}/overview`),
    120000,
  )
}

export function fetchTeacherTaskOverview(classId, taskId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:overview`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}/overview`),
    120000,
  )
}

export function fetchTeacherTaskScoreSummaries(classId, taskId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:score-summaries`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}/score-summaries`),
    60000,
  )
}

export function fetchTeacherTaskGroupProgress(classId, taskId) {
  return cachedTeacherGet(`teacher:class:${classId}:task:${taskId}:group-progress`, () =>
    http.get(`/teacher/classes/${classId}/tasks/${taskId}/groups/progress`),
    60000,
  )
}
