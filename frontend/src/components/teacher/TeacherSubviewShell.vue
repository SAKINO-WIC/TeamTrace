<script setup>
defineProps({
  title: {
    type: String,
    required: true,
  },
  message: {
    type: String,
    default: '',
  },
  messageType: {
    type: String,
    default: 'info',
  },
})
</script>

<template>
  <div class="teacher-subview teacher-page">
    <header class="card topbar">
      <h2>{{ title }}</h2>
      <div class="actions">
        <slot name="actions" />
      </div>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <div class="teacher-page-scroll">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.teacher-subview {
  gap: 12px;
}

.teacher-subview .topbar {
  flex-shrink: 0;
}

.card {
  background: var(--teacher-surface);
  border-radius: var(--teacher-radius-card);
  box-shadow: var(--teacher-shadow);
  transition:
    transform var(--teacher-duration-fast) var(--teacher-ease-ios),
    box-shadow var(--teacher-duration-fast) var(--teacher-ease-ios);
}

.teacher-subview .topbar {
  margin-bottom: 12px;
}

@media (hover: hover) and (pointer: fine) {
  .teacher-subview .card:hover {
    transform: none;
    box-shadow: var(--teacher-shadow);
  }
}

@media (prefers-reduced-motion: reduce) {
  .card {
    transition: none;
  }
}

.topbar {
  padding: 14px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

h2 {
  margin: 0;
  font-size: 18px;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.message {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--teacher-text-tertiary);
}

.message.success {
  color: var(--teacher-success);
}

.message.error {
  color: var(--teacher-danger);
}

@media (max-width: 760px) {
  .topbar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
