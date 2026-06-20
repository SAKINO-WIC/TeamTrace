<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const returnTo = computed(() => {
  const target = String(route.query.returnTo || '')
  if (!target || target.startsWith('/product')) return '/auth'
  return target.startsWith('/') ? target : '/auth'
})

const loops = [
  { step: '01', title: '建班与成组', text: '教师创建班级，学生加入班级，小组邀请码和成员管理进入统一流程。' },
  { step: '02', title: '发布与拆解', text: '教师发布任务，组长拆成子任务，成员认领后形成可追踪的协作结构。' },
  { step: '03', title: '提交与审批', text: '成员提交材料，组长审批通过或打回，版本、附件和说明都保留痕迹。' },
  { step: '04', title: '互评与评分', text: '学生完成组内互评，教师查看依据并记录任务评分与课程评分。' },
  { step: '05', title: '申诉与通知', text: '学生可提交申诉，教师处理后留下说明，公告和通知把关键变更送达用户。' },
]

const roles = [
  {
    name: '教师端',
    mark: 'T',
    title: '从发布任务到评分复盘',
    points: ['班级、学生、小组和任务管理', '查看子任务进度、总报告和贡献痕迹', '任务评分、课程评分、成绩导出与申诉处理'],
  },
  {
    name: '学生端',
    mark: 'S',
    title: '从加入小组到证明贡献',
    points: ['加入班级和小组，查看任务与成员进度', '认领子任务、提交材料、编辑版本和查看审批', '参与组内互评，查看成绩并发起申诉'],
  },
  {
    name: '管理员端',
    mark: 'A',
    title: '维护系统运行秩序',
    points: ['账户、角色、教师邀请码和邮件中心管理', '系统公告、登录弹窗和欢迎邮件补发', '监控、日志、安全设置与基础运维入口'],
  },
]

const evidence = [
  '任务附件和小组总报告',
  '子任务提交版本',
  '组长审批记录',
  '组内互评数据',
  '教师评分备注',
  '申诉处理说明',
]

const demoTabs = [
  {
    key: 'teacher',
    label: '教师视角',
    badge: '发布任务',
    title: '老师看到的是一条可评分的协作链',
    subtitle: '从任务发布到评分中心，系统把小组总报告、个人贡献和申诉处理集中到同一条线上。',
    steps: ['创建班级并邀请学生', '发布任务并设置互评时间', '查看小组总报告与贡献痕迹', '记录任务评分和课程评分'],
    metrics: [
      { value: '3', label: '小组待评分' },
      { value: '12', label: '贡献痕迹' },
      { value: '2', label: '申诉待处理' },
    ],
    screenTitle: '教师评分中心',
    screenRows: [
      { name: '第一组 总报告', status: '已提交', tone: 'blue' },
      { name: '贡献痕迹', status: '12 条记录', tone: 'mint' },
      { name: '教师评分', status: '待保存', tone: 'pink' },
    ],
  },
  {
    key: 'student',
    label: '学生视角',
    badge: '证明贡献',
    title: '学生不是只交一个结果，而是留下过程',
    subtitle: '成员认领子任务、提交附件、等待组长审批、参与互评，最后能看到自己的成绩和申诉入口。',
    steps: ['加入班级和小组', '认领并提交子任务', '查看审批和全组进度', '互评、查分、必要时申诉'],
    metrics: [
      { value: '4/5', label: '任务进度' },
      { value: 'V3', label: '提交版本' },
      { value: '1', label: '待互评成员' },
    ],
    screenTitle: '学生任务详情',
    screenRows: [
      { name: '我的子任务', status: '组长已通过', tone: 'mint' },
      { name: '小组总报告', status: '可查看', tone: 'blue' },
      { name: '互评状态', status: '进行中', tone: 'pink' },
    ],
  },
  {
    key: 'admin',
    label: '管理员视角',
    badge: '系统维护',
    title: '管理员负责让系统稳定、清晰、可通知',
    subtitle: '账户、邀请码、系统公告、邮件中心和维护提示都集中在后台，减少上线运行时的信息断层。',
    steps: ['管理账号和角色', '维护教师邀请码', '发布系统公告', '补发欢迎邮件和维护通知'],
    metrics: [
      { value: '128', label: '注册用户' },
      { value: '9', label: '教师邀请码' },
      { value: '1', label: '系统公告' },
    ],
    screenTitle: '管理员工作台',
    screenRows: [
      { name: '系统公告', status: '登录弹窗启用', tone: 'blue' },
      { name: '欢迎邮件', status: '可补发', tone: 'mint' },
      { name: '账户管理', status: '角色可调整', tone: 'pink' },
    ],
  },
]

