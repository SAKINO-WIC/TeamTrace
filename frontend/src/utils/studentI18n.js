import { formatStudentAppealStatus } from './student'

const APPEAL_STATUS_KEYS = {
  待处理: 'appeals.statusPending',
  处理中: 'appeals.statusProcessing',
  已驳回: 'appeals.statusRejected',
  已通过: 'appeals.statusApproved',
  已处理: 'appeals.statusHandled',
}

const APPEAL_TYPE_KEYS = {
  teacher_score: 'appeals.typeTeacherScore',
  peer_review: 'appeals.typePeerReview',
  task_review: 'appeals.typeSubtask',
}

export function localizeAppealStatus(value, tm) {
  const zh = formatStudentAppealStatus(value)
  if (!zh || zh === '-') return '—'
  const key = APPEAL_STATUS_KEYS[zh]
  return key ? tm(key) : zh
}

export function localizeAppealType(type, tm) {
  const key = APPEAL_TYPE_KEYS[String(type || '').toLowerCase()]
  return key ? tm(key) : tm('appeals.typeDefault')
}

export function formatStudentDateTime(value, isEn) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat(isEn ? 'en-US' : 'zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
