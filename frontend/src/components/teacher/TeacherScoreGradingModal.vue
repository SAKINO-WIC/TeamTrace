<script setup>
import { computed, ref, watch } from 'vue'
import {
  canPreviewMedia,
  downloadMediaFile,
  fileExtensionLabel,
  formatFileSize,
  resolveMediaUrl,
  resolvePreviewMode,
} from '../../utils/mediaUrl'

const props = defineProps({
  open: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  taskName: { type: String, default: '' },
  targetLabel: { type: String, default: '' },
  targetType: { type: String, default: 'student' },
  submissionStatus: { type: String, default: 'none' },
  submissionHint: { type: String, default: '' },
  submittedAtText: { type: String, default: '' },
  submissionLink: { type: String, default: '' },
  submissionText: { type: String, default: '' },
  files: { type: Array, default: () => [] },
  initialScore: { type: [Number, String], default: '' },
})

const emit = defineEmits(['close', 'confirm'])

const scoreInput = ref('')
const previewUrl = ref('')
const previewTitle = ref('')

watch(
  () => [props.open, props.initialScore],
  () => {
    if (props.open) {
      scoreInput.value =
        props.initialScore === null || props.initialScore === undefined || props.initialScore === ''
          ? ''
          : String(props.initialScore)
      previewUrl.value = ''
      previewTitle.value = ''
    }
  },
  { immediate: true },
)

const statusMeta = computed(() => {
  if (props.submissionStatus === 'onTime') {
    return {
      label: '按时提交',
      tone: 'ok',
      description: props.submissionHint || '作业已在截止前提交。',
    }
  }
  if (props.submissionStatus === 'late') {
    return {
      label: '超时提交',
      tone: 'warn',
      description: props.submissionHint || '已超过截止时间，请结合迟交情况评分。',
    }
  }
  if (props.submissionStatus === 'partial') {
    return {
      label: '部分提交',
      tone: 'warn',
      description: props.submissionHint || '部分子任务已提交，仍有未完成项。',
    }
  }
  return {
    label: '尚未提交',
    tone: 'muted',
    description: props.submissionHint || '未检测到提交记录，评分前请与学生确认。',
  }
})

const displayLink = computed(() => resolveMediaUrl(props.submissionLink))

const fileItems = computed(() =>
  (props.files || []).filter((item) => String(item?.url || '').trim()),
)

function fileBadgeLabel(file) {
  if (file?.kind === 'link') return 'URL'
  return fileExtensionLabel(file?.name || file?.url, 'DOC')
}

function openFilePreview(file) {
  const url = resolveMediaUrl(file?.url)
  if (!url) return
  if (!canPreviewMedia(url)) {
    window.alert('当前文件不支持在线预览，请下载后查看。')
    return
  }
  previewUrl.value = url
  previewTitle.value = file?.name || '作业文件'
}

function canPreviewFile(file) {
  return canPreviewMedia(file?.url)
}

async function downloadFile(file) {
  try {
    await downloadMediaFile(file?.url, file?.name || '附件')
  } catch {
    window.alert('文件下载失败，请稍后重试。')
  }
}

function clearPreview() {
  previewUrl.value = ''
  previewTitle.value = ''
}

function handleConfirm() {
  const parsed = Number(scoreInput.value)
  if (Number.isNaN(parsed) || parsed < 0 || parsed > 100) {
    return
  }
  emit('confirm', parsed)
}

