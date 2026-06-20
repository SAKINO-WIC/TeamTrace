<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const returnTo = computed(() => {
  const target = String(route.query.returnTo || '')
  if (!target || target.startsWith('/moyu-terminator')) return '/auth'
  return target.startsWith('/') ? target : '/auth'
})

function goAuth(mode = 'login') {
  router.push({ path: '/auth', query: { mode } })
}

function goBack() {
  router.push(returnTo.value)
}

function goProductIntro() {
  router.push({ path: '/product', query: { returnTo: route.fullPath || '/moyu-terminator' } })
}
</script>

<template>
  <main class="moyu-page">
    <nav class="moyu-top-actions" aria-label="彩蛋页导航">
      <button type="button" class="moyu-top-btn moyu-top-btn--primary" @click="goProductIntro">
        查看产品介绍
      </button>
      <button type="button" class="moyu-top-btn" @click="goBack">
        返回
      </button>
    </nav>

    <div class="soft-grid" aria-hidden="true"></div>
    <div class="moyu-ambient" aria-hidden="true">
      <span class="blob blob--a"></span>
      <span class="blob blob--b"></span>
      <span class="blob blob--c"></span>
      <span class="fish fish--a"></span>
      <span class="fish fish--b"></span>
      <span class="fish fish--c"></span>
      <span class="paw paw--a"></span>
      <span class="paw paw--b"></span>
      <span class="paw paw--c"></span>
      <span class="spark spark--a">过程</span>
      <span class="spark spark--b">协作</span>
      <span class="spark spark--c">留痕</span>
    </div>

    <section class="moyu-shell" aria-labelledby="moyu-title">
      <div class="story-card">
        <p class="moyu-kicker">隐藏入口</p>
        <h1 id="moyu-title">摸鱼终结者计划</h1>
        <p class="moyu-copy">
          让每一次协作都有痕迹，让认真做事的人被看见。猫猫负责盯梢，小鱼负责提醒，TeamTrace 负责把小组过程记录清楚。
        </p>
        <div class="moyu-actions" aria-label="登录入口">
          <button type="button" class="moyu-primary" @click="goAuth('login')">进入登录</button>
          <button type="button" class="moyu-secondary" @click="goAuth('register')">创建账号</button>
          <button type="button" class="moyu-ghost" @click="goBack">返回上一页</button>
        </div>
      </div>

      <div class="logo-stage" aria-label="TeamTrace 摸鱼终结者">
        <div class="orbit orbit--outer" aria-hidden="true"></div>
        <div class="orbit orbit--inner" aria-hidden="true"></div>
        <div class="logo-frame">
          <img src="/TeamTraceLogo.png" alt="TeamTrace 摸鱼终结者" />
        </div>
        <span class="floating-label floating-label--left">过程留痕</span>
        <span class="floating-label floating-label--right">公平协作</span>
        <span class="floating-label floating-label--bottom">小鱼提醒</span>
      </div>

      <div class="signal-board" aria-label="系统提示">
        <article class="signal-card signal-card--blue">
          <span class="signal-dot"></span>
          <strong>任务被拆清楚</strong>
          <p>小组工作不再只看最后交付。</p>
        </article>
        <article class="signal-card signal-card--pink">
          <span class="signal-dot"></span>
          <strong>贡献被看见</strong>
          <p>提交、审批、互评和申诉都有记录。</p>
        </article>
        <article class="signal-card signal-card--mint">
          <span class="signal-dot"></span>
          <strong>摸鱼有提醒</strong>
          <p>不是惩罚谁，而是让协作更透明。</p>
        </article>
      </div>
    </section>
  </main>
</template>

<style scoped>
.moyu-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: grid;
  place-items: center;
  padding: clamp(24px, 5vw, 64px);
  background:
    radial-gradient(circle at 16% 18%, oklch(0.86 0.1 207 / 0.72), transparent 31%),
    radial-gradient(circle at 82% 17%, oklch(0.9 0.08 344 / 0.54), transparent 28%),
    radial-gradient(circle at 55% 88%, oklch(0.9 0.09 190 / 0.5), transparent 34%),
    linear-gradient(145deg, oklch(0.98 0.018 220), oklch(0.94 0.04 226) 48%, oklch(0.99 0.026 78));
  color: oklch(0.22 0.06 248);
  font-family: Inter, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.moyu-top-actions {
  position: fixed;
  top: clamp(16px, 3vw, 28px);
  right: clamp(16px, 3vw, 32px);
  z-index: 5;
  display: flex;
  gap: 10px;
  align-items: center;
}

