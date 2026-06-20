<script setup>
import EmptyState from '../components/common/EmptyState.vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  approveGroupMember,
  createStudentGroup,
  fetchGroupJoinPending,
  fetchStudentClassDetail,
  fetchStudentClassGroups,
  joinStudentGroup,
  refreshGroupInviteCode,
  rejectGroupMember,
  removeGroupMember,
} from '../services/student'
import { getActiveSession, getCurrentUserId } from '../utils/auth'
import { readSessionCache } from '../utils/sessionCache'
import { loadWithStaleSessionCache } from '../utils/staleSessionLoad'
import MemberDetailPopup from '../components/student/MemberDetailPopup.vue'
import { useStudentLocale } from '../composables/useStudentLocale'
import { copyText } from '../utils/admin'

const route = useRoute()
const router = useRouter()
const { tm, t } = useStudentLocale()
const classId = route.params.classId

const loading = ref(true)
const detailLoadError = ref('')
const groupLoadError = ref('')
const pendingLoadError = ref('')
const detail = ref(null)
const groups = ref([])
const message = ref('')
const messageType = ref('info')

const showCreateDialog = ref(false)
const createForm = ref({ name: '', joinMode: 1, inviteCodeExpireMinutes: 1440 })
const creating = ref(false)
const createdInvite = ref(null)

const showJoinDialog = ref(false)
const joinCode = ref('')
const joining = ref(false)
const joinError = ref('')

const pendingList = ref([])
const loadingPending = ref(false)
const latestInvite = ref(null)
const removeTarget = ref(null)
const removingMemberId = ref('')
const selectedMember = ref(null)

const currentUserId = computed(() => getCurrentUserId())

const myGroup = computed(() => {
  if (!detail.value?.groupId) return null
  return groups.value.find((group) => String(group.groupId) === String(detail.value.groupId)) || null
})

const isLeader = computed(() => {
  if (!myGroup.value || currentUserId.value === null) return false
  return Number(myGroup.value.leaderId) === Number(currentUserId.value)
})

const canManage = computed(() => {
  if (isLeader.value) return true
  if (!detail.value || currentUserId.value === null) return false
  return Number(detail.value.teacherId) === Number(currentUserId.value)
})

const canCreateOrJoin = computed(() => {
  if (!detail.value) return false
  if (detail.value.groupingLocked) return false
  if (detail.value.groupId) return false
  return true
})

const memberRows = computed(() => {
  if (!myGroup.value) return []
  const ids = Array.isArray(myGroup.value.memberStudentIds) ? myGroup.value.memberStudentIds : []

  const memberNames = myGroup.value.memberNames || {}

  return ids.map((studentId, index) => {
    const numericId = Number(studentId)
    const leader = Number(myGroup.value.leaderId) === numericId
    const self = currentUserId.value !== null && Number(currentUserId.value) === numericId
    const nameFromMap = memberNames[numericId]
    const userName = getActiveSession()?.user?.name || ''
    const displayName = self ? (userName || nameFromMap || `成员 ${numericId}`) : (nameFromMap || `成员 ${numericId}`)

    return {
      key: `${studentId}-${index}`,
      studentId: numericId,
      id: numericId,
      title: displayName,
      name: displayName,
      role: leader ? '组长' : '组员',
      caption: self ? `学号 ${numericId}` : `成员 ID ${numericId}`,
      groupName: myGroup.value.name || '',
      self,
      leader,
      isLeader: leader,
      removable: canManage.value && !leader && !self,
    }
  })
})

const inviteCodeText = computed(() => {
  if (latestInvite.value?.code) return latestInvite.value.code
  if (myGroup.value?.inviteCode) return myGroup.value.inviteCode
  if (createdInvite.value?.code) return createdInvite.value.code
  return ''
})

const inviteExpireText = computed(() => {
  return latestInvite.value?.expireAt
    || myGroup.value?.inviteCodeExpireAt
    || myGroup.value?.inviteCodeExpire
    || createdInvite.value?.expireAt
    || ''
})