const activeDemoKey = ref('teacher')

const activeDemo = computed(() => demoTabs.find((item) => item.key === activeDemoKey.value) || demoTabs[0])

function goAuth() {
  router.push('/auth')
}

function goBack() {
  router.push(returnTo.value)
}

function scrollToLoop() {
  document.getElementById('trace-loop')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <main class="product-page">
    <div class="product-grid" aria-hidden="true"></div>
    <div class="product-ambient" aria-hidden="true">
      <span class="mote mote--a"></span>
      <span class="mote mote--b"></span>
      <span class="mote mote--c"></span>
      <span class="swim swim--a"></span>
      <span class="swim swim--b"></span>
    </div>

    <nav class="product-nav" aria-label="产品介绍导航">
      <button type="button" class="nav-logo" aria-label="返回登录页" @click="goAuth">
        <img src="/TeamTraceLogo.png" alt="TeamTrace" />
      </button>
      <div class="nav-actions">
        <button type="button" class="nav-link" @click="goBack">返回</button>
        <button type="button" class="nav-primary" @click="goAuth">进入系统</button>
      </div>
    </nav>

    <section class="product-hero" aria-labelledby="product-title">
      <div class="hero-copy">
        <p class="eyebrow">TeamTrace 产品介绍</p>
        <h1 id="product-title">让小组协作不再只看最后一份文件</h1>
        <p class="hero-lede">
          TeamTrace 是面向课程小组作业的协作过程追踪系统。它不替教师决定最终成绩，而是把任务拆解、提交、审批、互评、评分、申诉和通知串成一条可复盘的证据链。
        </p>
        <div class="hero-actions">
          <button type="button" class="hero-primary" @click="goAuth">进入 TeamTrace</button>
          <button type="button" class="hero-secondary" @click="scrollToLoop">查看功能闭环</button>
        </div>
      </div>

      <div class="hero-system" aria-label="系统能力示意">
        <div class="logo-core">
          <img src="/TeamTraceLogo.png" alt="TeamTrace 摸鱼终结者" />
        </div>
        <div class="orbit-card orbit-card--teacher">
          <span>教师</span>
          <strong>发布与评分</strong>
        </div>
        <div class="orbit-card orbit-card--student">
          <span>学生</span>
          <strong>提交与互评</strong>
        </div>
        <div class="orbit-card orbit-card--admin">
          <span>管理员</span>
          <strong>公告与维护</strong>
        </div>
      </div>
    </section>

    <section id="trace-loop" class="loop-section" aria-labelledby="loop-title">
      <div class="section-heading">
        <p class="eyebrow">协作闭环</p>
        <h2 id="loop-title">从“谁做了什么”到“凭什么评分”</h2>
      </div>
      <div class="loop-track">
        <article v-for="item in loops" :key="item.step" class="loop-item">
          <span>{{ item.step }}</span>
          <h3>{{ item.title }}</h3>
          <p>{{ item.text }}</p>
        </article>
      </div>
    </section>

    <section class="role-section" aria-labelledby="role-title">
      <div class="section-heading section-heading--wide">
        <p class="eyebrow">三端协作</p>
        <h2 id="role-title">每个角色都有明确的工作台</h2>
      </div>
      <div class="role-grid">
        <article v-for="role in roles" :key="role.name" class="role-panel">
          <div class="role-mark">{{ role.mark }}</div>
          <p>{{ role.name }}</p>
          <h3>{{ role.title }}</h3>
          <ul>
            <li v-for="point in role.points" :key="point">{{ point }}</li>
          </ul>
        </article>
      </div>
    </section>

    <section class="demo-section" aria-labelledby="demo-title">
      <div class="section-heading section-heading--wide">
        <p class="eyebrow">系统演示</p>
        <h2 id="demo-title">用一个模拟流程看懂 TeamTrace 怎么运转</h2>
      </div>

      <div class="demo-console">
        <div class="demo-tabs" role="tablist" aria-label="演示视角">
          <button
            v-for="tab in demoTabs"
            :key="tab.key"
            type="button"
            role="tab"
            class="demo-tab"
            :class="{ active: activeDemoKey === tab.key }"
            :aria-selected="activeDemoKey === tab.key"
            @click="activeDemoKey = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <div class="demo-stage">
          <article class="demo-story">
            <span class="demo-badge">{{ activeDemo.badge }}</span>
            <h3>{{ activeDemo.title }}</h3>
            <p>{{ activeDemo.subtitle }}</p>
            <ol class="demo-steps">
              <li v-for="step in activeDemo.steps" :key="step">
                <span></span>
                {{ step }}
              </li>
            </ol>
          </article>

          <div class="demo-screen" aria-label="模拟系统界面">
            <div class="demo-screen__bar">
              <span></span>
              <span></span>
              <span></span>
              <strong>{{ activeDemo.screenTitle }}</strong>
            </div>
            <div class="demo-metrics">
              <div v-for="metric in activeDemo.metrics" :key="metric.label" class="demo-metric">
                <strong>{{ metric.value }}</strong>
                <span>{{ metric.label }}</span>
              </div>
            </div>
            <div class="demo-flow">
              <div
                v-for="row in activeDemo.screenRows"
                :key="row.name"
                class="demo-row"
                :class="`demo-row--${row.tone}`"
              >
                <span class="demo-row__dot"></span>
                <div>
                  <strong>{{ row.name }}</strong>
                  <p>{{ row.status }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="evidence-section" aria-labelledby="evidence-title">
      <div class="evidence-copy">
        <p class="eyebrow">过程留痕</p>
        <h2 id="evidence-title">系统记录依据，教师保留判断权</h2>
        <p>
          TeamTrace 的核心不是自动算出一个不可质疑的分数，而是把协作过程里的关键材料沉淀下来。教师可以直接评分，也可以查看依据后评分；学生如果认为结果存在问题，可以用申诉说明情况。
        </p>
      </div>
      <div class="evidence-list">
        <span v-for="item in evidence" :key="item">{{ item }}</span>
      </div>
    </section>

    <section class="final-section" aria-label="进入系统">
      <div>
        <p class="eyebrow">摸鱼终结者计划</p>
        <h2>让认真协作的人被看见</h2>
      </div>
      <button type="button" class="hero-primary" @click="goAuth">进入系统</button>
    </section>
  </main>
</template>

<style scoped>
.product-page {
  min-height: 100vh;
  position: relative;
  overflow-x: hidden;
  padding: clamp(18px, 3vw, 34px);
  background:
    radial-gradient(circle at 16% 12%, oklch(0.86 0.1 207 / 0.72), transparent 30%),
    radial-gradient(circle at 84% 16%, oklch(0.9 0.08 344 / 0.48), transparent 28%),
    radial-gradient(circle at 56% 92%, oklch(0.91 0.09 116 / 0.44), transparent 35%),
    linear-gradient(145deg, oklch(0.985 0.018 220), oklch(0.95 0.04 226) 48%, oklch(0.99 0.026 78));
  color: oklch(0.22 0.06 248);
  font-family: 'SF Pro SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.product-grid {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(oklch(0.55 0.04 230 / 0.1) 1px, transparent 1px),
    linear-gradient(90deg, oklch(0.55 0.04 230 / 0.1) 1px, transparent 1px);
  background-size: 76px 76px;
  mask-image: radial-gradient(circle at 50% 28%, black, transparent 78%);
}

.product-ambient span {
  position: fixed;
  pointer-events: none;
}

.mote {
  width: 210px;
  aspect-ratio: 1;
  border-radius: 50%;
  filter: blur(8px);
  animation: floatDrift 13s ease-in-out infinite;
}

.mote--a { left: 5%; top: 14%; background: radial-gradient(circle, oklch(0.78 0.12 210 / 0.34), transparent 67%); }
.mote--b { right: 6%; top: 42%; background: radial-gradient(circle, oklch(0.86 0.12 342 / 0.28), transparent 66%); animation-delay: -5s; }
.mote--c { left: 38%; bottom: -8%; background: radial-gradient(circle, oklch(0.86 0.1 154 / 0.26), transparent 68%); animation-delay: -8s; }

.swim {
  width: 44px;
  height: 20px;
  border-radius: 999px 70% 70% 999px;
  background: oklch(0.68 0.15 215 / 0.22);
  animation: swimAcross 18s linear infinite;
}

.swim::after {
  content: '';
  position: absolute;
  right: -11px;
  top: 3px;
  width: 14px;
  height: 14px;
  background: inherit;
  clip-path: polygon(0 50%, 100% 0, 100% 100%);
}

.swim--a { left: -8%; top: 24%; }
.swim--b { left: -12%; top: 68%; animation-delay: -7s; transform: scale(0.82); }

.product-nav,
.product-hero,
.loop-section,
.role-section,
.evidence-section,
.final-section {
  position: relative;
  z-index: 1;
  width: min(1180px, 100%);
  margin-inline: auto;
}

.product-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.nav-logo {
  width: 54px;
  height: 54px;
  overflow: hidden;
  border: 1px solid oklch(0.8 0.04 224 / 0.62);
  border-radius: 18px;
  padding: 0;
  background: oklch(0.99 0.012 220 / 0.84);
  cursor: pointer;
  box-shadow: 0 18px 42px oklch(0.46 0.08 230 / 0.14);
}

.nav-logo img {
  width: 112%;
  height: 112%;
  object-fit: cover;
  transform: translate(-5%, -5%);
}

.nav-actions {
  display: flex;
  gap: 10px;
}

.nav-link,
.nav-primary,
.hero-primary,
.hero-secondary {
  min-height: 42px;
  border-radius: 999px;
  padding: 0 18px;
  font: inherit;
  font-size: 14px;
  font-weight: 900;
  text-decoration: none;
  cursor: pointer;
}

.nav-link {
  border: 1px solid oklch(0.75 0.04 230 / 0.58);
  background: oklch(0.99 0.012 220 / 0.62);
  color: oklch(0.38 0.055 245);
}

.nav-primary,
.hero-primary {
  border: 0;
  background: linear-gradient(135deg, oklch(0.67 0.18 235), oklch(0.75 0.13 204));
  color: oklch(0.99 0.006 230);
  box-shadow: 0 18px 40px oklch(0.53 0.13 224 / 0.28);
}

.product-hero {
  min-height: calc(100vh - 112px);
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(340px, 0.9fr);
  align-items: center;
  gap: clamp(28px, 6vw, 80px);
  padding-block: clamp(52px, 8vw, 112px);
}

.eyebrow {
  width: fit-content;
  margin: 0 0 18px;
  padding: 8px 14px;
  border-radius: 999px;
  background: oklch(0.87 0.08 202 / 0.76);
  color: oklch(0.34 0.12 238);
  font-size: 13px;
  font-weight: 950;
}

.hero-copy h1 {
  max-width: 10.2em;
  margin: 0;
  color: oklch(0.24 0.08 250);
  font-size: clamp(54px, 8vw, 106px);
  line-height: 0.92;
  letter-spacing: 0;
}

.hero-lede {
  max-width: 58ch;
  margin: 28px 0 0;
  color: oklch(0.42 0.045 246);
  font-size: clamp(16px, 1.8vw, 19px);
  line-height: 1.86;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 34px;
}

.hero-secondary {
  display: inline-flex;
  align-items: center;
  border: 1px solid oklch(0.76 0.04 230 / 0.64);
  background: oklch(0.99 0.014 220 / 0.6);
  color: oklch(0.34 0.075 246);
}

.hero-system {
  min-height: 560px;
  position: relative;
  display: grid;
  place-items: center;
}

.hero-system::before,
.hero-system::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  border: 1px dashed oklch(0.6 0.08 220 / 0.28);
}

.hero-system::before {
  width: min(540px, 88vw);
  aspect-ratio: 1;
  animation: floatDrift 10s ease-in-out infinite;
}

.hero-system::after {
  width: min(360px, 64vw);
  aspect-ratio: 1;
  animation: floatDrift 8s ease-in-out infinite reverse;
}

.logo-core {
  width: min(300px, 62vw);
  aspect-ratio: 1;
  position: relative;
  z-index: 2;
  overflow: hidden;
  border-radius: 38px;
  background: oklch(0.99 0.01 230);
  box-shadow:
    0 30px 76px oklch(0.5 0.11 230 / 0.22),
    0 0 0 11px oklch(0.99 0.012 220 / 0.7);
  transform: rotate(-2deg);
}

.logo-core img {
  width: 108%;
  height: 108%;
  object-fit: cover;
  transform: translate(-4%, -4%);
}

.orbit-card {
  position: absolute;
  z-index: 3;
  min-width: 152px;
  border: 1px solid oklch(0.78 0.045 224 / 0.52);
  border-radius: 24px;
  padding: 16px 18px;
  background: oklch(0.99 0.014 220 / 0.82);
  box-shadow: 0 22px 48px oklch(0.46 0.08 230 / 0.16);
  backdrop-filter: blur(16px);
}

.orbit-card span {
  display: block;
  color: oklch(0.52 0.06 238);
  font-size: 12px;
  font-weight: 900;
}

.orbit-card strong {
  display: block;
  margin-top: 6px;
  color: oklch(0.27 0.075 250);
  font-size: 18px;
}

.orbit-card--teacher { top: 8%; right: 6%; transform: rotate(2deg); }
.orbit-card--student { left: 0; bottom: 18%; transform: rotate(-3deg); }
.orbit-card--admin { right: 8%; bottom: 9%; transform: rotate(1deg); }

.loop-section,
.role-section,
.demo-section,
.evidence-section,
.final-section {
  padding-block: clamp(54px, 8vw, 104px);
}

.section-heading {
  max-width: 760px;
  margin-bottom: 34px;
}

.section-heading--wide {
  max-width: 860px;
}

.section-heading h2,
.evidence-copy h2,
.final-section h2 {
  margin: 0;
  color: oklch(0.25 0.075 250);
  font-size: clamp(34px, 5vw, 66px);
  line-height: 1.02;
}

.loop-track {
  display: grid;
  grid-template-columns: repeat(5, minmax(180px, 1fr));
  gap: 14px;
  overflow-x: auto;
  padding-bottom: 6px;
}

.loop-item,
.role-panel,
.demo-console,
.evidence-section,
.final-section {
  border: 1px solid oklch(0.76 0.045 224 / 0.48);
  background: oklch(0.99 0.014 220 / 0.74);
  box-shadow: 0 24px 62px oklch(0.5 0.09 230 / 0.14);
  backdrop-filter: blur(18px);
}

.loop-item {
  min-height: 250px;
  border-radius: 30px;
  padding: 24px;
}

.loop-item span {
  display: inline-grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 16px;
  background: oklch(0.86 0.08 202 / 0.72);
  color: oklch(0.32 0.12 238);
  font-weight: 950;
}

.loop-item h3,
.role-panel h3 {
  margin: 22px 0 0;
  color: oklch(0.27 0.075 250);
  font-size: 22px;
  line-height: 1.22;
}

.loop-item p,
.role-panel li,
.evidence-copy p {
  color: oklch(0.45 0.045 246);
  line-height: 1.75;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.role-panel {
  min-height: 410px;
  border-radius: 34px;
  padding: 28px;
}

.role-mark {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  background: linear-gradient(135deg, oklch(0.71 0.14 218), oklch(0.86 0.09 176));
  color: oklch(0.18 0.055 246);
  font-size: 24px;
  font-weight: 950;
}

.role-panel > p {
  margin: 24px 0 0;
  color: oklch(0.46 0.06 238);
  font-weight: 950;
}

.role-panel ul {
  display: grid;
  gap: 14px;
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
}

.role-panel li {
  position: relative;
  padding-left: 18px;
}

.role-panel li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.72em;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: oklch(0.71 0.14 218);
}

.demo-console {
  border-radius: 42px;
  padding: clamp(18px, 3vw, 30px);
  overflow: hidden;
}

.demo-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 22px;
}

