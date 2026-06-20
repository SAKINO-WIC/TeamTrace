import { ref } from 'vue'
import { readSessionCache, writeSessionCache } from '../utils/sessionCache'

/**
 * 先展示 session 缓存，再后台刷新（类似主流产品的 stale-while-revalidate）。
 */
export function useStalePageLoad(options) {
  const {
    resolveCacheKey,
    fetchFresh,
    ttlMs = 180000,
    applyCached,
    applyFresh,
    onError,
  } = options

  const loading = ref(false)
  const refreshing = ref(false)

  async function run(loadOptions = {}) {
    const cacheKey = resolveCacheKey()
    const force = loadOptions.force === true
    const cached = force ? null : readSessionCache(cacheKey, ttlMs)
    const hadCache = cached != null

    if (hadCache) {
      applyCached(cached)
      loading.value = false
      refreshing.value = true
    } else {
      loading.value = true
      refreshing.value = false
    }

    try {
      const fresh = await fetchFresh()
      applyFresh(fresh)
      writeSessionCache(cacheKey, fresh)
    } catch (error) {
      if (!hadCache) {
        onError?.(error)
      }
      throw error
    } finally {
      loading.value = false
      refreshing.value = false
    }
  }

  return { loading, refreshing, run }
}
