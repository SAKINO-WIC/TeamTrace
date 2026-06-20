/**
 * Run work after the browser is idle (or soon), so route/dialog paint stays responsive.
 */
export function scheduleIdle(callback, timeout = 600) {
  if (typeof window === 'undefined') {
    callback()
    return () => {}
  }

  if (typeof window.requestIdleCallback === 'function') {
    const id = window.requestIdleCallback(() => callback(), { timeout })
    return () => window.cancelIdleCallback?.(id)
  }

  const id = window.setTimeout(callback, 16)
  return () => window.clearTimeout(id)
}
