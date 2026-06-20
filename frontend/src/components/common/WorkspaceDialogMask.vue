<script setup>
defineProps({
  open: {
    type: Boolean,
    default: false,
  },
  overlayClass: {
    type: String,
    default: 'dialog-mask',
  },
})

const emit = defineEmits(['close'])
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      :class="overlayClass"
      @click.self="emit('close')"
    >
      <slot />
    </div>
  </Teleport>
</template>
