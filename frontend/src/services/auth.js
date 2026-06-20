import http from './http'

export function login(payload) {
  return http.post('/auth/login', payload)
}

export function sendEmailCode(payload) {
  return http.post('/auth/email/send-code', payload)
}

export function registerStudent(payload) {
  return http.post('/auth/student/register', payload)
}

export function registerTeacher(payload) {
  return http.post('/auth/teacher/register', payload)
}

export function resetPassword(payload) {
  return http.post('/auth/reset-password', payload)
}

export function markCeremonySeen() {
  return http.post('/auth/ceremony/seen')
}

export function getUserProfile() {
  return http.get('/user/profile')
}

export function updateUserProfile(payload) {
  return http.put('/user/profile', payload)
}

export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/user/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function deleteAccount(payload) {
  return http.post('/user/delete', payload)
}

export function verifyPassword(payload) {
  return http.post('/user/verify-password', payload)
}

export function changePassword(payload) {
  return http.post('/user/change-password', payload)
}
