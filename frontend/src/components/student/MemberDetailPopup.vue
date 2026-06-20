<script setup>
import { computed } from 'vue'
import { getCurrentUserId } from '../../utils/auth'

const props = defineProps({
  member: { type: Object, default: null },
})

defineEmits(['close'])

const isSelf = computed(() => {
  if (!props.member) return false
  const uid = getCurrentUserId()
  return uid !== null && Number(uid) === Number(props.member.id || props.member.studentId)
})
</script>

<template>
  <Teleport to="body">
    <div v-if="member" class="dialog-mask member-popup-overlay" @click.self="$emit('close')">
      <div class="member-popup-card">
        <div class="popup-header">
          <div class="popup-avatar">{{ (member.name || '?').slice(0, 1) }}</div>
          <div class="popup-identity">
            <p class="popup-name">{{ member.name }}</p>
            <div class="popup-badges">
              <span v-if="member.isLeader" class="popup-badge leader">组长</span>
              <span v-else class="popup-badge">组员</span>
              <span v-if="isSelf" class="popup-badge self">当前用户</span>
            </div>
          </div>
          <button class="popup-close" type="button" @click="$emit('close')">✕</button>
        </div>
        <div class="popup-details">
          <div class="detail-row">
            <span class="detail-label">学号</span>
            <span class="detail-value mono">{{ member.studentId || member.id }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">所属小组</span>
            <span class="detail-value">{{ member.groupName || '未分组' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">角色</span>
            <span class="detail-value">{{ member.isLeader ? '组长' : '组员' }}</span>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.member-popup-overlay {
  /* Positioning from teacher-dialog.css (aligned to student main column). */
}

.member-popup-card {
  width: min(420px, var(--tt-dialog-available-width, 100%));
  padding: 28px;
  border-radius: 24px;
  background: var(--student-surface);
  box-shadow: 0 30px 80px rgba(15, 23, 42, 0.18);
  border: 1px solid var(--student-divider);
  display: grid;
  gap: 20px;
}

.popup-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.popup-avatar {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: var(--student-accent-soft);
  color: var(--student-accent);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  flex-shrink: 0;
}

.popup-identity {
  min-width: 0;
  flex: 1;
}

.popup-name {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  color: var(--student-text-primary);
}

.popup-badges {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}

.popup-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  background: rgba(15, 23, 42, 0.05);
  color: var(--student-text-secondary);
}

.popup-badge.leader {
  background: rgba(52, 199, 89, 0.12);
  color: var(--student-success);
}

.popup-badge.self {
  background: rgba(0, 122, 255, 0.1);
  color: var(--student-accent);
}

.popup-close {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid var(--student-border);
  background: var(--student-surface);
  color: var(--student-text-secondary);
  cursor: pointer;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.popup-close:hover {
  background: var(--student-surface-muted);
  color: var(--student-text-primary);
}

.popup-details {
  display: grid;
  gap: 12px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.detail-label {
  font-size: 13px;
  color: var(--student-text-secondary);
}

.detail-value {
  font-size: 14px;
  font-weight: 700;
  color: var(--student-text-primary);
}

.detail-value.mono {
  font-family: 'SF Mono', monospace;
}
</style>
