/**
 * 与后端 EmailValidation 保持一致的注册/登录邮箱白名单
 */
export const ALLOWED_EMAIL_RE =
  /^[\w.+-]+@(qq\.com|foxmail\.com|163\.com|126\.com|yeah\.net|188\.com|sina\.com|sina\.cn|sohu\.com|139\.com|gmail\.com|outlook\.com|hotmail\.com|live\.cn|live\.com|icloud\.com|([\w-]+\.)+edu\.cn)$/i

export const ALLOWED_EMAIL_HINT = '支持 QQ/163/Gmail/edu.cn 等'

export const EMAIL_REGISTER_PLACEHOLDER = 'QQ/163/Gmail/edu.cn 邮箱'

export const EMAIL_RESET_PLACEHOLDER = EMAIL_REGISTER_PLACEHOLDER

export function normalizeEmail(email) {
  return String(email || '').trim().toLowerCase()
}

export function isAllowedEmail(email) {
  return ALLOWED_EMAIL_RE.test(normalizeEmail(email))
}

/** @deprecated 使用 isAllowedEmail */
export function isQqEmail(email) {
  return isAllowedEmail(email)
}
