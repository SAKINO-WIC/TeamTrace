<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { markCeremonySeen } from '../services/auth'
import { getActiveRole } from '../utils/auth'

const CEREMONY_SESSION_KEY = 'teamtrace_pending_ceremony'

const router = useRouter()
const ceremony = ref(null)
const entering = ref(false)
const errorMessage = ref('')

const homePath = computed(() => {
  if (ceremony.value?.homePath) return ceremony.value.homePath
  const role = ceremony.value?.role || getActiveRole()
  if (role === 'teacher') return '/teacher'
  if (role === 'student') return '/student'
  if (role === 'admin') return '/admin'
  return '/dashboard'
})

const title = computed(() => ceremony.value?.title || '欢迎加入 TeamTrace')
const ceremonyCode = computed(() => ceremony.value?.ceremonyCode || 'TT-000000')
const subtitle = computed(() => (
  ceremony.value?.subtitle ||
  '欢迎加入 TeamTrace 摸鱼终结者计划。你的每一次协作，都会留下清晰、公平、可被看见的痕迹。'
))

function readPendingCeremony() {
  try {
    const raw = sessionStorage.getItem(CEREMONY_SESSION_KEY)
    ceremony.value = raw ? JSON.parse(raw) : null
  } catch {
    ceremony.value = null
  }
  if (!ceremony.value?.ceremonyNo) {
    router.replace(homePath.value)
  }
}

async function enterSystem() {
  if (entering.value) return
  entering.value = true
  errorMessage.value = ''
  try {
    await markCeremonySeen()
    sessionStorage.removeItem(CEREMONY_SESSION_KEY)
    router.replace(homePath.value)
  } catch (error) {
    errorMessage.value = error?.message || '进入系统失败，请稍后重试。'
  } finally {
    entering.value = false
  }
}

onMounted(() => {
  readPendingCeremony()
})
</script>

<template>
  <main class="ceremony-page">
    <section class="ceremony-stage" aria-label="TeamTrace 欢迎仪式">
      <div class="ceremony-mark">
        <span>TeamTrace</span>
        <strong>摸鱼终结者计划</strong>
      </div>

      <div class="ceremony-content">
        <p class="ceremony-eyebrow">WELCOME ABOARD</p>
        <h1>{{ title }}</h1>
        <div class="ceremony-code">{{ ceremonyCode }}</div>
        <p class="ceremony-subtitle">{{ subtitle }}</p>
      </div>

      <div class="ceremony-proof">
        <div>
          <span>协作痕迹</span>
          <strong>可追踪</strong>
        </div>
        <div>
          <span>贡献表达</span>
          <strong>可看见</strong>
        </div>
        <div>
          <span>公平记录</span>
          <strong>可沉淀</strong>
        </div>
      </div>

      <p v-if="errorMessage" class="ceremony-error">{{ errorMessage }}</p>
      <button class="ceremony-enter" type="button" :disabled="entering" @click="enterSystem">
        {{ entering ? '正在进入...' : '进入系统' }}
      </button>
    </section>
  </main>
</template>

<style scoped>
.ceremony-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  color: oklch(20% 0.025 240);
  background:
    radial-gradient(circle at 18% 16%, oklch(86% 0.12 178 / 0.48), transparent 30%),
    radial-gradient(circle at 82% 12%, oklch(78% 0.11 38 / 0.42), transparent 28%),
    linear-gradient(135deg, oklch(97% 0.012 220), oklch(91% 0.018 82));
}

.ceremony-stage {
  width: min(1060px, 100%);
  min-height: min(720px, calc(100vh - 64px));
  display: grid;
  grid-template-rows: auto 1fr auto auto;
  gap: 28px;
  position: relative;
  overflow: hidden;
  padding: clamp(28px, 5vw, 56px);
  border: 1px solid oklch(75% 0.025 230);
  border-radius: 28px;
  background: oklch(98% 0.01 220 / 0.88);
  box-shadow: 0 28px 90px oklch(31% 0.03 240 / 0.18);
}

.ceremony-stage::before {
  content: "";
  position: absolute;
  inset: 16px;
  border: 1px solid oklch(78% 0.03 220);
  border-radius: 22px;
  pointer-events: none;
}

.ceremony-mark,
.ceremony-content,
.ceremony-proof,
.ceremony-enter,
.ceremony-error {
  position: relative;
  z-index: 1;
}

.ceremony-mark {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  text-transform: uppercase;
  letter-spacing: 0;
  font-weight: 900;
}

.ceremony-mark span {
  font-size: 15px;
  color: oklch(24% 0.04 236);
}

.ceremony-mark strong {
  padding: 8px 12px;
  border: 1px solid oklch(66% 0.05 178);
  border-radius: 999px;
  color: oklch(34% 0.09 174);
  background: oklch(93% 0.04 178);
  font-size: 13px;
}

.ceremony-content {
  align-self: center;
  max-width: 820px;
}

.ceremony-eyebrow {
  margin: 0 0 18px;
  color: oklch(42% 0.09 38);
  font-size: 13px;
  font-weight: 900;
}

.ceremony-content h1 {
  margin: 0;
  font-size: clamp(42px, 8vw, 92px);
  line-height: 0.98;
  letter-spacing: 0;
  color: oklch(18% 0.03 236);
}

.ceremony-code {
  width: fit-content;
  margin-top: 24px;
  padding: 12px 18px;
  border: 1px solid oklch(25% 0.04 236);
  border-radius: 12px;
  background: oklch(23% 0.035 236);
  color: oklch(94% 0.04 178);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: clamp(28px, 5vw, 54px);
  font-weight: 900;
}

.ceremony-subtitle {
  max-width: 62ch;
  margin: 24px 0 0;
  color: oklch(37% 0.025 236);
  font-size: clamp(16px, 2vw, 20px);
  line-height: 1.8;
}

.ceremony-proof {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.ceremony-proof div {
  padding: 16px;
  border: 1px solid oklch(78% 0.025 220);
  border-radius: 16px;
  background: oklch(96% 0.012 220 / 0.74);
}

.ceremony-proof span,
.ceremony-proof strong {
  display: block;
}

.ceremony-proof span {
  color: oklch(45% 0.025 236);
  font-size: 13px;
  font-weight: 800;
}

.ceremony-proof strong {
  margin-top: 8px;
  color: oklch(22% 0.035 236);
  font-size: 22px;
}

.ceremony-error {
  margin: 0;
  color: oklch(46% 0.14 28);
  font-weight: 800;
}

.ceremony-enter {
  justify-self: end;
  min-width: 180px;
  height: 52px;
  border: 0;
  border-radius: 999px;
  background: oklch(24% 0.04 236);
  color: oklch(96% 0.012 220);
  font-size: 16px;
  font-weight: 900;
  cursor: pointer;
  transition: transform 180ms ease, box-shadow 180ms ease, opacity 180ms ease;
  box-shadow: 0 16px 38px oklch(24% 0.04 236 / 0.28);
}

.ceremony-enter:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 20px 44px oklch(24% 0.04 236 / 0.34);
}

.ceremony-enter:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}

@media (max-width: 720px) {
  .ceremony-page {
    padding: 16px;
  }

  .ceremony-stage {
    min-height: calc(100vh - 32px);
    border-radius: 22px;
  }

  .ceremony-mark {
    align-items: flex-start;
    flex-direction: column;
  }

  .ceremony-proof {
    grid-template-columns: 1fr;
  }

  .ceremony-enter {
    justify-self: stretch;
    width: 100%;
  }
}
</style>