const inviteDisplayNote = computed(() => {
  if (!inviteCodeText.value || !inviteExpireText.value) return ''
  return `有效期至：${formatDateTime(inviteExpireText.value)}`
})

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
  if (type === 'success') {
    setTimeout(() => {
      if (message.value === text) {
        message.value = ''
      }
    }, 3000)
  }
}

function resetCreateDialogState() {
  createForm.value = { name: '', joinMode: 1, inviteCodeExpireMinutes: 1440 }
  createdInvite.value = null
}

function clearIntentQuery() {
  if (!route.query.action) return
  router.replace({ path: route.path })
}

function closeCreateDialog() {
  showCreateDialog.value = false
  clearIntentQuery()
}

function closeJoinDialog() {
  showJoinDialog.value = false
  clearIntentQuery()
}

function closeRemoveConfirm() {
  if (removingMemberId.value) return
  removeTarget.value = null
}

function openCreateDialog() {
  resetCreateDialogState()
  showCreateDialog.value = true
}

function openJoinDialog() {
  joinCode.value = ''
  joinError.value = ''
  showJoinDialog.value = true
}

function openRemoveConfirm(member) {
  if (!member?.removable) {
    setMessage('只能由组长或教师移除普通组员，不能移除自己或组长。', 'error')
    return
  }
  removeTarget.value = member
}

function openMemberPopup(member) {
  selectedMember.value = member
}

function closeMemberPopup() {
  selectedMember.value = null
}

function goBackToClassDetail() {
  router.push({
    path: `/student/classes/${classId}`,
    query: { tab: 'groups' },
  })
}

function openGroupWorkspaceByIntent() {
  if (route.query.action === 'create' && canCreateOrJoin.value) {
    openCreateDialog()
    return
  }

  if (route.query.action === 'join' && canCreateOrJoin.value) {
    openJoinDialog()
  }
}

async function loadPending() {
  pendingLoadError.value = ''
  if (!myGroup.value || !canManage.value) {
    pendingList.value = []
    return
  }

  loadingPending.value = true
  try {
    const { data } = await fetchGroupJoinPending(classId, myGroup.value.groupId)
    pendingList.value = data?.data || []
  } catch (error) {
    pendingList.value = []
    pendingLoadError.value = error?.message || '入组申请列表暂时不可用，请稍后刷新。'
  } finally {
    loadingPending.value = false
  }
}

function groupsHubCacheKey() {
  return `student:class:${classId}:groups-hub`
}

function applyGroupsHubPayload(payload) {
  detail.value = payload?.detail || null
  groups.value = Array.isArray(payload?.groups) ? payload.groups : []
}

async function loadData(options = {}) {
  detailLoadError.value = ''
  groupLoadError.value = ''
  pendingLoadError.value = ''
  const hadCacheBefore = Boolean(readSessionCache(groupsHubCacheKey(), 180000))
  loading.value = !hadCacheBefore

  try {
    const { error } = await loadWithStaleSessionCache({
      cacheKey: groupsHubCacheKey(),
      ttlMs: 180000,
      force: options.force === true,
      apply: applyGroupsHubPayload,
      fetchFresh: async () => {
        const [detailRes, groupRes] = await Promise.allSettled([
          fetchStudentClassDetail(classId),
          fetchStudentClassGroups(classId),
        ])
        if (detailRes.status === 'rejected') {
          throw detailRes.reason
        }
        const nextDetail = detailRes.value?.data?.data || null
        if (!nextDetail) {
          throw new Error('未找到班级信息，或你暂时没有访问该班级的权限。')
        }
        const nextGroups =
          groupRes.status === 'fulfilled' && Array.isArray(groupRes.value?.data?.data)
            ? groupRes.value.data.data
            : []
        if (groupRes.status === 'rejected') {
          groupLoadError.value = groupRes.reason?.message || '班级小组暂时不可用。'
        }
        return { detail: nextDetail, groups: nextGroups }
      },
    })
    if (error && !detail.value) {
      detailLoadError.value = error?.message || '班级小组信息加载失败，请稍后重试。'
    }
    await loadPending()
  } catch (error) {
    if (!detail.value) {
      detail.value = null
      groups.value = []
      pendingList.value = []
      detailLoadError.value = error?.message || '班级小组信息加载失败，请稍后重试。'
    }
  } finally {
    loading.value = false
  }
}

