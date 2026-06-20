<script setup>
import '../styles/admin-workspace.css'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import AdminToolbar from '../components/admin/AdminToolbar.vue'
import {
  batchCreateTeacherInviteCodes,
  batchRevokeTeacherInviteCodes,
  createTeacherInviteCode,
  deleteTeacherInviteCode,
  fetchTeacherInviteCodes,
  resumeTeacherInviteCode,
  revokeTeacherInviteCode,
  revokeTeacherInviteCodesByQuery,
} from '../services/admin'
import { copyText, formatDateTime } from '../utils/admin'

const inviteRows = ref([])
const selectedCodes = ref([])
const loading = ref(false)
const actionCode = ref('')
const message = ref('')
const messageType = ref('info')
const latestInvite = ref(null)
const latestBatchCreateResult = ref({ succeeded: [], failed: [] })

const createForm = reactive({
  expireDays: 30,
  batchCount: 10,
})

const filters = reactive({
  code: '',
  status: '',
  expired: '',
  expireFrom: '',
  expireTo: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
  pages: 0,
  hasNext: false,
})

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '未使用' },
  { value: 1, label: '已使用' },
  { value: 2, label: '已停用' },
]

const expiredOptions = [
  { value: '', label: '全部期限' },
  { value: 'false', label: '未过期' },
  { value: 'true', label: '已过期' },
]

function setMessage(text, type = 'info') {
  message.value = text
  messageType.value = type
}

function getErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

function normalizeInvitePagePayload(payload) {
  return {
    list: payload?.list || payload?.records || payload?.content || [],
    page: Number(payload?.page) || 1,
    size: Number(payload?.size) || pagination.size,
    total: Number(payload?.total) || 0,
    pages: Number(payload?.pages) || 0,
    hasNext: Boolean(payload?.hasNext),
  }
}

function normalizeBatchResult(payload) {
  return {
    succeeded: Array.isArray(payload?.succeeded) ? payload.succeeded : [],
    failed: Array.isArray(payload?.failed) ? payload.failed : [],
  }
}

function isExpired(invite) {
  if (!invite?.expireAt) return false
  const expireAt = new Date(invite.expireAt)
  return !Number.isNaN(expireAt.getTime()) && expireAt.getTime() < Date.now()
}

function getInviteStatusLabel(invite) {
  const status = Number(invite?.status)
  if (status === 2) return '已停用'
  if (status === 1) return '已使用'
  if (isExpired(invite)) return '已过期'
  return '未使用'
}

function getInviteStatusTone(invite) {
  const status = Number(invite?.status)
  if (status === 2) return 'danger'
  if (status === 1) return 'neutral'
  if (isExpired(invite)) return 'warning'
  return 'success'
}

function canStop(invite) {
  return Number(invite?.status) !== 2
}

function canResume(invite) {
  return Number(invite?.status) !== 0
}

function canDelete(invite) {
  return Boolean(invite?.code)
}

function getInviteActionReason(invite) {
  const status = Number(invite?.status)
  if (status === 1) return '已绑定教师，管理员仍可强制停用、恢复或删除，请谨慎操作。'
  if (status === 2) return '已停用，管理员可恢复或删除。'
  if (status === 0 && isExpired(invite)) return '已过期未使用，管理员可恢复状态、停用或删除。'
  if (status === 0) return '未使用，管理员可复制、停用或删除。'
  return '状态未知，请刷新后再操作。'
}

function getInviteRiskText(invite, action) {
  const status = Number(invite?.status)
  const code = invite?.code || ''
  const usedBy = formatUsedBy(invite)
  const parts = [`确认${action}邀请码 ${code} 吗？`]
  if (status === 1) {
    parts.push(`该邀请码已绑定教师：${usedBy}。`)
  }
  if (isExpired(invite)) {
    parts.push('该邀请码已过期。')
  }
  if (action === '删除') {
    parts.push('删除后不可在系统中恢复，历史绑定信息也会失去邀请码记录。')
  }
  if (action === '停用') {
    parts.push('停用后新注册将不能继续使用该邀请码。')
  }
  if (action === '恢复') {
    parts.push('恢复后该邀请码会变为未使用状态，请确认不会造成重复注册风险。')
  }
  return parts.join('\n')
}

