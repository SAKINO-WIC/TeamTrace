import { getTeacherLocale } from './teacherWorkspace'

/** Standalone translate helper — reads locale at call time (use in normalize/load functions). */
export function tt(zh, en) {
  return getTeacherLocale() === 'en-US' ? en : zh
}

/** Internal sentinel keys — never shown to user, safe across locale switches. */
export const GROUP_UNASSIGNED = '__ungrouped__'
export const GROUP_UNAVAILABLE = '__group_unavailable__'

export function displayGroupName(keyOrName, t) {
  if (keyOrName === GROUP_UNASSIGNED) return t('未分组', 'Ungrouped')
  if (keyOrName === GROUP_UNAVAILABLE) return t('小组信息暂不可用', 'Group info unavailable')
  return keyOrName || t('未分组', 'Ungrouped')
}