async function handleCreateGroup() {
  const name = String(createForm.value.name || '').trim()
  if (!name) {
    setMessage('请输入小组名称。', 'error')
    return
  }

  creating.value = true
  try {
    const { data } = await createStudentGroup(classId, createForm.value)
    const payload = data?.data || {}
    createdInvite.value = {
      code: payload?.inviteCode || '',
      expireAt: payload?.inviteCodeExpireAt || '',
    }
    latestInvite.value = createdInvite.value
    setMessage('小组创建成功，邀请码已生成。', 'success')
    await loadData()
    clearIntentQuery()
  } catch (error) {
    setMessage(error.message || '创建失败。', 'error')
  } finally {
    creating.value = false
  }
}

async function handleJoinGroup() {
  const code = String(joinCode.value || '').trim()
  if (!code) {
    joinError.value = '请输入小组邀请码'
    return
  }

  joining.value = true
  joinError.value = ''
  try {
    const response = await joinStudentGroup(classId, code)
    const payload = response?.data?.data || response?.data || {}
    const status = String(payload?.membershipStatus || payload?.status || '').toLowerCase()
    closeJoinDialog()
    joinCode.value = ''
    setMessage(status === 'active' || status === 'joined' ? '已成功加入小组。' : '加入申请已提交。', 'success')
    await loadData()
  } catch (error) {
    joinError.value = error.message || '加入失败'
  } finally {
    joining.value = false
  }
}

async function handleApprove(userId) {
  try {
    await approveGroupMember(classId, myGroup.value.groupId, userId)
    setMessage('已通过该入组申请。', 'success')
    await loadPending()
    await loadData()
  } catch (error) {
    setMessage(error.message || '操作失败', 'error')
  }
}

async function handleReject(userId) {
  try {
    await rejectGroupMember(classId, myGroup.value.groupId, userId)
    setMessage('已拒绝该入组申请。', 'success')
    await loadPending()
  } catch (error) {
    setMessage(error.message || '操作失败', 'error')
  }
}

async function handleRemoveMember() {
  const target = removeTarget.value
  if (!target?.studentId || !myGroup.value?.groupId) {
    setMessage('未找到可移除的组员。', 'error')
    return
  }
  if (!target.removable) {
    setMessage('只能移除普通组员，不能移除自己或组长。', 'error')
    return
  }

  removingMemberId.value = String(target.studentId)
  try {
    await removeGroupMember(classId, myGroup.value.groupId, target.studentId)
    setMessage(`已将 ${target.title} 移出小组。`, 'success')
    removeTarget.value = null
    await loadData()
  } catch (error) {
    setMessage(error.message || '移除组员失败，请稍后重试。', 'error')
  } finally {
    removingMemberId.value = ''
  }
}

async function handleRefreshCode() {
  try {
    const { data } = await refreshGroupInviteCode(classId, myGroup.value.groupId)
    const payload = data?.data || {}
    latestInvite.value = {
      code: payload?.inviteCode || '',
      expireAt: payload?.inviteCodeExpireAt || '',
    }
    setMessage('')
  } catch (error) {
    setMessage(error.message || '刷新失败', 'error')
  }
}

async function copyInviteCode() {
  const code = String(inviteCodeText.value || '').replace(/\s+/g, '').trim()
  if (!code) {
    setMessage(tm('groups.noInviteToCopy'), 'error')
    return
  }
  try {
    const ok = await copyText(code)
    if (ok) {
      setMessage(tm('groups.inviteCopied'), 'success')
    } else {
      setMessage(tm('groups.copyInviteFailed'), 'error')
    }
  } catch {
    setMessage(tm('groups.copyInviteFailed'), 'error')
  }
}

watch(
  () => route.query.action,
  () => {
    openGroupWorkspaceByIntent()
  },
)

onMounted(async () => {
  await loadData()
  openGroupWorkspaceByIntent()
})
</script>