function clampNumber(value, min, max, fallback) {
  const parsed = Number(value)
  if (Number.isNaN(parsed)) return fallback
  return Math.min(max, Math.max(min, Math.trunc(parsed)))
}

function buildQueryParams() {
  const params = {
    page: pagination.page,
    size: pagination.size,
  }

  const code = String(filters.code || '').trim()
  if (code) params.code = code
  if (filters.status !== '' && filters.status !== null && filters.status !== undefined) params.status = Number(filters.status)
  if (filters.expired !== '') params.expired = String(filters.expired) === 'true'
  if (filters.expireFrom) params.expireFrom = filters.expireFrom
  if (filters.expireTo) params.expireTo = filters.expireTo

  return params
}

function buildRevokeByQueryPayload() {
  const payload = {
    limit: 200,
  }
  if (filters.status !== '' && filters.status !== null && filters.status !== undefined) payload.status = Number(filters.status)
  if (filters.expired !== '') payload.expired = String(filters.expired) === 'true'
  if (filters.expireFrom) payload.expireFrom = filters.expireFrom
  if (filters.expireTo) payload.expireTo = filters.expireTo
  return payload
}

function formatUsedBy(invite) {
  if (!invite?.usedByUser) return '-'
  const name = invite.usedByUser.name || '未命名教师'
  const account = invite.usedByUser.email || invite.usedByUser.phone || '-'
  return `${name} / ${account}`
}

async function loadInviteCodes(options = {}) {
  const shouldNotify = options.shouldNotify !== false
  loading.value = true
  try {
    const { data } = await fetchTeacherInviteCodes(buildQueryParams())
    const payload = normalizeInvitePagePayload(data?.data || {})
    inviteRows.value = payload.list
    pagination.page = payload.page
    pagination.size = payload.size
    pagination.total = payload.total
    pagination.pages = payload.pages
    pagination.hasNext = payload.hasNext
    selectedCodes.value = selectedCodes.value.filter((code) => payload.list.some((item) => item.code === code))
    if (shouldNotify) setMessage('')
  } catch (error) {
    inviteRows.value = []
    selectedCodes.value = []
    pagination.total = 0
    pagination.pages = 0
    pagination.hasNext = false
    setMessage(getErrorMessage(error, '加载邀请码列表失败'), 'error')
  } finally {
    loading.value = false
  }
}

async function queryInviteCodes() {
  pagination.page = 1
  await loadInviteCodes()
}

async function resetFilters() {
  filters.code = ''
  filters.status = ''
  filters.expired = ''
  filters.expireFrom = ''
  filters.expireTo = ''
  pagination.page = 1
  await loadInviteCodes()
}

async function prevPage() {
  if (loading.value || pagination.page <= 1) return
  pagination.page -= 1
  await loadInviteCodes()
}

async function nextPage() {
  if (loading.value || !pagination.hasNext) return
  pagination.page += 1
  await loadInviteCodes()
}

async function changePageSize() {
  pagination.page = 1
  await loadInviteCodes()
}

async function copyCode(code) {
  try {
    await copyText(code)
    setMessage(`邀请码已复制：${code}`, 'success')
  } catch {
    setMessage('当前环境复制失败，请手动复制。', 'error')
  }
}

async function copyBatchCodes(codes) {
  const normalizedCodes = (codes || []).filter(Boolean)
  if (!normalizedCodes.length) {
    setMessage('当前没有可复制的邀请码。', 'warn')
    return
  }
  try {
    await copyText(normalizedCodes.join('\n'))
    setMessage(`已复制 ${normalizedCodes.length} 个邀请码。`, 'success')
  } catch {
    setMessage('批量复制失败，请手动复制。', 'error')
  }
}