.demo-tab {
  min-height: 42px;
  border: 1px solid oklch(0.76 0.045 224 / 0.62);
  border-radius: 999px;
  padding: 0 16px;
  background: oklch(0.98 0.018 220 / 0.66);
  color: oklch(0.4 0.055 245);
  font: inherit;
  font-size: 14px;
  font-weight: 950;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    background 0.18s ease,
    box-shadow 0.18s ease;
}

.demo-tab:hover,
.demo-tab.active {
  transform: translateY(-2px);
  background: linear-gradient(135deg, oklch(0.72 0.13 215 / 0.88), oklch(0.86 0.09 176 / 0.82));
  color: oklch(0.18 0.055 246);
  box-shadow: 0 16px 34px oklch(0.48 0.1 220 / 0.16);
}

.demo-stage {
  display: grid;
  grid-template-columns: minmax(280px, 0.86fr) minmax(360px, 1.14fr);
  gap: clamp(18px, 4vw, 40px);
  align-items: stretch;
}

.demo-story {
  min-height: 440px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  border-radius: 32px;
  padding: clamp(24px, 4vw, 42px);
  background:
    radial-gradient(circle at 18% 16%, oklch(0.88 0.09 198 / 0.58), transparent 34%),
    oklch(0.965 0.022 218 / 0.72);
  box-shadow: inset 0 0 0 1px oklch(0.82 0.04 224 / 0.32);
}

