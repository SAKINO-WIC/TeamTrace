import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const isDev = mode === 'development'
  // 与后端当前默认监听端口保持一致；未设置时回退到 8082
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8082'

  return {
    plugins: [
      vue(),
      isDev && vueDevTools(),
    ],
    server: {
      // 避免仅监听 [::1] 导致浏览器访问 localhost(127.0.0.1) 连不上
      host: '0.0.0.0',
      allowedHosts: [
        'swerabzvduak.sealoshzh.site',
        'swcbvwaoxphg.sealoshzh.site',
        'hlqlocaewxgt.sealoshzh.site',
      ],
      cors: {
        origin: [
          'http://localhost:5173',
          'http://127.0.0.1:5173',
          'https://swerabzvduak.sealoshzh.site',
          'https://swcbvwaoxphg.sealoshzh.site',
          'https://hlqlocaewxgt.sealoshzh.site',
        ],
        methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
        allowedHeaders: ['Content-Type', 'Authorization'],
      },
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
        },
        '/uploads': {
          target: apiProxyTarget,
          changeOrigin: true,
        },
      },
    },
    preview: {
      host: '0.0.0.0',
      port: 8086,
      allowedHosts: true,
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      },
    },
    build: {
      // 不把 pdf-export 列入首屏 modulepreload（此前导致登录页也下载 ~675KB）
      modulePreload: {
        resolveDependencies(_filename, deps) {
          return deps.filter((dep) => !dep.includes('pdf-export'))
        },
      },
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return
            // 不单独拆 pdf-export，避免 Vite 动态 import 辅助函数落入该 chunk 导致 Shell 误加载 675KB
            if (id.includes('vue-router')) return 'vue-router'
            if (id.includes('/vue/') || id.includes('@vue/')) return 'vue'
          },
        },
      },
    },
  }
})
