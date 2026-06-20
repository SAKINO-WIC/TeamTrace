const RUNTIME_MODES = ['live', 'hybrid', 'mock']

export function getRuntimeMode() {
  const raw = String(import.meta.env.VITE_APP_RUNTIME || 'live')
    .trim()
    .toLowerCase()

  return RUNTIME_MODES.includes(raw) ? raw : 'hybrid'
}

export function getRuntimeLabel(mode = getRuntimeMode()) {
  const map = {
    live: '真实联调',
    hybrid: '混合联调',
    mock: '本地演示',
  }

  return map[mode] || '混合联调'
}

export function getRuntimeDescription(mode = getRuntimeMode()) {
  const map = {
    live: '所有请求都直连真实接口，接口失败会直接中断当前操作。',
    hybrid: '优先请求真实接口；接口不可用时自动回退为本地演示数据，方便继续开发页面和交互。',
    mock: '所有请求都使用本地演示数据，不依赖后端服务。',
  }

  return map[mode] || map.hybrid
}

function wrapMockResponse(data, meta = {}) {
  return {
    data: {
      code: 0,
      message: meta.message || 'ok',
      data,
      meta: {
        source: 'mock',
        runtimeMode: getRuntimeMode(),
        ...meta,
      },
    },
  }
}

function attachApiMeta(response) {
  if (response?.data && typeof response.data === 'object') {
    response.data.meta = {
      ...(response.data.meta || {}),
      source: 'api',
      runtimeMode: getRuntimeMode(),
    }
  }

  return response
}

export async function resolveRequest(apiCall, mockFactory, options = {}) {
  const mode = getRuntimeMode()

  if (mode === 'mock') {
    const mockData = await mockFactory(null)
    return wrapMockResponse(mockData, {
      feature: options.feature,
      fallbackReason: 'mock_mode',
    })
  }

  try {
    const response = await apiCall()
    return attachApiMeta(response)
  } catch (error) {
    if (mode !== 'hybrid') {
      throw error
    }

    const mockData = await mockFactory(error)
    return wrapMockResponse(mockData, {
      feature: options.feature,
      fallbackReason: 'api_error',
      errorMessage: error?.message || '',
    })
  }
}
