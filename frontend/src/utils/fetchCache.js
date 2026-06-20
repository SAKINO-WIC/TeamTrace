const cacheStore = new Map()
const inflightStore = new Map()

export function fetchWithCache(key, fetcher, ttlMs = 45000) {
  const now = Date.now()
  const cached = cacheStore.get(key)
  if (cached && now - cached.at < cached.ttl) {
    return Promise.resolve(cached.value)
  }

  const pending = inflightStore.get(key)
  if (pending) {
    return pending
  }

  const promise = Promise.resolve()
    .then(fetcher)
    .then((value) => {
      cacheStore.set(key, { value, at: Date.now(), ttl: ttlMs })
      inflightStore.delete(key)
      return value
    })
    .catch((error) => {
      inflightStore.delete(key)
      throw error
    })

  inflightStore.set(key, promise)
  return promise
}

export function invalidateCache(key) {
  cacheStore.delete(key)
  inflightStore.delete(key)
}

export function invalidateCacheByPrefix(prefix) {
  for (const key of cacheStore.keys()) {
    if (key.startsWith(prefix)) {
      cacheStore.delete(key)
    }
  }
  for (const key of inflightStore.keys()) {
    if (key.startsWith(prefix)) {
      inflightStore.delete(key)
    }
  }
}

export function clearFetchCache() {
  cacheStore.clear()
  inflightStore.clear()
}

export async function mapWithConcurrency(items, mapper, limit = 6) {
  if (!items.length) {
    return []
  }

  const results = new Array(items.length)
  let cursor = 0
  const workerCount = Math.min(Math.max(limit, 1), items.length)

  async function worker() {
    while (cursor < items.length) {
      const index = cursor
      cursor += 1
      results[index] = await mapper(items[index], index)
    }
  }

  await Promise.all(Array.from({ length: workerCount }, worker))
  return results
}