<template>
  <div class="student-page student-class-groups">
    <div v-if="loading" class="loading-state">
      <div v-for="i in 3" :key="i" class="skeleton-card" />
    </div>

    <section v-else-if="detailLoadError" class="card state-panel error-panel">
      <p class="section-kicker">我的小组</p>
      <h2>小组信息加载失败</h2>
      <p class="section-note">{{ detailLoadError }}</p>
      <div class="section-actions">
        <button class="neutral-btn" type="button" @click="goBackToClassDetail">返回班级详情</button>
      </div>
    </section>

    <template v-else-if="detail">
      <p v-if="message" class="message" :class="messageType">{{ message }}</p>
      <p v-if="groupLoadError || pendingLoadError" class="message info">
        {{ [groupLoadError, pendingLoadError].filter(Boolean).join('；') }}
      </p>

      <section v-if="myGroup" class="group-workspace">
        <article class="card group-hero">
          <div class="group-hero-main">
            <h3>{{ myGroup.name }}</h3>
          </div>
        </article>

        <div class="group-main-grid" :class="{ 'group-main-grid--solo': !canManage }">
        <article class="card invite-card">
          <div class="panel-head">
            <div>
              <h3>小组邀请码</h3>
            </div>
            <div class="invite-head-actions">
              <button
                class="copy-btn"
                type="button"
                :disabled="!inviteCodeText"
                @click="copyInviteCode"
              >
                {{ tm('groups.copyInvite') }}
              </button>
              <button v-if="canManage" class="refresh-btn" type="button" @click="handleRefreshCode">
                刷新邀请码
              </button>
            </div>
          </div>

          <div class="invite-command">
            <div class="invite-box">
              <strong class="invite-code">{{ inviteCodeText || '暂未显示' }}</strong>
              <span class="invite-expire">{{ inviteDisplayNote }}</span>
            </div>
          </div>
        </article>

        <article v-if="canManage" class="card pending-card">
          <div class="panel-head">
            <div>
              <h3>入组审批</h3>
            </div>
          </div>

          <div class="pending-body">
            <div v-if="loadingPending" class="loading-inline">审批列表加载中...</div>
            <div v-else-if="pendingLoadError" class="empty-card compact-empty error-empty">
              {{ pendingLoadError }}
            </div>
            <div v-else-if="pendingList.length" class="pending-list">
              <div v-for="userId in pendingList" :key="userId" class="pending-row">
                <div>
                  <p class="pending-title">待审核成员</p>
                  <p class="pending-id">用户 {{ userId }}</p>
                </div>
                <div class="pending-actions">
                  <button class="approve-btn" type="button" @click="handleApprove(userId)">通过</button>
                  <button class="reject-btn" type="button" @click="handleReject(userId)">拒绝</button>
                </div>
              </div>
            </div>
            <div v-else class="empty-card compact-empty">当前没有待审批申请</div>
          </div>
        </article>
        </div>

        <section class="card members-card">
          <div class="panel-head list-panel-head">
            <div>
              <h3>成员列表</h3>
            </div>
            <span class="member-count">{{ memberRows.length }} 人</span>
          </div>
          <div class="member-list-scroll">
            <div class="member-list">
              <article
                v-for="member in memberRows"
                :key="member.key"
                class="member-row"
                @click="openMemberPopup(member)"
              >
                <div class="member-row-main">
                  <div class="member-row-avatar">{{ member.title.slice(0, 1) }}</div>
                  <div class="member-row-copy">
                    <p class="member-row-name">{{ member.title }}</p>
                    <p class="member-row-meta">{{ member.caption }}</p>
                  </div>
                </div>
                <div class="member-row-actions">
                  <span class="member-badge" :class="{ leader: member.leader, self: member.self }">{{ member.role }}</span>
                  <button
                    v-if="member.removable"
                    class="text-danger-btn"
                    type="button"
                    :disabled="removingMemberId === String(member.studentId)"
                    @click.stop="openRemoveConfirm(member)"
                  >
                    {{ removingMemberId === String(member.studentId) ? '移除中...' : '移除' }}
                  </button>
                </div>
              </article>
              <p v-if="!memberRows.length" class="empty-card compact-empty">当前小组没有成员。</p>
            </div>
          </div>
        </section>
      </section>

      <section v-else class="group-shell">
        <article class="card empty-group-card">
          <EmptyState
            v-if="canCreateOrJoin"
            variant="panel"
            icon="group"
            kicker="我的小组"
            title="你还没有加入任何小组"
            description=""
            :action-label="tm('groups.createGroup')"
            :secondary-label="tm('groups.enterInviteJoin')"
            @action="openCreateDialog"
            @secondary="openJoinDialog"
          />
          <EmptyState
            v-else
            variant="panel"
            icon="group"
            kicker="我的小组"
            title="暂时无法加入小组"
            description=""
            compact
          />
        </article>
      </section>
    </template>

    <!-- Member Detail Popup -->
    <MemberDetailPopup :member="selectedMember" @close="closeMemberPopup" />

    <!-- Create Group Dialog -->
    <Teleport to="body">
      <div v-if="showCreateDialog" class="dialog-overlay" @click.self="closeCreateDialog">
        <section class="dialog-panel">
          <div class="panel-head panel-head--title-only">
            <div>
              <p class="section-kicker">我的小组</p>
              <h3>{{ createdInvite ? t('小组创建成功', 'Group Created') : tm('groups.createGroup') }}</h3>
            </div>
            <button
              v-if="!createdInvite"
              class="neutral-btn"
              type="button"
              @click="closeCreateDialog"
            >
              {{ tm('common.close') }}
            </button>
          </div>

          <div v-if="createdInvite" class="dialog-success">
            <div class="invite-box success-box">
              <span class="invite-label">{{ tm('groups.inviteCode') }}</span>
              <strong class="invite-code">{{ createdInvite.code || '暂未返回' }}</strong>
              <span class="invite-expire">有效期至：{{ formatDateTime(createdInvite.expireAt) }}</span>
            </div>
            <div class="dialog-actions dialog-actions--footer">
              <button class="neutral-btn" type="button" @click="closeCreateDialog">{{ tm('common.close') }}</button>
              <button class="primary-btn" type="button" @click="closeCreateDialog">{{ tm('common.confirm') }}</button>
            </div>
          </div>

          <div v-else class="dialog-body">
            <label class="field">
              <span>{{ tm('groups.groupName') }}</span>
              <input v-model.trim="createForm.name" class="dialog-input" type="text" maxlength="100" placeholder="请输入小组名称" />
            </label>
            <label class="field">
              <span>加入模式</span>
              <div class="radio-grid">
                <label class="radio-item">
                  <input v-model="createForm.joinMode" :value="1" type="radio" />
                  <div>
                    <strong>直通模式</strong>
                    <span>成员输入邀请码后直接加入</span>
                  </div>
                </label>
                <label class="radio-item">
                  <input v-model="createForm.joinMode" :value="2" type="radio" />
                  <div>
                    <strong>审批模式</strong>
                    <span>成员提交申请后需组长审批</span>
                  </div>
                </label>
              </div>
            </label>
            <label class="field">
              <span>邀请码有效期</span>
              <select v-model.number="createForm.inviteCodeExpireMinutes" class="dialog-select">
                <option :value="720">12小时</option>
                <option :value="1440">24小时</option>
                <option :value="4320">3天</option>
                <option :value="10080">7天</option>
              </select>
            </label>
            <div class="dialog-actions">
              <button class="neutral-btn" type="button" @click="closeCreateDialog">取消</button>
              <button class="primary-btn" type="button" :disabled="creating" @click="handleCreateGroup">
                {{ creating ? '创建中...' : '确认创建' }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </Teleport>

    <!-- Join Group Dialog -->
    <Teleport to="body">
      <div v-if="showJoinDialog" class="dialog-overlay" @click.self="closeJoinDialog">
        <section class="dialog-panel">
          <div class="panel-head">
            <div>
              <p class="section-kicker">我的小组</p>
              <h3>加入小组</h3>
            </div>
            <button class="neutral-btn" type="button" @click="closeJoinDialog">关闭</button>
          </div>

          <div class="dialog-body">
            <label class="field">
              <span>小组邀请码</span>
              <input
                v-model.trim="joinCode"
                class="dialog-input"
                :class="{ error: joinError }"
                type="text"
                placeholder="请输入小组邀请码"
                @input="joinError = ''"
                @keyup.enter="handleJoinGroup"
              />
            </label>
            <p v-if="joinError" class="field-error">{{ joinError }}</p>
            <div class="dialog-actions">
              <button class="neutral-btn" type="button" @click="closeJoinDialog">取消</button>
              <button class="primary-btn" type="button" :disabled="joining" @click="handleJoinGroup">
                {{ joining ? '提交中...' : '确认加入' }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </Teleport>

    <!-- Remove Member Confirmation Dialog -->
    <Teleport to="body">
      <div v-if="removeTarget" class="dialog-overlay" @click.self="closeRemoveConfirm">
        <section class="dialog-panel confirm-panel">
          <div class="panel-head">
            <div>
              <p class="section-kicker">管理操作</p>
              <h3>{{ tm('groups.removeMember') }}</h3>
            </div>
            <button class="neutral-btn" type="button" :disabled="Boolean(removingMemberId)" @click="closeRemoveConfirm">{{ tm('common.close') }}</button>
          </div>

          <div class="confirm-body">
            <p class="confirm-title">确定将 {{ removeTarget.title }} 移出小组？</p>
          </div>

          <div class="dialog-actions">
            <button class="neutral-btn" type="button" :disabled="Boolean(removingMemberId)" @click="closeRemoveConfirm">{{ tm('common.cancel') }}</button>
            <button class="danger-btn" type="button" :disabled="Boolean(removingMemberId)" @click="handleRemoveMember">
              {{ removingMemberId ? tm('common.submitting') : tm('common.remove') }}
            </button>
          </div>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.student-class-groups {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - var(--tt-header-height) - 40px);
  padding-bottom: 24px;
}

.group-shell,
.topbar,
.pending-list,
.dialog-body,
.dialog-success,
.field,
.radio-grid {
  display: grid;
  gap: 18px;
}

.topbar,
.overview-card,
.group-hero,
.invite-card,
.pending-card,
.members-card {
  padding: 22px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow:
    0 18px 40px rgba(15, 23, 42, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(14px);
}

.empty-group-card {
  padding: 0;
  overflow: hidden;
  border: 1px solid rgba(59, 130, 246, 0.12);
  border-radius: 22px;
  background:
    radial-gradient(circle at 100% 0%, rgba(59, 130, 246, 0.08), transparent 42%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(248, 250, 252, 0.88));
  box-shadow:
    0 18px 40px rgba(15, 23, 42, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(14px);
}

.topbar-main,
.topbar-actions,
.section-head,
.panel-head,
.pending-row,
.pending-actions,
.dialog-actions,
.section-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.topbar-main,
.topbar-actions,
.section-head,
.panel-head,
.pending-actions,
.dialog-actions {
  align-items: flex-start;
}

.pending-row,
.section-actions {
  align-items: center;
}

.topbar-main h2,
.section-head h3,
.panel-head h3,
.group-hero h3,
.invite-card h3,
.pending-card h3,
.members-card h3 {
  margin: 0;
}

.eyebrow,
.section-kicker {
  margin: 0;
}

.topbar-note,
.section-note,
.message,
.member-note,
.invite-expire,
.pending-title,
.pending-id,
.field-hint,
.loading-inline {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--student-text-secondary);
}

.message.success {
  color: var(--student-success);
}

.message.info {
  color: var(--student-accent);
}

.message.error {
  color: var(--student-danger);
}

.topbar-actions,
.pending-actions,
.dialog-actions,
.section-actions {
  flex-wrap: wrap;
}

.dialog-actions--footer {
  margin-top: 8px;
  align-items: center;
}

.dialog-actions--footer .primary-btn {
  margin-left: auto;
}

.panel-head--title-only {
  align-items: center;
}

.group-workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.group-hero {
  display: flex;
  align-items: center;
  padding: 28px 32px;
  border-color: rgba(59, 130, 246, 0.14);
  background:
    radial-gradient(circle at 0% 0%, rgba(59, 130, 246, 0.14), transparent 42%),
    radial-gradient(circle at 100% 100%, rgba(99, 102, 241, 0.1), transparent 38%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.96) 0%, rgba(239, 246, 255, 0.92) 100%);
}

.group-hero-main {
  display: grid;
  gap: 14px;
  width: 100%;
}

.group-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 16px;
  min-height: clamp(240px, 34vh, 360px);
}

