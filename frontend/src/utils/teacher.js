import { getTeacherLocale } from './teacherWorkspace'

function isEnglishLocale(locale = getTeacherLocale()) {
  return locale === 'en-US'
}

export function formatDateTime(value, locale = getTeacherLocale()) {
  if (!value) {
    return '-'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  return new Intl.DateTimeFormat(isEnglishLocale(locale) ? 'en-US' : 'zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function formatBoolean(value, { trueLabel, falseLabel, unknownLabel = '-', locale = getTeacherLocale() } = {}) {
  const en = isEnglishLocale(locale)
  const resolvedTrue = trueLabel ?? (en ? 'Yes' : '是')
  const resolvedFalse = falseLabel ?? (en ? 'No' : '否')
  if (value === true) return resolvedTrue
  if (value === false) return resolvedFalse
  return unknownLabel
}

export function formatTaskStatus(value, locale = getTeacherLocale()) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  const en = isEnglishLocale(locale)
  const raw = String(value).trim()
  const parsed = Number(raw)
  if (!Number.isNaN(parsed)) {
    const numericMap = en
      ? { 0: 'Not started', 1: 'In progress', 2: 'Closed' }
      : { 0: '未开始', 1: '进行中', 2: '已截止' }
    if (numericMap[parsed]) {
      return numericMap[parsed]
    }
  }

  const normalized = raw.toLowerCase()
  const map = en
    ? {
        open: 'Not started',
        pending: 'Pending',
        not_started: 'Not started',
        in_progress: 'In progress',
        active: 'In progress',
        running: 'In progress',
        closed: 'Closed',
        overdue: 'Overdue',
        done: 'Finished',
        finished: 'Finished',
        archived: 'Archived',
      }
    : {
        open: '未开始',
        pending: '待开始',
        not_started: '未开始',
        in_progress: '进行中',
        active: '进行中',
        running: '进行中',
        closed: '已截止',
        overdue: '已截止',
        done: '已结束',
        finished: '已结束',
        archived: '已归档',
      }
  return map[normalized] || raw
}

export function formatClassStatus(value, locale = getTeacherLocale()) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const en = isEnglishLocale(locale)
  const parsed = Number(value)
  if (!Number.isNaN(parsed)) {
    return parsed === 1 ? (en ? 'Active' : '进行中') : (en ? 'Archived' : '已归档')
  }
  const raw = String(value).trim()
  if (!raw) return '-'
  const normalized = raw.toLowerCase()
  const map = en
    ? { active: 'Active', archived: 'Archived', open: 'Active', closed: 'Archived' }
    : { active: '进行中', archived: '已归档', open: '进行中', closed: '已归档' }
  return map[normalized] || raw
}

export function formatStudentStatus(value, locale = getTeacherLocale()) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const en = isEnglishLocale(locale)
  const parsed = Number(value)
  if (!Number.isNaN(parsed)) {
    return parsed === 1 ? (en ? 'In class' : '在班') : (en ? 'Not in class' : '非在班')
  }
  const raw = String(value).trim()
  if (!raw) return '-'
  const normalized = raw.toLowerCase()
  const map = en
    ? { in_class: 'In class', active: 'In class', left: 'Not in class', inactive: 'Not in class' }
    : { in_class: '在班', active: '在班', left: '非在班', inactive: '非在班' }
  return map[normalized] || raw
}

export function formatAppealStatus(value, locale = getTeacherLocale()) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const en = isEnglishLocale(locale)
  const raw = String(value).trim()
  const normalized = raw.toLowerCase()
  const map = en
    ? {
        0: 'Pending',
        1: 'Processing',
        2: 'Rejected',
        3: 'Approved',
        pending: 'Pending',
        submitted: 'Pending',
        created: 'Pending',
        processing: 'Processing',
        approved: 'Approved',
        rejected: 'Rejected',
        resolved: 'Resolved',
      }
    : {
        0: '待处理',
        1: '处理中',
        2: '已驳回',
        3: '已通过',
        pending: '待处理',
        submitted: '待处理',
        created: '待处理',
        processing: '处理中',
        approved: '已通过',
        rejected: '已驳回',
        resolved: '已处理',
      }
  return map[normalized] || raw
}

export function formatSemesterLabel(value, locale = getTeacherLocale()) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }

  const raw = String(value).trim()
  if (!raw) {
    return '-'
  }

  const en = isEnglishLocale(locale)
  const match = raw.match(/^(\d{4})-(\d{4})学年度\s*第([12])学期$/)
  if (match) {
    const startYear = match[1]
    const endYear = match[2]
    const term = match[3]
    if (en) {
      return term === '1'
        ? `${startYear}–${endYear} Term 1`
        : `${startYear}–${endYear} Term 2`
    }
    return raw
  }

  const springFallMatch = raw.match(/^(\d{4})-(spring|fall)$/i)
  if (springFallMatch) {
    const year = Number(springFallMatch[1])
    const term = springFallMatch[2].toLowerCase()
    if (term === 'spring') {
      return en
        ? `${year - 1}–${year} Term 2`
        : `${year - 1}-${year}学年度 第2学期`
    }
    return en
      ? `${year}–${year + 1} Term 1`
      : `${year}-${year + 1}学年度 第1学期`
  }

  return raw
}

export function normalizePagedPayload(payload) {
  const list = payload?.list || payload?.items || payload?.records || payload?.content || []
  const total = Number(payload?.total ?? payload?.totalElements) || 0
  const size = Number(payload?.size) || 0
  const page = Number(payload?.page) || 1
  const pages = Number(payload?.pages) || (size > 0 ? Math.ceil(total / size) : 0)
  const hasNext = typeof payload?.hasNext === 'boolean' ? payload.hasNext : size > 0 && (page * size) < total
  return {
    list,
    total,
    pages,
    hasNext,
  }
}