.demo-badge {
  width: fit-content;
  border-radius: 999px;
  padding: 8px 13px;
  background: oklch(0.99 0.018 80 / 0.82);
  color: oklch(0.36 0.09 78);
  font-size: 13px;
  font-weight: 950;
}

.demo-story h3 {
  margin: 22px 0 0;
  color: oklch(0.24 0.08 250);
  font-size: clamp(30px, 4vw, 52px);
  line-height: 1.03;
  letter-spacing: 0;
}

.demo-story p {
  max-width: 48ch;
  margin: 20px 0 0;
  color: oklch(0.43 0.045 246);
  font-size: 16px;
  line-height: 1.78;
}

.demo-steps {
  display: grid;
  gap: 13px;
  margin: 28px 0 0;
  padding: 0;
  list-style: none;
}

.demo-steps li {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  color: oklch(0.34 0.06 246);
  font-size: 15px;
  font-weight: 850;
  line-height: 1.5;
}

.demo-steps li span {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 50% 50%, oklch(0.99 0.01 220) 0 27%, transparent 29%),
    linear-gradient(135deg, oklch(0.69 0.16 225), oklch(0.86 0.1 176));
  box-shadow: 0 8px 18px oklch(0.52 0.12 220 / 0.2);
}

.demo-screen {
  position: relative;
  min-height: 440px;
  border-radius: 34px;
  padding: 22px;
  overflow: hidden;
  background:
    linear-gradient(135deg, oklch(0.22 0.065 248), oklch(0.32 0.075 238)),
    radial-gradient(circle at 74% 18%, oklch(0.72 0.13 215 / 0.28), transparent 42%);
  color: oklch(0.96 0.01 230);
  box-shadow:
    inset 0 0 0 1px oklch(0.92 0.02 230 / 0.18),
    0 24px 58px oklch(0.28 0.08 240 / 0.24);
}