.moyu-top-btn {
  min-height: 40px;
  border: 1px solid oklch(0.74 0.04 232 / 0.64);
  border-radius: 999px;
  padding: 0 16px;
  background: oklch(0.99 0.014 220 / 0.74);
  color: oklch(0.38 0.055 245);
  font: inherit;
  font-size: 13px;
  font-weight: 900;
  cursor: pointer;
  box-shadow: 0 14px 34px oklch(0.46 0.08 230 / 0.12);
  backdrop-filter: blur(16px);
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    background 180ms ease;
}

.moyu-top-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 40px oklch(0.46 0.08 230 / 0.18);
}

.moyu-top-btn:focus-visible {
  outline: 3px solid oklch(0.78 0.12 210 / 0.74);
  outline-offset: 4px;
}

.moyu-top-btn--primary {
  border-color: oklch(0.69 0.12 214 / 0.52);
  background: linear-gradient(135deg, oklch(0.72 0.13 215 / 0.88), oklch(0.84 0.09 185 / 0.88));
  color: oklch(0.2 0.065 246);
}

.soft-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(oklch(0.55 0.04 230 / 0.11) 1px, transparent 1px),
    linear-gradient(90deg, oklch(0.55 0.04 230 / 0.11) 1px, transparent 1px);
  background-size: 68px 68px;
  mask-image: radial-gradient(circle at 50% 45%, black, transparent 76%);
}

.moyu-shell {
  width: min(1180px, 100%);
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(280px, 0.88fr) minmax(330px, 1.04fr) minmax(300px, 0.9fr);
  align-items: center;
  gap: clamp(20px, 3vw, 36px);
}

.story-card,
.signal-board {
  border: 1px solid oklch(0.76 0.045 224 / 0.5);
  background: oklch(0.99 0.014 220 / 0.78);
  box-shadow: 0 28px 74px oklch(0.5 0.09 230 / 0.18);
  backdrop-filter: blur(18px);
}

.story-card {
  min-height: 520px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: clamp(28px, 4vw, 48px);
  border-radius: 36px;
}

.moyu-kicker {
  width: fit-content;
  margin: 0 0 22px;
  padding: 9px 16px;
  border-radius: 999px;
  background: oklch(0.87 0.08 202 / 0.84);
  color: oklch(0.35 0.12 238);
  font-size: 13px;
  font-weight: 900;
}

h1 {
  margin: 0;
  max-width: 6.1em;
  font-size: clamp(56px, 7.4vw, 98px);
  line-height: 0.88;
  letter-spacing: 0;
  color: oklch(0.24 0.08 250);
}

.moyu-copy {
  margin: 28px 0 0;
  max-width: 39ch;
  color: oklch(0.42 0.045 246);
  font-size: 17px;
  line-height: 1.85;
}

.moyu-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 34px;
}

.moyu-actions button {
  min-height: 48px;
  border: 0;
  border-radius: 999px;
  padding: 0 20px;
  font: inherit;
  font-weight: 900;
  cursor: pointer;
  transition: transform 180ms ease, box-shadow 180ms ease, background 180ms ease;
}

.moyu-actions button:hover {
  transform: translateY(-2px);
}

.moyu-primary {
  background: linear-gradient(135deg, oklch(0.67 0.18 235), oklch(0.73 0.14 208));
  color: oklch(0.99 0.006 230);
  box-shadow: 0 18px 34px oklch(0.53 0.13 224 / 0.28);
}

.moyu-secondary {
  background: oklch(0.98 0.026 82);
  color: oklch(0.3 0.08 250);
  box-shadow: inset 0 0 0 1px oklch(0.78 0.055 84 / 0.8);
}

