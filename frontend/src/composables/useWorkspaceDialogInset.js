import { onBeforeUnmount, onMounted, watch } from 'vue'

function debounce(fn, waitMs = 80) {
  let timer = null
  return (...args) => {
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      timer = null
      fn(...args)
    }, waitMs)
  }
}

/**
 * Align fixed dialog overlays with the workspace main column (not sidebar).
 * Uses layout measurements so Windows scrollbar width / zoom do not skew centering.
 */
export function syncWorkspaceDialogBounds({ mainSelector, leftVar, rightVar, isFullscreen }) {
  if (typeof document === 'undefined') {
    return
  }

  if (isFullscreen()) {
    document.documentElement.style.setProperty(leftVar, '0px')
    document.documentElement.style.setProperty(rightVar, '0px')
    return
  }

  const main = document.querySelector(mainSelector)
  if (!main) {
    document.documentElement.style.setProperty(leftVar, '0px')
    document.documentElement.style.setProperty(rightVar, '0px')
    return
  }

  const rect = main.getBoundingClientRect()
  document.documentElement.style.setProperty(leftVar, `${Math.max(0, rect.left)}px`)
  document.documentElement.style.setProperty(
    rightVar,
    `${Math.max(0, window.innerWidth - rect.right)}px`,
  )
}

export function useWorkspaceDialogInset({
  mainSelector,
  leftVar,
  rightVar,
  isFullscreen,
  watchSources = [],
}) {
  let resizeObserver = null
  let observedMain = null

  const sync = () => {
    syncWorkspaceDialogBounds({ mainSelector, leftVar, rightVar, isFullscreen })
  }

  const attachObserver = () => {
    const main = document.querySelector(mainSelector)
    if (!main) {
      resizeObserver?.disconnect()
      resizeObserver = null
      observedMain = null
      return
    }
    if (main === observedMain && resizeObserver) {
      return
    }
    observedMain = main
    resizeObserver?.disconnect()
    resizeObserver = new ResizeObserver(sync)
    resizeObserver.observe(main)
    const layout = main.closest('.teacher-layout, .student-layout')
    if (layout) {
      resizeObserver.observe(layout)
    }
  }

  const scheduleSync = debounce(() => {
    sync()
    requestAnimationFrame(() => {
      attachObserver()
      sync()
    })
  }, 80)

  onMounted(() => {
    scheduleSync()
    window.addEventListener('resize', scheduleSync)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', scheduleSync)
    resizeObserver?.disconnect()
    resizeObserver = null
    observedMain = null
  })

  for (const source of watchSources) {
    watch(source, scheduleSync, { flush: 'post', immediate: true })
  }

  return { sync: scheduleSync }
}
