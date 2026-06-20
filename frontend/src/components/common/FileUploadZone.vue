<script setup>
import { computed, ref } from 'vue'
import { uploadFile } from '../../services/http'
import { formatFileSize } from '../../utils/mediaUrl'
import AttachmentFileBadge from './AttachmentFileBadge.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  maxFiles: { type: Number, default: 5 },
  maxSizeMb: { type: Number, default: 10 },
  accept: {
    type: String,
    default: '.jpg,.jpeg,.png,.gif,.webp,.bmp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.zip,.rar',
  },
})

const emit = defineEmits(['update:modelValue', 'error'])

const inputRef = ref(null)
const dragActive = ref(false)
const uploadingCount = ref(0)

const maxSizeBytes = computed(() => props.maxSizeMb * 1024 * 1024)
const isBusy = computed(() => uploadingCount.value > 0)
const canAddMore = computed(() => props.modelValue.length < props.maxFiles && !props.disabled && !isBusy.value)

const allowedExtensions = computed(() =>
  props.accept
    .split(',')
    .map((item) => item.trim().replace(/^\./, '').toLowerCase())
    .filter(Boolean),
)

function emitError(message) {
  emit('error', message)
}

function validateFile(file) {
  if (!file) {
    return '未选择文件'
  }
  if (file.size > maxSizeBytes.value) {
    return `「${file.name}」超过 ${props.maxSizeMb}MB 限制`
  }
  const ext = String(file.name || '').split('.').pop()?.toLowerCase() || ''
  if (allowedExtensions.value.length && !allowedExtensions.value.includes(ext)) {
    return `不支持「.${ext}」格式`
  }
  return ''
}

async function uploadOne(file) {
  const validation = validateFile(file)
  if (validation) {
    emitError(validation)
    return
  }

  uploadingCount.value += 1
  try {
    const response = await uploadFile(file)
    const url = response?.data?.data?.url || response?.data?.url || ''
    if (!url) {
      throw new Error('上传成功但未返回文件地址')
    }
    const next = [
      ...props.modelValue,
      {
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        name: file.name,
        url: String(url).trim(),
        size: file.size,
      },
    ]
    emit('update:modelValue', next)
  } catch (error) {
    emitError(error?.message || `「${file.name}」上传失败`)
  } finally {
    uploadingCount.value -= 1
  }
}

async function handleFiles(fileList) {
  if (!canAddMore.value || !fileList?.length) {
    return
  }

  const remaining = props.maxFiles - props.modelValue.length
  const queue = Array.from(fileList).slice(0, remaining)

  if (fileList.length > remaining) {
    emitError(`最多上传 ${props.maxFiles} 个文件，已忽略多余文件`)
  }

  for (const file of queue) {
    await uploadOne(file)
  }
}

function openPicker() {
  if (!canAddMore.value) return
  inputRef.value?.click()
}

function onInputChange(event) {
  handleFiles(event.target.files)
  event.target.value = ''
}

function onDrop(event) {
  event.preventDefault()
  dragActive.value = false
  if (!canAddMore.value) return
  handleFiles(event.dataTransfer?.files)
}

function onDragOver(event) {
  event.preventDefault()
  if (canAddMore.value) {
    dragActive.value = true
  }
}

function onDragLeave(event) {
  event.preventDefault()
  dragActive.value = false
}

function removeFile(id) {
  emit(
    'update:modelValue',
    props.modelValue.filter((item) => item.id !== id),
  )
}
</script>

<template>
  <div class="file-upload-zone">
    <input
      ref="inputRef"
      class="file-input-hidden"
      type="file"
      :accept="accept"
      multiple
      :disabled="!canAddMore"
      @change="onInputChange"
    />

    <button
      type="button"
      class="drop-surface"
      :class="{ active: dragActive, disabled: !canAddMore, busy: isBusy }"
      :disabled="!canAddMore"
      @click="openPicker"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop="onDrop"
    >
      <span class="drop-icon" aria-hidden="true">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
          <path
            d="M12 16V4m0 0 7 7M12 4 5 11M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </span>
      <span class="drop-title">{{ isBusy ? '正在上传…' : '点击上传文件' }}</span>
      <span class="drop-hint">或将文件拖拽到此处</span>
      <span class="drop-meta">支持图片、PDF、Office、压缩包等，单文件不超过 {{ maxSizeMb }}MB</span>
    </button>

    <ul v-if="modelValue.length" class="file-chip-list">
      <li v-for="file in modelValue" :key="file.id" class="file-chip">
        <AttachmentFileBadge :name="file.name" :url="file.url" kind="file" />
        <span class="chip-copy">
          <strong>{{ file.name }}</strong>
          <span v-if="file.size">{{ formatFileSize(file.size) }}</span>
        </span>
        <button
          v-if="!disabled"
          class="chip-remove"
          type="button"
          aria-label="移除文件"
          @click.stop="removeFile(file.id)"
        >
          ×
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.file-upload-zone {
  display: grid;
  gap: 10px;
}

.file-input-hidden {
  display: none;
}

.drop-surface {
  width: 100%;
  border: 1.5px dashed color-mix(in srgb, var(--tt-accent) 28%, var(--tt-border));
  border-radius: 16px;
  padding: 22px 18px;
  background:
    radial-gradient(circle at 50% 0%, color-mix(in srgb, var(--tt-accent) 10%, transparent), transparent 58%),
    color-mix(in srgb, var(--tt-accent) 3%, var(--tt-surface));
  color: var(--tt-text);
  cursor: pointer;
  display: grid;
  justify-items: center;
  gap: 6px;
  text-align: center;
  font-family: var(--tt-font);
  transition:
    border-color 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.drop-surface:hover:not(:disabled),
.drop-surface.active {
  border-color: var(--tt-accent);
  background:
    radial-gradient(circle at 50% 0%, color-mix(in srgb, var(--tt-accent) 16%, transparent), transparent 58%),
    color-mix(in srgb, var(--tt-accent) 6%, var(--tt-surface));
  box-shadow: 0 10px 28px color-mix(in srgb, var(--tt-accent) 12%, transparent);
}

.drop-surface:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.drop-surface.busy {
  cursor: wait;
}

.drop-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: var(--tt-accent);
  background: color-mix(in srgb, var(--tt-accent) 12%, #fff);
  border: 1px solid color-mix(in srgb, var(--tt-accent) 18%, transparent);
}

.drop-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--tt-text);
}

.drop-hint {
  font-size: 13px;
  color: var(--tt-text-secondary);
}

.drop-meta {
  font-size: 12px;
  color: var(--tt-text-tertiary);
  max-width: 360px;
  line-height: 1.5;
}

.file-chip-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.file-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--tt-accent) 12%, var(--tt-border-subtle));
  background: var(--tt-surface);
}

.chip-copy {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 2px;
}

.chip-copy strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--tt-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-copy span {
  font-size: 11px;
  color: var(--tt-text-tertiary);
}

.chip-remove {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 8px;
  background: var(--tt-surface-muted);
  color: var(--tt-text-secondary);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.chip-remove:hover {
  background: var(--tt-danger-soft);
  color: var(--tt-danger);
}
</style>
