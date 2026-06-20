import { computed, inject, onBeforeUnmount, onMounted, ref, unref, watch } from 'vue'
import { studentMessageMap } from '../i18n/studentMessages'
import {
  STUDENT_LOCALE_KEY,
  STUDENT_WORKSPACE_EVENT,
  getStudentLocale,
} from '../utils/studentWorkspace'

export const STUDENT_LOCALE_INJECT_KEY = Symbol('studentLocale')

function interpolate(text, params = {}) {
  return String(text).replace(/\{(\w+)\}/g, (_, key) => String(params[key] ?? ''))
}

export function useStudentLocale() {
  const injected = inject(STUDENT_LOCALE_INJECT_KEY, null)
  const locale = injected ?? ref(getStudentLocale())

  function syncLocale() {
    locale.value = getStudentLocale()
  }

  function handleWorkspaceChange(event) {
    const key = event?.detail?.key
    if (!key || key === STUDENT_LOCALE_KEY) {
      syncLocale()
    }
  }

  if (!injected) {
    onMounted(() => {
      syncLocale()
      window.addEventListener(STUDENT_WORKSPACE_EVENT, handleWorkspaceChange)
    })
    onBeforeUnmount(() => {
      window.removeEventListener(STUDENT_WORKSPACE_EVENT, handleWorkspaceChange)
    })
  }

  const isEn = computed(() => unref(locale) === 'en-US')

  function t(zh, en) {
    return isEn.value ? en : zh
  }

  function tm(key, params) {
    const pair = studentMessageMap[key]
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