.demo-screen::before {
  content: '';
  position: absolute;
  inset: -20%;
  background:
    linear-gradient(112deg, transparent 12%, oklch(0.92 0.05 205 / 0.12) 42%, transparent 64%),
    repeating-linear-gradient(90deg, oklch(0.98 0.01 230 / 0.04) 0 1px, transparent 1px 70px);
  animation: screenSweep 8s ease-in-out infinite;
}

.demo-screen > * {
  position: relative;
  z-index: 1;
}

.demo-screen__bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.demo-screen__bar span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: oklch(0.78 0.13 28);
}

.demo-screen__bar span:nth-child(2) {
  background: oklch(0.88 0.12 92);
}

.demo-screen__bar span:nth-child(3) {
  background: oklch(0.78 0.12 170);
}

.demo-screen__bar strong {
  margin-left: 10px;
  color: oklch(0.92 0.02 230);
  font-size: 14px;
  font-weight: 950;
}

.demo-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 28px;
}

.demo-metric {
  min-height: 112px;
  border: 1px solid oklch(0.92 0.02 230 / 0.16);
  border-radius: 22px;
  padding: 18px;
  background: oklch(0.99 0.012 220 / 0.08);
}

.demo-metric strong {
  display: block;
  color: oklch(0.9 0.08 185);
  font-size: clamp(26px, 4vw, 42px);
  line-height: 1;
}

