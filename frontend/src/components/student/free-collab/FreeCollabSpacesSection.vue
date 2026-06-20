<script setup>
defineProps({
  spaces: { type: Array, default: () => [] },
  selectedSpace: { type: Object, default: null },
  selectedSpaceId: { type: [Number, String, null], default: null },
  selectedMembers: { type: Array, default: () => [] },
  projects: { type: Array, default: () => [] },
  selectedProjectId: { type: [Number, String, null], default: null },
  canOrganize: { type: Boolean, default: false },
  isSubmitting: { type: Boolean, default: false },
  selectedSpaceStats: { type: Object, required: true },
  inviteStatus: { type: Object, required: true },
  copiedCode: { type: String, default: '' },
  createSpaceForm: { type: Object, required: true },
  joinForm: { type: Object, required: true },
  selectSpace: { type: Function, required: true },
  selectProject: { type: Function, required: true },
  submitCreateSpace: { type: Function, required: true },
  submitJoinSpace: { type: Function, required: true },
  generateInviteCode: { type: Function, required: true },
  copyInviteCode: { type: Function, required: true },
  avatarSrc: { type: Function, required: true },
  formatDate: { type: Function, required: true },
  switchSection: { type: Function, required: true },
})
</script>

<template>
  <section class="spaces-workspace space-redesign">
    <form class="form-panel create-space-band" @submit.prevent="submitCreateSpace">
      <div class="section-head compact">
        <div>
          <span>发起自由协作</span>
          <h2>创建一个新的团队空间</h2>
        </div>
        <button class="primary-btn" type="submit" :disabled="isSubmitting">创建空间</button>
      </div>
      <div class="create-space-grid">
        <input v-model="createSpaceForm.name" class="free-input" placeholder="空间名称" />
        <textarea
          v-model="createSpaceForm.description"
          class="free-textarea"
          placeholder="写清楚目标、规则、交付方向，成员加入后会先看到这里。"
        />
      </div>
    </form>

    <div class="space-main">
      <section v-if="selectedSpace" class="space-detail">
        <div class="space-title">
          <div>
            <span>{{ selectedSpace.myRole === 'OWNER' ? '我发起的空间' : '我加入的空间' }}</span>
            <h2>{{ selectedSpace.name }}</h2>
            <p>{{ selectedSpace.description || '暂未填写空间目标说明。' }}</p>
          </div>
        </div>

        <div class="space-stat-strip">
          <article>
            <strong>{{ selectedSpaceStats.memberCount }}</strong>
            <span>成员</span>
          </article>
          <article>
            <strong>{{ selectedSpaceStats.projectCount }}</strong>
            <span>项目</span>
          </article>
          <article>
            <strong>{{ selectedSpaceStats.taskCount }}</strong>
            <span>任务</span>
          </article>
          <article>
            <strong>{{ selectedSpaceStats.completionRate }}%</strong>
            <span>完成率</span>
          </article>
        </div>

        <div class="space-operating-grid">
          <div class="invite-band">
            <div>
              <span>当前邀请码</span>
              <strong>{{ selectedSpace.activeInviteCode || '未生成' }}</strong>
              <small>{{ inviteStatus.label }} · {{ inviteStatus.hint }}</small>
            </div>
            <div class="invite-actions">
              <button
                v-if="canOrganize"
                type="button"
                class="primary-btn"
                :disabled="isSubmitting"
                @click="generateInviteCode"
              >
                生成邀请码
              </button>
              <button
                type="button"
                class="secondary-btn"
                :disabled="!selectedSpace.activeInviteCode"
                @click="copyInviteCode(selectedSpace.activeInviteCode)"
              >
                {{ copiedCode === selectedSpace.activeInviteCode ? '已复制' : '复制口令' }}
              </button>
            </div>
          </div>

          <div class="space-next-card">
            <span>下一步</span>
            <h3>{{ projects.length ? '继续推进协作项目' : '先发布第一个协作项目' }}</h3>
            <p>
              {{
                projects.length
                  ? '进入任务面板查看认领、提交、接收和交接情况。'
                  : '创建项目后，团队成员就可以认领任务并开始推进。'
              }}
            </p>
            <button type="button" class="secondary-btn" @click="switchSection('tasks')">去任务面板</button>
          </div>
        </div>

        <div class="space-columns">
          <article>
            <div class="section-head compact">
              <div>
                <span>成员列表</span>
                <h2>{{ selectedMembers.length }} 人</h2>
              </div>
            </div>
            <div class="member-grid">
              <div v-for="member in selectedMembers" :key="member.studentId" class="member-row">
                <img v-if="avatarSrc(member)" :src="avatarSrc(member)" alt="" />
                <i v-else>{{ member.name?.slice(0, 1) || '协' }}</i>
                <div>
                  <strong>{{ member.name || '成员' }}</strong>
                  <span>{{ member.role === 'OWNER' ? '发起人' : '成员' }} · {{ formatDate(member.joinedAt) }}</span>
                </div>
              </div>
            </div>
          </article>

          <article>
            <div class="section-head compact">
              <div>
                <span>项目入口</span>
                <h2>{{ projects.length }} 个项目</h2>
              </div>
              <button type="button" class="text-btn" @click="switchSection('tasks')">去任务面板</button>
            </div>
            <div v-if="!projects.length" class="empty-state">
              这个空间还没有项目。发起人可以到任务面板发布第一个项目。
            </div>
            <div v-else class="project-list">
              <button
                v-for="project in projects"
                :key="project.id"
                type="button"
                :class="{ active: selectedProjectId === project.id }"
                @click="selectProject(project.id); switchSection('tasks')"
              >
                <strong>{{ project.title }}</strong>
                <span>{{ project.completedTaskCount || 0 }}/{{ project.taskCount || 0 }} 完成 · {{ formatDate(project.dueAt) }}</span>
              </button>
            </div>
          </article>
        </div>
      </section>

      <section v-else class="empty-state large">
        选择一个空间后，这里会显示邀请码、成员、项目入口和当前完成情况。
      </section>
    </div>

    <aside class="space-control">
      <section class="space-directory">
        <div class="section-head compact">
          <div>
            <span>空间切换</span>
            <h2>{{ spaces.length }} 个团队</h2>
          </div>
        </div>
        <div v-if="!spaces.length" class="empty-state">
          你还没有自由协作空间。先创建一个团队，或用邀请码加入别人的团队。
        </div>
        <div v-else class="space-list">
          <button
            v-for="space in spaces"
            :key="space.id"
            type="button"
            :class="{ active: selectedSpaceId === space.id }"
            @click="selectSpace(space.id)"
          >
            <strong>{{ space.name }}</strong>
            <span>
              {{ space.myRole === 'OWNER' ? '我发起' : '我加入' }} ·
              {{ space.memberCount || 0 }} 人 · {{ space.projectCount || 0 }} 项目
            </span>
          </button>
        </div>
      </section>

      <section class="space-actions compact-actions">
        <form class="form-panel" @submit.prevent="submitJoinSpace">
          <div class="section-head compact">
            <div>
              <span>加入团队</span>
              <h2>输入邀请码</h2>
            </div>
          </div>
          <input v-model="joinForm.inviteCode" class="free-input" placeholder="例如 TT-FREE-2026" />
          <button class="secondary-btn" type="submit" :disabled="isSubmitting">加入空间</button>
        </form>
      </section>
    </aside>
  </section>
</template>
