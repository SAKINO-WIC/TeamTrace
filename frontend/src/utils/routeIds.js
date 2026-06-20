/** 校验并规范化路由/接口用的数字 ID，避免 undefined 进入 URL 触发 400。 */
export function resolvePositiveId(value) {
  if (value === null || value === undefined) {
    return ''
  }
  const text = String(value).trim()
  if (!text || text === 'undefined' || text === 'null') {
    return ''
  }
  const parsed = Number(text)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return ''
  }
  return String(Math.trunc(parsed))
}

export function areRouteIdsReady(classId, taskId) {
  return Boolean(resolvePositiveId(classId) && resolvePositiveId(taskId))
}