.demo-metric span {
  display: block;
  margin-top: 11px;
  color: oklch(0.83 0.025 230);
  font-size: 13px;
  font-weight: 850;
}

.demo-flow {
  display: grid;
  gap: 12px;
  margin-top: 26px;
}

.demo-row {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  min-height: 76px;
  border: 1px solid oklch(0.92 0.02 230 / 0.16);
  border-radius: 22px;
  padding: 15px 16px;
  background: oklch(0.99 0.012 220 / 0.1);
  animation: rowFloat 5s ease-in-out infinite;
}

.demo-row:nth-child(2) {
  animation-delay: -1.4s;
}

.demo-row:nth-child(3) {
  animation-delay: -2.6s;
}

.demo-row__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: oklch(0.74 0.15 218);
  box-shadow: 0 0 22px oklch(0.74 0.15 218 / 0.56);
}

.demo-row--mint .demo-row__dot {
  background: oklch(0.78 0.12 170);
  box-shadow: 0 0 22px oklch(0.78 0.12 170 / 0.5);
}

.demo-row--pink .demo-row__dot {
  background: oklch(0.78 0.13 342);
  box-shadow: 0 0 22px oklch(0.78 0.13 342 / 0.5);
}

.demo-row strong {
  display: block;
  color: oklch(0.97 0.01 230);
  font-size: 16px;
}

