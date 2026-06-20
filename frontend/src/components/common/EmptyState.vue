<script setup>
import IconSystem from './IconSystem.vue'

defineProps({
  icon: { type: String, default: 'task' },
  kicker: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  actionLabel: { type: String, default: '' },
  secondaryLabel: { type: String, default: '' },
  compact: { type: Boolean, default: false },
  /** center：居中（列表空态）；panel：左对齐卡片空态（参考 Notion / Linear） */
  variant: { type: String, default: 'center' },
})

const emit = defineEmits(['action', 'secondary'])
</script>

<template>
  <div
    class="empty-state"
    :class="[variant, { compact }]"
  >
    <div class="empty-icon-wrap" aria-hidden="true">
      <span class="empty-icon"><IconSystem :name="icon" :size="compact ? 28 : variant === 'panel' ? 28 : 40" /></span>
    </div>

    <div class="empty-content">
      <p v-if="kicker" class="empty-kicker">{{ kicker }}</p>
      <h3 class="empty-title">{{ title }}</h3>
      <p v-if="description" class="empty-desc">{{ description }}</p>

      <div v-if="actionLabel || secondaryLabel" class="empty-actions">
        <button
          v-if="actionLabel"
          class="empty-btn primary"
          type="button"
          @click="emit('action')"
        >
          {{ actionLabel }}
        </button>
        <button
          v-if="secondaryLabel"
          class="empty-btn secondary"
          type="button"
          @click="emit('secondary')"
        >
          {{ secondaryLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--tt-space-10) var(--tt-space-5);
  gap: var(--tt-space-4);
}

.empty-state.compact {
  padding: var(--tt-space-5) var(--tt-space-4);
  gap: var(--tt-space-3);
}

.empty-state.panel {
  flex-direction: row;
  align-items: center;
  text-align: left;
  padding: 28px 32px;
  gap: 22px;
}

.empty-state.panel.compact {
  padding: 20px 22px;
  gap: 16px;
}

.empty-icon-wrap {
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.empty-state.panel .empty-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background:
    radial-gradient(circle at 30% 20%, rgba(59, 130, 246, 0.22), transparent 58%),
    linear-gradient(145deg, rgba(239, 246, 255, 0.95), rgba(219, 234, 254, 0.88));
  border: 1px solid rgba(59, 130, 246, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
}

.empty-state.panel .empty-icon {
  color: var(--tt-accent, #2563eb);
}

.empty-icon {
  color: var(--tt-text-tertiary);
}

.empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--tt-space-3);
  min-width: 0;
}

.empty-state.panel .empty-content {
  align-items: flex-start;
  flex: 1;
  gap: 10px;
}

.empty-kicker {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--tt-accent, #2563eb);
}

.empty-title {
  margin: 0;
  font-size: var(--tt-text-lg);
  font-weight: 600;
  color: var(--tt-text);
  line-height: 1.35;
}

.empty-state.panel .empty-title {
  font-size: 22px;
  letter-spacing: -0.02em;
}

.compact .empty-title {
  font-size: var(--tt-text-base);
}

.empty-desc {
  margin: 0;
  max-width: 420px;
  font-size: var(--tt-text-base);
  line-height: 1.65;
  color: var(--tt-text-secondary);
}

.empty-state.panel .empty-desc {
  max-width: 46ch;
  font-size: 14px;
}

.compact .empty-desc {
  font-size: var(--tt-text-sm);
}

.empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: var(--tt-space-4);
  justify-content: center;
}

.empty-state.panel .empty-actions {
  margin-top: 18px;
  justify-content: flex-start;
}

.empty-state.compact .empty-actions {
  margin-top: var(--tt-space-4);
}

.empty-btn {
  height: 40px;
  padding: 0 18px;
  border-radius: var(--tt-radius-md, 10px);
  border: none;
  font-size: var(--tt-text-sm);
  font-weight: 600;
  cursor: pointer;
  font-family: var(--tt-font);
  transition:
    background var(--tt-duration-fast, 0.18s) var(--tt-ease, ease),
    border-color var(--tt-duration-fast, 0.18s) var(--tt-ease, ease),
    transform var(--tt-duration-fast, 0.18s) var(--tt-ease, ease);
}

.empty-btn:hover {
  transform: translateY(-1px);
}

.empty-btn.primary {
  background: var(--tt-accent);
  color: var(--tt-text-inverse);
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.22);
}

.empty-btn.primary:hover {
  background: var(--tt-accent-hover);
}

.empty-btn.secondary {
  background: var(--tt-surface, #fff);
  border: 1px solid var(--tt-border);
  color: var(--tt-text-secondary);
}

.empty-btn.secondary:hover {
  border-color: var(--tt-border-strong);
  color: var(--tt-text);
}
</style>
