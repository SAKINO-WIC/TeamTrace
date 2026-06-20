<script setup>
defineProps({
  activityLogs: { type: Array, default: () => [] },
  selectedSpace: { type: Object, default: null },
  selectedProject: { type: Object, default: null },
  activityFilter: { type: String, default: 'ALL' },
  activityFilterOptions: { type: Array, default: () => [] },
  activityTypeStats: { type: Array, default: () => [] },
  recentActivityLogs: { type: Array, default: () => [] },
  groupedActivityLogs: { type: Array, default: () => [] },
  filteredActivityLogs: { type: Array, default: () => [] },
  setActivityFilter: { type: Function, required: true },
  loadActivityLogs: { type: Function, required: true },
  activityTypeLabel: { type: Function, required: true },
  activityActionLabel: { type: Function, required: true },
  activityMeta: { type: Function, required: true },
  resolveActivityTask: { type: Function, required: true },
  openTaskContext: { type: Function, required: true },
  switchSection: { type: Function, required: true },
  formatDate: { type: Function, required: true },
})

function openLogContext(log, resolveActivityTask, openTaskContext, switchSection) {
  const task = resolveActivityTask(log)
  if (task) {
    openTaskContext(task)
    return
  }
  switchSection('tasks')
}
</script>

<template>
  <section class="trace-page">
    <section class="trace-cockpit">
      <div>
        <span>协作痕迹</span>
        <h2>谁在什么时候推进了什么</h2>
        <p>{{ selectedSpace?.name || '未选择空间' }} · {{ selectedProject?.title || '全部项目' }}</p>
      </div>
      <div class="trace-counters">
        <article>
          <strong>{{ activityLogs.length }}</strong>
          <span>全部记录</span>
        </article>
        <article>
          <strong>{{ filteredActivityLogs.length }}</strong>
          <span>当前筛选</span>
        </article>
        <article>
          <strong>{{ activityTypeStats.length }}</strong>
          <span>动作类型</span>
        </article>
      </div>
      <button type="button" class="secondary-btn" @click="loadActivityLogs">刷新痕迹</button>
    </section>

    <section class="trace-review-strip">
      <article>
        <span>复盘重点</span>
        <strong>{{ recentActivityLogs.length ? activityActionLabel(recentActivityLogs[0]) : '暂无动作' }}</strong>
        <p>{{ recentActivityLogs.length ? recentActivityLogs[0].summary : '协作开始后，关键动作会自动沉淀在这里。' }}</p>
      </article>
      <article>
        <span>最近推进</span>
        <strong>{{ recentActivityLogs.length }}</strong>
        <p>展示当前筛选下最近的协作动作，用来快速确认谁刚刚推动了任务。</p>
      </article>
      <article>
        <span>当前范围</span>
        <strong>{{ selectedProject?.title || selectedSpace?.name || '全部协作' }}</strong>
        <p>可通过顶部空间和项目选择器切换复盘范围。</p>
      </article>
    </section>

    <section class="trace-layout redesigned">
      <aside class="trace-filter">
        <div class="section-head compact">
          <div>
            <span>筛选</span>
            <h2>按动作查看</h2>
          </div>
        </div>
        <div class="filter-list compact">
          <button
            v-for="option in activityFilterOptions"
            :key="option.key"
            type="button"
            :class="{ active: activityFilter === option.key }"
            @click="setActivityFilter(option.key)"
          >
            <span>{{ option.label }}</span>
            <strong v-if="option.key === 'ALL'">{{ activityLogs.length }}</strong>
          </button>
        </div>
        <div v-if="activityTypeStats.length" class="trace-stats compact">
          <button v-for="stat in activityTypeStats" :key="stat.key" type="button" @click="setActivityFilter(stat.key)">
            <span>{{ stat.label }}</span>
            <strong>{{ stat.count }}</strong>
          </button>
        </div>
      </aside>

      <section class="trace-main">
        <div class="section-head">
          <div>
            <span>{{ activityFilter === 'ALL' ? '全部记录' : activityFilterOptions.find((item) => item.key === activityFilter)?.label }}</span>
            <h2>时间线</h2>
          </div>
        </div>

        <div v-if="!activityLogs.length" class="empty-state large">
          还没有协作痕迹。创建空间、发布项目、认领和提交都会在这里留下记录。
        </div>
        <div v-else-if="!filteredActivityLogs.length" class="empty-state large">
          当前筛选条件下没有记录。
        </div>
        <div v-else class="timeline grouped rich">
          <section v-for="group in groupedActivityLogs" :key="group.date" class="timeline-day">
            <header>
              <span>{{ group.date }}</span>
              <strong>{{ group.logs.length }} 条</strong>
            </header>
            <article v-for="log in group.logs" :key="log.id">
              <i>{{ activityTypeLabel(log).slice(0, 1) }}</i>
              <div>
                <div class="timeline-meta">
                  <span>{{ activityActionLabel(log) }}</span>
                  <em>{{ activityMeta(log) }}</em>
                </div>
                <h3>{{ log.summary }}</h3>
                <div class="timeline-foot">
                  <p>{{ log.actorName || '系统' }} · {{ formatDate(log.createdAt) }}</p>
                  <button type="button" class="text-btn" @click="openLogContext(log, resolveActivityTask, openTaskContext, switchSection)">
                    {{ resolveActivityTask(log) ? '查看相关任务' : '去任务面板' }}
                  </button>
                </div>
              </div>
            </article>
          </section>
        </div>
      </section>
    </section>
  </section>
</template>