.moyu-ghost {
  background: oklch(0.99 0.014 220 / 0.52);
  color: oklch(0.42 0.05 245);
  box-shadow: inset 0 0 0 1px oklch(0.74 0.04 232 / 0.72);
}

.logo-stage {
  min-height: 570px;
  position: relative;
  display: grid;
  place-items: center;
}

.logo-frame {
  width: min(380px, 74vw);
  aspect-ratio: 1;
  position: relative;
  z-index: 2;
  overflow: hidden;
  border-radius: 42px;
  background: oklch(0.99 0.01 230);
  box-shadow:
    0 30px 76px oklch(0.5 0.11 230 / 0.22),
    0 0 0 12px oklch(0.99 0.012 220 / 0.72),
    0 0 80px oklch(0.77 0.11 205 / 0.38);
  transform: rotate(-2deg);
}

.logo-frame img {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 108%;
  height: 108%;
  display: block;
  object-fit: cover;
  transform: translate(-50%, -50%);
}

.orbit {
  position: absolute;
  border-radius: 50%;
  border: 1px dashed oklch(0.62 0.08 220 / 0.32);
}

.orbit--outer {
  width: min(560px, 90vw);
  aspect-ratio: 1;
  animation: orbitFloat 8s ease-in-out infinite;
}

.orbit--inner {
  width: min(390px, 66vw);
  aspect-ratio: 1;
  animation: orbitFloat 6s ease-in-out infinite reverse;
}

.floating-label {
  position: absolute;
  z-index: 3;
  padding: 12px 16px;
  border-radius: 999px;
  background: oklch(0.99 0.014 220 / 0.9);
  color: oklch(0.3 0.09 246);
  font-size: 14px;
  font-weight: 900;
  box-shadow: 0 16px 32px oklch(0.46 0.08 230 / 0.16);
}

.floating-label--left { left: 1%; bottom: 27%; }
.floating-label--right { right: 1%; top: 30%; }
.floating-label--bottom { right: 16%; bottom: 12%; }

.signal-board {
  width: min(340px, 100%);
  justify-self: center;
  padding: 22px;
  border-radius: 30px;
  display: grid;
  gap: 14px;
  align-self: center;
  transform: translateY(-6px);
}

.signal-card {
  min-width: 0;
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  column-gap: 13px;
  row-gap: 7px;
  align-items: start;
  min-height: 104px;
  padding: 18px 20px 19px;
  border-radius: 22px;
  background: oklch(0.96 0.018 226 / 0.72);
  box-shadow: inset 0 0 0 1px oklch(0.82 0.03 225 / 0.25);
}

.signal-card--blue { transform: translateX(3px) rotate(0.45deg); }
.signal-card--pink { transform: translateX(-6px) rotate(-0.45deg); }
.signal-card--mint { transform: translateX(4px) rotate(0.35deg); }

.signal-dot {
  grid-column: 1;
  grid-row: 1 / span 2;
  width: 11px;
  height: 11px;
  margin-top: 5px;
  border-radius: 50%;
  background: oklch(0.7 0.16 230);
}

.signal-card--pink .signal-dot { background: oklch(0.78 0.14 346); }
.signal-card--mint .signal-dot { background: oklch(0.78 0.12 174); }

.signal-card strong {
  grid-column: 2;
  display: block;
  min-width: 0;
  color: oklch(0.28 0.075 250);
  font-size: 16px;
  font-weight: 900;
  line-height: 1.32;
  letter-spacing: 0;
  text-wrap: balance;
}

.signal-card p {
  grid-column: 2;
  min-width: 0;
  max-width: 24ch;
  margin: 0;
  color: oklch(0.48 0.045 245);
  font-size: 13.75px;
  line-height: 1.68;
  letter-spacing: 0;
  white-space: normal;
  writing-mode: horizontal-tb;
  overflow-wrap: break-word;
}

.moyu-ambient span {
  position: absolute;
  pointer-events: none;
  z-index: 0;
}

.blob {
  width: 190px;
  aspect-ratio: 1;
  border-radius: 50%;
  filter: blur(4px);
  animation: drift 11s ease-in-out infinite;
}

