<script setup>
defineProps({
  displayName: { type: String, default: '协作者' },
  dashboard: { type: Object, default: null },
  workflowSteps: { type: Array, default: () => [] },
  nextHomeAction: { type: Object, required: true },
  statusLabels: { type: Object, required: true },
  switchSection: { type: Function, required: true },
  openTaskContext: { type: Function, required: true },
  formatDate: { type: Function, required: true },
  activityTypeLabel: { type: Function, required: true },
})
</script>

<template>
  <section class="free-dashboard command-home">
    <article class="focus-panel dashboard-hero home-brief">
      <div class="section-head">
        <div>
          <span>{{ displayName }} 的自由协作工作台</span>
          <h2>{{ nextHomeAction.title }}</h2>
        </div>
        <button type="button" class="primary-btn" @click="switchSection(nextHomeAction.section)">
          {{ nextHomeAction.action }}
        </button>
      </div>
      <div class="metric-strip">
        <article>
          <strong>{{ dashboard?.myActiveTaskCount || 0 }}</strong>
          <span>我正在推进</span>
        </article>
        <article>
          <strong>{{ dashboard?.waitingForMeCount || 0 }}</strong>
          <span>等我接收</span>
        </article>
        <article>
          <strong>{{ dashboard?.dueSoonCount || 0 }}</strong>
          <span>临近截止</span>
        </article>
        <article>
          <strong>{{ dashboard?.activeProjectCount || 0 }}</strong>
          <span>活跃项目</span>
        </article>
      </div>
    </article>

    <article class="route-panel home-flow">
      <div class="section-head compact">
        <div>
          <span>协作路径</span>
          <h2>从组队到复盘</h2>
        </div>
      </div>
      <div class="route-steps">
        <button v-for="step in workflowSteps" :key="step.key" type="button" @click="switchSection(step.key)">
          <strong>{{ step.title }}</strong>
          <span>{{ step.text }}</span>
        </button>
      </div>
    </article>

    <article class="work-panel dashboard-card home-primary-list">
      <div class="section-head compact">
        <div>
          <span>我的推进队列</span>
          <h2>我负责交付的任务</h2>
        </div>
        <button type="button" class="text-btn" @click="switchSection('tasks')">去任务面板</button>
      </div>
      <div v-if="!dashboard?.myTasks?.length" class="empty-state">
        暂无待推进任务
      </div>
      <div v-else class="task-list">
        <button v-for="task in dashboard.myTasks" :key="task.id" type="button" class="task-row" @click="openTaskContext(task)">
          <strong>{{ task.title }}</strong>
          <span>{{ statusLabels[task.status] || task.status }} · 截止 {{ formatDate(task.dueAt) }}</span>
        </button>
      </div>
    </article>

    <article class="work-panel dashboard-card">
      <div class="section-head compact">
        <div>
          <span>交接队列</span>
          <h2>等待我接收</h2>
        </div>
      </div>
      <div v-if="!dashboard?.waitingForMe?.length" class="empty-state">
        暂无待接收任务
      </div>
      <div v-else class="task-list">
        <button v-for="task in dashboard.waitingForMe" :key="task.id" type="button" class="task-row urgent" @click="openTaskContext(task)">
          <strong>{{ task.title }}</strong>
          <span>{{ task.assigneeName || '成员' }} 已提交 · {{ formatDate(task.submittedAt) }}</span>
        </button>
      </div>
    </article>

    <article class="work-panel dashboard-card">
      <div class="section-head compact">
        <div>
          <span>时间风险</span>
          <h2>72 小时内截止</h2>
        </div>
        <button type="button" class="text-btn" @click="switchSection('progress')">看进度</button>
      </div>
      <div v-if="!dashboard?.dueSoonTasks?.length" class="empty-state">
        暂无临近截止
      </div>
      <div v-else class="task-list">
        <button v-for="task in dashboard.dueSoonTasks" :key="task.id" type="button" class="task-row warning" @click="openTaskContext(task)">
          <strong>{{ task.title }}</strong>
          <span>{{ task.assigneeName || '待认领' }} · {{ formatDate(task.dueAt) }}</span>
        </button>
      </div>
    </article>

    <article class="work-panel dashboard-card">
      <div class="section-head compact">
        <div>
          <span>最近推进</span>
          <h2>协作痕迹</h2>
        </div>
        <button type="button" class="text-btn" @click="switchSection('contributions')">查看全部</button>
      </div>
      <div v-if="!dashboard?.recentActivities?.length" class="empty-state">
        暂无协作痕迹
      </div>
      <div v-else class="activity-mini">
        <div v-for="log in dashboard.recentActivities" :key="log.id" class="activity-row">
          <i>{{ activityTypeLabel(log).slice(0, 1) }}</i>
          <div>
            <strong>{{ log.summary }}</strong>
            <span>{{ log.actorName || '系统' }} · {{ formatDate(log.createdAt) }}</span>
          </div>
        </div>
      </div>
    </article>
  </section>
</template>
