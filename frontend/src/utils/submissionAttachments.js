function basenameFromUrl(url) {
  const raw = String(url || '').trim()
  if (!raw) return '附件'
  const segment = raw.split('/').pop() || raw
  return decodeURIComponent(segment.split('?')[0] || '附件')
}

function normalizeAttachmentItem(item, index = 0) {
  if (!item || typeof item !== 'object') {
    return null
  }
  const value = String(item.value ?? item.url ?? item.href ?? item.link ?? '').trim()
  if (!value) {
    return null
  }
  const typeRaw = String(item.type || '').trim().toLowerCase()
  const isUpload = value.startsWith('/uploads/') || value.startsWith('uploads/')
  const type = typeRaw || (isUpload ? 'file' : 'link')
  return {
    type,
    name: String(item.name || item.title || basenameFromUrl(value) || `附件 ${index + 1}`).trim(),
    value,
    size: item.size ?? null,
  }
}

export function parseSubmissionContent(raw) {
  if (!raw) {
    return { text: '', attachment: '', attachments: [], files: [], link: '' }
  }

  try {
    const parsed = JSON.parse(raw)
    const attachments = (Array.isArray(parsed?.attachments) ? parsed.attachments : [])
      .map((item, index) => normalizeAttachmentItem(item, index))
      .filter(Boolean)

    const legacyUrl = String(
      parsed?.attachment || parsed?.attachmentUrl || '',
    ).trim()

    if (legacyUrl && !attachments.some((item) => item.value === legacyUrl)) {
      attachments.push(
        normalizeAttachmentItem(
          { type: legacyUrl.startsWith('/uploads/') ? 'file' : 'link', value: legacyUrl },
          attachments.length,
        ),
      )
    }

    const files = attachments.filter((item) => item.type === 'file' || item.value.startsWith('/uploads/'))
    const linkItem = attachments.find((item) => item.type === 'link' && !item.value.startsWith('/uploads/'))
    const firstFile = files[0]

    return {
      text: String(parsed?.text ?? parsed?.content ?? parsed?.description ?? '').trim(),
      attachment: firstFile?.value || linkItem?.value || '',
      attachments,
      files,
      link: linkItem?.value || '',
    }
  } catch {
    return {
      text: String(raw).trim(),
      attachment: '',
      attachments: [],
      files: [],
      link: '',
    }
  }
}

export function buildSubmissionContent({ text = '', link = '', files = [] } = {}) {
  const attachments = []
  const normalizedFiles = Array.isArray(files) ? files : []

  normalizedFiles.forEach((file) => {
    const url = String(file?.url || file?.value || '').trim()
    if (!url) return
    attachments.push({
      type: 'file',
      name: String(file?.name || basenameFromUrl(url)).trim(),
      value: url,
      size: file?.size ?? null,
    })
  })

  const trimmedLink = String(link || '').trim()
  if (trimmedLink) {
    attachments.push({
      type: 'link',
      name: '附件链接',
      value: trimmedLink,
    })
  }

  return JSON.stringify({
    text: String(text || '').trim(),
    attachments,
  })
}

export function filesFromSubmissionParsed(parsed) {
  if (!parsed) return []
  return (parsed.files || []).map((item, index) => ({
    id: `file-${index}-${item.value}`,
    name: item.name || basenameFromUrl(item.value),
    url: item.value,
    size: item.size,
  }))
}

export function buildAppealAttachments(files = []) {
  const attachments = (Array.isArray(files) ? files : [])
    .map((file) => {
      const value = String(file?.url || file?.value || '').trim()
      if (!value) return null
      return {
        type: 'file',
        name: String(file?.name || basenameFromUrl(value)).trim(),
        value,
        size: file?.size ?? null,
      }
    })
    .filter(Boolean)

  return attachments.length ? JSON.stringify(attachments) : undefined
}

export function parseAppealAttachments(raw) {
  if (!raw) return []

  const source =
    Array.isArray(raw)
      ? raw
      : typeof raw === 'string'
        ? (() => {
            const trimmed = raw.trim()
            if (!trimmed) return []
            try {
              const parsed = JSON.parse(trimmed)
              if (Array.isArray(parsed)) return parsed
              if (parsed && typeof parsed === 'object') return [parsed]
            } catch {
              if (/^https?:\/\//i.test(trimmed) || trimmed.startsWith('/uploads/')) {
                return [{ type: 'file', value: trimmed, name: basenameFromUrl(trimmed) }]
              }
            }
            return []
          })()
        : []

  return source
    .map((item, index) => {
      const value = String(item?.value ?? item?.url ?? item?.href ?? item ?? '').trim()
      if (!value) return null
      return {
        id: String(item?.id ?? `appeal-file-${index}`),
        name: String(item?.name ?? item?.title ?? basenameFromUrl(value)),
        url: value,
        size: item?.size ?? null,
      }
    })
    .filter(Boolean)
}