async function generateSingleInviteCode() {
  createForm.expireDays = clampNumber(createForm.expireDays, 1, 365, 30)
  loading.value = true
  latestBatchCreateResult.value = { succeeded: [], failed: [] }
  try {
    const { data } = await createTeacherInviteCode(createForm.expireDays)
    latestInvite.value = data?.data || null
    pagination.page = 1
    await loadInviteCodes({ shouldNotify: false })
    setMessage(`邀请码生成成功：${latestInvite.value?.code || '-'}`, 'success')
  } catch (error) {
    setMessage(getErrorMessage(error, '生成邀请码失败'), 'error')
  } finally {
    loading.value = false
  }
}

async function generateBatchInviteCodes() {
  createForm.expireDays = clampNumber(createForm.expireDays, 1, 365, 30)
  createForm.batchCount = clampNumber(createForm.batchCount, 1, 200, 10)
  loading.value = true
  latestInvite.value = null
  try {
    const { data } = await batchCreateTeacherInviteCodes(createForm.batchCount, createForm.expireDays)
    latestBatchCreateResult.value = normalizeBatchResult(data?.data || {})
    const succeededCount = latestBatchCreateResult.value.succeeded.length
    const failedCount = latestBatchCreateResult.value.failed.length
    pagination.page = 1
    await loadInviteCodes({ shouldNotify: false })
    setMessage(`批量生成完成：成功 ${succeededCount} 个，失败 ${failedCount} 个。`, failedCount ? 'warn' : 'success')
  } catch (error) {
    setMessage(getErrorMessage(error, '批量生成邀请码失败'), 'error')
  } finally {
    loading.value = false
  }
}

async function stopCode(invite) {
  if (!window.confirm(getInviteRiskText(invite, '停用'))) return
  actionCode.value = invite.code
  try {
    await revokeTeacherInviteCode(invite.code)
    await loadInviteCodes({ shouldNotify: false })
    setMessage(`邀请码已停用：${invite.code}`, 'success')
  } catch (error) {
    setMessage(getErrorMessage(error, '停用邀请码失败'), 'error')
  } finally {
    actionCode.value = ''
  }
}

async function resumeCode(invite) {
  if (!window.confirm(getInviteRiskText(invite, '恢复'))) return
  actionCode.value = invite.code
  try {
    await resumeTeacherInviteCode(invite.code)
    await loadInviteCodes({ shouldNotify: false })
    setMessage(`邀请码已恢复为未使用：${invite.code}`, 'success')
  } catch (error) {
    setMessage(getErrorMessage(error, '恢复邀请码失败'), 'error')
  } finally {
    actionCode.value = ''
  }
}

async function removeCode(invite) {
  if (!window.confirm(getInviteRiskText(invite, '删除'))) return
  actionCode.value = invite.code
  try {
    await deleteTeacherInviteCode(invite.code)
    await loadInviteCodes({ shouldNotify: false })
    setMessage(`邀请码已删除：${invite.code}`, 'success')
  } catch (error) {
    setMessage(getErrorMessage(error, '删除邀请码失败'), 'error')
  } finally {
    actionCode.value = ''
  }
}

async function stopSelectedCodes() {
  const codes = selectedCodes.value.filter(Boolean)
  if (!codes.length) {
    setMessage('请先选择要停用的邀请码。', 'warn')
    return
  }
  if (!window.confirm(`确认强制停用已选中的 ${codes.length} 个邀请码吗？已绑定教师或已过期的邀请码也会被停用。`)) return

  loading.value = true
  try {
    const { data } = await batchRevokeTeacherInviteCodes(codes)
    const payload = normalizeBatchResult(data?.data || {})
    await loadInviteCodes({ shouldNotify: false })
    setMessage(`批量停用完成：成功 ${payload.succeeded.length} 个，失败 ${payload.failed.length} 个。`, payload.failed.length ? 'warn' : 'success')
  } catch (error) {
    setMessage(getErrorMessage(error, '批量停用失败'), 'error')
  } finally {
    loading.value = false
  }
}

