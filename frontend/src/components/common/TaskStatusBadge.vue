<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: [String, Number, null],
    default: '',
  },
  label: {
    type: String,
    default: '',
  },
  size: {
    type: String,
    default: 'md',
  },
})

function normalizeTaskStatus(value) {
  if (value === null || value === undefined || value === '') {
    return 'unknown'
  }

  const raw = String(value).trim()
  const parsed = Number(raw)
  if (!Number.isNaN(parsed)) {
    if (parsed === 0) return 'not-started'
    if (parsed === 1) return 'active'
    if (parsed === 2) return 'closed'
    return 'unknown'
  }

  const normalized = raw.toLowerCase()
  const map = {
    open: 'not-started',
    pending: 'pending',
    not_started: 'not-started',
    'not-started': 'not-started',
    in_progress: 'active',
    'in-progress': 'active',
    active: 'active',
    running: 'active',
    closed: 'closed',
    overdue: 'closed',
    done: 'finished',
    finished: 'finished',
    archived: 'archived',
  }
  return map[normalized] || 'unknown'
}

function defaultLabel(tone, value) {
  const map = {
    'not-started': '未开始',
    pending: '待开始',
    active: '进行中',
    closed: '已截止',
    finished: '已结束',
    archived: '已归档',
    unknown: value ? String(value) : '-',
  }
  return map[tone] || map.unknown
}

const tone = computed(() => normalizeTaskStatus(props.status))
const displayLabel = computed(() => props.label || defaultLabel(tone.value, props.status))
</script>

<template>
  <span class="task-status-badge" :class="[`is-${tone}`, `size-${size}`]">
    {{ displayLabel }}
  </span>
</template>

<style scoped>
.task-status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  white-space: nowrap;
}

.task-status-badge.size-sm {
  min-height: 24px;
  padding: 0 8px;
  font-size: 11px;
}

.task-status-badge.size-lg {
  min-height: 32px;
  padding: 0 12px;
  font-size: 13px;
}

.task-status-badge.is-not-started,
.task-status-badge.is-pending {
  background: color-mix(in srgb, #64748b 10%, transparent);
  border-color: color-mix(in srgb, #64748b 22%, transparent);
  color: #475569;
}

.task-status-badge.is-active {
  background: color-mix(in srgb, #2563eb 10%, transparent);
  border-color: color-mix(in srgb, #2563eb 24%, transparent);
  color: #1d4ed8;
}

.task-status-badge.is-closed {
  background: color-mix(in srgb, #f97316 12%, transparent);
  border-color: color-mix(in srgb, #f97316 26%, transparent);
  color: #c2410c;
}

.task-status-badge.is-finished {
  background: color-mix(in srgb, #059669 12%, transparent);
  border-color: color-mix(in srgb, #059669 24%, transparent);
  color: #047857;
}

.task-status-badge.is-archived,
.task-status-badge.is-unknown {
  background: color-mix(in srgb, #6b7280 10%, transparent);
  border-color: color-mix(in srgb, #6b7280 20%, transparent);
  color: #4b5563;
}

html.dark .task-status-badge.is-not-started,
html.dark .task-status-badge.is-pending,
html[data-teacher-theme='midnight-classroom'] .task-status-badge.is-not-started,
html[data-teacher-theme='midnight-classroom'] .task-status-badge.is-pending,
html[data-student-theme='dark'] .task-status-badge.is-not-started,
html[data-student-theme='dark'] .task-status-badge.is-pending {
  color: #cbd5e1;
}

html.dark .task-status-badge.is-active,
html[data-teacher-theme='midnight-classroom'] .task-status-badge.is-active,
html[data-student-theme='dark'] .task-status-badge.is-active {
  color: #93c5fd;
}

html.dark .task-status-badge.is-closed,
html[data-teacher-theme='midnight-classroom'] .task-status-badge.is-closed,
html[data-student-theme='dark'] .task-status-badge.is-closed {
  color: #fdba74;
}

html.dark .task-status-badge.is-finished,
html[data-teacher-theme='midnight-classroom'] .task-status-badge.is-finished,
html[data-student-theme='dark'] .task-status-badge.is-finished {
  color: #86efac;
}
</style>
