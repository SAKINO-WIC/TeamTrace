<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTeacherLocale } from '../../composables/useTeacherLocale'
import { resolveTeacherTaskDetailEntry } from '../../utils/teacherTaskNavigation'

const props = defineProps({
  classId: { type: [String, Number], required: true },
  studentCount: { type: Number, default: null },
  groupCount: { type: Number, default: null },
  taskCount: { type: Number, default: null },
  activeTaskId: { type: [String, Number], default: '' },
})

const route = useRoute()
const router = useRouter()
const { t } = useTeacherLocale()

const classBasePath = computed(() => `/teacher/classes/${props.classId}`)

const tabs = computed(() => [
  { id: 'overview', label: t('班级概览', 'Class overview'), count: null, path: classBasePath.value },
  { id: 'students', label: t('学生管理', 'Students'), count: props.studentCount, path: `${classBasePath.value}/students` },
  { id: 'groups', label: t('分组管理', 'Groups'), count: props.groupCount, path: `${classBasePath.value}/groups` },
  { id: 'tasks', label: t('任务管理', 'Tasks'), count: props.taskCount, path: `${classBasePath.value}/tasks` },
  { id: 'task-detail', label: t('任务详情', 'Task details'), count: null, path: null },
])

function isActive(tab) {
  if (tab.id === 'overview') return route.path === tab.path
  if (tab.id === 'tasks') return route.path === tab.path
  if (tab.id === 'task-detail') {
    return route.path.startsWith(`${classBasePath.value}/tasks/`) && route.path !== `${classBasePath.value}/tasks`
  }
  return route.path === tab.path || route.path.startsWith(`${tab.path}/`)
}

async function navigate(tab) {
  if (tab.id !== 'task-detail') {
    router.push(tab.path)
    return
  }

  const location = await resolveTeacherTaskDetailEntry(props.classId, props.activeTaskId || route.params.taskId)
  router.push(location)
}
</script>

<template>
  <section class="class-nav">
    <button
      v-for="item in tabs"
      :key="item.id"
      class="nav-tab"
      :class="{ active: isActive(item) }"
      type="button"
      @click="navigate(item)"
    >
      {{ item.label }}
      <span v-if="item.count != null" class="nav-count">{{ item.count }}</span>
    </button>
  </section>
</template>

<style scoped>
.class-nav {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--tt-space-1);
  padding: var(--tt-space-1);
  border: 1px solid var(--tt-border);
  border-radius: var(--tt-radius-lg);
  background: var(--tt-surface-muted);
}

.nav-tab {
  min-height: 40px;
  border: 1px solid transparent;
  border-radius: var(--tt-radius-md);
  padding: 0 var(--tt-space-3);
  background: transparent;
  color: var(--tt-text-secondary);
  font-family: var(--tt-font);
  font-size: var(--tt-text-sm);
  font-weight: 500;
  cursor: pointer;
  text-align: center;
  transition: all var(--tt-duration-fast) var(--tt-ease);
}

.nav-tab:hover {
  background: var(--tt-surface-hover);
  color: var(--tt-text);
}

.nav-tab.active {
  background: var(--tt-surface);
  color: var(--tt-accent);
  font-weight: 600;
  box-shadow: var(--tt-shadow-xs);
}

.nav-tab.active .nav-count {
  background: var(--tt-accent-soft);
  color: var(--tt-accent);
}

.nav-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 18px;
  margin-left: var(--tt-space-1);
  padding: 0 var(--tt-space-1);
  border-radius: var(--tt-radius-full);
  background: var(--tt-surface-active);
  color: var(--tt-text-secondary);
  font-size: var(--tt-text-xs);
  font-weight: 600;
  text-align: center;
}

@media (max-width: 1100px) {
  .class-nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .class-nav {
    grid-template-columns: 1fr;
  }
}
</style>