async function stopByCurrentFilter() {
  const activeFilters = revokeQueryFilterCount.value
  if (!activeFilters) {
    setMessage('请至少设置状态、期限或失效时间中的一个条件后再按条件停用。', 'warn')
    return
  }
  if (!window.confirm('确认按当前状态、期限或失效时间批量停用邀请码吗？系统最多处理 200 条匹配记录。')) return

  loading.value = true
  try {
    const { data } = await revokeTeacherInviteCodesByQuery(buildRevokeByQueryPayload())
    const payload = normalizeBatchResult(data?.data || {})
    await loadInviteCodes({ shouldNotify: false })
    setMessage(`按条件停用完成：成功 ${payload.succeeded.length} 个，失败 ${payload.failed.length} 个。`, payload.failed.length ? 'warn' : 'success')
  } catch (error) {
    setMessage(getErrorMessage(error, '按条件停用失败'), 'error')
  } finally {
    loading.value = false
  }
}

const visibleStopCodes = computed(() => inviteRows.value.filter(canStop).map((item) => item.code))

const allVisibleStopSelected = computed(() => {
  const codes = visibleStopCodes.value
  return codes.length > 0 && codes.every((code) => selectedCodes.value.includes(code))
})

function toggleAllVisibleStopCodes(event) {
  const checked = event.target.checked
  const current = new Set(selectedCodes.value)
  visibleStopCodes.value.forEach((code) => {
    if (checked) current.add(code)
    else current.delete(code)
  })
  selectedCodes.value = Array.from(current)
}

function toggleCodeSelection(code, checked) {
  const current = new Set(selectedCodes.value)
  if (checked) current.add(code)
  else current.delete(code)
  selectedCodes.value = Array.from(current)
}

const summaryItems = computed(() => {
  const unusedCount = inviteRows.value.filter((item) => Number(item.status) === 0 && !isExpired(item)).length
  const usedCount = inviteRows.value.filter((item) => Number(item.status) === 1).length
  const stoppedCount = inviteRows.value.filter((item) => Number(item.status) === 2).length
  const expiredCount = inviteRows.value.filter((item) => Number(item.status) === 0 && isExpired(item)).length

  return [
    { label: '筛选总数', value: `${pagination.total}` },
    { label: '当前页未使用', value: `${unusedCount}` },
    { label: '当前页已使用', value: `${usedCount}` },
    { label: '当前页已停用', value: `${stoppedCount}` },
    { label: '当前页已过期', value: `${expiredCount}` },
  ]
})

const activeFilterCount = computed(() => [
  String(filters.code || '').trim(),
  filters.status !== '' ? 'status' : '',
  filters.expired !== '' ? 'expired' : '',
  filters.expireFrom,
  filters.expireTo,
].filter(Boolean).length)

const revokeQueryFilterCount = computed(() => [
  filters.status !== '' ? 'status' : '',
  filters.expired !== '' ? 'expired' : '',
  filters.expireFrom,
  filters.expireTo,
].filter(Boolean).length)

const visibleRangeText = computed(() => {
  if (!inviteRows.value.length || !pagination.total) return '当前没有邀请码记录'
  const start = (pagination.page - 1) * pagination.size + 1
  const end = start + inviteRows.value.length - 1
  return `显示 ${start}-${end} / ${pagination.total}`
})

const latestGeneratedCodes = computed(() => latestBatchCreateResult.value.succeeded.map((item) => item.code))
const selectedCount = computed(() => selectedCodes.value.length)
const selectableCount = computed(() => visibleStopCodes.value.length)
const canStopByCurrentFilter = computed(() => revokeQueryFilterCount.value > 0 && !loading.value)
const bulkActionHint = computed(() => {
  if (selectedCount.value > 0) return `已选择 ${selectedCount.value} 个邀请码，管理员可强制停用。`
  if (selectableCount.value > 0) return `当前页有 ${selectableCount.value} 个非停用状态的邀请码可批量停用。`
  return '当前页没有可批量停用的邀请码。'
})