.group-main-grid--solo {
  grid-template-columns: 1fr;
}

.invite-card,
.pending-card,
.members-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
}

.invite-card {
  border-color: rgba(14, 165, 233, 0.16);
  background:
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.1), transparent 34%),
    rgba(255, 255, 255, 0.84);
}

.pending-card {
  border-color: rgba(139, 92, 246, 0.14);
  background:
    radial-gradient(circle at top left, rgba(139, 92, 246, 0.1), transparent 36%),
    rgba(255, 255, 255, 0.84);
}

.members-card {
  flex: 1;
  min-height: clamp(260px, 38vh, 520px);
  border-color: rgba(16, 185, 129, 0.12);
  background:
    radial-gradient(circle at bottom right, rgba(16, 185, 129, 0.08), transparent 40%),
    rgba(255, 255, 255, 0.86);
}

.invite-card h3 {
  color: #0369a1;
}

.pending-card h3 {
  color: #6d28d9;
}

.members-card h3 {
  color: #0f766e;
}

.group-hero h3 {
  font-size: clamp(22px, 2.4vw, 30px);
  line-height: 1.25;
  color: var(--student-text-primary);
  letter-spacing: -0.02em;
}

.primary-btn,
.secondary-btn,
.neutral-btn {
  min-height: 42px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid transparent;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
}

