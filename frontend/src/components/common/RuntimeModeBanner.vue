<script setup>
import { computed } from 'vue'
import { getRuntimeDescription, getRuntimeLabel, getRuntimeMode } from '../../services/runtime'

const mode = getRuntimeMode()

const bannerClass = computed(() => {
  if (mode === 'live') {
    return 'live'
  }
  if (mode === 'mock') {
    return 'mock'
  }
  return 'hybrid'
})
</script>

<template>
  <div class="runtime-banner" :class="bannerClass">
    <span class="runtime-badge">{{ getRuntimeLabel(mode) }}</span>
    <span class="runtime-text">{{ getRuntimeDescription(mode) }}</span>
  </div>
</template>

<style scoped>
.runtime-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid transparent;
}

.runtime-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.runtime-text {
  font-size: 13px;
  line-height: 1.6;
}

.runtime-banner.live {
  background: rgba(34, 197, 94, 0.08);
  color: #166534;
  border-color: rgba(34, 197, 94, 0.16);
}

.runtime-banner.live .runtime-badge {
  background: rgba(34, 197, 94, 0.16);
  color: #166534;
}

.runtime-banner.hybrid {
  background: rgba(59, 130, 246, 0.08);
  color: #1d4ed8;
  border-color: rgba(59, 130, 246, 0.16);
}

.runtime-banner.hybrid .runtime-badge {
  background: rgba(59, 130, 246, 0.16);
  color: #1d4ed8;
}

.runtime-banner.mock {
  background: rgba(245, 158, 11, 0.08);
  color: #92400e;
  border-color: rgba(245, 158, 11, 0.18);
}

.runtime-banner.mock .runtime-badge {
  background: rgba(245, 158, 11, 0.16);
  color: #92400e;
}
</style>
