const PREFIX = 'tt:page:'

export function readSessionCache(key, ttlMs = 120000) {
  if (typeof sessionStorage === 'undefined') {
    return null
  }
  try {
    const raw = sessionStorage.getItem(`${PREFIX}${key}`)
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw)
    if (!parsed || Date.now() - Number(parsed.at) > ttlMs) {
      sessionStorage.removeItem(`${PREFIX}${key}`)
      return null
    }
    return parsed.value
  } catch {
    return null
  }
}

export function writeSessionCache(key, value) {
  if (typeof sessionStorage === 'undefined') {
    return
  }
  try {
    sessionStorage.setItem(`${PREFIX}${key}`, JSON.stringify({ at: Date.now(), value }))
  } catch {
    /* quota exceeded — ignore */
  }
}

export function clearSessionCache(key) {
  if (typeof sessionStorage === 'undefined') {
    return
  }
  try {
    sessionStorage.removeItem(`${PREFIX}${key}`)
  } catch {
    /* ignore */
  }
}