.primary-btn {
  background: var(--student-accent);
  color: #fff;
}

.secondary-btn {
  background: rgba(255, 255, 255, 0.88);
  color: #0369a1;
  border-color: rgba(14, 165, 233, 0.18);
}

.invite-head-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
}

.copy-btn,
.refresh-btn {
  min-height: 40px;
  padding: 0 18px;
  border-radius: 999px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 160ms, box-shadow 160ms, filter 160ms, border-color 160ms;
}

.copy-btn {
  border: 1px solid rgba(14, 165, 233, 0.22);
  background: rgba(255, 255, 255, 0.92);
  color: #0369a1;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
}

.copy-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(14, 165, 233, 0.36);
  box-shadow: 0 8px 18px rgba(2, 132, 199, 0.14);
}

.copy-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.refresh-btn {
  padding: 0 18px;
  border: 0;
  background: linear-gradient(135deg, #38bdf8 0%, #0284c7 52%, #0369a1 100%);
  color: #fff;
  box-shadow:
    0 10px 24px rgba(2, 132, 199, 0.28),
    inset 0 1px 0 rgba(255, 255, 255, 0.28);
}

.refresh-btn:hover {
  transform: translateY(-1px);
  filter: brightness(1.04);
  box-shadow:
    0 14px 28px rgba(2, 132, 199, 0.34),
    inset 0 1px 0 rgba(255, 255, 255, 0.32);
}

.neutral-btn {
  background: var(--student-surface-muted);
  color: var(--student-text-primary);
  border-color: var(--student-border);
}

.invite-command {
  display: flex;
  flex: 1;
  min-height: 0;
}

.invite-box {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 10px;
  min-height: 180px;
  padding: 28px 24px;
  border-radius: 20px;
  text-align: center;
  background:
    linear-gradient(145deg, rgba(224, 242, 254, 0.72) 0%, rgba(255, 255, 255, 0.92) 52%, rgba(254, 243, 199, 0.28) 100%);
  border: 1px solid rgba(14, 165, 233, 0.12);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
}

.pending-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.pending-body > .empty-card,
.pending-body > .loading-inline,
.pending-body > .pending-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.pending-list {
  gap: 12px;
  justify-content: flex-start;
}

.invite-label {
  font-size: 12px;
  color: var(--student-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  font-weight: 700;
}

.invite-code {
  font-family: 'SF Mono', ui-monospace, monospace;
  font-size: clamp(34px, 5vw, 52px);
  font-weight: 800;
  letter-spacing: 0.18em;
  line-height: 1.15;
  color: #0369a1;
  word-break: break-all;
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.8);
}

.invite-expire {
  max-width: 28ch;
}

.approve-btn,
.reject-btn {
  min-height: 36px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  font-family: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.approve-btn {
  background: var(--student-success);
  color: #fff;
}

.reject-btn {
  background: var(--student-danger-soft);
  color: var(--student-danger);
}

.danger-btn,
.text-danger-btn {
  border: 0;
  font-family: inherit;
  font-weight: 700;
  cursor: pointer;
}

.danger-btn {
  min-height: 42px;
  padding: 0 14px;
  border-radius: 12px;
  background: var(--student-danger);
  color: #fff;
}

.text-danger-btn {
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--student-danger-soft);
  color: var(--student-danger);
  font-size: 12px;
}

.danger-btn:disabled,
.text-danger-btn:disabled,
.neutral-btn:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.member-list-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.list-panel-head {
  align-items: center;
}

.member-count {
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.1);
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
}