function handleClose() {
  clearPreview()
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="dialog-overlay score-grading-overlay" @click.self="handleClose">
      <section class="score-modal" role="dialog" aria-modal="true" aria-labelledby="score-grading-title">
        <header class="score-modal-head">
          <div class="head-text">
            <p class="head-eyebrow">作业评分</p>
            <h3 id="score-grading-title">{{ taskName || '未命名任务' }}</h3>
            <div class="head-meta">
              <span class="meta-tag">{{ targetType === 'group' ? '小组' : '学生' }}</span>
              <span class="meta-name">{{ targetLabel || '-' }}</span>
            </div>
          </div>
          <button class="head-close" type="button" aria-label="关闭" @click="handleClose">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </button>
        </header>

        <div class="score-modal-body">
          <div class="status-strip" :class="`is-${statusMeta.tone}`">
            <span class="status-dot" aria-hidden="true"></span>
            <div class="status-copy">
              <strong>{{ statusMeta.label }}</strong>
              <span>{{ statusMeta.description }}</span>
              <em v-if="submittedAtText">最近提交 {{ submittedAtText }}</em>
            </div>
          </div>

          <div v-if="loading" class="body-loading">
            <span class="loading-dot"></span>
            正在加载提交内容…
          </div>

          <template v-else>
            <article class="info-card info-card-wide submission-files-card">
              <h4>
                <span class="card-icon" aria-hidden="true">📎</span>
                学生提交
                <span v-if="fileItems.length" class="file-count">{{ fileItems.length }}</span>
              </h4>
              <div class="card-body">
                <div v-if="!fileItems.length && !displayLink" class="placeholder-box">
                  暂无上传文件或外链
                </div>
                <ul v-else class="file-list">
                  <li v-for="(file, index) in fileItems" :key="`${file.url}-${index}`">
                    <div class="file-item">
                      <span class="file-badge" aria-hidden="true">{{ fileBadgeLabel(file) }}</span>
                      <span class="file-info">
                        <strong>{{ file.name || `文件 ${index + 1}` }}</strong>
                        <span v-if="file.size">{{ formatFileSize(file.size) }}</span>
                        <span v-else-if="file.subtaskName">{{ file.subtaskName }}</span>
                      </span>
                      <div class="file-actions">
                        <button v-if="canPreviewFile(file)" class="file-action-btn" type="button" @click="openFilePreview(file)">预览</button>
                        <button class="file-action-btn ghost" type="button" @click="downloadFile(file)">下载</button>
                      </div>
                    </div>
                  </li>
                </ul>
                <div v-if="displayLink" class="external-link-box">
                  <span class="external-link-label">补充链接</span>
                  <a class="link-text" :href="displayLink" target="_blank" rel="noreferrer">{{ displayLink }}</a>
                </div>
              </div>
            </article>

            <article v-if="submissionText" class="note-card">
              <h4>完成说明</h4>
              <p>{{ submissionText }}</p>
            </article>

            <section v-if="previewUrl" class="preview-panel">
              <div class="preview-bar">
                <span>{{ previewTitle }}</span>
                <button type="button" @click="clearPreview">收起</button>
              </div>
              <iframe
                v-if="resolvePreviewMode(previewUrl) === 'pdf'"
                class="preview-frame"
                :src="previewUrl"
                title="文件预览"
              ></iframe>
              <img
                v-else-if="resolvePreviewMode(previewUrl) === 'image'"
                class="preview-image"
                :src="previewUrl"
                :alt="previewTitle"
              />
            </section>
          </template>
        </div>

        <footer class="score-modal-foot">
          <div class="score-block">
            <span class="score-label">教师评分</span>
            <div class="score-input-wrap">
              <input
                v-model="scoreInput"
                type="number"
                min="0"
                max="100"
                step="0.1"
                placeholder="请输入"
                aria-label="教师评分"
                @keyup.enter="handleConfirm"
              />
              <span class="score-suffix">分</span>
            </div>
            <span class="score-hint">0 – 100</span>
          </div>

          <div class="foot-btns">
            <button class="btn-ghost" type="button" @click="handleClose">取消</button>
            <button
              class="btn-primary"
              type="button"
              :disabled="loading || scoreInput === '' || Number.isNaN(Number(scoreInput))"
              @click="handleConfirm"
            >
              确定
            </button>
          </div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.score-modal {
  width: min(520px, var(--tt-dialog-available-width, 100%));
  max-height: min(86vh, 760px);
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  border-radius: 18px;
  background: var(--tt-surface);
  border: 1px solid var(--tt-border-subtle);
  box-shadow: var(--tt-shadow-xl);
  overflow: hidden;
}

/* ── Header ── */
.score-modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 22px 18px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--tt-accent) 12%, transparent),
    transparent 60%
  );
  border-bottom: 1px solid var(--tt-border-subtle);
}

.head-eyebrow {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--tt-accent-secondary);
}

.score-modal-head h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--tt-text);
}

.head-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  min-width: 0;
}

.meta-tag {
  flex-shrink: 0;
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  font-size: 11px;
  font-weight: 700;
  line-height: 22px;
}

.meta-name {
  font-size: 13px;
  color: var(--tt-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.head-close {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 8px;
  background: var(--tt-surface-hover);
  color: var(--tt-text-secondary);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.head-close:hover {
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
}

/* ── Body ── */
.score-modal-body {
  padding: 16px 22px 18px;
  overflow-y: auto;
  display: grid;
  gap: 14px;
}

.status-strip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
}

.status-strip.is-ok {
  background: rgba(22, 163, 74, 0.07);
  border-color: rgba(22, 163, 74, 0.16);
}

.status-strip.is-warn {
  background: rgba(234, 88, 12, 0.07);
  border-color: rgba(234, 88, 12, 0.16);
}

.status-strip.is-muted {
  background: rgba(100, 116, 139, 0.07);
  border-color: rgba(100, 116, 139, 0.14);
}

.status-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  margin-top: 0;
  border-radius: 50%;
  background: currentColor;
}

.status-strip.is-ok .status-dot { color: #16a34a; }
.status-strip.is-warn .status-dot { color: #ea580c; }
.status-strip.is-muted .status-dot { color: #94a3b8; }

.status-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.status-copy strong {
  font-size: 13px;
  font-weight: 700;
  color: var(--tt-text);
}

.status-copy span {
  font-size: 12px;
  line-height: 1.5;
  color: var(--tt-text-secondary);
}

.status-copy em {
  margin-top: 2px;
  font-size: 11px;
  font-style: normal;
  color: var(--tt-text-tertiary);
}

.body-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 28px;
  font-size: 13px;
  color: var(--tt-text-tertiary);
}

.loading-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--tt-accent);
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; transform: scale(0.85); }
  50% { opacity: 1; transform: scale(1); }
}

