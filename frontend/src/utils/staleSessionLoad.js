import { readSessionCache, writeSessionCache } from './sessionCache'

/**
 * 先展示 sessionStorage 缓存，再后台拉新（stale-while-revalidate）。
 * @returns {{ hadCache: boolean, error?: unknown }}
 */
export async function loadWithStaleSessionCache({
  cacheKey,
  ttlMs = 180000,
  force = false,
  apply,
  fetchFresh,
}) {
  const cached = force ? null : readSessionCache(cacheKey, ttlMs)
  const hadCache = cached != null

  if (hadCache) {
    apply(cached)
  }

  try {
    const fresh = await fetchFresh()
    apply(fresh)
    writeSessionCache(cacheKey, fresh)
    return { hadCache }
  } catch (error) {
    if (!hadCache) {
      throw error
    }
    return { hadCache, error }
  }
}