watch(() => pagination.size, () => {
  pagination.size = clampNumber(pagination.size, 1, 100, 10)
})

onMounted(loadInviteCodes)
</script>

<template>
  <div class="admin-page invite-page">
    <section class="admin-panel invite-panel">
      <div class="invite-header">
        <div>
          <p class="admin-section-label">Teacher invites</p>
          <h2>教师邀请码管理</h2>
          <p>生成教师注册邀请码，并管理未使用的邀请码状态。</p>
        </div>
        <button type="button" class="admin-btn-secondary" :disabled="loading" @click="loadInviteCodes">
          {{ loading ? '刷新中...' : '刷新列表' }}
        </button>
      </div>

      <p v-if="message" class="admin-message admin-message-card" :class="messageType">{{ message }}</p>

      <section class="summary-row" aria-label="邀请码统计">
        <article v-for="item in summaryItems" :key="item.label" class="summary-cell">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>

      <section class="management-section">
        <div class="section-title">
          <h3>生成邀请码</h3>
          <span>有效期 1-365 天，批量最多 200 个。</span>
        </div>
        <div class="create-grid">
          <label class="admin-field">
            <span>有效天数</span>
            <input v-model.number="createForm.expireDays" type="number" min="1" max="365" />
          </label>
          <label class="admin-field">
            <span>批量数量</span>
            <input v-model.number="createForm.batchCount" type="number" min="1" max="200" />
          </label>
          <div class="button-group">
            <button type="button" class="admin-btn" :disabled="loading" @click="generateSingleInviteCode">
              {{ loading ? '处理中...' : '生成 1 个' }}
            </button>
            <button type="button" class="admin-btn-secondary" :disabled="loading" @click="generateBatchInviteCodes">
              批量生成
            </button>
          </div>
          <div class="latest-result">
            <div>
              <span>单个</span>
              <strong>{{ latestInvite?.code || '暂无' }}</strong>
            </div>
            <button v-if="latestInvite?.code" type="button" class="admin-link-btn" @click="copyCode(latestInvite.code)">复制</button>
            <div>
              <span>批量</span>
              <strong>成功 {{ latestBatchCreateResult.succeeded.length }} / 失败 {{ latestBatchCreateResult.failed.length }}</strong>
            </div>
            <button v-if="latestGeneratedCodes.length" type="button" class="admin-link-btn" @click="copyBatchCodes(latestGeneratedCodes)">复制全部</button>
          </div>
        </div>
      </section>

      <section class="management-section">
        <div class="section-title">
          <h3>筛选查询</h3>
          <span>筛选条件会影响列表，也会影响“按筛选停用”。</span>
        </div>
        <div class="filters-grid">
          <label class="admin-field">
            <span>邀请码</span>
            <input v-model.trim="filters.code" type="text" placeholder="精确邀请码" />
          </label>
          <label class="admin-field">
            <span>状态</span>
            <select v-model="filters.status">
              <option v-for="option in statusOptions" :key="`${option.value}`" :value="option.value">{{ option.label }}</option>
            </select>
          </label>
          <label class="admin-field">
            <span>期限</span>
            <select v-model="filters.expired">
              <option v-for="option in expiredOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>
          <label class="admin-field">
            <span>失效开始</span>
            <input v-model="filters.expireFrom" type="datetime-local" />
          </label>
          <label class="admin-field">
            <span>失效结束</span>
            <input v-model="filters.expireTo" type="datetime-local" />
          </label>
          <label class="admin-field">
            <span>每页</span>
            <select v-model.number="pagination.size" @change="changePageSize">
              <option :value="10">10 条</option>
              <option :value="20">20 条</option>
              <option :value="50">50 条</option>
              <option :value="100">100 条</option>
            </select>
          </label>
        </div>
        <div class="query-actions">
          <div class="button-group">
            <button type="button" class="admin-btn" :disabled="loading" @click="queryInviteCodes">
              {{ loading ? '查询中...' : '查询' }}
            </button>
            <button type="button" class="admin-btn-secondary" :disabled="loading" @click="resetFilters">重置</button>
          </div>
          <div class="meta-row">
            <span class="admin-chip">筛选 {{ activeFilterCount }} 项</span>
            <span class="admin-chip subtle-chip">{{ visibleRangeText }}</span>
            <span class="admin-chip subtle-chip">已选 {{ selectedCount }}</span>
          </div>
        </div>
      </section>

      <section class="management-section table-section">
        <div class="table-tools">
          <div>
            <h3>邀请码列表</h3>
            <p>{{ bulkActionHint }}</p>
          </div>
          <div class="button-group">
            <button
              type="button"
              class="admin-btn-secondary"
              :disabled="loading || !selectedCount"
              :title="selectedCount ? '强制停用已选邀请码' : '请先勾选非停用状态的邀请码'"
              @click="stopSelectedCodes"
            >
              停用已选
            </button>
            <button
              type="button"
              class="admin-btn-ghost danger"
              :disabled="!canStopByCurrentFilter"
              :title="revokeQueryFilterCount ? '按状态、期限或失效时间最多停用 200 条' : '请先设置状态、期限或失效时间条件'"
              @click="stopByCurrentFilter"
            >
              按筛选停用
            </button>
          </div>
        </div>

        <div class="admin-table-wrap">
          <table class="admin-table invite-table">
            <thead>
              <tr>
                <th class="check-col">
                  <input
                    type="checkbox"
                    :checked="allVisibleStopSelected"
                    :disabled="!visibleStopCodes.length"
                    @change="toggleAllVisibleStopCodes"
                  />
                </th>
                <th>邀请码</th>
                <th>状态</th>
                <th>失效时间</th>
                <th>使用人</th>
                <th>使用时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in inviteRows" :key="item.id || item.code">
                <td class="check-col">
                  <input
                    type="checkbox"
                    :checked="selectedCodes.includes(item.code)"
                    :disabled="!canStop(item)"
                    :title="canStop(item) ? '选择后可批量停用' : '已停用的邀请码无需再次停用'"
                    @change="toggleCodeSelection(item.code, $event.target.checked)"
                  />
                </td>
                <td>
                  <div class="code-cell">
                    <strong>{{ item.code }}</strong>
                    <span>ID: {{ item.id || '-' }}</span>
                  </div>
                </td>
                <td>
                  <span class="admin-status-badge" :class="getInviteStatusTone(item)">{{ getInviteStatusLabel(item) }}</span>
                </td>
                <td>{{ formatDateTime(item.expireAt) }}</td>
                <td>{{ formatUsedBy(item) }}</td>
                <td>{{ formatDateTime(item.usedAt) }}</td>
                <td>
                  <div class="admin-inline-actions">
                    <button type="button" class="admin-link-btn" @click="copyCode(item.code)">复制</button>
                    <button
                      v-if="canStop(item)"
                      type="button"
                      class="admin-link-btn"
                      :disabled="actionCode === item.code"
                      @click="stopCode(item)"
                    >
                      {{ actionCode === item.code ? '处理中...' : '停用' }}
                    </button>
                    <button
                      v-if="canResume(item)"
                      type="button"
                      class="admin-link-btn"
                      :disabled="actionCode === item.code"
                      @click="resumeCode(item)"
                    >
                      {{ actionCode === item.code ? '处理中...' : '恢复' }}
                    </button>
                    <button
                      v-if="canDelete(item)"
                      type="button"
                      class="admin-link-btn danger"
                      :disabled="actionCode === item.code"
                      @click="removeCode(item)"
                    >
                      {{ actionCode === item.code ? '处理中...' : '删除' }}
                    </button>
                    <span class="action-reason">{{ getInviteActionReason(item) }}</span>
                  </div>
                </td>
              </tr>
              <tr v-if="!inviteRows.length">
                <td colspan="7" class="empty-row">当前筛选条件下没有邀请码记录。</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="admin-pager">
          <span>{{ visibleRangeText }}，第 {{ pagination.page }} / {{ pagination.pages || 1 }} 页</span>
          <div class="button-group">
            <button type="button" class="admin-btn-secondary" :disabled="loading || pagination.page <= 1" @click="prevPage">上一页</button>
            <button type="button" class="admin-btn-secondary" :disabled="loading || !pagination.hasNext" @click="nextPage">下一页</button>
          </div>
        </div>
      </section>
    </section>
  </div>