.blob--a { left: 7%; top: 10%; background: radial-gradient(circle, oklch(0.82 0.1 205 / 0.42), transparent 66%); }
.blob--b { right: 8%; bottom: 9%; background: radial-gradient(circle, oklch(0.9 0.08 344 / 0.32), transparent 64%); animation-delay: -4s; }
.blob--c { left: 39%; bottom: -7%; background: radial-gradient(circle, oklch(0.86 0.1 184 / 0.32), transparent 66%); animation-delay: -7s; }

.fish {
  width: 38px;
  height: 18px;
  border-radius: 999px 70% 70% 999px;
  background: oklch(0.68 0.15 215 / 0.28);
  animation: swim 15s linear infinite;
}

.fish::after {
  content: '';
  position: absolute;
  right: -10px;
  top: 3px;
  width: 13px;
  height: 13px;
  background: inherit;
  clip-path: polygon(0 50%, 100% 0, 100% 100%);
}

.fish--a { left: -7%; top: 18%; }
.fish--b { left: -12%; top: 54%; animation-delay: -5s; transform: scale(0.78); }
.fish--c { left: -10%; top: 76%; animation-delay: -9s; transform: scale(1.12); }

.paw {
  width: 36px;
  height: 30px;
  border-radius: 52% 52% 48% 48%;
  background: oklch(0.82 0.11 348 / 0.22);
  transform: rotate(-16deg);
}

.paw::before,
.paw::after {
  content: '';
  position: absolute;
  top: -10px;
  width: 12px;
  height: 13px;
  border-radius: 50%;
  background: inherit;
}

.paw::before { left: 5px; }
.paw::after { right: 5px; }
.paw--a { left: 9%; bottom: 10%; }
.paw--b { right: 12%; top: 14%; transform: rotate(20deg); }
.paw--c { right: 8%; bottom: 20%; transform: rotate(-8deg); }

.spark {
  padding: 8px 12px;
  border-radius: 999px;
  background: oklch(0.99 0.014 220 / 0.58);
  color: oklch(0.55 0.11 220 / 0.62);
  font-size: 13px;
  font-weight: 900;
  animation: drift 12s ease-in-out infinite;
}

.spark--a { left: 42%; top: 18%; }
.spark--b { right: 20%; top: 22%; animation-delay: -5s; }
.spark--c { left: 16%; bottom: 24%; animation-delay: -8s; }

@keyframes swim {
  0% { translate: 0 0; opacity: 0; }
  10% { opacity: 1; }
  50% { translate: 52vw -14px; }
  90% { opacity: 1; }
  100% { translate: 112vw 8px; opacity: 0; }
}

@keyframes drift {
  0%, 100% { transform: translate3d(0, 0, 0); }
  50% { transform: translate3d(12px, -18px, 0); }
}

@keyframes orbitFloat {
  0%, 100% { transform: rotate(0deg) scale(1); opacity: 0.72; }
  50% { transform: rotate(4deg) scale(1.035); opacity: 1; }
}

@media (max-width: 980px) {
  .moyu-shell {
    grid-template-columns: 1fr;
    gap: 18px;
  }
  .logo-stage {
    order: -1;
    min-height: 380px;
  }
  .story-card,
  .signal-board {
    width: min(680px, 100%);
    justify-self: center;
  }
  .story-card { min-height: auto; }
  h1 { max-width: 8em; }
  .signal-card { transform: none; }
}

@media (max-width: 560px) {
  .moyu-page { padding: 16px; }
  .moyu-top-actions {
    position: absolute;
    top: 14px;
    right: 14px;
    left: 14px;
    justify-content: center;
  }
  .moyu-top-btn {
    min-height: 38px;
    padding: 0 13px;
    font-size: 12px;
  }
  .story-card { padding: 24px; border-radius: 28px; }
  .logo-stage { min-height: 300px; }
  .logo-frame { width: min(245px, 72vw); border-radius: 34px; }
  .floating-label { display: none; }
  .moyu-actions { flex-direction: column; }
  .moyu-actions button { width: 100%; }
}
</style>
