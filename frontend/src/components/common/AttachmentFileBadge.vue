<script setup>
import { computed } from 'vue'
import { fileExtensionLabel } from '../../utils/mediaUrl'

const props = defineProps({
  name: {
    type: String,
    default: '',
  },
  url: {
    type: String,
    default: '',
  },
  kind: {
    type: String,
    default: '',
  },
  size: {
    type: String,
    default: 'md',
    validator: (value) => ['sm', 'md'].includes(value),
  },
})

const label = computed(() => {
  const kind = String(props.kind || '').toLowerCase()
  const source = props.name || props.url
  if (kind === 'link') return 'URL'
  if (kind === 'image') return fileExtensionLabel(source, 'IMG')
  return fileExtensionLabel(source, 'DOC')
})
</script>

<template>
  <span class="attachment-file-badge" :class="`size-${size}`" aria-hidden="true">{{ label }}</span>
</template>

<style scoped>
.attachment-file-badge {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 10px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: var(--tt-accent);
  background: var(--tt-accent-soft);
}

.size-md {
  width: 36px;
  height: 36px;
  font-size: 10px;
}

.size-sm {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  font-size: 9px;
}
</style>