</template>

<style scoped>
.invite-page {
  gap: 0;
}

.invite-panel {
  padding: 22px;
}

.invite-header,
.query-actions,
.table-tools {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.invite-header h2,
.section-title h3,
.table-tools h3 {
  margin: 0;
  color: var(--admin-text-primary);
}

.invite-header h2 {
  margin: 6px 0 0;
  font-size: 24px;
  line-height: 1.2;
}

.invite-header p,
.section-title span,
.table-tools p {
  margin: 6px 0 0;
  color: var(--admin-text-secondary);
  line-height: 1.5;
}

.admin-message-card {
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-control);
  background: var(--admin-content-surface-strong);
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.summary-cell {
  padding: 14px 16px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-control);
  background: var(--admin-content-surface-strong);
}

.summary-cell span {
  display: block;
  color: var(--admin-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.summary-cell strong {
  display: block;
  margin-top: 8px;
  color: var(--admin-text-primary);
  font-size: 26px;
  line-height: 1;
}

.management-section {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--admin-border);
}

.section-title {
  margin-bottom: 12px;
}

.create-grid {
  display: grid;
  grid-template-columns: minmax(160px, 0.7fr) minmax(160px, 0.7fr) auto minmax(320px, 1fr);
  align-items: end;
  gap: 12px;
}

.filters-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(180px, 1fr));
  gap: 12px;
}