.member-row {
  flex: 0 0 auto;
  min-height: 64px;
  padding: 0 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  cursor: pointer;
  transition: border-color 160ms, box-shadow 160ms, transform 160ms;
}

.member-row:hover {
  border-color: rgba(14, 165, 233, 0.22);
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.05);
  transform: translateY(-1px);
}

.member-row-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.member-row-avatar {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.16), rgba(99, 102, 241, 0.12));
  color: #2563eb;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 15px;
  flex-shrink: 0;
}

.member-row-copy {
  min-width: 0;
}

.member-row-name {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--student-text-primary);
}

.member-row-meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--student-text-secondary);
}

.member-row-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.member-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  background: rgba(15, 23, 42, 0.05);
  color: var(--student-text-secondary);
  align-self: center;
}

.member-badge.leader {
  background: rgba(0, 122, 255, 0.12);
  color: var(--student-accent);
}

.member-badge.self {
  box-shadow: inset 0 0 0 1px rgba(0, 122, 255, 0.16);
}

.dialog-panel {
  width: min(560px, var(--tt-dialog-available-width, 100%));
}

.confirm-panel.inline-confirm {
  display: grid;
  gap: 18px;
}

.confirm-body {
  display: grid;
  gap: 8px;
}

.success-box {
  margin-top: 6px;
}

