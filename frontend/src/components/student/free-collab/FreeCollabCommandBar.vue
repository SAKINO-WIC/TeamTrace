<script setup>
defineProps({
  sectionMeta: { type: Object, required: true },
  selectedSpaceId: { type: [Number, String, null], default: null },
  selectedProjectId: { type: [Number, String, null], default: null },
  spaces: { type: Array, default: () => [] },
  projects: { type: Array, default: () => [] },
  handleSpaceChange: { type: Function, required: true },
  handleProjectChange: { type: Function, required: true },
  refresh: { type: Function, required: true },
})
</script>

<template>
  <section class="free-command-bar">
    <div class="command-title">
      <span class="command-eyebrow">
        自由协作 · {{ sectionMeta.stage }}
        <em class="beta-badge">Beta</em>
      </span>
      <h1>{{ sectionMeta.title }}</h1>
      <p>{{ sectionMeta.subtitle }}</p>
    </div>

    <div class="command-context">
      <label class="context-select">
        <span>空间</span>
        <select :value="selectedSpaceId || ''" @change="handleSpaceChange">
          <option value="">暂未选择</option>
          <option v-for="space in spaces" :key="space.id" :value="space.id">
            {{ space.name }}
          </option>
        </select>
      </label>
      <label class="context-select">
        <span>项目</span>
        <select :value="selectedProjectId || ''" :disabled="!selectedSpaceId" @change="handleProjectChange">
          <option value="">全部项目</option>
          <option v-for="project in projects" :key="project.id" :value="project.id">
            {{ project.title }}
          </option>
        </select>
      </label>
      <button type="button" class="secondary-btn" @click="refresh">刷新</button>
    </div>
  </section>
</template>