.button-group,
.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.latest-result {
  min-height: 42px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--admin-border);
  border-radius: var(--admin-radius-control);
  background: var(--admin-content-surface-strong);
}

.latest-result div {
  min-width: 0;
}

.latest-result span,
.muted-action {
  color: var(--admin-text-tertiary);
  font-size: 12px;
  font-weight: 700;
}

.latest-result strong {
  display: block;
  margin-top: 3px;
  color: var(--admin-text-primary);
  font-size: 14px;
  white-space: nowrap;
}

.query-actions,
.table-tools {
  margin-top: 12px;
}

.subtle-chip {
  background: var(--admin-surface-muted);
  color: var(--admin-text-secondary);
}

.table-section {
  padding-top: 16px;
}

.invite-table {
  min-width: 1120px;
}

.check-col {
  width: 42px;
  text-align: center;
}

.code-cell {
  display: grid;
  gap: 4px;
}

.code-cell strong {
  font-size: 15px;
  letter-spacing: 0.02em;
}

.code-cell span {
  color: var(--admin-text-secondary);
  font-size: 12px;
}

.empty-row {
  text-align: center;
  color: var(--admin-text-secondary);
  padding: 30px 12px;
}

.action-reason {
  flex-basis: 100%;
  color: var(--admin-text-tertiary);
  font-size: 12px;
  line-height: 1.45;
}

.danger {
  color: var(--admin-danger);
}

.admin-btn-ghost.danger {
  border-color: color-mix(in oklab, var(--admin-danger) 28%, var(--admin-border));
}

@media (max-width: 1180px) {
  .create-grid,
  .filters-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .latest-result {
    grid-column: 1 / -1;
  }

  .summary-row {
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  }
}

@media (max-width: 760px) {
  .invite-panel {
    padding: 16px;
  }

  .invite-header,
  .query-actions,
  .table-tools {
    align-items: stretch;
    flex-direction: column;
  }

  .create-grid,
  .filters-grid {
    grid-template-columns: 1fr;
  }

  .latest-result {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
