import http from '../services/http'

let warmed = false

/**
 * Render 免费实例休眠后首包可达 30～120s；进入工作台后尽早 ping 以并行唤醒。
 * 超时设长一些，避免冷启动时尚未响应就放弃。
 */
export function warmBackendOnce() {
  if (warmed || typeof window === 'undefined') {
    return
  }
  warmed = true
  void http.get('/health', { timeout: 120000 }).catch(() => {})
}
