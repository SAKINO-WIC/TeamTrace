<script setup>
import '../styles/admin-workspace.css'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAdminUsers } from '../services/admin'
import { getDisplayAccount, getTokenPayload } from '../utils/auth'

const router = useRouter()
const userSnapshot = ref({
  total: 0,
  admin: 0,
  teacher: 0,
  student: 0,
})
const snapshotLoading = ref(false)
const snapshotMessage = ref('')

const adminProfile = computed(() => {
  const payload = getTokenPayload() || {}
  return {
    account: getDisplayAccount(null, payload),
    name: payload?.name || '管理员',
  }
})

const metrics = computed(() => [
  { title: '账户总数', value: `${userSnapshot.value.total}` },
  { title: '教师', value: `${userSnapshot.value.teacher}` },
  { title: '学生', value: `${userSnapshot.value.student}` },
])

const modules = [
  {
    title: '教师邀请码',
    desc: '生成、筛选、停用、恢复和删除教师注册邀请码。',
    action: '进入',
    to: '/admin/invites',
  },
  {
    title: '账户管理',
    desc: '查看用户状态，处理教师账户、密码重置和账号状态。',
    action: '进入',
    to: '/admin/users',
  },
  {
    title: '安全设置',
    desc: '维护当前管理员自己的登录密码和安全信息。',
    action: '进入',
    to: '/admin/security',
  },
]

const roleCards = computed(() => [
  { label: '管理员', value: userSnapshot.value.admin },
  { label: '教师', value: userSnapshot.value.teacher },
  { label: '学生', value: userSnapshot.value.student },
])

async function loadSnapshot() {
  snapshotLoading.value = true
  snapshotMessage.value = ''
  try {
    const [allUsers, adminUsers, teacherUsers, studentUsers] = await Promise.all([
      fetchAdminUsers({ page: 1, size: 1 }),
      fetchAdminUsers({ page: 1, size: 1, role: 'admin' }),
      fetchAdminUsers({ page: 1, size: 1, role: 'teacher' }),
      fetchAdminUsers({ page: 1, size: 1, role: 'student' }),
    ])

    const allPayload = allUsers?.data?.data || {}
    userSnapshot.value = {
      total: Number(allPayload.total) || 0,
      admin: Number(adminUsers?.data?.data?.total) || 0,
      teacher: Number(teacherUsers?.data?.data?.total) || 0,
      student: Number(studentUsers?.data?.data?.total) || 0,
    }
  } catch (error) {
    snapshotMessage.value = error?.response?.data?.message || error.message || '加载实时统计失败'
  } finally {
    snapshotLoading.value = false
  }
}

onMounted(loadSnapshot)
</script>

<template>
  <div class="admin-page">
    <section class="admin-hero admin-home-hero">
      <div class="admin-hero__meta">
        <p class="admin-eyebrow">Workspace</p>
        <span class="admin-chip">Admin</span>
      </div>
      <h2 class="admin-hero__title">管理员工作台</h2>
      <p class="admin-hero__desc">
        当前管理员端优先围绕账户、教师邀请码、安全与监控展开。首页只保留高频入口和实时账户快照，减少来回跳转时的认知负担。
      </p>
      <div class="admin-actions">
        <button type="button" class="admin-btn" @click="router.push('/admin/invites')">管理邀请码</button>
        <button type="button" class="admin-btn-secondary" @click="router.push('/admin/users')">账户管理</button>
        <button type="button" class="admin-btn-ghost" @click="router.push('/admin/monitor')">系统监控</button>
      </div>
    </section>

    <section class="admin-stats-grid">
      <article v-for="item in metrics" :key="item.title" class="admin-stat-card">
        <p class="admin-stat-card__label">{{ item.title }}</p>
        <p class="admin-stat-card__value">{{ item.value }}</p>
      </article>
    </section>

    <section class="admin-grid-2">
      <article class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <p class="admin-section-label">Core modules</p>
            <h3 class="admin-panel__title">核心模块</h3>
            <p class="admin-panel__desc">管理员端先服务系统维护，不介入教师和学生的日常评分流程。</p>
          </div>
        </div>
        <div class="module-list">
          <article v-for="item in modules" :key="item.to" class="admin-mini-card module-card">
            <div>
              <h4>{{ item.title }}</h4>
              <p>{{ item.desc }}</p>
            </div>
            <button type="button" class="admin-link-btn module-link" @click="router.push(item.to)">{{ item.action }}</button>
          </article>
        </div>
      </article>

      <article class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <p class="admin-section-label">Live snapshot</p>
            <h3 class="admin-panel__title">实时账户快照</h3>
          </div>
          <button type="button" class="admin-btn-ghost" :disabled="snapshotLoading" @click="loadSnapshot">
            {{ snapshotLoading ? '刷新中...' : '刷新' }}
          </button>
        </div>
        <div class="admin-kpi-stack">
          <article v-for="item in roleCards" :key="item.label" class="admin-mini-card kpi-row">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>
        <p v-if="snapshotMessage" class="admin-message error">{{ snapshotMessage }}</p>
      </article>
    </section>

    <section class="admin-grid-2 compact-grid">
      <article class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <p class="admin-section-label">Profile</p>
            <h3 class="admin-panel__title">当前管理员</h3>
          </div>
          <button type="button" class="admin-btn-ghost" @click="router.push('/admin/security')">安全设置</button>
        </div>
        <article class="admin-mini-card profile-row">
          <div>
            <p class="mini-code">{{ adminProfile.name }}</p>
            <p class="admin-muted-copy">登录账号：{{ adminProfile.account }}</p>
          </div>
          <span class="admin-status-badge success">真实登录</span>
        </article>
      </article>
    </section>
  </div>
</template>

<style scoped>
.admin-home-hero {
  padding-bottom: 22px;
}

.module-list {
  display: grid;
  gap: 12px;
}

.module-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}

.module-card h4 {
  margin: 0;
  color: var(--admin-text-primary);
  font-size: 17px;
}

.module-card p {
  margin: 6px 0 0;
  color: var(--admin-text-secondary);
  line-height: 1.55;
}

.module-link {
  color: var(--admin-accent);
  white-space: nowrap;
}

.kpi-row,
.profile-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}

.kpi-row span {
  color: var(--admin-text-secondary);
}

.kpi-row strong {
  color: var(--admin-text-primary);
  font-size: 22px;
}

.mini-code {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--admin-text-primary);
}

.compact-grid {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

@media (max-width: 720px) {
  .module-card,
  .profile-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .compact-grid {
    grid-template-columns: 1fr;
  }
}
</style>
