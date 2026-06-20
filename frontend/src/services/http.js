import axios from 'axios'
import { getToken } from '../utils/auth'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const payload = error?.response?.data
    const serverMessage =
      typeof payload === 'string'
        ? payload
        : payload?.message || payload?.error
    const fallbackMessage = error?.message || '请求失败，请稍后重试'

    const err = new Error(serverMessage || fallbackMessage)
    err.status = error?.response?.status
    return Promise.reject(err)
  },
)

export default http

export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/common/uploads', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
