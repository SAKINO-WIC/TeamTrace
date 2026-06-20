import http from './http'

export function fetchPendingSystemAnnouncements() {
  return http.get('/system-announcements/pending')
}

export function acknowledgeSystemAnnouncement(id, action = 'dismiss') {
  return http.put(`/system-announcements/${id}/ack`, null, {
    params: { action },
  })
}
