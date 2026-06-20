import { computed, inject, onBeforeUnmount, onMounted, ref, unref, watch } from 'vue'
import { teacherMessageMap } from '../i18n/teacherMessages'
import {
  TEACHER_LOCALE_KEY,
  TEACHER_WORKSPACE_EVENT,
  getTeacherLocale,
} from '../utils/teacherWorkspace'

export const TEACHER_LOCALE_INJECT_KEY = Symbol('teacherLocale')

function interpolate(text, params = {}) {
  return String(text).replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? ''))
}

export function useTeacherLocale() {
  const injected = inject(TEACHER_LOCALE_INJECT_KEY, null)
  const locale = injected ?? ref(getTeacherLocale())

  function syncLocale() {
    locale.value = getTeacherLocale()
  }

  function handleWorkspaceChange(event) {
    const key = event?.detail?.key
    if (!key || key === TEACHER_LOCALE_KEY) {
      syncLocale()
    }
  }

  if (!injected) {
    onMounted(() => {
      syncLocale()
      window.addEventListener(TEACHER_WORKSPACE_EVENT, handleWorkspaceChange)
    })
    onBeforeUnmount(() => {
      window.removeEventListener(TEACHER_WORKSPACE_EVENT, handleWorkspaceChange)
    })
  }

  const isEn = computed(() => unref(locale) === 'en-US')

  function t(zh, en) {
    return isEn.value ? en : zh
  }

  function tm(key, params) {
    const pair = teacherMessageMap[key]
    if (!pair) {
      return key
    }
    const text = isEn.value ? pair[1] : pair[0]
    return params ? interpolate(text, params) : text
  }

  function onLocaleChange(callback) {
    watch(isEn, () => callback())
  }

  return { locale, isEn, t, tm, onLocaleChange }
}