.demo-row p {
  margin: 5px 0 0;
  color: oklch(0.8 0.03 230);
  font-size: 13px;
}

.evidence-section {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(320px, 0.8fr);
  gap: clamp(24px, 5vw, 70px);
  align-items: center;
  border-radius: 42px;
  padding: clamp(30px, 5vw, 58px);
}

.evidence-copy p {
  max-width: 58ch;
  margin: 24px 0 0;
  font-size: 17px;
}

.evidence-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.evidence-list span {
  border: 1px solid oklch(0.78 0.045 224 / 0.58);
  border-radius: 999px;
  padding: 12px 16px;
  background: oklch(0.97 0.022 210 / 0.68);
  color: oklch(0.34 0.075 246);
  font-weight: 900;
}

.final-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  margin-bottom: 28px;
  border-radius: 42px;
  padding: clamp(30px, 5vw, 54px);
}

@keyframes floatDrift {
  0%, 100% { transform: translate3d(0, 0, 0) rotate(0deg); }
  50% { transform: translate3d(12px, -16px, 0) rotate(2deg); }
}

@keyframes swimAcross {
  0% { translate: 0 0; opacity: 0; }
  10% { opacity: 1; }
  50% { translate: 54vw -16px; }
  90% { opacity: 1; }
  100% { translate: 112vw 8px; opacity: 0; }
}

@keyframes screenSweep {
  0%, 100% { transform: translate3d(-3%, 0, 0); opacity: 0.78; }
  50% { transform: translate3d(4%, -2%, 0); opacity: 1; }
}

@keyframes rowFloat {
  0%, 100% { transform: translate3d(0, 0, 0); }
  50% { transform: translate3d(0, -4px, 0); }
}

@media (max-width: 980px) {
  .product-hero,
  .demo-stage,
  .evidence-section {
    grid-template-columns: 1fr;
  }

  .product-hero {
    min-height: auto;
  }

  .hero-system {
    min-height: 430px;
    order: -1;
  }

  .role-grid {
    grid-template-columns: 1fr;
  }

  .role-panel {
    min-height: auto;
  }

  .demo-story,
  .demo-screen {
    min-height: auto;
  }

  .loop-track {
    grid-template-columns: repeat(5, minmax(230px, 1fr));
  }
}

@media (max-width: 620px) {
  .product-page {
    padding: 14px;
  }

  .product-nav {
    align-items: flex-start;
  }

  .nav-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .nav-link,
  .nav-primary,
  .hero-primary,
  .hero-secondary {
    min-height: 40px;
    padding-inline: 14px;
    font-size: 13px;
  }

  .hero-copy h1 {
    font-size: clamp(42px, 13vw, 62px);
  }

  .hero-actions,
  .final-section {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-actions > *,
  .final-section button {
    justify-content: center;
    width: 100%;
  }

  .orbit-card {
    min-width: 132px;
    padding: 13px 14px;
  }

  .orbit-card strong {
    font-size: 15px;
  }

  .demo-tabs {
    display: grid;
    grid-template-columns: 1fr;
  }

  .demo-metrics {
    grid-template-columns: 1fr;
  }

  .demo-story,
  .demo-screen {
    padding: 20px;
    border-radius: 26px;
  }
}
</style>
