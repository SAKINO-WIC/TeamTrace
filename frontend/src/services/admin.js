import http from './http'

export function fetchAdminUsers(params) {
  return http.get('/admin/users', { params })
}

export function createAdminUser(payload) {
  return http.post('/admin/users', payload)
}

export function updateAdminUser(userId, payload) {
  return http.put(`/admin/users/${userId}`, payload)
}

export function updateAdminUserStatus(userId, status) {
  return http.put(`/admin/users/${userId}/status`, { status })
}

export function updateAdminUserRole(userId, payload) {
  return http.put(`/admin/users/${userId}/role`, payload)
}

export function deleteAdminUser(userId) {
  return http.delete(`/admin/users/${userId}`)
}

export function restoreAdminUser(userId) {
  return http.post(`/admin/users/${userId}/restore`)
}

export function resetAdminUserPassword(userId, newPassword) {
  return http.post(`/admin/users/${userId}/reset-password`, newPassword ? { newPassword } : {})
}

export function sendAdminEmail(payload) {
  return http.post('/admin/emails/send', payload)
}

export function fetchWelcomeEmailSummary() {
  return http.get('/admin/welcome-emails/summary')
}

export function resendPendingWelcomeEmails() {
  return http.post('/admin/welcome-emails/resend-pending')
}

export function resendSelectedWelcomeEmails(userIds) {
  return http.post('/admin/welcome-emails/resend-selected', { userIds })
}

export function createTeacherInviteCode(expireDays = 30) {
  return http.post(`/admin/teacher-invite-codes?expireDays=${expireDays}`)
}

export function batchCreateTeacherInviteCodes(count, expireDays = 30) {
  return http.post('/admin/teacher-invite-codes/batch', { count, expireDays })
}

export function fetchTeacherInviteCodes(params) {
  return http.get('/admin/teacher-invite-codes', { params })
}

export function revokeTeacherInviteCode(code) {
  return http.post(`/admin/teacher-invite-codes/${code}/revoke`)
}

export function resumeTeacherInviteCode(code) {
  return http.post(`/admin/teacher-invite-codes/${code}/resume`)
}

export function deleteTeacherInviteCode(code) {
  return http.delete(`/admin/teacher-invite-codes/${code}`)
}

export function batchRevokeTeacherInviteCodes(codes) {
  return http.post('/admin/teacher-invite-codes/revoke-batch', { codes })
}

export function revokeTeacherInviteCodesByQuery(payload) {
  return http.post('/admin/teacher-invite-codes/revoke-by-query', payload)
}

export function changeAdminOwnPassword(oldPassword, newPassword) {
  return http.put('/admin/me/password', { oldPassword, newPassword })
}

export function fetchAdminOperationLogs(params) {
  return http.get('/admin/operation-logs', { params })
}

export function fetchAdminMonitorOverview() {
  return http.get('/admin/monitor/overview')
}

export function fetchAdminMonitorClasses(params) {
  return http.get('/admin/monitor/classes', { params })
}

export function fetchAdminMonitorTasks(params) {
  return http.get('/admin/monitor/tasks', { params })
}

export function fetchSystemAnnouncements(params) {
  return http.get('/admin/system-announcements', { params })
}

export function createSystemAnnouncement(payload) {
  return http.post('/admin/system-announcements', payload)
}

export function withdrawSystemAnnouncement(id) {
  return http.put(`/admin/system-announcements/${id}/withdraw`)
}

export function deleteSystemAnnouncement(id) {
  return http.delete(`/admin/system-announcements/${id}`)
}