.empty-card {
  padding: 28px 24px;
  border-radius: 18px;
  border: 1px dashed rgba(148, 163, 184, 0.28);
  background: rgba(248, 250, 252, 0.72);
  color: #64748b;
  text-align: center;
  font-size: 14px;
}

.error-empty {
  border-color: rgba(255, 59, 48, 0.22);
  color: var(--student-danger);
}

.compact-empty {
  padding: 18px;
}

.compact-actions {
  justify-content: center;
  margin-top: 10px;
}

.state-panel {
  padding: 28px;
  display: grid;
  gap: 14px;
  text-align: center;
  justify-items: center;
}

.error-panel {
  border-color: rgba(255, 59, 48, 0.18);
}

.loading-state {
  display: grid;
  gap: 18px;
}

.skeleton-card {
  height: 120px;
  border-radius: 24px;
  background: var(--student-surface-muted);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.5;
  }
}

@media (max-width: 760px) {
  .student-class-groups {
    min-height: auto;
  }

  .topbar-main,
  .topbar-actions,
  .section-head,
  .panel-head,
  .pending-row,
  .pending-actions,
  .dialog-actions,
  .section-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .group-main-grid,
  .group-main-grid--solo {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .members-card {
    min-height: 320px;
  }

  .member-row {
    flex-direction: column;
    align-items: stretch;
    padding: 14px 16px;
  }

  .member-row-actions {
    justify-content: space-between;
  }
}
</style>
