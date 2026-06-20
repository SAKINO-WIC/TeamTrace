const store = new Map()

export function readProgressCache(key, ttlMs = 30000) {
  const entry = store.get(key)
  if (!entry) {
    return null
  }
  if (Date.now() - entry.at > ttlMs) {
    store.delete(key)
    return null
  }
  return entry.value
}

export function writeProgressCache(key, value) {
  store.set(key, { value, at: Date.now() })
}

export function clearProgressCache(key) {
  if (key) {
    store.delete(key)
    return
  }
  store.clear()
}
