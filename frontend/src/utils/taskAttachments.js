function basenameFromUrl(url) {
  const raw = String(url || '').trim()
  if (!raw) return 'attachment'
  const segment = raw.split('/').pop() || raw
  return decodeURIComponent(segment.split('?')[0] || 'attachment')
}

export function normalizeTaskAttachment(item, index = 0) {
  if (!item || typeof item !== 'object') {
    return null
  }
  const url = String(item.url ?? item.value ?? item.href ?? item.link ?? '').trim()
  if (!url) {
    return null
  }
  const typeRaw = String(item.type || '').trim().toLowerCase()
  const isUpload = url.startsWith('/uploads/') || url.startsWith('uploads/')
  const type = typeRaw || (isUpload ? 'file' : 'link')
  const name = String(item.name || item.title || basenameFromUrl(url) || `attachment-${index + 1}`).trim()
  return {
    id: String(item.attachmentId ?? item.id ?? `task-attachment-${index}`),
    type,
    name,
    url,
    value: url,
    size: item.size ?? item.sizeBytes ?? null,
    isFile: type === 'file' || isUpload,
    isLink: type === 'link' && !isUpload,
  }
}

export function normalizeTaskAttachments(raw) {
  return (Array.isArray(raw) ? raw : [])
    .map((item, index) => normalizeTaskAttachment(item, index))
    .filter(Boolean)
}

export function buildTaskAttachments({ files = [], link = '' } = {}) {
  const attachments = []
  ;(Array.isArray(files) ? files : []).forEach((file) => {
    const url = String(file?.url || file?.value || '').trim()
    if (!url) return
    attachments.push({
      type: 'file',
      name: String(file?.name || basenameFromUrl(url)).trim(),
      url,
      size: file?.size ?? null,
    })
  })

  const trimmedLink = String(link || '').trim()
  if (trimmedLink) {
    attachments.push({
      type: 'link',
      name: 'Attachment link',
      url: trimmedLink,
    })
  }

  return attachments
}
