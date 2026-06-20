/**
 * Resolve uploaded file paths (/uploads/...) and external URLs for preview/download.
 */
export function resolveMediaUrl(url) {
  const raw = String(url || '').trim()
  if (!raw) return ''
  if (/^https?:\/\//i.test(raw)) return raw
  if (raw.startsWith('/uploads/')) return raw
  if (raw.startsWith('uploads/')) return `/${raw}`
  return raw
}

export function isExternalMediaUrl(url) {
  return /^https?:\/\//i.test(resolveMediaUrl(url))
}

export function isUploadedMediaUrl(url) {
  return resolveMediaUrl(url).startsWith('/uploads/')
}

export function formatFileSize(bytes) {
  const size = Number(bytes)
  if (!Number.isFinite(size) || size <= 0) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

export function fileExtensionLabel(nameOrUrl, fallback = 'FILE') {
  const source = String(nameOrUrl || '').trim()
  const match = source.match(/\.([a-z0-9]{2,5})(?:\?.*)?$/i)
  return match ? match[1].toUpperCase() : fallback
}

export function resolvePreviewMode(url) {
  const normalized = resolveMediaUrl(url)
  if (!normalized) return ''
  if (/\.(png|jpg|jpeg|gif|webp|svg|bmp)(\?.*)?$/i.test(normalized)) return 'image'
  if (/\.pdf(\?.*)?$/i.test(normalized)) return 'pdf'
  return 'external'
}

export function canPreviewMedia(url) {
  const mode = resolvePreviewMode(url)
  return mode === 'image' || mode === 'pdf'
}

function fallbackFileName(url) {
  const normalized = resolveMediaUrl(url)
  const segment = normalized.split('/').pop()?.split('?')[0] || ''
  try {
    return decodeURIComponent(segment)
  } catch {
    return segment
  }
}

function sanitizeDownloadName(name) {
  return String(name || '')
    .trim()
    .replace(/[\\/:*?"<>|]/g, '_')
}

export async function downloadMediaFile(url, fileName = '') {
  const resolved = resolveMediaUrl(url)
  if (!resolved) return false

  if (isExternalMediaUrl(resolved)) {
    window.open(resolved, '_blank', 'noopener,noreferrer')
    return true
  }

  const response = await fetch(resolved, { credentials: 'same-origin' })
  if (!response.ok) {
    throw new Error('文件下载失败')
  }

  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = sanitizeDownloadName(fileName) || sanitizeDownloadName(fallbackFileName(resolved)) || 'attachment'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
  return true
}
