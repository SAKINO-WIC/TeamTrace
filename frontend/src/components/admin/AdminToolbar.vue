<template>
  <section class="toolbar-shell">
    <div class="toolbar-head">
      <div>
        <p v-if="$slots.label" class="toolbar-label"><slot name="label" /></p>
        <p v-if="$slots.description" class="toolbar-desc"><slot name="description" /></p>
      </div>
      <div v-if="$slots.actions" class="toolbar-actions">
        <slot name="actions" />
      </div>
    </div>
    <div v-if="$slots.default" class="toolbar-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.toolbar-shell {
  padding: 20px;
  background: var(--admin-surface);
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-card);
  box-shadow: var(--admin-shadow);
}

.toolbar-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.toolbar-label {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--admin-text-tertiary);
  font-weight: 700;
}

.toolbar-desc {
  margin: 10px 0 0;
  color: var(--admin-text-secondary);
  line-height: 1.6;
  max-width: 860px;
}

.toolbar-actions,
.toolbar-body {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-body {
  margin-top: 18px;
}

@media (max-width: 760px) {
  .toolbar-head {
    flex-direction: column;
  }
}
</style>