/* ── Info cards ── */
.info-grid {
  display: grid;
  gap: 10px;
}

.info-card {
  border: 1px solid var(--tt-border-subtle);
  border-radius: 12px;
  background: var(--tt-surface-muted);
  overflow: hidden;
}

.info-card h4 {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  padding: 10px 12px 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--tt-text-secondary);
}

.card-icon {
  font-size: 13px;
  line-height: 1;
}

.file-count {
  margin-left: auto;
  height: 18px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
}

.card-body {
  padding: 8px 12px 12px;
}

.link-text {
  display: block;
  font-size: 13px;
  line-height: 1.5;
  color: var(--tt-accent);
  word-break: break-all;
  text-decoration: none;
}

.link-text:hover {
  text-decoration: underline;
}

.placeholder-text,
.placeholder-box {
  font-size: 13px;
  color: var(--tt-text-tertiary);
}

.placeholder-box {
  padding: 10px;
  text-align: center;
  border-radius: 8px;
  border: 1px dashed var(--tt-border);
  background: var(--tt-surface);
}

.file-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 6px;
}

.submission-files-card {
  grid-column: 1 / -1;
}

.file-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--tt-border-subtle);
  border-radius: 12px;
  background: var(--tt-surface);
  text-align: left;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.file-item:hover {
  border-color: var(--tt-accent-border);
  box-shadow: var(--tt-shadow-sm);
}

.file-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.file-action-btn {
  min-height: 30px;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid var(--tt-accent-border);
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.file-action-btn.ghost {
  background: var(--tt-surface-muted);
  border-color: var(--tt-border-subtle);
  color: var(--tt-text-secondary);
}

.external-link-box {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px dashed var(--tt-border);
  background: var(--tt-surface-muted);
  display: grid;
  gap: 6px;
}

.external-link-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--tt-text-tertiary);
}

.file-badge {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.02em;
}

.file-info {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 1px;
}

.file-info strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--tt-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-info span {
  font-size: 11px;
  color: var(--tt-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-card {
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--tt-surface-muted);
  border: 1px solid var(--tt-border-subtle);
}

.note-card h4 {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--tt-text-secondary);
}

.note-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--tt-text);
}

.preview-panel {
  border: 1px solid var(--tt-border-subtle);
  border-radius: 10px;
  overflow: hidden;
}

.preview-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--tt-surface-muted);
  font-size: 12px;
  font-weight: 600;
  color: var(--tt-text);
}

.preview-bar button {
  border: 0;
  background: transparent;
  color: var(--tt-accent);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.preview-frame,
.preview-image {
  width: 100%;
  min-height: 200px;
  max-height: 280px;
  border: 0;
  display: block;
  object-fit: contain;
  background: var(--tt-bg-sunken);
}

/* ── Footer ── */
.score-modal-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 22px 18px;
  border-top: 1px solid var(--tt-border-subtle);
  background: var(--tt-surface-muted);
}

.score-block {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.score-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--tt-text-secondary);
  white-space: nowrap;
}

.score-input-wrap {
  display: inline-flex;
  align-items: center;
  width: 88px;
  height: 40px;
  padding: 0 4px 0 12px;
  border-radius: 10px;
  border: 1.5px solid var(--tt-accent-border);
  background: var(--tt-surface);
  box-shadow: var(--tt-shadow-xs);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.score-input-wrap:focus-within {
  border-color: var(--tt-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--tt-accent) 18%, transparent);
}

.score-input-wrap input {
  width: 100%;
  min-width: 0;
  height: 100%;
  border: 0;
  outline: none;
  background: transparent;
  font-size: 15px;
  font-weight: 600;
  color: var(--tt-text);
  text-align: center;
  -moz-appearance: textfield;
  appearance: textfield;
}

.score-input-wrap input::placeholder {
  font-size: 12px;
  font-weight: 400;
  color: var(--tt-text-tertiary);
}

.score-input-wrap input::-webkit-outer-spin-button,
.score-input-wrap input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.score-suffix {
  flex-shrink: 0;
  padding-right: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--tt-text-tertiary);
}

.score-hint {
  font-size: 14px;
  font-weight: 500;
  color: var(--tt-text-tertiary);
  white-space: nowrap;
}

.foot-btns {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.btn-ghost,
.btn-primary {
  height: 38px;
  padding: 0 18px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: filter 0.15s ease, box-shadow 0.15s ease;
}

.btn-ghost {
  border: 1px solid var(--tt-border);
  background: var(--tt-surface-muted);
  color: var(--tt-text);
}

.btn-ghost:hover {
  background: var(--tt-surface-hover);
}

.btn-primary {
  border: 0;
  background: var(--tt-accent);
  color: var(--tt-text-inverse);
  box-shadow: var(--tt-shadow-accent);
}

.btn-primary:hover:not(:disabled) {
  filter: brightness(1.04);
}

.btn-primary:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  box-shadow: none;
}

@media (max-width: 480px) {
  .score-modal-foot {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .score-block {
    justify-content: space-between;
  }

  .foot-btns {
    margin-left: 0;
    justify-content: flex-end;
  }
}
</style>
